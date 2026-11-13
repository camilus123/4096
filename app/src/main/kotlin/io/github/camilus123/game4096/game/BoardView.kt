package io.github.camilus123.game4096.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.camilus123.game4096.core.Direction
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.Rules
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The game board, drawn by hand on a Canvas.
 *
 * Frame-pacing rules this class keeps:
 *  - nothing is allocated in [onDraw], [doFrame] or the touch path;
 *  - frames are requested only while something is animating, so a resting board costs nothing;
 *  - animation time comes from the Choreographer's vsync timestamp, not the wall clock,
 *    so motion stays even at 60, 90 and 120 Hz.
 *
 * A move is applied to the [Game] the moment the swipe is recognised; the view then animates
 * from the board before the move to the board after it.
 */
@SuppressLint("ViewConstructor")
class BoardView(context: Context) : View(context), Choreographer.FrameCallback {

    /** Called after each move that changed the board (not per frame). */
    var onMoved: (() -> Unit)? = null

    var inputEnabled: Boolean = true

    var palette: BoardPalette = BoardPalette.LIGHT
        set(value) {
            if (field !== value) {
                field = value
                invalidate()
            }
        }

    private var game: Game? = null
    private var size = 4

    // Board before and after the move being animated, and the move's trace. Sized for the largest grid.
    private val prev = IntArray(MAX_CELLS)
    private val cur = IntArray(MAX_CELLS)
    private val dest = IntArray(MAX_CELLS)
    private val merged = BooleanArray(MAX_CELLS)
    private var spawnIndex = -1

    private var animating = false
    private var intro = false // every tile scales in (new game)
    private var animStartNanos = -1L
    private var animMs = 0f
    private var framePosted = false

    private val queue = IntArray(QUEUE_SIZE)
    private var queued = 0

    // Geometry, recomputed on size change.
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSide = 0f
    private var cell = 0f
    private var gap = 0f
    private var tileRadius = 0f
    private var boardRadius = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val textSizes = FloatArray(MAX_DIGITS + 1)
    private val textBaselines = FloatArray(MAX_DIGITS + 1)

    // Touch
    private val density = resources.displayMetrics.density
    private val swipeDistance = max(ViewConfiguration.get(context).scaledTouchSlop * 1.6f, 16f * density)
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    private val choreographer = Choreographer.getInstance()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /** Shows [newGame]. Passing the game already shown does nothing. */
    fun bind(newGame: Game?) {
        if (newGame === game) return
        game = newGame
        queued = 0
        animating = false
        if (newGame != null) {
            require(newGame.cellCount <= MAX_CELLS) { "grid too large for BoardView" }
            size = newGame.size
            newGame.copyCellsInto(cur)
            computeGeometry()
            intro = true
            startAnimation()
        }
        invalidate()
    }

    // ---- Input -------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tracking = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                val dx = event.x - downX
                val dy = event.y - downY
                val ax = abs(dx)
                val ay = abs(dy)
                // Fire as soon as the finger has clearly committed to a direction, not on lift.
                if (max(ax, ay) >= swipeDistance && max(ax, ay) > 1.4f * min(ax, ay)) {
                    val dir = if (ax > ay) {
                        if (dx > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (dy > 0) Direction.DOWN else Direction.UP
                    }
                    swipe(dir)
                    // Re-arm from here so a second swipe needs no lift.
                    downX = event.x
                    downY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Direction.RIGHT
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Direction.DOWN
            else -> return super.onKeyDown(keyCode, event)
        }
        swipe(dir)
        return true
    }

    private fun swipe(dir: Direction) {
        if (!inputEnabled || game == null) return
        if (animating && !intro && animMs < SLIDE_MS) {
            // Tiles are still sliding: remember the swipe instead of dropping it.
            if (queued < QUEUE_SIZE) {
                queue[queued++] = dir.ordinal
            } else {
                queue[QUEUE_SIZE - 1] = dir.ordinal
            }
        } else {
            applyMove(dir)
        }
    }

    private fun applyMove(dir: Direction): Boolean {
        val g = game ?: return false
        g.copyCellsInto(prev)
        if (g.move(dir) == Rules.NO_MOVE) return false
        g.copyCellsInto(cur)
        val n = g.cellCount
        System.arraycopy(g.lastTrace.dest, 0, dest, 0, n)
        System.arraycopy(g.lastTrace.merged, 0, merged, 0, n)
        spawnIndex = g.lastSpawnIndex
        intro = false
        startAnimation()
        onMoved?.invoke()
        return true
    }

    // ---- Animation ---------------------------------------------------------------------

    private fun startAnimation() {
        animating = true
        animStartNanos = -1L
        animMs = 0f
        postFrame()
        invalidate()
    }

