package io.github.camilus123.game4096.game

import android.graphics.Color

/** Colours for the custom-drawn board. Tile arrays are indexed by exponent (1 is the "2" tile). */
class BoardPalette(
    val board: Int,
    val emptyCell: Int,
    private val tiles: IntArray,
    private val darkText: Int,
    private val lightText: Int,
) {
    private val text = IntArray(tiles.size) { if (luminance(tiles[it]) > 0.55f) darkText else lightText }

    fun tile(exponent: Int): Int = tiles[if (exponent < tiles.size) exponent else tiles.size - 1]

    fun text(exponent: Int): Int = text[if (exponent < text.size) exponent else text.size - 1]

    companion object {
        private fun luminance(c: Int): Float =
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

        private fun c(hex: Long): Int = hex.toInt()

        /** Warm classic look for the light theme. */
        val LIGHT = BoardPalette(
            board = c(0xFFB9AB9D),
            emptyCell = c(0xFFCBBFB1),
            tiles = intArrayOf(
                0,
                c(0xFFEDE3D6), // 2
                c(0xFFEBDDC2), // 4
                c(0xFFF3A96C), // 8
                c(0xFFF58F58), // 16
                c(0xFFF4775A), // 32
                c(0xFFF2603F), // 64
                c(0xFFECCB6A), // 128
                c(0xFFECC85A), // 256
                c(0xFFECC44A), // 512
                c(0xFFECC13A), // 1024
                c(0xFFECBE2A), // 2048
                c(0xFFE0557F), // 4096
                c(0xFFC83F74), // 8192
                c(0xFF8E44AD), // 16384
                c(0xFF5B48B5), // 32768
                c(0xFF2F6FB8), // 65536
                c(0xFF3B3A34), // beyond
            ),
            darkText = c(0xFF746B62),
            lightText = c(0xFFFBF7F0),
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
        )
    }
}
