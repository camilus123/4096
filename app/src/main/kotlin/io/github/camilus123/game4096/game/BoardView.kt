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
 *  - frames are requested only while something is moving, so at Low and Medium quality a
 *    resting board costs nothing (High keeps a slow ambient animation, see [Quality]);
 *  - animation time comes from the Choreographer's vsync timestamp, not the wall clock,
 *    so motion stays even at 60, 90 and 120 Hz.
 *
 * A move is applied to the [Game] the moment the swipe is recognised; the view then animates
 * from the board before the move to the board after it. The slide and pop take the same time
 * at every quality level.
 */
@SuppressLint("ViewConstructor")
class BoardView(context: Context) : View(context), Choreographer.FrameCallback {

    /** Called after each move that changed the board (not per frame). */
    var onMoved: (() -> Unit)? = null

    /**
     * Asked before every move, including queued ones. Returning false refuses the move.
     * This is how a clock or a move budget stops play at exactly the right moment.
     */
    var moveGate: (() -> Boolean)? = null

    var inputEnabled: Boolean = true

    /** False for the opponent's board in Versus: it only shows moves, it takes no input. */
    var interactive: Boolean = true
        set(value) {
            field = value
            isFocusable = value
            isFocusableInTouchMode = value
        }

    var palette: BoardPalette = BoardPalette.LIGHT
        set(value) {
            if (field !== value) {
                field = value
                invalidate()
            }
        }

    var quality: Quality = Quality.LOW
        set(value) {
            if (field != value) {
                field = value
                if (value != Quality.HIGH) particleCount = 0
                invalidate()
                postFrameIfNeeded()
            }
        }

    /**
     * Lets a board that takes no touches still run High quality's ambient motion. The
     * performance test needs this; the opponent's small board in Versus does not want it.
     */
    var ambientWhenPassive: Boolean = false

    private var game: Game? = null
    private var size = 4

    // Board before and after the move being animated, and the move's trace. Sized for the largest grid.
    private val prev = IntArray(MAX_CELLS)
    private val cur = IntArray(MAX_CELLS)
    private val dest = IntArray(MAX_CELLS)
    private val merged = BooleanArray(MAX_CELLS)
    private var spawnIndex = -1
    private var shownVersion = 0

    /** True while tiles are moving. */
    var isAnimating = false
        private set
    private var intro = false // every tile scales in (new game)
    private var animStartNanos = -1L
    private var animMs = 0f
    private var framePosted = false
    private var lastFrameNanos = 0L

    private val queue = IntArray(QUEUE_SIZE)
    private var queued = 0

    // High quality: merge particles, kept in fixed arrays.
    private val particleX = FloatArray(MAX_PARTICLES)
    private val particleY = FloatArray(MAX_PARTICLES)
    private val particleVx = FloatArray(MAX_PARTICLES)
    private val particleVy = FloatArray(MAX_PARTICLES)
    private val particleAge = FloatArray(MAX_PARTICLES) // milliseconds
    private val particleColor = IntArray(MAX_PARTICLES)
    private var particleCount = 0
    private var burstPending = false
    private var randomState = 0x2545F491

    // High quality: time for the glow pulse and the drifting lights.
    private var ambientSeconds = 0f
    private var lastAmbientDrawNanos = 0L
    private var windowVisible = false

    // Geometry, recomputed on size change.
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSide = 0f
    private var cell = 0f
    private var gap = 0f
    private var tileRadius = 0f
    private var boardRadius = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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

    /** True if the view will keep asking for frames: tiles moving, particles flying, or High quality's ambient motion. */
    val wantsFrames: Boolean
        get() = isAnimating || particleCount > 0 || ambientActive()

    private fun ambientActive(): Boolean =
        ambientMotionAllowed && quality == Quality.HIGH && (interactive || ambientWhenPassive) && windowVisible && game != null && boardSide > 0f

