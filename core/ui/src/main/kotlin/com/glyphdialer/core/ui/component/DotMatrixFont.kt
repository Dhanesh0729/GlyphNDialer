// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

/**
 * A tiny built-in 5×7 dot-matrix glyph table.
 *
 * Several signature components ([DotMatrixText], [TickerCaption]) render characters
 * as a grid of dots rather than rasterizing a real font into a [androidx.compose.ui.graphics.Canvas]
 * — sampling an arbitrary font's glyph coverage on every frame is expensive and the
 * real OFL dot-matrix `.ttf` is not bundled in this generated module (see
 * `:core:designsystem` `Type.kt` / `FONTS.md`). A fixed 5×7 cell covers the
 * characters a dialer actually shows — digits, the dial symbols, A–Z, space and a
 * handful of punctuation — which is enough for numbers, captions and avatars while
 * staying allocation-free.
 *
 * Each glyph is 5 columns × 7 rows, encoded as 7 row strings of `#`/space. Lookup is
 * case-insensitive; unknown characters fall back to a hollow box so missing coverage
 * is visible rather than silently blank (honesty over a fake render).
 *
 * This is an internal helper — not part of the module's public API.
 */
internal object DotMatrixFont {

    const val COLS = 5
    const val ROWS = 7

    /** Inter-character gap in columns when laying out a string. */
    const val CHAR_GAP_COLS = 1

    private val UNKNOWN = arrayOf(
        "#####",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#####",
    )

    private val SPACE = arrayOf(
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
    )

    private val glyphs: Map<Char, Array<String>> = buildMap {
        put('0', arrayOf(" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "))
        put('1', arrayOf("  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "))
        put('2', arrayOf(" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"))
        put('3', arrayOf(" ### ", "#   #", "    #", "  ## ", "    #", "#   #", " ### "))
        put('4', arrayOf("   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "))
        put('5', arrayOf("#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "))
        put('6', arrayOf("  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "))
        put('7', arrayOf("#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "))
        put('8', arrayOf(" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "))
        put('9', arrayOf(" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "))

        put('+', arrayOf("     ", "  #  ", "  #  ", "#####", "  #  ", "  #  ", "     "))
        put('-', arrayOf("     ", "     ", "     ", "#####", "     ", "     ", "     "))
        put('*', arrayOf("     ", "# # #", " ### ", "#####", " ### ", "# # #", "     "))
        put('#', arrayOf(" # # ", " # # ", "#####", " # # ", "#####", " # # ", " # # "))
        put('.', arrayOf("     ", "     ", "     ", "     ", "     ", " ##  ", " ##  "))
        put(',', arrayOf("     ", "     ", "     ", "     ", " ##  ", " ##  ", "#    "))
        put('(', arrayOf("   # ", "  #  ", " #   ", " #   ", " #   ", "  #  ", "   # "))
        put(')', arrayOf(" #   ", "  #  ", "   # ", "   # ", "   # ", "  #  ", " #   "))
        put('/', arrayOf("    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    "))
        put(':', arrayOf("     ", " ##  ", " ##  ", "     ", " ##  ", " ##  ", "     "))
        put('?', arrayOf(" ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  "))
        put('!', arrayOf("  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "     ", "  #  "))

        put('A', arrayOf(" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"))
        put('B', arrayOf("#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "))
        put('C', arrayOf(" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "))
        put('D', arrayOf("#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "))
        put('E', arrayOf("#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"))
        put('F', arrayOf("#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "))
        put('G', arrayOf(" ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ### "))
        put('H', arrayOf("#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"))
        put('I', arrayOf(" ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "))
        put('J', arrayOf("  ###", "   # ", "   # ", "   # ", "#  # ", "#  # ", " ##  "))
        put('K', arrayOf("#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"))
        put('L', arrayOf("#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"))
        put('M', arrayOf("#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #"))
        put('N', arrayOf("#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"))
        put('O', arrayOf(" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "))
        put('P', arrayOf("#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "))
        put('Q', arrayOf(" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"))
        put('R', arrayOf("#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"))
        put('S', arrayOf(" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "))
        put('T', arrayOf("#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "))
        put('U', arrayOf("#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "))
        put('V', arrayOf("#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "))
        put('W', arrayOf("#   #", "#   #", "#   #", "# # #", "# # #", "## ##", "#   #"))
        put('X', arrayOf("#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"))
        put('Y', arrayOf("#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "))
        put('Z', arrayOf("#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"))
    }

    /** Returns the 5×7 pattern for [c] (case-insensitive), or a hollow box if unknown. */
    fun glyphFor(c: Char): Array<String> = when (c) {
        ' ' -> SPACE
        else -> glyphs[c.uppercaseChar()] ?: UNKNOWN
    }

    /** True if a given [row]/[col] within character [c]'s 5×7 cell is "on". */
    fun isDotOn(c: Char, row: Int, col: Int): Boolean {
        if (row !in 0 until ROWS || col !in 0 until COLS) return false
        val pattern = glyphFor(c)
        return pattern[row][col] == '#'
    }
}
