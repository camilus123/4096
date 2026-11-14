package io.github.camilus123.game4096.game

import android.graphics.Color

/** Colours for the custom-drawn board. Tile arrays are indexed by exponent (1 is the "2" tile). */
class BoardPalette(
    val board: Int,
    val emptyCell: Int,
    private val tiles: IntArray,
    private val darkText: Int,
    private val lightText: Int,
    /** Translucent colour of the drop shadow under tiles (Medium and High quality). */
    val shadow: Int,
    /** RGB of the drifting background lights (High quality); the alpha is set when drawing. */
    val mote: Int,
    /** Which tiles get the dark label. Defaults to "the bright ones". */
    usesDarkText: (exponent: Int, color: Int) -> Boolean = { _, color -> luminance(color) > 0.55f },
) {
    private val text = IntArray(tiles.size) { if (usesDarkText(it, tiles[it])) darkText else lightText }

    fun tile(exponent: Int): Int = tiles[if (exponent < tiles.size) exponent else tiles.size - 1]

    fun text(exponent: Int): Int = text[if (exponent < text.size) exponent else text.size - 1]

    companion object {
        private fun luminance(c: Int): Float =
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

        private fun c(hex: Long): Int = hex.toInt()

        /** Warm classic look for the light theme. */
        val LIGHT = BoardPalette(
            board = c(0xFFC2A68A),
            emptyCell = c(0xFFD8C3A8),
            tiles = intArrayOf(
                0,
                c(0xFFF7E8CF), // 2
                c(0xFFF6DCA8), // 4
                c(0xFFFFA655), // 8
                c(0xFFFF8840), // 16
                c(0xFFFF6845), // 32
                c(0xFFF84526), // 64
                c(0xFFF8D058), // 128
                c(0xFFF8CA40), // 256
                c(0xFFF9C428), // 512
                c(0xFFFABD10), // 1024
                c(0xFFFFB700), // 2048
                c(0xFFEC3F78), // 4096
                c(0xFFD02A6E), // 8192
                c(0xFF9B3EC6), // 16384
                c(0xFF5C46D2), // 32768
                c(0xFF2474D4), // 65536
                c(0xFF3B352E), // beyond
            ),
            darkText = c(0xFF6E5F52),
            lightText = c(0xFFFFFAF2),
            shadow = c(0x38604020),
            mote = c(0x00F2A04A),
            // Brown on the pale tiles and the yellows, white on the oranges, reds and beyond.
            usesDarkText = { exponent, _ -> exponent <= 2 || exponent in 7..11 },
        )

        /** Dark neon look for the dark theme. */
        val DARK = BoardPalette(
            board = c(0xFF161A2C),
            emptyCell = c(0xFF20253D),
            tiles = intArrayOf(
                0,
                c(0xFF2C3460), // 2
                c(0xFF37427F), // 4
                c(0xFF0E9FB0), // 8
                c(0xFF00BFA6), // 16
                c(0xFF2BC96D), // 32
                c(0xFF9AD42C), // 64
                c(0xFFF2CC00), // 128
                c(0xFFFF9D1B), // 256
                c(0xFFFF5D5A), // 512
                c(0xFFFF2D90), // 1024
                c(0xFFC338FF), // 2048
                c(0xFF7C4DFF), // 4096
                c(0xFF3D8BFF), // 8192
                c(0xFF19D3FF), // 16384
                c(0xFF7CFFCB), // 32768
                c(0xFFE8FF6A), // 65536
                c(0xFFFFFFFF), // beyond
            ),
            darkText = c(0xFF0C0E19),
            lightText = c(0xFFE6EAFF),
            shadow = c(0x66000000),
            mote = c(0x007C6BFF),
        )
    }
}