    /** Shows [newGame]. Passing the game already shown does nothing. */
    fun bind(newGame: Game?) {
        if (newGame === game) return
        game = newGame
        queued = 0
        isAnimating = false
        particleCount = 0
        burstPending = false
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

    /**
     * Redraws from the game after it changed without a swipe (an undo). [version] is any
     * counter that changes when that happens; repeating the same value does nothing.
     */
    fun showVersion(version: Int) {
        if (version == shownVersion) return
        shownVersion = version
        val g = game ?: return
        g.copyCellsInto(cur)
        queued = 0
        intro = false
        isAnimating = false
        burstPending = false
        invalidate()
    }

    /**
     * Animates the move that was just made on the game by someone else (the opponent's AI).
     * Call once after each such move. If a move was missed the board simply snaps to the
     * current state.
     */
    fun showLatestMove() {
        val g = game ?: return
        val n = g.cellCount
        System.arraycopy(cur, 0, prev, 0, n)
        g.copyCellsInto(cur)
        System.arraycopy(g.lastTrace.dest, 0, dest, 0, n)
        System.arraycopy(g.lastTrace.merged, 0, merged, 0, n)
        spawnIndex = g.lastSpawnIndex
        queued = 0
        intro = false
        // The trace describes one move from the board we were showing. If it does not fit
        // (a tile it moves is not where we drew it), skip the animation.
        var fits = true
        for (i in 0 until n) {
            if ((prev[i] != 0) != (dest[i] >= 0)) {
                fits = false
                break
            }
        }
        if (fits) startAnimation() else {
            isAnimating = false
            invalidate()
        }
    }

    /** Makes a move as if the player had swiped. For the performance test and for keyboards. */
    fun swipe(dir: Direction) {
        if (!inputEnabled || game == null) return
        if (isAnimating && !intro && animMs < SLIDE_MS) {
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

    // ---- Input -------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
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

    private fun applyMove(dir: Direction): Boolean {
        val g = game ?: return false
        val gate = moveGate
        if (gate != null && !gate()) {
            queued = 0
            return false
        }
        g.copyCellsInto(prev)
        if (g.move(dir) == Rules.NO_MOVE) return false
        g.copyCellsInto(cur)
        val n = g.cellCount
        System.arraycopy(g.lastTrace.dest, 0, dest, 0, n)
        System.arraycopy(g.lastTrace.merged, 0, merged, 0, n)
        spawnIndex = g.lastSpawnIndex
        intro = false
        burstPending = quality == Quality.HIGH
        startAnimation()
        onMoved?.invoke()
        return true
    }

    // ---- Animation ---------------------------------------------------------------------

    private fun startAnimation() {
        isAnimating = true
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

    private fun postFrameIfNeeded() {
        if (wantsFrames) postFrame()
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePosted = false
        // Time since the previous frame, for particles and ambient motion. Capped so a pause
        // (or the first frame after resting) does not make things jump.
        var deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
        if (deltaMs < 0f || deltaMs > 50f) deltaMs = 16f
        lastFrameNanos = frameTimeNanos

        var redraw = false
        if (isAnimating) {
            if (animStartNanos < 0L) animStartNanos = frameTimeNanos
            animMs = (frameTimeNanos - animStartNanos) / 1_000_000f
            if (burstPending && animMs >= SLIDE_MS) {
                burstPending = false
                emitMergeParticles()
            }
            if (queued > 0 && (intro || animMs >= SLIDE_MS)) {
                // The slide is done: start the next queued swipe now and skip the rest of the pop.
                isAnimating = false
                while (queued > 0) {
                    val dir = DIRECTIONS[queue[0]]
                    for (i in 1 until queued) queue[i - 1] = queue[i]
                    queued--
                    if (applyMove(dir)) break
                }
            } else if (animMs >= (if (intro) POP_MS else SLIDE_MS + POP_MS)) {
                isAnimating = false
                intro = false
            }
            redraw = true
        }
        if (particleCount > 0) {
            stepParticles(deltaMs)
            redraw = true
        }
        if (ambientActive()) {
            ambientSeconds += deltaMs / 1000f
            // Ambient motion is slow: when it is the only thing moving, 30 frames a second is plenty.
            if (!redraw && frameTimeNanos - lastAmbientDrawNanos >= AMBIENT_FRAME_NANOS) redraw = true
        }
        if (redraw) {
            lastAmbientDrawNanos = frameTimeNanos
            invalidate()
        }
        if (wantsFrames) postFrame()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postFrameIfNeeded()
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(this)
        framePosted = false
        // Nothing can drive the animation while detached; settle on the final board.
        isAnimating = false
        intro = false
        queued = 0
        particleCount = 0
        burstPending = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Ambient motion must not run while the app is in the background.
        windowVisible = visibility == VISIBLE
        postFrameIfNeeded()
    }

    // ---- Particles (High quality) --------------------------------------------------------

    /** Tiny xorshift generator: random enough for sparkles, and it never allocates. */
    private fun nextRandom(): Float {
        var x = randomState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        randomState = x
        return (x ushr 8) / 16_777_216f
    }

    private fun emitMergeParticles() {
        val n = size * size
        val p = palette
        for (i in 0 until n) {
            if (!merged[i]) continue
            val cx = cellX(i) + cell / 2f
            val cy = cellY(i) + cell / 2f
            val color = p.tile(cur[i]) and 0x00FFFFFF
            // Bigger tiles throw more sparks.
            val count = min(6 + cur[i], 16)
            for (k in 0 until count) {
                if (particleCount == MAX_PARTICLES) return
                val angle = nextRandom() * 2f * PI.toFloat()
                val speed = cell * (0.0026f + 0.0038f * nextRandom()) // pixels per millisecond
                val j = particleCount++
                particleX[j] = cx
                particleY[j] = cy
                particleVx[j] = kotlin.math.cos(angle) * speed
                particleVy[j] = sin(angle) * speed
                particleAge[j] = 0f
                particleColor[j] = color
            }
        }
    }

    private fun stepParticles(deltaMs: Float) {
        var i = 0
        while (i < particleCount) {
            particleAge[i] += deltaMs
            if (particleAge[i] >= PARTICLE_LIFE_MS) {
                // Remove by moving the last particle into this slot.
                val last = --particleCount
                particleX[i] = particleX[last]
                particleY[i] = particleY[last]
                particleVx[i] = particleVx[last]
                particleVy[i] = particleVy[last]
                particleAge[i] = particleAge[last]
                particleColor[i] = particleColor[last]
            } else {
                particleX[i] += particleVx[i] * deltaMs
                particleY[i] += particleVy[i] * deltaMs
                // Air resistance: sparks shoot out and slow down.
                val drag = 1f - 0.004f * deltaMs
                particleVx[i] *= drag
                particleVy[i] *= drag
                i++
            }
        }
    }

    // ---- Layout and drawing ------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
        postFrameIfNeeded()
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
        val q = quality
        val n = size * size

        if (q == Quality.HIGH && (interactive || ambientWhenPassive)) drawMotes(canvas, p)

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

        if (isAnimating && !intro && animMs < SLIDE_MS) {
            // Slide phase: every tile of the old board travels to its destination.
            val t = easeOut(animMs / SLIDE_MS)
            for (i in 0 until n) {
                val e = prev[i]
                if (e == 0) continue
                val d = dest[i]
                val x0 = cellX(i)
                val y0 = cellY(i)
                drawTile(canvas, p, q, x0 + (cellX(d) - x0) * t, y0 + (cellY(d) - y0) * t, e, 1f, i)
            }
        } else {
            // Pop phase (or at rest): the new board, with merged tiles bumping and new ones growing.
            val popping = isAnimating
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
                        // Medium and High let the new tile overshoot slightly and settle.
                        scale = 0.25f + 0.75f * (if (q == Quality.LOW) easeOut(t) else easeOutBack(t))
                    } else if (merged[i]) {
                        scale = 1f + 0.13f * sin(PI.toFloat() * t)
                    }
                }
                drawTile(canvas, p, q, cellX(i), cellY(i), e, scale, i)
            }
            if (popping && !intro && q != Quality.LOW) drawMergeRings(canvas, p, n, t)
        }
        if (particleCount > 0) drawParticles(canvas)
    }

