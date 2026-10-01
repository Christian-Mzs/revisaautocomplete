package com.example.ui.keyboard

/** Ratios measured from tecladosamsung.png, converted using the Revisa reference density.
 * Letter/number height ~1.21; letter gap/height ~0.28; home-row inset ~half a cell.
 * Widths remain relative so the same geometry adapts to the available screen width.
 */
object KeyboardGeometry {
    const val SCALE = 1.05f
    const val KEY_HEIGHT_DP = 45
    const val NUMBER_KEY_HEIGHT_DP = 37
    const val KEY_HORIZONTAL_GAP_DP = 6
    const val KEY_VERTICAL_GAP_DP = 12
    const val SIDE_PADDING_DP = 6
    const val BOTTOM_PADDING_DP = 12
    const val KEY_CORNER_RADIUS_DP = 5
    const val LETTER_FONT_SP = 30f * SCALE
    const val NUMBER_FONT_SP = 28f * SCALE
    const val SYMBOL_FONT_SP = 26f * SCALE
    const val SPECIAL_FONT_SP = 18f * SCALE
    const val SHIFT_FONT_SP = 30f * SCALE
    const val BACKSPACE_FONT_SP = 28f * SCALE
    const val SPACE_LABEL_FONT_SP = 16f * SCALE
    const val LETTER_KEY_WEIGHT = 1f
    const val SHIFT_KEY_WEIGHT = 1.4f
    const val BACKSPACE_KEY_WEIGHT = 1.4f
    const val THIRD_ROW_SPACER_WEIGHT = 0.2f
    const val SYMBOL_KEY_WEIGHT = 1.57f
    const val PUNCTUATION_KEY_WEIGHT = 1f
    const val EMOJI_KEY_WEIGHT = 1f
    const val SPACE_KEY_WEIGHT = 4.7f
    const val ACTION_KEY_WEIGHT = 1.57f
    // Half a letter cell on each side keeps the nine-letter row centered.
    const val SECOND_ROW_SIDE_INSET_WEIGHT = 0.5f
    const val TOOLBAR_HEIGHT_DP = 44
    const val TOOLBAR_BOTTOM_GAP_DP = 6
    const val TOOL_FONT_SP = 13.5f * SCALE
    const val ACCENT_GAP_DP = 8
}
