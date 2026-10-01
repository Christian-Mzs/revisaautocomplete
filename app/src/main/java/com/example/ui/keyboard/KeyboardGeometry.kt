package com.example.ui.keyboard

/** Physical dimensions calibrated against the Galaxy M52 reference captures.
 * Ten equal horizontal slots align QWERTY; ASDF starts half a slot in;
 * ZXCV starts one and a half slots in. SCALE applies to text only.
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
    const val SHIFT_KEY_WEIGHT = 4f / 3f
    const val BACKSPACE_KEY_WEIGHT = 4f / 3f
    const val THIRD_ROW_SPACER_WEIGHT = 1f / 6f
    const val SYMBOL_KEY_WEIGHT = 1.5f
    const val PUNCTUATION_KEY_WEIGHT = 1f
    const val SPACE_KEY_WEIGHT = 5f
    const val ACTION_KEY_WEIGHT = 1.5f
    // Half a letter cell on each side keeps the nine-letter row centered.
    const val SECOND_ROW_SIDE_INSET_WEIGHT = 0.5f
    const val TOOLBAR_HEIGHT_DP = 44
    const val TOOLBAR_BOTTOM_GAP_DP = 6
    const val TOOL_FONT_SP = 13.5f * SCALE
    const val ACCENT_GAP_DP = 8
}