    private fun drawTile(canvas: Canvas, p: BoardPalette, q: Quality, x: Float, y: Float, exponent: Int, scale: Float, index: Int) {
        val scaled = scale != 1f
        if (scaled) {
            canvas.save()
            canvas.scale(scale, scale, x + cell / 2f, y + cell / 2f)
        }
        val color = p.tile(exponent)
        if (q != Quality.LOW) {
            if (q == Quality.HIGH && exponent >= GLOW_EXPONENT && (interactive || ambientWhenPassive)) {
                // A soft halo made of three translucent, slightly larger copies of the tile.
                // Each tile pulses on its own phase so the board does not throb in unison.
                val pulse = 0.55f + 0.45f * sin(ambientSeconds * 2.2f + index * 1.7f)
                val rgb = color and 0x00FFFFFF
                for (k in 3 downTo 1) {
                    val grow = cell * 0.035f * k
                    fill.color = ((72f / k * pulse).toInt() shl 24) or rgb
                    canvas.drawRoundRect(x - grow, y - grow, x + cell + grow, y + cell + grow, tileRadius + grow, tileRadius + grow, fill)
                }
            }
            val drop = cell * 0.045f
            fill.color = p.shadow
            canvas.drawRoundRect(x, y + drop, x + cell, y + cell + drop, tileRadius, tileRadius, fill)
        }
        fill.color = color
        canvas.drawRoundRect(x, y, x + cell, y + cell, tileRadius, tileRadius, fill)
        val label = LABELS[if (exponent < LABELS.size) exponent else LABELS.size - 1]
        val digits = if (label.length > MAX_DIGITS) MAX_DIGITS else label.length
        text.color = p.text(exponent)
        text.textSize = textSizes[digits]
        canvas.drawText(label, x + cell / 2f, y + cell / 2f + textBaselines[digits], text)
        if (scaled) canvas.restore()
    }

