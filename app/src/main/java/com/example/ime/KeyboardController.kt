package com.example.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.correction.CorrectionService
import com.example.privacy.SensitiveFieldDetector
import com.example.privacy.TranslationConsentManager
import com.example.translation.SupportedLanguages
import com.example.translation.TranslationResult
import com.example.translation.TranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class KeyboardMode {
    LETTERS,
    NUMBERS,
    SYMBOLS
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

    var currentMode: KeyboardMode = KeyboardMode.LETTERS
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var uiState: TextActionUiState = TextActionUiState.Idle
        private set(value) {
            field = value
            if (value is TextActionUiState.Idle) {
                toolbarMode = ToolbarMode.SUGGESTIONS
                refreshSuggestions()
            } else {
                currentWord = null
                suggestions = emptyList()
            }
        }

    var toolbarMode: ToolbarMode = ToolbarMode.SUGGESTIONS
        private set
    var suggestions: List<String> = emptyList()
        private set
    private var currentWord: CurrentWord? = null

    private var inputConnection: InputConnection? = null
    var currentEditorInfo: EditorInfo? = null
        private set

    private var lastShiftPressTime: Long = 0

    val isSensitiveField: Boolean
        get() = SensitiveFieldDetector.isSensitive(currentEditorInfo)

    fun updateInputConnection(ic: InputConnection?, editorInfo: EditorInfo?) {
        this.inputConnection = ic
        this.currentEditorInfo = editorInfo
        toolbarMode = ToolbarMode.SUGGESTIONS
        currentWord = null
        suggestions = emptyList()
        if (uiState !is TextActionUiState.Processing) {
            uiState = TextActionUiState.Idle
        }
        onStateChanged()
    }

    fun handleCharacter(char: String) {
        val textToInsert = when (shiftState) {
            ShiftState.CAPS_LOCK, ShiftState.ON -> char.uppercase()
            ShiftState.OFF -> char.lowercase()
        }
        inputConnection?.commitText(textToInsert, 1)

        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
        }
        onTypingChanged()
    }

    fun handleDirectCharacter(char: String) {
        inputConnection?.commitText(char, 1)
        onTypingChanged()
    }

    fun handleBackspace() {
        val ic = inputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        onTypingChanged()
    }

    fun handleSpace() {
        inputConnection?.commitText(" ", 1)
        onTypingChanged()
    }

    fun handleEnter() {
        val ic = inputConnection ?: return
        val editorInfo = currentEditorInfo

        if (editorInfo != null) {
            val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                onTypingChanged()
                return
            }
        }
        ic.commitText("\n", 1)
        onTypingChanged()
    }

    fun toggleToolbarMode() {
        if (uiState !is TextActionUiState.Idle) return
        toolbarMode = if (toolbarMode == ToolbarMode.SUGGESTIONS) ToolbarMode.TOOLS else ToolbarMode.SUGGESTIONS
        if (toolbarMode == ToolbarMode.SUGGESTIONS) refreshSuggestions()
        onStateChanged()
    }

    private fun refreshSuggestions() {
        currentWord = if (uiState is TextActionUiState.Idle) {
            CurrentWordExtractor.extract(inputConnection, currentEditorInfo)
        } else null
        suggestions = currentWord?.let { LocalSuggestionEngine.suggest(it.text) }.orEmpty()
    }

    private fun onTypingChanged() {
        toolbarMode = ToolbarMode.SUGGESTIONS
        refreshSuggestions()
        onStateChanged()
    }

    fun onCursorChanged() {
        refreshSuggestions()
        onStateChanged()
    }

    fun applySuggestion(candidate: String): Boolean {
        if (uiState !is TextActionUiState.Idle || isSensitiveField || candidate !in suggestions) return false
        val expected = currentWord ?: return false
        val replaced = CurrentWordReplacement.replace(inputConnection, currentEditorInfo, expected, candidate)
        onTypingChanged()
        return replaced
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

        val extracted = SentenceExtractor.extract(ic)
        if (extracted == null || extracted.textToCorrect.isBlank()) {
            uiState = TextActionUiState.Error("Nenhum texto encontrado.")
            onStateChanged()
            return
        }

        val job = coroutineScope.launch {
            val result = correctionService.correct(extracted.textToCorrect)

            withContext(Dispatchers.Main) {
                if (result.isSuccess && !result.correctedText.isNullOrBlank()) {
                    uiState = TextActionUiState.Preview(
                        title = "Correção",
                        originalText = extracted.textToCorrect,
                        resultText = result.correctedText,
                        extractedSentence = extracted
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
            val preferredLang = consentManager.getLastTranslationLanguageCode()
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

        consentManager.setLastTranslationLanguageCode(targetLanguageCode)

        val ic = inputConnection
        if (ic == null) {
            uiState = TextActionUiState.Error("Não foi possível acessar o texto.")
            onStateChanged()
            return
        }

        val extracted = SentenceExtractor.extract(ic)
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
                            extractedSentence = extracted
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
        TextReplacementController.replace(
            inputConnection = inputConnection,
            extractedSentence = state.extractedSentence,
            correctedText = state.resultText
        )
        uiState = TextActionUiState.Idle
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
