package com.example.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import com.example.MainActivity
import com.example.ime.CorrectionUiState
import com.example.ime.KeyboardController
import com.example.ime.KeyboardMode
import com.example.ime.ShiftState

@SuppressLint("ViewConstructor")
class KeyboardLayoutView(
    context: Context,
    private val controller: KeyboardController
) : LinearLayout(context) {

    private val toolbarContainer: FrameLayout
    private val keyboardKeysContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    // Accent mappings for Brazilian Portuguese
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

    // Colors
    private val bgColor = Color.parseColor("#18191E")
    private val keyBgColor = Color.parseColor("#292D36")
    private val keyActionBgColor = Color.parseColor("#373E4D")
    private val primaryAccentColor = Color.parseColor("#2563EB")
    private val successColor = Color.parseColor("#16A34A")
    private val keyTextColor = Color.parseColor("#FFFFFF")
    private val keySubTextColor = Color.parseColor("#94A3B8")
    private val pressedColor = Color.parseColor("#4B5563")

    init {
        orientation = VERTICAL
        setBackgroundColor(bgColor)
        val pad = dpToPx(4)
        setPadding(pad, pad, pad, dpToPx(8))

        // 1. Toolbar area (Dynamic: normal, correcting, preview, consent, error)
        toolbarContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(4)
            }
        }
        addView(toolbarContainer)

        // 2. Keyboard keys container
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
    // TOOLBAR RENDERING
    // -------------------------------------------------------------
    private fun renderToolbar() {
        toolbarContainer.removeAllViews()

        when (val state = controller.correctionUiState) {
            is CorrectionUiState.Idle -> renderIdleToolbar()
            is CorrectionUiState.Correcting -> renderCorrectingToolbar()
            is CorrectionUiState.ConsentRequired -> renderConsentToolbar()
            is CorrectionUiState.Preview -> renderPreviewToolbar(state)
            is CorrectionUiState.Error -> renderErrorToolbar(state.message)
        }
    }

    private fun renderIdleToolbar() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(44))
        }

        // Corrigir Button
        val correctButton = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            val isSens = controller.isSensitiveField
            val bg = createRoundedDrawable(
                if (isSens) Color.parseColor("#334155") else primaryAccentColor,
                dpToPx(20).toFloat()
            )
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            val padH = dpToPx(14)
            setPadding(padH, 0, padH, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38)).apply {
                marginEnd = dpToPx(8)
            }

            val label = TextView(context).apply {
                text = if (isSens) "🔒 Campo seguro" else "✨ Corrigir"
                setTextColor(if (isSens) Color.parseColor("#94A3B8") else Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(label)

            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.requestCorrection()
            }
        }
        row.addView(correctButton)

        // Spacer
        val spacer = View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        row.addView(spacer)

        // Switch Keyboard (IME Picker) Button
        val switchImeBtn = TextView(context).apply {
            text = "🌐"
            textSize = 18f
            gravity = Gravity.CENTER
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), null, null)
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.onSwitchImeRequested()
            }
        }
        row.addView(switchImeBtn)

        // Settings Button
        val settingsBtn = TextView(context).apply {
            text = "⚙️"
            textSize = 18f
            gravity = Gravity.CENTER
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), null, null)
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
        row.addView(settingsBtn)

        toolbarContainer.addView(row)
    }

    private fun renderCorrectingToolbar() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1E293B"), dpToPx(8).toFloat())
            background = bg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LayoutParams(dpToPx(20), dpToPx(20)).apply {
                marginEnd = dpToPx(10)
            }
        }
        row.addView(progressBar)

        val statusText = TextView(context).apply {
            text = "Corrigindo via tradução (PT → EN → PT)…"
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(statusText)

        val cancelBtn = TextView(context).apply {
            text = "Cancelar"
            setTextColor(Color.parseColor("#F87171"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.cancelCorrection()
            }
        }
        row.addView(cancelBtn)

        toolbarContainer.addView(row)
    }

    private fun renderConsentToolbar() {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1E293B"), dpToPx(10).toFloat(), Color.parseColor("#3B82F6"), 2)
            background = bg
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "Privacidade da Correção"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(title)

        val desc = TextView(context).apply {
            text = "Para corrigir o texto, a frase será enviada ao serviço de tradução do Google. O envio só acontece quando você toca em Corrigir."
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
            text = "Cancelar"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.declineConsent()
            }
        }
        btnRow.addView(cancelBtn)

        val continueBtn = TextView(context).apply {
            text = "Continuar"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            val bg = createRoundedDrawable(primaryAccentColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.acceptConsentAndCorrect()
            }
        }
        btnRow.addView(continueBtn)

        card.addView(btnRow)
        toolbarContainer.addView(card)
    }

    private fun renderPreviewToolbar(state: CorrectionUiState.Preview) {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#1E293B"), dpToPx(10).toFloat(), Color.parseColor("#10B981"), 2)
            background = bg
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Header: Title & Toggle Original
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = if (state.isShowingOriginal) "Original:" else "✨ Sugestão de correção:"
            setTextColor(if (state.isShowingOriginal) Color.parseColor("#F59E0B") else Color.parseColor("#34D399"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val toggleBtn = TextView(context).apply {
            text = if (state.isShowingOriginal) "Ver sugestão" else "Ver original"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 11.5f
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.togglePreviewOriginal()
            }
        }
        header.addView(toggleBtn)
        card.addView(header)

        // Text Display
        val previewScroll = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(6)
            }
        }

        val textContent = TextView(context).apply {
            val displayText = if (state.isShowingOriginal) {
                state.result.originalText
            } else {
                state.result.correctedText ?: ""
            }
            text = displayText
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 3
        }
        previewScroll.addView(textContent)
        card.addView(previewScroll)

        // Action Buttons Row
        val actionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Cancel button
        val cancelBtn = TextView(context).apply {
            text = "✕ Cancelar"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            val bg = createRoundedDrawable(Color.parseColor("#334155"), dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpToPx(8)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.cancelCorrection()
            }
        }
        actionsRow.addView(cancelBtn)

        // Replace button
        val replaceBtn = TextView(context).apply {
            text = "✓ Substituir"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12.5f
            val bg = createRoundedDrawable(successColor, dpToPx(6).toFloat())
            background = RippleDrawable(ColorStateList.valueOf(pressedColor), bg, null)
            setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.applyCorrection()
            }
        }
        actionsRow.addView(replaceBtn)

        card.addView(actionsRow)
        toolbarContainer.addView(card)
    }

    private fun renderErrorToolbar(message: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = createRoundedDrawable(Color.parseColor("#3B1B1B"), dpToPx(8).toFloat(), Color.parseColor("#EF4444"), 1)
            background = bg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val errorText = TextView(context).apply {
            text = "⚠️ $message"
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 12.5f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(errorText)

        val closeBtn = TextView(context).apply {
            text = "Fechar"
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

        // Shift Key
        val shiftBgColor = when (controller.shiftState) {
            ShiftState.OFF -> keyActionBgColor
            ShiftState.ON -> primaryAccentColor
            ShiftState.CAPS_LOCK -> Color.parseColor("#F59E0B")
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

        // Middle letters
        val r3Letters = listOf("z", "x", "c", "v", "b", "n", "m")
        for (char in r3Letters) {
            r3.addView(createLetterKey(char, 1.0f))
        }

        // Backspace Key
        val backspaceKey = createRepeatKey("⌫", 1.4f, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [?123] [ , ] [ Espaço ] [ . ] [ Enter ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
        }

        // Mode Switch (?123)
        val numModeKey = createSpecialKey("?123", 1.4f, keyActionBgColor) {
            controller.setMode(KeyboardMode.NUMBERS)
        }
        r4.addView(numModeKey)

        // Comma
        r4.addView(createDirectCharKey(",", 1.0f))

        // Spacebar
        val spaceKey = createSpaceKey("espaço", 4.2f)
        r4.addView(spaceKey)

        // Period
        r4.addView(createDirectCharKey(".", 1.0f))

        // Enter
        val enterKey = createSpecialKey("⏎", 1.4f, primaryAccentColor) {
            controller.handleEnter()
        }
        r4.addView(enterKey)

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

        // Row 4: [ABC] [ , ] [ Espaço ] [ . ] [ Enter ]
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

        val enterKey = createSpecialKey("⏎", 1.4f, primaryAccentColor) {
            controller.handleEnter()
        }
        r4.addView(enterKey)

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

        // Row 4: [ABC] [ , ] [ Espaço ] [ . ] [ Enter ]
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

        val enterKey = createSpecialKey("⏎", 1.4f, primaryAccentColor) {
            controller.handleEnter()
        }
        r4.addView(enterKey)

        keyboardKeysContainer.addView(r4)
    }

    // -------------------------------------------------------------
    // KEY VIEW BUILDERS
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

        // Check if accents exist for this letter
        val lowerChar = char.lowercase().firstOrNull() ?: ' '
        val accents = accentsMap[lowerChar]

        if (!accents.isNullOrEmpty()) {
            // Subtle dot or hint indicator
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

            // Setup Long Press for Accents
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
            val bg = createRoundedDrawable(Color.parseColor("#1E222B"), dpToPx(8).toFloat(), Color.parseColor("#475569"), 1)
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
                val bg = createRoundedDrawable(Color.parseColor("#334155"), dpToPx(6).toFloat())
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

        // Show above the anchor key
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

    // -------------------------------------------------------------
    // UTILS
    // -------------------------------------------------------------
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