    /** Medium and High: a ring in the tile's colour that expands from each merge and fades. */
    private fun drawMergeRings(canvas: Canvas, p: BoardPalette, n: Int, t: Float) {
        val alpha = (150f * (1f - t)).toInt()
        if (alpha <= 0) return
        stroke.strokeWidth = cell * 0.06f * (1f - t) + 1f
        val grow = cell * 0.2f * easeOut(t)
        for (i in 0 until n) {
            if (!merged[i] || cur[i] == 0) continue
            val x = cellX(i)
            val y = cellY(i)
            stroke.color = (alpha shl 24) or (p.tile(cur[i]) and 0x00FFFFFF)
            canvas.drawRoundRect(x - grow, y - grow, x + cell + grow, y + cell + grow, tileRadius + grow, tileRadius + grow, stroke)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        val radius = cell * 0.055f
        for (i in 0 until particleCount) {
            val life = 1f - particleAge[i] / PARTICLE_LIFE_MS
            fill.color = ((230f * life).toInt() shl 24) or particleColor[i]
            canvas.drawCircle(particleX[i], particleY[i], radius * (0.5f + 0.5f * life), fill)
        }
    }

    /** High: a handful of large, faint lights drifting slowly behind the board. */
    private fun drawMotes(canvas: Canvas, p: BoardPalette) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = ambientSeconds
        for (k in 0 until MOTES) {
            // Fixed, different speed and path per light; positions wrap around the view.
            val fx = fraction(MOTE_SEED_X[k] + t * MOTE_SPEED_X[k])
            val fy = fraction(MOTE_SEED_Y[k] + t * MOTE_SPEED_Y[k])
            val radius = density * (26f + 22f * MOTE_SEED_Y[k])
            val alpha = (13f + 9f * sin(t * 0.7f + k)).toInt()
            fill.color = (alpha shl 24) or p.mote
            canvas.drawCircle(fx * (w + 2f * radius) - radius, fy * (h + 2f * radius) - radius, radius, fill)
        }
    }

    private fun fraction(v: Float): Float {
        val f = v - v.toInt()
        return if (f < 0f) f + 1f else f
    }

    private fun easeOut(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    /** Like [easeOut] but goes a little past 1 before settling. */
    private fun easeOutBack(t: Float): Float {
        val u = t - 1f
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u
    }

    companion object {
        /**
         * Switch for High quality's never-ending ambient motion. Always true in the app. Tests
         * turn it off, because a test framework that runs the main thread "until nothing is
         * left to do" would otherwise never finish; the glow and the lights are then drawn still.
         */
        @Volatile internal var ambientMotionAllowed = true

        private const val MAX_CELLS = Rules.MAX_SIZE * Rules.MAX_SIZE
        private const val QUEUE_SIZE = 2
        private const val SLIDE_MS = 110f
        private const val POP_MS = 143f
        private const val MAX_DIGITS = 7

        private const val MAX_PARTICLES = 192
        private const val PARTICLE_LIFE_MS = 420f
        private const val GLOW_EXPONENT = 7 // tiles of 128 and up glow at High quality
        private const val AMBIENT_FRAME_NANOS = 33_000_000L
        private const val MOTES = 9
        private val MOTE_SEED_X = floatArrayOf(0.05f, 0.31f, 0.62f, 0.88f, 0.17f, 0.46f, 0.73f, 0.94f, 0.55f)
        private val MOTE_SEED_Y = floatArrayOf(0.12f, 0.71f, 0.33f, 0.90f, 0.48f, 0.05f, 0.82f, 0.27f, 0.60f)
        private val MOTE_SPEED_X = floatArrayOf(0.011f, -0.008f, 0.006f, -0.012f, 0.009f, -0.005f, 0.007f, 0.010f, -0.009f)
        private val MOTE_SPEED_Y = floatArrayOf(-0.006f, 0.009f, -0.011f, 0.005f, 0.008f, -0.010f, 0.006f, -0.007f, 0.012f)

        private val DIRECTIONS = Direction.entries.toTypedArray()

        /** Tile labels by exponent, built once so drawing never formats numbers. */
        private val LABELS: Array<String> = Array(41) { if (it == 0) "" else (1L shl it).toString() }

        /** Text height as a share of the cell, by number of digits. */
        private val TEXT_SCALE = floatArrayOf(0f, 0.52f, 0.46f, 0.38f, 0.30f, 0.245f, 0.205f, 0.175f)
    }
}
