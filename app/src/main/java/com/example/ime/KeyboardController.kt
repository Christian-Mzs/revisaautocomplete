package com.example.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.correction.CorrectionService
import com.example.privacy.SensitiveFieldDetector
import com.example.privacy.TranslationConsentManager
import com.example.settings.LanguagePreferences
import com.example.translation.SupportedLanguages
import com.example.translation.TranslationResult
import com.example.translation.TranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

enum class KeyboardMode {
    LETTERS,
    NUMBERS,
    SYMBOLS,
    EMOJIS
}

enum class ShiftState {
    OFF,
    ON,
    CAPS_LOCK
}

enum class ActionKeyType {
    SEARCH,
    SEND,
    DONE,
    GO,
    NEXT,
    ENTER
}

class KeyboardController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val translationService: TranslationService = TranslationService(),
    private val correctionService: CorrectionService = CorrectionService(translationService),
    val consentManager: TranslationConsentManager = TranslationConsentManager.getInstance(context),
    private val onStateChanged: () -> Unit,
    val onSwitchImeRequested: () -> Unit = {}
) {
    val clipboardHistory = com.example.clipboard.ClipboardHistory(context)
    val clipboardSuggestion = com.example.clipboard.ClipboardSuggestionController(context, coroutineScope, onStateChanged)
    val languagePreferences = LanguagePreferences(context)

    var currentMode: KeyboardMode = KeyboardMode.LETTERS
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var uiState: TextActionUiState = TextActionUiState.Idle
        private set

    private var inputConnection: InputConnection? = null
    var currentEditorInfo: EditorInfo? = null
        private set

    private var lastShiftPressTime: Long = 0

    val isSensitiveField: Boolean
        get() = SensitiveFieldDetector.isSensitive(currentEditorInfo)

    fun updateInputConnection(ic: InputConnection?, editorInfo: EditorInfo?) {
        this.inputConnection = ic
        this.currentEditorInfo = editorInfo
        if (uiState !is TextActionUiState.Processing) {
            uiState = TextActionUiState.Idle
        }
        clipboardSuggestion.updateEditor(ic, editorInfo, isSensitiveField)
        onStateChanged()
    }

    fun pasteClipboardSuggestion() {
        if (!clipboardSuggestion.paste()) {
            android.widget.Toast.makeText(context, "Não foi possível colar o conteúdo.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCharacter(char: String) {
        InputMetrics.character()
        val textToInsert = when (shiftState) {
            ShiftState.CAPS_LOCK, ShiftState.ON -> char.uppercase()
            ShiftState.OFF -> char.lowercase()
        }
        inputConnection?.let { commit(it, textToInsert) }

        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
            onStateChanged()
        }
    }

    fun handleDirectCharacter(char: String) {
        InputMetrics.direct()
        inputConnection?.let { commit(it, char) }
    }

    private fun commit(ic: InputConnection, text: String) {
        InputMetrics.commit()
        InputMetrics.commitResult(ic.commitText(text, 1))
    }

    fun handleBackspace() {
        val ic = inputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            commit(ic, "")
        } else {
            val before = if (isSensitiveField) null else ic.getTextBeforeCursor(64, 0)?.toString()
            if (before == null) {
                InputMetrics.delete()
                if (!ic.deleteSurroundingTextInCodePoints(1, 0)) {
                    InputMetrics.delete()
                    ic.deleteSurroundingText(1, 0)
                }
            } else {
                val length = GraphemeBackspace.deletionLength(before)
                if (length > 0) {
                    InputMetrics.delete()
                    ic.deleteSurroundingText(length, 0)
                }
            }
        }
    }

    fun handleSpace() {
        inputConnection?.let { commit(it, " ") }
    }

    fun handleEnter() {
        val ic = inputConnection ?: return
        val editorInfo = currentEditorInfo

        if (editorInfo != null) {
            val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                InputMetrics.editorAction()
                ic.performEditorAction(action)
                return
            }
        }
        commit(ic, "\n")
    }

    fun getActionKeyType(): ActionKeyType {
        val info = currentEditorInfo ?: return ActionKeyType.ENTER
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        return when (action) {
            EditorInfo.IME_ACTION_SEARCH -> ActionKeyType.SEARCH
            EditorInfo.IME_ACTION_SEND -> ActionKeyType.SEND
            EditorInfo.IME_ACTION_DONE -> ActionKeyType.DONE
            EditorInfo.IME_ACTION_GO -> ActionKeyType.GO
            EditorInfo.IME_ACTION_NEXT -> ActionKeyType.NEXT
            else -> ActionKeyType.ENTER
        }
    }

    fun toggleShift() {
        val now = System.currentTimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> {
                if (now - lastShiftPressTime < 400) {
                    ShiftState.CAPS_LOCK
                } else {
                    ShiftState.OFF
                }
            }
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
        lastShiftPressTime = now
        onStateChanged()
    }

    fun setMode(mode: KeyboardMode) {
        currentMode = mode
        onStateChanged()
    }

    // -------------------------------------------------------------
    // CORRECTION FLOW
    // -------------------------------------------------------------
    fun requestCorrection() {
        // SECURITY CHECK: Block before reading any text or calling network
        if (isSensitiveField) {
            uiState = TextActionUiState.Error("Desativado em campos de senha.")
            onStateChanged()
            return
        }

        if (!consentManager.hasAcceptedConsent()) {
            uiState = TextActionUiState.ConsentRequired(TextActionUiState.PendingAction.Correction)
            onStateChanged()
            return
        }

        executeCorrection()
    }

    private fun executeCorrection() {
        if (isSensitiveField) return

        val ic = inputConnection
        if (ic == null) {
            uiState = TextActionUiState.Error("Não foi possível acessar o texto.")
            onStateChanged()
            return
        }

        val extracted = TextExtractor.extract(ic)
        if (extracted == null || extracted.textToCorrect.isBlank()) {
            uiState = TextActionUiState.Error("Nenhum texto encontrado.")
            onStateChanged()
            return
        }

        val job = coroutineScope.launch {
            val result = correctionService.correct(extracted.textToCorrect,
                languagePreferences.correctionOutputLanguageCode)

            withContext(Dispatchers.Main) {
                if (result.isSuccess && !result.correctedText.isNullOrBlank()) {
                    uiState = TextActionUiState.Preview(
                        title = "Correção",
                        originalText = extracted.textToCorrect,
                        resultText = result.correctedText,
                        extractedRange = extracted
                    )
                } else {
                    val errMsg = result.errorMessage ?: "Não foi possível corrigir agora."
                    uiState = TextActionUiState.Error(errMsg)
                }
                onStateChanged()
            }
        }

        uiState = TextActionUiState.Processing("Corrigindo…", job)
        onStateChanged()
    }

    // -------------------------------------------------------------
    // TRANSLATION FLOW
    // -------------------------------------------------------------
    fun requestTranslationPicker() {
        // SECURITY CHECK: Block before reading any text or calling network
        if (isSensitiveField) {
            uiState = TextActionUiState.Error("Desativado em campos de senha.")
            onStateChanged()
            return
        }

        if (!consentManager.hasAcceptedConsent()) {
            val preferredLang = SupportedLanguages.getSortedWithPreferred(
                consentManager.getLastTranslationLanguageCode(), languagePreferences.translationLanguages).first().languageCode
            uiState = TextActionUiState.ConsentRequired(TextActionUiState.PendingAction.Translation(preferredLang))
            onStateChanged()
            return
        }

        uiState = TextActionUiState.SelectingLanguage
        onStateChanged()
    }

    fun selectLanguageAndTranslate(targetLanguageCode: String) {
        // SECURITY CHECK
        if (isSensitiveField) return
        if (languagePreferences.translationLanguages.none { it.languageCode == targetLanguageCode }) {
            requestTranslationPicker()
            return
        }

        consentManager.setLastTranslationLanguageCode(targetLanguageCode)

        val ic = inputConnection
        if (ic == null) {
            uiState = TextActionUiState.Error("Não foi possível acessar o texto.")
            onStateChanged()
            return
        }

        val extracted = TextExtractor.extract(ic)
        if (extracted == null || extracted.textToCorrect.isBlank()) {
            uiState = TextActionUiState.Error("Nenhum texto encontrado.")
            onStateChanged()
            return
        }

        val langName = SupportedLanguages.find(targetLanguageCode)?.displayName ?: targetLanguageCode

        val job = coroutineScope.launch {
            val result = translationService.translate(
                text = extracted.textToCorrect,
                sourceLanguage = "auto",
                targetLanguage = targetLanguageCode
            )

            withContext(Dispatchers.Main) {
                when (result) {
                    is TranslationResult.Success -> {
                        uiState = TextActionUiState.Preview(
                            title = "Tradução · $langName",
                            originalText = extracted.textToCorrect,
                            resultText = result.translatedText,
                            extractedRange = extracted
                        )
                    }
                    is TranslationResult.Error -> {
                        uiState = TextActionUiState.Error(result.errorMessage)
                    }
                }
                onStateChanged()
            }
        }

        uiState = TextActionUiState.Processing("Traduzindo para $langName…", job)
        onStateChanged()
    }

    // -------------------------------------------------------------
    // SHARED ACTIONS
    // -------------------------------------------------------------
    fun acceptConsentAndProceed(pendingAction: TextActionUiState.PendingAction) {
        consentManager.setConsentAccepted(true)
        when (pendingAction) {
            is TextActionUiState.PendingAction.Correction -> executeCorrection()
            is TextActionUiState.PendingAction.Translation -> selectLanguageAndTranslate(pendingAction.targetLanguageCode)
        }
    }

    fun cancelAction() {
        if (uiState is TextActionUiState.Processing) {
            (uiState as TextActionUiState.Processing).cancelJob.cancel()
        }
        uiState = TextActionUiState.Idle
        onStateChanged()
    }

    fun applyResult() {
        val state = uiState as? TextActionUiState.Preview ?: return
        if (isSensitiveField) return
        val replaced = TextReplacementController.replace(
            inputConnection = inputConnection,
            extractedRange = state.extractedRange,
            correctedText = state.resultText
        )
        uiState = if (replaced) TextActionUiState.Idle
            else TextActionUiState.Error("O texto mudou ou o editor recusou a substituição.")
        onStateChanged()
    }

    fun togglePreviewOriginal() {
        val state = uiState as? TextActionUiState.Preview ?: return
        state.isShowingOriginal = !state.isShowingOriginal
        onStateChanged()
    }

    fun dismissError() {
        uiState = TextActionUiState.Idle
        onStateChanged()
    }
}
