package com.example.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ime.ActionKeyType
import com.example.ime.KeyboardController
import com.example.ime.KeyboardMode
import com.example.ime.ShiftState
import com.example.ime.TextActionUiState
import com.example.translation.SupportedLanguages

@SuppressLint("ViewConstructor")
class KeyboardLayoutView(
    context: Context,
    private val controller: KeyboardController
) : LinearLayout(context) {

    private val toolbarContainer: FrameLayout
    private val keyboardKeysContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val accentsMap = mapOf(
        'a' to listOf("á", "à", "ã", "â", "ä"),
        'e' to listOf("é", "ê", "è", "ë"),
        'i' to listOf("í", "ì", "î", "ï"),
        'o' to listOf("ó", "õ", "ô", "ò", "ö"),
        'u' to listOf("ú", "ù", "ü", "û"),
        'c' to listOf("ç"),
        'n' to listOf("ñ")
    )

    private var activePopup: PopupWindow? = null

    // Clean, modern utility color palette (no flashy gradients or AI gimmicks)
    private val bgColor = Color.parseColor("#17191E")
    private val keyBgColor = Color.parseColor("#262933")
    private val keyActionBgColor = Color.parseColor("#343846")
    private val primaryActionColor = Color.parseColor("#2563EB")
    private val replaceSuccessColor = Color.parseColor("#15803D")
    private val keyTextColor = Color.parseColor("#FFFFFF")
    private val keySubTextColor = Color.parseColor("#94A3B8")
    private val pressedColor = Color.parseColor("#475569")
    private val pillBorderColor = Color.parseColor("#404656")

    init {
        orientation = VERTICAL
        setBackgroundColor(bgColor)
        val pad = dpToPx(4)
        setPadding(pad, pad, pad, dpToPx(6))

        toolbarContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }
        addView(toolbarContainer)

        keyboardKeysContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(keyboardKeysContainer)

        render()
    }

    fun render() {
        renderToolbar()
        renderKeys()
    }

    // -------------------------------------------------------------
    // TOOLBAR RENDERING (Corrigir | Traduzir | Switch IME)
    // -------------------------------------------------------------
    private fun renderToolbar() {
        toolbarContainer.removeAllViews()

        when (val state = controller.uiState) {
            is TextActionUiState.Idle -> renderIdleToolbar()
            is TextActionUiState.SelectingLanguage -> renderLanguageSelectorToolbar()
            is TextActionUiState.Processing -> renderProcessingToolbar(state.statusMessage)
            is TextActionUiState.Preview -> renderPreviewToolbar(state)
            is TextActionUiState.ConsentRequired -> renderConsentToolbar(state.pendingAction)
            is TextActionUiState.Error -> renderErrorToolbar(state.message)
        }
    }

    private fun renderIdleToolbar() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(42))
        }

        val isSens = controller.isSensitiveField

        // 1. "Corrigir" Button (Explicit text)
        val correctButton = TextView(context).apply {
            text = context.getString(R.string.correct_action)
            setTextColor(if (isSens) Color.parseColor("#64748B") else Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val bg = createRoundedDrawable(
                if (isSens) Color.parseColor("#222631") else keyActionBgColor,
                dpToPx(18).toFloat(),
                if (isSens) Color.TRANSPARENT else pillBorderColor,
                1
            )
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            val padH = dpToPx(14)
            setPadding(padH, 0, padH, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(36)).apply {
                marginEnd = dpToPx(8)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.requestCorrection()
            }
        }
        row.addView(correctButton)

        // 2. "Traduzir" Button (Explicit text, NOT an icon)
        val translateButton = TextView(context).apply {
            text = context.getString(R.string.translate_action)
            setTextColor(if (isSens) Color.parseColor("#64748B") else Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val bg = createRoundedDrawable(
                if (isSens) Color.parseColor("#222631") else keyActionBgColor,
                dpToPx(18).toFloat(),
                if (isSens) Color.TRANSPARENT else pillBorderColor,
                1
            )
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            val padH = dpToPx(14)
            setPadding(padH, 0, padH, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(36)).apply {
                marginEnd = dpToPx(8)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.requestTranslationPicker()
            }
        }
        row.addView(translateButton)

        // Flexible spacer
        val spacer = View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        row.addView(spacer)

        // 3. Switch Keyboard Icon Button (Clean keyboard silhouette, NOT a translation globe)
        val switchImeBtn = FrameLayout(context).apply {
            layoutParams = LayoutParams(dpToPx(38), dpToPx(36))
            val bg = createRoundedDrawable(Color.TRANSPARENT, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            val iconView = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_switch_keyboard))
                layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20), Gravity.CENTER)
            }
            addView(iconView)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.onSwitchImeRequested()
            }
        }
        row.addView(switchImeBtn)

        toolbarContainer.addView(row)
    }

    private fun renderLanguageSelectorToolbar() {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1E222B"), dpToPx(8).toFloat(), pillBorderColor, 1)
            background = bg
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Header with title and close
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.translate_to_title)
            setTextColor(keySubTextColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(keySubTextColor)
            textSize = 14f
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.cancelAction()
            }
        }
        header.addView(closeBtn)
        container.addView(header)

        // Horizontal scrollable list of languages (last used language is first and highlighted)
        val lastLang = controller.consentManager.getLastTranslationLanguageCode()
        val sortedLanguages = SupportedLanguages.getSortedWithPreferred(lastLang)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(2)
            }
        }

        val langRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for (lang in sortedLanguages) {
            val isPreferred = lang.languageCode.equals(lastLang, ignoreCase = true)
            val pill = TextView(context).apply {
                text = lang.displayName
                textSize = 12.5f
                setTextColor(Color.WHITE)
                typeface = if (isPreferred) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val bg = createRoundedDrawable(
                    if (isPreferred) primaryActionColor else keyBgColor,
                    dpToPx(14).toFloat(),
                    if (isPreferred) Color.TRANSPARENT else pillBorderColor,
                    1
                )
                background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
                val padH = dpToPx(12)
                setPadding(padH, dpToPx(6), padH, dpToPx(6))
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dpToPx(6)
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    controller.selectLanguageAndTranslate(lang.languageCode)
                }
            }
            langRow.addView(pill)
        }

        scroll.addView(langRow)
        container.addView(scroll)

        toolbarContainer.addView(container)
    }

    private fun renderProcessingToolbar(statusMessage: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1E222B"), dpToPx(8).toFloat())
            background = bg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(42))
        }

        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LayoutParams(dpToPx(18), dpToPx(18)).apply {
                marginEnd = dpToPx(10)
            }
        }
        row.addView(progressBar)

        val statusText = TextView(context).apply {
            text = statusMessage
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(statusText)

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.cancelAction()
            }
        }
        row.addView(cancelBtn)

        toolbarContainer.addView(row)
    }

    private fun renderPreviewToolbar(state: TextActionUiState.Preview) {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1B1F28"), dpToPx(8).toFloat(), primaryActionColor, 1)
            background = bg
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Header: Title and Toggle
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = if (state.isShowingOriginal) "Original" else state.title
            setTextColor(if (state.isShowingOriginal) Color.parseColor("#F59E0B") else Color.parseColor("#60A5FA"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val toggleBtn = TextView(context).apply {
            text = if (state.isShowingOriginal) context.getString(R.string.view_result) else context.getString(R.string.view_original)
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 11.5f
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.togglePreviewOriginal()
            }
        }
        header.addView(toggleBtn)
        card.addView(header)

        // Text Scroll
        val previewScroll = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(6)
            }
        }

        val textContent = TextView(context).apply {
            val textToDisplay = if (state.isShowingOriginal) state.originalText else state.resultText
            text = textToDisplay
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 3
        }
        previewScroll.addView(textContent)
        card.addView(previewScroll)

        // Actions Row
        val actionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(keySubTextColor)
            textSize = 12.5f
            val bg = createRoundedDrawable(Color.parseColor("#2D323F"), dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpToPx(8)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.cancelAction()
            }
        }
        actionsRow.addView(cancelBtn)

        val replaceBtn = TextView(context).apply {
            text = context.getString(R.string.replace)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12.5f
            val bg = createRoundedDrawable(replaceSuccessColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.applyResult()
            }
        }
        actionsRow.addView(replaceBtn)

        card.addView(actionsRow)
        toolbarContainer.addView(card)
    }

    private fun renderConsentToolbar(pendingAction: TextActionUiState.PendingAction) {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1B1F28"), dpToPx(8).toFloat(), primaryActionColor, 1)
            background = bg
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.privacy_consent_title)
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(title)

        val desc = TextView(context).apply {
            text = context.getString(R.string.privacy_consent_message)
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 11.5f
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(6)
            }
        }
        card.addView(desc)

        val btnRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(keySubTextColor)
            textSize = 13f
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.cancelAction()
            }
        }
        btnRow.addView(cancelBtn)

        val continueBtn = TextView(context).apply {
            text = context.getString(R.string.consent_continue)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            val bg = createRoundedDrawable(primaryActionColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.acceptConsentAndProceed(pendingAction)
            }
        }
        btnRow.addView(continueBtn)

        card.addView(btnRow)
        toolbarContainer.addView(card)
    }

    private fun renderErrorToolbar(message: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#341818"), dpToPx(8).toFloat(), Color.parseColor("#EF4444"), 1)
            background = bg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val errorText = TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 12.5f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(errorText)

        val closeBtn = TextView(context).apply {
            text = context.getString(R.string.close)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.dismissError()
            }
        }
        row.addView(closeBtn)

        toolbarContainer.addView(row)
    }

    // -------------------------------------------------------------
    // KEYBOARD KEYS RENDERING
    // -------------------------------------------------------------
    private fun renderKeys() {
        keyboardKeysContainer.removeAllViews()

        when (controller.currentMode) {
            KeyboardMode.LETTERS -> renderLetterKeys()
            KeyboardMode.NUMBERS -> renderNumberKeys()
            KeyboardMode.SYMBOLS -> renderSymbolKeys()
        }
    }

    private fun renderLetterKeys() {
        // Row 1: q w e r t y u i o p
        val r1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        keyboardKeysContainer.addView(createKeyRow(r1))

        // Row 2: a s d f g h j k l ç
        val r2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ç")
        keyboardKeysContainer.addView(createKeyRow(r2))

        // Row 3: [Shift] z x c v b n m [Backspace]
        val r3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                bottomMargin = dpToPx(4)
            }
        }

        val shiftBgColor = when (controller.shiftState) {
            ShiftState.OFF -> keyActionBgColor
            ShiftState.ON -> primaryActionColor
            ShiftState.CAPS_LOCK -> Color.parseColor("#2563EB")
        }
        val shiftLabel = when (controller.shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.ON -> "⇧"
            ShiftState.CAPS_LOCK -> "⇪"
        }
        val shiftKey = createSpecialKey(shiftLabel, 1.4f, shiftBgColor) {
            controller.toggleShift()
        }
        r3.addView(shiftKey)

        val r3Letters = listOf("z", "x", "c", "v", "b", "n", "m")
        for (char in r3Letters) {
            r3.addView(createLetterKey(char, 1.0f))
        }

        val backspaceKey = createRepeatKey("⌫", 1.4f, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [?123] [ , ] [ Espaço ] [ . ] [ Action Key ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
        }

        val numModeKey = createSpecialKey("?123", 1.4f, keyActionBgColor) {
            controller.setMode(KeyboardMode.NUMBERS)
        }
        r4.addView(numModeKey)

        r4.addView(createDirectCharKey(",", 1.0f))
        r4.addView(createSpaceKey("espaço", 4.2f))
        r4.addView(createDirectCharKey(".", 1.0f))

        // Vector-based Action Key (Enter / Search / Send / Done / Go)
        val actionKey = createActionKey(1.4f, primaryActionColor) {
            controller.handleEnter()
        }
        r4.addView(actionKey)

        keyboardKeysContainer.addView(r4)
    }

    private fun renderNumberKeys() {
        // Row 1: 1 2 3 4 5 6 7 8 9 0
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        keyboardKeysContainer.addView(createDirectRow(r1))

        // Row 2: @ # $ % & * - + ( )
        val r2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")")
        keyboardKeysContainer.addView(createDirectRow(r2))

        // Row 3: [=\<] ! " ' : ; / ? [Backspace]
        val r3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                bottomMargin = dpToPx(4)
            }
        }

        val moreSymbolsKey = createSpecialKey("=\\<", 1.4f, keyActionBgColor) {
            controller.setMode(KeyboardMode.SYMBOLS)
        }
        r3.addView(moreSymbolsKey)

        val r3Symbols = listOf("!", "\"", "'", ":", ";", "/", "?")
        for (sym in r3Symbols) {
            r3.addView(createDirectCharKey(sym, 1.0f))
        }

        val backspaceKey = createRepeatKey("⌫", 1.4f, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [ABC] [ , ] [ Espaço ] [ . ] [ Action Key ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
        }

        val abcKey = createSpecialKey("ABC", 1.4f, keyActionBgColor) {
            controller.setMode(KeyboardMode.LETTERS)
        }
        r4.addView(abcKey)

        r4.addView(createDirectCharKey(",", 1.0f))
        r4.addView(createSpaceKey("espaço", 4.2f))
        r4.addView(createDirectCharKey(".", 1.0f))

        val actionKey = createActionKey(1.4f, primaryActionColor) {
            controller.handleEnter()
        }
        r4.addView(actionKey)

        keyboardKeysContainer.addView(r4)
    }

    private fun renderSymbolKeys() {
        // Row 1: ~ ` | • √ π ÷ × ¶ ∆
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
        keyboardKeysContainer.addView(createDirectRow(r1))

        // Row 2: £ € ¥ ¢ ^ ° = { } \
        val r2 = listOf("£", "€", "¥", "¢", "^", "°", "=", "{", "}", "\\")
        keyboardKeysContainer.addView(createDirectRow(r2))

        // Row 3: [?123] % _ < > [ ] « » [Backspace]
        val r3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                bottomMargin = dpToPx(4)
            }
        }

        val numKey = createSpecialKey("?123", 1.4f, keyActionBgColor) {
            controller.setMode(KeyboardMode.NUMBERS)
        }
        r3.addView(numKey)

        val r3Symbols = listOf("%", "_", "<", ">", "[", "]", "«", "»")
        for (sym in r3Symbols) {
            r3.addView(createDirectCharKey(sym, 1.0f))
        }

        val backspaceKey = createRepeatKey("⌫", 1.4f, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [ABC] [ , ] [ Espaço ] [ . ] [ Action Key ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
        }

        val abcKey = createSpecialKey("ABC", 1.4f, keyActionBgColor) {
            controller.setMode(KeyboardMode.LETTERS)
        }
        r4.addView(abcKey)

        r4.addView(createDirectCharKey(",", 1.0f))
        r4.addView(createSpaceKey("espaço", 4.2f))
        r4.addView(createDirectCharKey(".", 1.0f))

        val actionKey = createActionKey(1.4f, primaryActionColor) {
            controller.handleEnter()
        }
        r4.addView(actionKey)

        keyboardKeysContainer.addView(r4)
    }

    // -------------------------------------------------------------
    // KEY BUILDERS
    // -------------------------------------------------------------
    private fun createKeyRow(chars: List<String>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                bottomMargin = dpToPx(4)
            }
            for (char in chars) {
                addView(createLetterKey(char, 1.0f))
            }
        }
    }

    private fun createDirectRow(chars: List<String>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                bottomMargin = dpToPx(4)
            }
            for (char in chars) {
                addView(createDirectCharKey(char, 1.0f))
            }
        }
    }

    private fun createLetterKey(char: String, weight: Float): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                val m = dpToPx(2)
                setMargins(m, 0, m, 0)
            }
            val bg = createRoundedDrawable(keyBgColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
        }

        val isShifted = controller.shiftState != ShiftState.OFF
        val displayChar = if (isShifted) char.uppercase() else char.lowercase()

        val tv = TextView(context).apply {
            text = displayChar
            setTextColor(keyTextColor)
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        val lowerChar = char.lowercase().firstOrNull() ?: ' '
        val accents = accentsMap[lowerChar]

        if (!accents.isNullOrEmpty()) {
            val hint = TextView(context).apply {
                text = "·"
                setTextColor(keySubTextColor)
                textSize = 10f
                gravity = Gravity.TOP or Gravity.END
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.END
                    marginEnd = dpToPx(4)
                    topMargin = dpToPx(2)
                }
            }
            frame.addView(hint)

            frame.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAccentsPopup(it, accents, isShifted)
                true
            }
        }

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            controller.handleCharacter(char)
        }

        return frame
    }

    private fun createDirectCharKey(char: String, weight: Float): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                val m = dpToPx(2)
                setMargins(m, 0, m, 0)
            }
            val bg = createRoundedDrawable(keyBgColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
        }

        val tv = TextView(context).apply {
            text = char
            setTextColor(keyTextColor)
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            controller.handleDirectCharacter(char)
        }

        return frame
    }

    private fun createSpecialKey(
        label: String,
        weight: Float,
        backgroundColor: Int,
        onClick: () -> Unit
    ): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                val m = dpToPx(2)
                setMargins(m, 0, m, 0)
            }
            val bg = createRoundedDrawable(backgroundColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
        }

        val tv = TextView(context).apply {
            text = label
            setTextColor(keyTextColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }

        return frame
    }

    private fun createActionKey(
        weight: Float,
        backgroundColor: Int,
        onClick: () -> Unit
    ): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                val m = dpToPx(2)
                setMargins(m, 0, m, 0)
            }
            val bg = createRoundedDrawable(backgroundColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
        }

        val actionType = controller.getActionKeyType()
        val drawableRes = when (actionType) {
            ActionKeyType.SEARCH -> R.drawable.ic_action_search
            ActionKeyType.SEND -> R.drawable.ic_action_send
            ActionKeyType.DONE -> R.drawable.ic_action_done
            ActionKeyType.GO, ActionKeyType.NEXT -> R.drawable.ic_action_go
            ActionKeyType.ENTER -> R.drawable.ic_action_enter
        }

        val iv = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, drawableRes))
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(22), dpToPx(22), Gravity.CENTER)
        }
        frame.addView(iv)

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }

        return frame
    }

    private fun createSpaceKey(label: String, weight: Float): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                val m = dpToPx(2)
                setMargins(m, 0, m, 0)
            }
            val bg = createRoundedDrawable(keyBgColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
        }

        val tv = TextView(context).apply {
            text = label
            setTextColor(keySubTextColor)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            controller.handleSpace()
        }

        return frame
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createRepeatKey(
        label: String,
        weight: Float,
        backgroundColor: Int,
        onAction: () -> Unit
    ): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                val m = dpToPx(2)
                setMargins(m, 0, m, 0)
            }
            val bg = createRoundedDrawable(backgroundColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
        }

        val tv = TextView(context).apply {
            text = label
            setTextColor(keyTextColor)
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        var isRepeating = false
        val repeatRunnable = object : Runnable {
            override fun run() {
                if (isRepeating) {
                    frame.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAction()
                    handler.postDelayed(this, 50)
                }
            }
        }

        frame.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAction()
                    isRepeating = true
                    handler.postDelayed(repeatRunnable, 350)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isRepeating = false
                    handler.removeCallbacks(repeatRunnable)
                    true
                }
                else -> false
            }
        }

        return frame
    }

    // -------------------------------------------------------------
    // ACCENTS POPUP
    // -------------------------------------------------------------
    private fun showAccentsPopup(anchor: View, accents: List<String>, isShifted: Boolean) {
        dismissPopup()

        val popupView = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val bg = createRoundedDrawable(Color.parseColor("#1B1F28"), dpToPx(8).toFloat(), Color.parseColor("#383E4C"), 1)
            background = bg
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        }

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        activePopup = popup

        for (accent in accents) {
            val displayAccent = if (isShifted) accent.uppercase() else accent.lowercase()
            val item = TextView(context).apply {
                text = displayAccent
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val bg = createRoundedDrawable(Color.parseColor("#2B2F3A"), dpToPx(6).toFloat())
                background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
                layoutParams = LayoutParams(dpToPx(42), dpToPx(42)).apply {
                    val m = dpToPx(2)
                    setMargins(m, 0, m, 0)
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    controller.handleCharacter(accent)
                    popup.dismiss()
                }
            }
            popupView.addView(item)
        }

        popupView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val popupHeight = popupView.measuredHeight
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

        popup.showAtLocation(
            anchor,
            Gravity.NO_GRAVITY,
            location[0] - dpToPx(10),
            location[1] - popupHeight - dpToPx(8)
        )
    }

    fun dismissPopup() {
        activePopup?.dismiss()
        activePopup = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun createRoundedDrawable(
        fillColor: Int,
        cornerRadius: Float,
        strokeColor: Int = 0,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            this.cornerRadius = cornerRadius
            if (strokeWidth > 0 && strokeColor != 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }
}
