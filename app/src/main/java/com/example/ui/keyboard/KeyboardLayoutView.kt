package com.example.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
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
import android.widget.GridView
import android.widget.BaseAdapter
import android.graphics.drawable.StateListDrawable
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ime.ActionKeyType
import com.example.ime.InputMetrics
import com.example.ime.KeyboardController
import com.example.ime.KeyboardMode
import com.example.ime.ShiftState
import com.example.ime.TextActionUiState
import com.example.ui.keyboard.KeyboardGeometry as G
import com.example.translation.SupportedLanguages

@SuppressLint("ViewConstructor")
class KeyboardLayoutView(
    context: Context,
    private val controller: KeyboardController
) : LinearLayout(context) {

    private val toolbarContainer: FrameLayout
    private val keyboardKeysContainer: KeyboardSurfaceView
    private val handler = Handler(Looper.getMainLooper())
    private data class KeyBinding(val action: () -> Unit, val accents: List<String> = emptyList(),
        val accentAction: ((String) -> Unit)? = null, val repeat: Boolean = false)
    private class Press(val key: View, val binding: KeyBinding, val startX: Float) {
        var strip: AccentStrip? = null
        var timer: Runnable? = null
    }
    private val keyBindings = java.util.IdentityHashMap<View, KeyBinding>()
    private val pointers = android.util.SparseArray<Press>()
    private val screenPosition = IntArray(2)


    private val accentsMap = mapOf(
        'a' to listOf("á", "à", "ã", "â", "ä"),
        'e' to listOf("é", "ê", "è", "ë"),
        'i' to listOf("í", "ì", "î", "ï"),
        'o' to listOf("ó", "õ", "ô", "ò", "ö"),
        'u' to listOf("ú", "ù", "ü", "û"),
        'c' to listOf("ç", "ć", "ĉ", "č"),
        'n' to listOf("ñ")
    )

    private var activePopup: PopupWindow? = null
    private var renderedKeys: Triple<KeyboardMode, ShiftState, ActionKeyType>? = null
    private var renderedToolbar: List<Any?>? = null
    private var clipboardOpen = false
    private var emojiCategoryIndex = 0
    private val emojiPreferences by lazy { context.getSharedPreferences("emoji_recents", Context.MODE_PRIVATE) }
    private val recentEmojis: List<String>
        get() = if (controller.isSensitiveField) emptyList() else
            emojiPreferences.getString("items", "").orEmpty().split(" ").filter { it.isNotEmpty() }

    private fun commitEmoji(emoji: String) {
        controller.handleDirectCharacter(emoji)
        if (!controller.isSensitiveField) {
            val recent = (listOf(emoji) + recentEmojis).distinct().take(40)
            emojiPreferences.edit().putString("items", recent.joinToString(" ")).apply()
        }
    }

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
        isMotionEventSplittingEnabled = true
        setBackgroundColor(bgColor)
        val pad = dpToPx(G.SIDE_PADDING_DP)
        setPadding(pad, dpToPx(4), pad, dpToPx(G.BOTTOM_PADDING_DP))

        toolbarContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(G.TOOLBAR_BOTTOM_GAP_DP)
            }
        }
        addView(toolbarContainer)

        keyboardKeysContainer = KeyboardSurfaceView(context).apply {
            visualHorizontalInsetPx = dpToPx(G.KEY_HORIZONTAL_GAP_DP) / 2
            isKey = { keyBindings.containsKey(it) }
            onPointerEvent = { event, key -> handleSurfaceTouch(event, key) }
            onGeometryInvalidated = { cancelPointers() }
            orientation = VERTICAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, keyboardKeysHeight())
        }
        addView(keyboardKeysContainer)

        render()
    }

    fun resetNavigation() {
        clipboardOpen = false
        emojiCategoryIndex = 0
        renderedKeys = null
        render()
    }

    fun refreshClipboard() {
        if (clipboardOpen) renderKeys()
    }

    fun render() {
        renderToolbar()
        val keys = Triple(controller.currentMode, controller.shiftState, controller.getActionKeyType())
        if (keys != renderedKeys) {
            val previous = renderedKeys
            if (previous != null && previous.first == keys.first && previous.third == keys.third) {
                updateLetterLabels(keyboardKeysContainer)
            } else {
                dismissPopup()
                renderKeys()
            }
            renderedKeys = keys
        }
    }

    private fun updateLetterLabels(group: ViewGroup) {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            val letter = child.tag as? String
            if (child.tag == ShiftState::class.java && child is FrameLayout) {
                (child.getChildAt(0) as TextView).text =
                    if (controller.shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
                val color = if (controller.shiftState == ShiftState.OFF) keyActionBgColor else primaryActionColor
                child.background = keyBackground(
                    createRoundedDrawable(color, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat()))
            } else if (letter != null && child is FrameLayout) {
                (child.getChildAt(0) as TextView).text =
                    if (controller.shiftState != ShiftState.OFF) letter.uppercase() else letter.lowercase()
            } else if (child is ViewGroup) updateLetterLabels(child)
        }
    }

    // -------------------------------------------------------------
    // TOOLBAR RENDERING (Corrigir | Traduzir | Switch IME)
    // -------------------------------------------------------------
    private fun renderToolbar() {
        val snapshot = listOf(controller.uiState, controller.isSensitiveField, controller.clipboardSuggestion.suggestion)
        if (snapshot == renderedToolbar) return
        renderedToolbar = snapshot
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
        controller.clipboardSuggestion.suggestion?.let { renderClipboardSuggestion(it); return }
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(G.TOOLBAR_HEIGHT_DP))
        }
        row.addView(toolbarIcon(R.drawable.ic_toolbar_emoji, context.getString(R.string.open_emojis)).apply {
            layoutParams = LayoutParams(dpToPx(42), LayoutParams.MATCH_PARENT)
            contentDescription = context.getString(R.string.open_emojis)
            setOnClickListener { clipboardOpen = false; controller.setMode(KeyboardMode.EMOJIS); renderKeys() }
        })
        addToolbarDivider(row)
        val enabled = !controller.isSensitiveField
        val correct = toolbarIcon(R.drawable.ic_toolbar_correct, context.getString(R.string.correct_action)).apply {
            isEnabled = enabled
            setColorFilter(if (enabled) keyTextColor else keySubTextColor)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.requestCorrection()
            }
        }
        row.addView(correct)
        addToolbarDivider(row)
        val translate = toolbarIcon(R.drawable.ic_toolbar_translate, context.getString(R.string.translate_action)).apply {
            isEnabled = enabled
            setColorFilter(if (enabled) keyTextColor else keySubTextColor)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.requestTranslationPicker()
            }
        }
        row.addView(translate)
        addToolbarDivider(row)
        val switch = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            contentDescription = context.getString(R.string.switch_keyboard)
            background = RippleDrawable(ColorStateList.valueOf(pressedColor),
                createRoundedDrawable(Color.TRANSPARENT, dpToPx(5).toFloat()), null)
            addView(ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_switch_keyboard))
                layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20), Gravity.CENTER)
            })
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.onSwitchImeRequested()
            }
        }
        row.addView(ImageView(context).apply {
            contentDescription = "Área de transferência"
            layoutParams = LayoutParams(dpToPx(38), LayoutParams.MATCH_PARENT)
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_clipboard))
            setColorFilter(keyTextColor)
            setPadding(dpToPx(9), dpToPx(9), dpToPx(9), dpToPx(9))
            isEnabled = !controller.isSensitiveField
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener {
                dismissPopup()
                clipboardOpen = !clipboardOpen
                if (clipboardOpen) controller.clipboardHistory.capture()
                renderKeys()
            }
        })
        row.addView(switch)
        toolbarContainer.addView(row)
    }

    private fun renderClipboardSuggestion(item: com.example.clipboard.ClipboardSuggestion) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.TOOLBAR_HEIGHT_DP))
        }
        val pill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            background = createRoundedDrawable(keyBgColor, dpToPx(18).toFloat())
            contentDescription = "Colar conteúdo copiado"
            setOnClickListener { controller.pasteClipboardSuggestion() }
        }
        pill.addView(ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(32), dpToPx(32))
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (item is com.example.clipboard.ClipboardSuggestion.Image && item.thumbnail != null) {
                setImageBitmap(item.thumbnail)
            } else {
                setImageResource(if (item is com.example.clipboard.ClipboardSuggestion.Image)
                    android.R.drawable.ic_menu_gallery else R.drawable.ic_clipboard)
                setColorFilter(keyTextColor)
            }
        })
        pill.addView(TextView(context).apply {
            text = if (item is com.example.clipboard.ClipboardSuggestion.Text) item.text else "Colar imagem"
            setTextColor(keyTextColor)
            textSize = G.TOOL_FONT_SP
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dpToPx(8), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(pill)
        row.addView(toolbarText("×", 26f).apply {
            layoutParams = LayoutParams(dpToPx(48), LayoutParams.MATCH_PARENT)
            contentDescription = "Dispensar sugestão de clipboard"
            setOnClickListener { controller.clipboardSuggestion.dismiss() }
        })
        toolbarContainer.addView(row)
    }

    private fun toolbarIcon(drawable: Int, label: String): ImageView = ImageView(context).apply {
        contentDescription = label
        setImageDrawable(ContextCompat.getDrawable(context, drawable))
        setColorFilter(keyTextColor)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        background = RippleDrawable(ColorStateList.valueOf(pressedColor),
            createRoundedDrawable(Color.TRANSPARENT, dpToPx(5).toFloat()), null)
    }

    private fun toolbarText(label: String, fontSize: Float = G.TOOL_FONT_SP): TextView = TextView(context).apply {
        text = label
        textSize = fontSize
        setTextColor(keyTextColor)
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dpToPx(4), 0, dpToPx(4), 0)
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        background = RippleDrawable(ColorStateList.valueOf(pressedColor),
            createRoundedDrawable(Color.TRANSPARENT, dpToPx(5).toFloat()), null)
    }

    private fun addToolbarDivider(row: LinearLayout) {
        row.addView(View(context).apply {
            setBackgroundColor(pillBorderColor)
            layoutParams = LayoutParams(dpToPx(1), dpToPx(20))
        })
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
            isMotionEventSplittingEnabled = true
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
        val sortedLanguages = SupportedLanguages.getSortedWithPreferred(lastLang,
            controller.languagePreferences.translationLanguages)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(2)
                bottomMargin = dpToPx(2)
            }
        }

        val langRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            gravity = Gravity.CENTER_VERTICAL
        }

        for (lang in sortedLanguages) {
            val isPreferred = lang.languageCode.equals(lastLang, ignoreCase = true)
            val pill = TextView(context).apply {
                text = if (lang.secondaryName == null) lang.displayName else {
                    val label = "${lang.displayName}\n${lang.secondaryName}"
                    SpannableString(label).apply {
                        val start = lang.displayName.length + 1
                        setSpan(RelativeSizeSpan(0.8f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(ForegroundColorSpan(Color.parseColor("#CBD5E1")), start, length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTextColor(Color.WHITE)
                typeface = if (isPreferred) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val bg = createRoundedDrawable(
                    if (isPreferred) primaryActionColor else keyBgColor,
                    dpToPx(14).toFloat(),
                    if (isPreferred) Color.TRANSPARENT else pillBorderColor,
                    1
                )
                background = keyBackground(bg)
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
            isMotionEventSplittingEnabled = true
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
            isMotionEventSplittingEnabled = true
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
            isMotionEventSplittingEnabled = true
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(keySubTextColor)
            textSize = 12.5f
            val bg = createRoundedDrawable(Color.parseColor("#2D323F"), dpToPx(6).toFloat())
            background = keyBackground(bg)
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
            background = keyBackground(bg)
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
            isMotionEventSplittingEnabled = true
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
            background = keyBackground(bg)
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
            isMotionEventSplittingEnabled = true
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
        cancelPointers()
        keyBindings.clear()
        keyboardKeysContainer.centralized = !clipboardOpen && controller.currentMode != KeyboardMode.EMOJIS
        keyboardKeysContainer.removeAllViews()

        if (clipboardOpen) {
            renderClipboard()
            return
        }
        when (controller.currentMode) {
            KeyboardMode.LETTERS -> renderLetterKeys()
            KeyboardMode.NUMBERS -> renderNumberKeys()
            KeyboardMode.SYMBOLS -> renderSymbolKeys()
            KeyboardMode.EMOJIS -> renderEmojiKeys()
        }
    }

    private fun renderLetterKeys() {
        keyboardKeysContainer.addView(createDirectRow(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            G.NUMBER_KEY_HEIGHT_DP, G.NUMBER_FONT_SP
        ))
        // Row 1: q w e r t y u i o p
        val r1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        keyboardKeysContainer.addView(createKeyRow(r1))

        // Row 2: nine letters with half-cell insets; ç remains available on long-press c.
        val r2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        keyboardKeysContainer.addView(createKeyRow(r2, inset = true))

        // Row 3: [Shift] z x c v b n m [Backspace]
        val r3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP)).apply {
                bottomMargin = dpToPx(G.KEY_VERTICAL_GAP_DP)
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
        val shiftKey = createSpecialKey(shiftLabel, G.SHIFT_KEY_WEIGHT, shiftBgColor) {
            controller.toggleShift()
        }
        r3.addView(shiftKey)
        addWeightedSpacer(r3, G.THIRD_ROW_SPACER_WEIGHT)

        val r3Letters = listOf("z", "x", "c", "v", "b", "n", "m")
        for (char in r3Letters) {
            r3.addView(createLetterKey(char, G.LETTER_KEY_WEIGHT))
        }

        val backspaceKey = createRepeatKey("⌫", G.BACKSPACE_KEY_WEIGHT, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        addWeightedSpacer(r3, G.THIRD_ROW_SPACER_WEIGHT)
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [?123] [ , ] [ Espaço ] [ . ] [ Action Key ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP))
        }

        val numModeKey = createSpecialKey("?123", G.SYMBOL_KEY_WEIGHT, keyActionBgColor) {
            controller.setMode(KeyboardMode.NUMBERS)
        }
        r4.addView(numModeKey)

        r4.addView(createDirectCharKey(",", G.PUNCTUATION_KEY_WEIGHT))
        r4.addView(createSpaceKey("espaço", G.SPACE_KEY_WEIGHT))
        r4.addView(createDirectCharKey(".", G.PUNCTUATION_KEY_WEIGHT))

        // Vector-based Action Key (Enter / Search / Send / Done / Go)
        val actionKey = createActionKey(G.ACTION_KEY_WEIGHT, primaryActionColor) {
            controller.handleEnter()
        }
        r4.addView(actionKey)

        keyboardKeysContainer.addView(r4)
    }

    private fun renderNumberKeys() {
        // Row 1: 1 2 3 4 5 6 7 8 9 0
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        keyboardKeysContainer.addView(createDirectRow(r1, G.NUMBER_KEY_HEIGHT_DP, G.NUMBER_FONT_SP))

        // Row 2: @ # $ % & * - + ( )
        val r2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")")
        keyboardKeysContainer.addView(createDirectRow(r2))

        keyboardKeysContainer.addView(createDirectRow(listOf("£", "€", "¥", "¢", "°", "©", "®", "™", "\\", "|")))

        // Row 3: [=\<] ! " ' : ; / ? [Backspace]
        val r3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP)).apply {
                bottomMargin = dpToPx(G.KEY_VERTICAL_GAP_DP)
            }
        }

        val moreSymbolsKey = createSpecialKey("=\\<", G.SYMBOL_KEY_WEIGHT, keyActionBgColor) {
            controller.setMode(KeyboardMode.SYMBOLS)
        }
        r3.addView(moreSymbolsKey)

        val r3Symbols = listOf("!", "\"", "'", ":", ";", "/", "?")
        for (sym in r3Symbols) {
            r3.addView(createDirectCharKey(sym, G.LETTER_KEY_WEIGHT))
        }

        val backspaceKey = createRepeatKey("⌫", G.BACKSPACE_KEY_WEIGHT, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [ABC] [ , ] [ Espaço ] [ . ] [ Action Key ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP))
        }

        val abcKey = createSpecialKey("ABC", G.SYMBOL_KEY_WEIGHT, keyActionBgColor) {
            controller.setMode(KeyboardMode.LETTERS)
        }
        r4.addView(abcKey)

        r4.addView(createDirectCharKey(",", G.PUNCTUATION_KEY_WEIGHT))
        r4.addView(createSpaceKey("espaço", G.SPACE_KEY_WEIGHT))
        r4.addView(createDirectCharKey(".", G.PUNCTUATION_KEY_WEIGHT))

        val actionKey = createActionKey(G.ACTION_KEY_WEIGHT, primaryActionColor) {
            controller.handleEnter()
        }
        r4.addView(actionKey)

        keyboardKeysContainer.addView(r4)
    }

    private fun renderSymbolKeys() {
        keyboardKeysContainer.addView(createDirectRow(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            G.NUMBER_KEY_HEIGHT_DP, G.NUMBER_FONT_SP))
        // Row 1: ~ ` | • √ π ÷ × ¶ ∆
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
        keyboardKeysContainer.addView(createDirectRow(r1))

        // Row 2: £ € ¥ ¢ ^ ° = { } \
        val r2 = listOf("£", "€", "¥", "¢", "^", "°", "=", "{", "}", "\\")
        keyboardKeysContainer.addView(createDirectRow(r2))

        // Row 3: [?123] % _ < > [ ] « » [Backspace]
        val r3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP)).apply {
                bottomMargin = dpToPx(G.KEY_VERTICAL_GAP_DP)
            }
        }

        val numKey = createSpecialKey("?123", G.SYMBOL_KEY_WEIGHT, keyActionBgColor) {
            controller.setMode(KeyboardMode.NUMBERS)
        }
        r3.addView(numKey)

        val r3Symbols = listOf("%", "_", "<", ">", "[", "]", "«", "»")
        for (sym in r3Symbols) {
            r3.addView(createDirectCharKey(sym, G.LETTER_KEY_WEIGHT))
        }

        val backspaceKey = createRepeatKey("⌫", G.BACKSPACE_KEY_WEIGHT, keyActionBgColor, onAction = {
            controller.handleBackspace()
        })
        r3.addView(backspaceKey)
        keyboardKeysContainer.addView(r3)

        // Row 4: [ABC] [ , ] [ Espaço ] [ . ] [ Action Key ]
        val r4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP))
        }

        val abcKey = createSpecialKey("ABC", G.SYMBOL_KEY_WEIGHT, keyActionBgColor) {
            controller.setMode(KeyboardMode.LETTERS)
        }
        r4.addView(abcKey)

        r4.addView(createDirectCharKey(",", G.PUNCTUATION_KEY_WEIGHT))
        r4.addView(createSpaceKey("espaço", G.SPACE_KEY_WEIGHT))
        r4.addView(createDirectCharKey(".", G.PUNCTUATION_KEY_WEIGHT))

        val actionKey = createActionKey(G.ACTION_KEY_WEIGHT, primaryActionColor) {
            controller.handleEnter()
        }
        r4.addView(actionKey)

        keyboardKeysContainer.addView(r4)
    }

    // -------------------------------------------------------------
    // KEY BUILDERS
    // -------------------------------------------------------------
    private fun renderClipboard() {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.NUMBER_KEY_HEIGHT_DP))
        }
        header.addView(TextView(context).apply {
            text = "Área de transferência"
            textSize = 16f
            setTextColor(keyTextColor)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        })
        header.addView(TextView(context).apply {
            text = "ABC"
            contentDescription = "Voltar ao teclado"
            setTextColor(keyTextColor)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(dpToPx(48), LayoutParams.MATCH_PARENT)
            setOnClickListener {
                clipboardOpen = false
                controller.setMode(KeyboardMode.LETTERS)
                renderKeys()
            }
        })
        keyboardKeysContainer.addView(header)
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(list)
        }
        keyboardKeysContainer.addView(scroll)
        val entries = if (controller.isSensitiveField) emptyList() else controller.clipboardHistory.entries()
        if (entries.isEmpty()) list.addView(TextView(context).apply {
            text = "Os textos que você copiar aparecerão aqui (até 30 itens)."
            setTextColor(keySubTextColor)
            setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16))
        })
        entries.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(3), 0, dpToPx(3))
            }
            row.addView(TextView(context).apply {
                text = item
                textSize = 16f
                setTextColor(keyTextColor)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                minHeight = dpToPx(48)
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                background = createRoundedDrawable(keyBgColor, dpToPx(6).toFloat())
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { if (!controller.isSensitiveField) controller.handleDirectCharacter(item) }
            })
            row.addView(TextView(context).apply {
                text = "×"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(keySubTextColor)
                contentDescription = "Excluir item"
                layoutParams = LayoutParams(dpToPx(44), dpToPx(48))
                setOnClickListener { controller.clipboardHistory.remove(item); renderKeys() }
            })
            list.addView(row)
        }
    }

    private fun renderEmojiKeys() {
        val categories = listOf(EmojiCategory("Recentes", "", recentEmojis)) + EmojiCatalog.categories
        val categoryRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val categoryScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.NUMBER_KEY_HEIGHT_DP)).apply {
                bottomMargin = dpToPx(4)
            }
            addView(categoryRow)
        }
        categories.forEachIndexed { index, category ->
            categoryRow.addView(TextView(context).apply {
                text = category.icon
                if (index == 0) {
                    setCompoundDrawablesWithIntrinsicBounds(null,
                        ContextCompat.getDrawable(context, R.drawable.ic_recent_emojis)?.apply {
                            setTint(if (emojiCategoryIndex == -1) primaryActionColor else keyTextColor)
                        }, null, null)
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(8), dpToPx(3), dpToPx(8), 0)
                }
                textSize = G.SPECIAL_FONT_SP
                gravity = Gravity.CENTER
                contentDescription = category.name
                layoutParams = LayoutParams(dpToPx(40), LayoutParams.MATCH_PARENT)
                setTextColor(if (index == emojiCategoryIndex + 1) primaryActionColor else keyTextColor)
                setOnClickListener {
                    emojiCategoryIndex = index - 1
                    dismissPopup()
                    renderKeys()
                }
            })
        }
        keyboardKeysContainer.addView(categoryScroll)

        val emojis = categories[emojiCategoryIndex + 1].emojis
        val grid = EmojiSwipeGrid(context) { direction ->
            val next = (emojiCategoryIndex + direction).coerceIn(-1, categories.size - 2)
            if (next != emojiCategoryIndex) {
                emojiCategoryIndex = next
                dismissPopup()
                renderKeys()
            }
        }.apply {
            numColumns = 8
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dpToPx(3)
            isVerticalScrollBarEnabled = false
            setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = dpToPx(G.KEY_VERTICAL_GAP_DP)
            }
            adapter = object : BaseAdapter() {
                override fun getCount() = emojis.size
                override fun getItem(position: Int) = emojis[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val emoji = emojis[position]
                    val key = (convertView as? TextView) ?: TextView(context).apply {
                        textSize = G.LETTER_FONT_SP
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(keyTextColor)
                        layoutParams = android.widget.AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP))
                    }
                    keyBindings.remove(key)
                    key.text = emoji
                    key.contentDescription = emoji
                    key.setOnClickListener { commitEmoji(emoji) }
                    installCharacterTouch(key, emoji, EmojiCatalog.variants[emoji].orEmpty(), direct = true,
                        emoji = true)
                    return key
                }
            }
        }
        keyboardKeysContainer.addView(grid)

        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP))
        }
        bottom.addView(createSpecialKey("ABC", G.SYMBOL_KEY_WEIGHT, keyActionBgColor) {
            controller.setMode(KeyboardMode.LETTERS)
        })
        bottom.addView(createSpaceKey("espaço", G.SPACE_KEY_WEIGHT))
        bottom.addView(createRepeatKey("⌫", G.BACKSPACE_KEY_WEIGHT, keyActionBgColor) {
            controller.handleBackspace()
        })
        bottom.addView(createActionKey(G.ACTION_KEY_WEIGHT, primaryActionColor) { controller.handleEnter() })
        keyboardKeysContainer.addView(bottom)
    }

    private fun addWeightedSpacer(row: LinearLayout, weight: Float, margin: Int = 0) {
        row.addView(View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(margin, 0, margin, 0)
            }
        })
    }

    private fun createKeyRow(chars: List<String>, inset: Boolean = false): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(G.KEY_HEIGHT_DP)).apply {
                bottomMargin = dpToPx(G.KEY_VERTICAL_GAP_DP)
            }
            val insetMargin = dpToPx(G.KEY_HORIZONTAL_GAP_DP) / 4
            if (inset) addWeightedSpacer(this, G.SECOND_ROW_SIDE_INSET_WEIGHT, insetMargin)
            for (char in chars) {
                addView(createLetterKey(char, G.LETTER_KEY_WEIGHT))
            }
            if (inset) addWeightedSpacer(this, G.SECOND_ROW_SIDE_INSET_WEIGHT, insetMargin)
        }
    }

    private fun createDirectRow(
        chars: List<String>, height: Int = G.KEY_HEIGHT_DP, fontSize: Float = G.SYMBOL_FONT_SP
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(height)).apply {
                bottomMargin = dpToPx(G.KEY_VERTICAL_GAP_DP)
            }
            for (char in chars) {
                addView(createDirectCharKey(char, G.LETTER_KEY_WEIGHT, fontSize))
            }
        }
    }

    private fun createLetterKey(char: String, weight: Float): View {
        val frame = FrameLayout(context).apply {
            // The visual gap belongs to the drawable, so touches in the gap reach a key.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            val bg = createRoundedDrawable(keyBgColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat())
            background = keyBackground(bg)
        }

        val isShifted = controller.shiftState != ShiftState.OFF
        val displayChar = if (isShifted) char.uppercase() else char.lowercase()

        val tv = TextView(context).apply {
            text = displayChar
            setTextColor(keyTextColor)
            textSize = G.LETTER_FONT_SP
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        frame.tag = char
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

        }

        installCharacterTouch(frame, char, accents.orEmpty())

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            controller.handleCharacter(char)
        }

        return frame
    }

    private fun createDirectCharKey(char: String, weight: Float, fontSize: Float = G.SYMBOL_FONT_SP): View {
        val frame = FrameLayout(context).apply {
            // The visual gap belongs to the drawable, so touches in the gap reach a key.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            val bg = createRoundedDrawable(keyBgColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat())
            background = keyBackground(bg)
        }

        val tv = TextView(context).apply {
            text = char
            setTextColor(keyTextColor)
            textSize = fontSize
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        installCharacterTouch(frame, char, emptyList(), direct = true)

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
            // The visual gap belongs to the drawable, so touches in the gap reach a key.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            val bg = createRoundedDrawable(backgroundColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat())
            background = keyBackground(bg)
        }

        val tv = TextView(context).apply {
            text = label
            setTextColor(keyTextColor)
            textSize = if (label == "⇧" || label == "⇪") G.SHIFT_FONT_SP else G.SPECIAL_FONT_SP
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)
        if (label == "⇧" || label == "⇪") frame.tag = ShiftState::class.java

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }

        installTapTouch(frame) { onClick() }
        return frame
    }

    private fun createActionKey(
        weight: Float,
        backgroundColor: Int,
        onClick: () -> Unit
    ): View {
        val frame = FrameLayout(context).apply {
            // The visual gap belongs to the drawable, so touches in the gap reach a key.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            val bg = createRoundedDrawable(backgroundColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat())
            background = keyBackground(bg)
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

        installTapTouch(frame) { onClick() }
        return frame
    }

    private fun createSpaceKey(label: String, weight: Float): View {
        val frame = FrameLayout(context).apply {
            // The visual gap belongs to the drawable, so touches in the gap reach a key.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            val bg = createRoundedDrawable(keyBgColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat())
            background = keyBackground(bg)
        }

        val tv = TextView(context).apply {
            text = label
            setTextColor(keySubTextColor)
            textSize = G.SPACE_LABEL_FONT_SP
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            controller.handleSpace()
        }

        installTapTouch(frame) { controller.handleSpace() }
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
            // The visual gap belongs to the drawable, so touches in the gap reach a key.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            val bg = createRoundedDrawable(backgroundColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat())
            background = keyBackground(bg)
        }

        val tv = TextView(context).apply {
            text = label
            setTextColor(keyTextColor)
            textSize = G.BACKSPACE_FONT_SP
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(tv)

        installBinding(frame, KeyBinding(onAction, repeat = true))
        frame.setOnClickListener { onAction() } // Accessibility only.

        return frame
    }

    // -------------------------------------------------------------
    // ACCENTS POPUP
    // -------------------------------------------------------------
    private data class AccentStrip(
        val popup: PopupWindow,
        val cells: List<TextView>,
        val screenLeft: Int,
        val cellWidth: Int,
        var selected: Int = 0
    ) {
        fun select(index: Int) {
            selected = index.coerceIn(0, cells.lastIndex)
            cells.forEachIndexed { i, cell ->
                cell.setBackgroundColor(if (i == selected) Color.parseColor("#475569") else Color.TRANSPARENT)
            }
        }
    }

    private fun showAboveKey(anchor: View, content: View, width: Int, height: Int,
        firstCellWidth: Int = width): Pair<PopupWindow, Int> {
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        val rootScreen = IntArray(2)
        anchor.getLocationInWindow(location)
        rootView.getLocationInWindow(rootLocation)
        rootView.getLocationOnScreen(rootScreen)
        val x = (location[0] + anchor.width / 2 - firstCellWidth / 2).coerceIn(
            rootLocation[0], (rootLocation[0] + rootView.width - width).coerceAtLeast(rootLocation[0]))
        val y = location[1] - height - dpToPx(G.ACCENT_GAP_DP)
        val popup = PopupWindow(content, width, height, false).apply {
            isTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            isClippingEnabled = false
        }
        if (anchor.windowToken != null) popup.showAtLocation(rootView, Gravity.TOP or Gravity.LEFT, x, y)
        return popup to (x + rootScreen[0] - rootLocation[0])
    }

    private fun showAccentsPopup(anchor: View, accents: List<String>): AccentStrip {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            background = createRoundedDrawable(Color.parseColor("#1B1F28"), dpToPx(6).toFloat(),
                Color.parseColor("#383E4C"), dpToPx(1))
        }
        val cellWidth = anchor.width.coerceAtLeast(dpToPx(28))
            .coerceAtMost((rootView.width / accents.size).coerceAtLeast(dpToPx(28)))
        val height = dpToPx(G.KEY_HEIGHT_DP)
        val cells = accents.map { accent ->
            TextView(context).apply {
                text = if (controller.shiftState != ShiftState.OFF) accent.uppercase() else accent.lowercase()
                textSize = G.LETTER_FONT_SP
                includeFontPadding = false
                setTextColor(keyTextColor)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(cellWidth, height)
                row.addView(this)
            }
        }
        val (popup, screenLeft) = showAboveKey(anchor, row, cellWidth * accents.size, height, cellWidth)
        activePopup = popup
        return AccentStrip(popup, cells, screenLeft, cellWidth).apply { select(0) }
    }

    private fun installCharacterTouch(key: View, char: String, accents: List<String>, direct: Boolean = false, emoji: Boolean = false) {
        val commit: (String) -> Unit = { selected ->
            if (emoji) commitEmoji(selected)
            else if (direct) controller.handleDirectCharacter(selected)
            else controller.handleCharacter(selected)
        }
        installBinding(key, KeyBinding({ commit(char) }, accents, commit))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installBinding(key: View, binding: KeyBinding) {
        keyBindings[key] = binding
        // Retain direct View dispatch for emoji scrolling and accessibility tests.
        // Normal rows receive touch exclusively through KeyboardSurfaceView.
        key.setOnTouchListener { _, event ->
            handleSurfaceTouch(event, key, fromSurface = false)
            true
        }
    }

    private fun handleSurfaceTouch(event: MotionEvent, resolved: View?, fromSurface: Boolean = true) {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        if (!fromSurface) InputMetrics.event(event.actionMasked, event.pointerCount)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (fromSurface && event.actionMasked == MotionEvent.ACTION_DOWN) cancelPointers()
                val binding = resolved?.let { keyBindings[it] }
                if (resolved == null || binding == null) {
                    InputMetrics.miss()
                    return
                }
                InputMetrics.resolved()
                val press = Press(resolved, binding, event.getX(index))
                pointers.put(id, press)
                resolved.isPressed = true
                if (binding.repeat) {
                    activate(press)
                    // The action may rebuild the keyboard; never schedule a stale press.
                    if (pointers[id] !== press) return
                    val timer = object : Runnable {
                        override fun run() {
                            if (pointers[id] !== press) return
                            activate(press)
                            if (pointers[id] === press) handler.postDelayed(this, 50)
                        }
                    }
                    press.timer = timer
                    handler.postDelayed(timer, 350)
                } else if (binding.accents.isNotEmpty()) {
                    val timer = Runnable {
                        if (pointers[id] === press) {
                            press.strip = showAccentsPopup(resolved, binding.accents)
                            resolved.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    press.timer = timer
                    handler.postDelayed(timer, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                keyboardKeysContainer.getLocationOnScreen(screenPosition)
                for (i in 0 until event.pointerCount) {
                    val press = pointers[event.getPointerId(i)] ?: continue
                    val strip = press.strip ?: continue
                    if (kotlin.math.abs(event.getX(i) - press.startX) > android.view.ViewConfiguration.get(context).scaledTouchSlop) {
                        // Direct emoji/key dispatch has local coordinates; central dispatch is surface-local.
                        val origin = if (resolved != null) {
                            resolved.getLocationOnScreen(screenPosition)
                            screenPosition[0]
                        } else screenPosition[0]
                        strip.select(((event.getX(i) + origin - strip.screenLeft) / strip.cellWidth).toInt())
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val press = pointers[id] ?: return
                pointers.remove(id) // Remove before synchronous actions that may change mode.
                press.timer?.let { handler.removeCallbacks(it) }
                updatePressed(press.key)
                val strip = press.strip
                strip?.popup?.dismiss()
                if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                    if (!press.binding.repeat) InputMetrics.abandoned()
                    return
                }
                if (!press.binding.repeat) {
                    InputMetrics.activation()
                    if (strip != null) press.binding.accentAction?.invoke(press.binding.accents[strip.selected])
                    else press.binding.action()
                    press.key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (fromSurface) cancelPointers() // Android cancels the entire surface stream.
                else for (i in 0 until event.pointerCount) cancelPointer(event.getPointerId(i))
            }
        }
    }

    private fun activate(press: Press) {
        InputMetrics.activation()
        press.binding.action()
        press.key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun updatePressed(key: View) {
        var held = false
        for (i in 0 until pointers.size()) if (pointers.valueAt(i).key === key) held = true
        key.isPressed = held
    }

    private fun cancelPointers() {
        while (pointers.size() > 0) cancelPointer(pointers.keyAt(0))
    }

    private fun cancelPointer(id: Int) {
        val press = pointers[id] ?: return
        pointers.remove(id)
        press.timer?.let { handler.removeCallbacks(it) }
        press.strip?.popup?.dismiss()
        updatePressed(press.key)
        if (!press.binding.repeat) InputMetrics.abandoned()
    }

    fun dismissPopup() {
        cancelPointers()
        activePopup?.dismiss()
        activePopup = null
    }

    override fun onDetachedFromWindow() {
        dismissPopup()
        super.onDetachedFromWindow()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat() * G.SCALE,
            context.resources.displayMetrics
        ).roundToInt()
    }

    private fun keyboardKeysHeight(): Int = dpToPx(G.NUMBER_KEY_HEIGHT_DP) +
        4 * dpToPx(G.KEY_HEIGHT_DP) + 4 * dpToPx(G.KEY_VERTICAL_GAP_DP)

    private fun keyBackground(normal: GradientDrawable): android.graphics.drawable.Drawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                createRoundedDrawable(pressedColor, dpToPx(G.KEY_CORNER_RADIUS_DP).toFloat()))
            addState(intArrayOf(), normal)
        }
        val gap = dpToPx(G.KEY_HORIZONTAL_GAP_DP) / 2
        return android.graphics.drawable.InsetDrawable(states, gap, 0, gap, 0)
    }

    private fun installTapTouch(key: View, action: () -> Unit) {
        key.setOnClickListener { action() } // Accessibility activation only.
        installBinding(key, KeyBinding(action))
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