    private fun postFrame() {
        if (!framePosted && isAttachedToWindow) {
            framePosted = true
            choreographer.postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePosted = false
        if (!animating) return
        if (animStartNanos < 0L) animStartNanos = frameTimeNanos
        animMs = (frameTimeNanos - animStartNanos) / 1_000_000f

        if (queued > 0 && (intro || animMs >= SLIDE_MS)) {
            // The slide is done: start the next queued swipe now and skip the rest of the pop.
            animating = false
            while (queued > 0) {
                val dir = DIRECTIONS[queue[0]]
                for (i in 1 until queued) queue[i - 1] = queue[i]
                queued--
                if (applyMove(dir)) break
            }
        } else if (animMs >= (if (intro) POP_MS else SLIDE_MS + POP_MS)) {
            animating = false
            intro = false
        }
        invalidate()
        if (animating) postFrame()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animating) postFrame()
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(this)
        framePosted = false
        // Nothing can drive the animation while detached; settle on the final board.
        animating = false
        intro = false
        queued = 0
        super.onDetachedFromWindow()
    }

    // ---- Layout and drawing ------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        boardSide = min(w, h)
        boardLeft = (w - boardSide) / 2f
        boardTop = (h - boardSide) / 2f
        gap = boardSide * 0.11f / size
        cell = (boardSide - gap * (size + 1)) / size
        tileRadius = cell * 0.09f
        boardRadius = boardSide * 0.022f
        for (digits in 1..MAX_DIGITS) {
            val ts = cell * TEXT_SCALE[digits]
            textSizes[digits] = ts
            text.textSize = ts
            val fm = text.fontMetrics
            textBaselines[digits] = -(fm.ascent + fm.descent) / 2f
        }
    }

    private fun cellX(index: Int): Float = boardLeft + gap + (index % size) * (cell + gap)

    private fun cellY(index: Int): Float = boardTop + gap + (index / size) * (cell + gap)

    override fun onDraw(canvas: Canvas) {
        if (boardSide <= 0f) return
        val p = palette
        val n = size * size

        fill.color = p.board
        canvas.drawRoundRect(
            boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide,
            boardRadius, boardRadius, fill,
        )
        fill.color = p.emptyCell
        for (i in 0 until n) {
            val x = cellX(i)
            val y = cellY(i)
            canvas.drawRoundRect(x, y, x + cell, y + cell, tileRadius, tileRadius, fill)
        }
        if (game == null) return

        if (animating && !intro && animMs < SLIDE_MS) {
            // Slide phase: every tile of the old board travels to its destination.
            val t = easeOut(animMs / SLIDE_MS)
            for (i in 0 until n) {
                val e = prev[i]
                if (e == 0) continue
                val d = dest[i]
                val x0 = cellX(i)
                val y0 = cellY(i)
                drawTile(canvas, p, x0 + (cellX(d) - x0) * t, y0 + (cellY(d) - y0) * t, e, 1f)
            }
        } else {
            // Pop phase (or at rest): the new board, with merged tiles bumping and new ones growing.
            val popping = animating
            val t = if (!popping) 1f else {
                val raw = (if (intro) animMs else animMs - SLIDE_MS) / POP_MS
                if (raw < 0f) 0f else if (raw > 1f) 1f else raw
            }
            for (i in 0 until n) {
                val e = cur[i]
                if (e == 0) continue
                var scale = 1f
                if (popping) {
                    if (intro || i == spawnIndex) {
                        scale = 0.25f + 0.75f * easeOut(t)
                    } else if (merged[i]) {
                        scale = 1f + 0.13f * sin(PI.toFloat() * t)
                    }
                }
                drawTile(canvas, p, cellX(i), cellY(i), e, scale)
            }
        }
    }

    private fun drawTile(canvas: Canvas, p: BoardPalette, x: Float, y: Float, exponent: Int, scale: Float) {
        val scaled = scale != 1f
        if (scaled) {
            canvas.save()
            canvas.scale(scale, scale, x + cell / 2f, y + cell / 2f)
        }
        fill.color = p.tile(exponent)
        canvas.drawRoundRect(x, y, x + cell, y + cell, tileRadius, tileRadius, fill)
        val label = LABELS[if (exponent < LABELS.size) exponent else LABELS.size - 1]
        val digits = if (label.length > MAX_DIGITS) MAX_DIGITS else label.length
        text.color = p.text(exponent)
        text.textSize = textSizes[digits]
        canvas.drawText(label, x + cell / 2f, y + cell / 2f + textBaselines[digits], text)
        if (scaled) canvas.restore()
    }

    private fun easeOut(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    private companion object {
        const val MAX_CELLS = Rules.MAX_SIZE * Rules.MAX_SIZE
        const val QUEUE_SIZE = 2
        const val SLIDE_MS = 100f
        const val POP_MS = 130f
        const val MAX_DIGITS = 7

        val DIRECTIONS = Direction.entries.toTypedArray()

        /** Tile labels by exponent, built once so drawing never formats numbers. */
        val LABELS: Array<String> = Array(41) { if (it == 0) "" else (1L shl it).toString() }

        /** Text height as a share of the cell, by number of digits. */
        val TEXT_SCALE = floatArrayOf(0f, 0.52f, 0.46f, 0.38f, 0.30f, 0.245f, 0.205f, 0.175f)
    }
}
