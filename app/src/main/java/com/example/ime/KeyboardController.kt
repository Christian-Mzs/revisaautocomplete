package com.example.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.codex.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import com.example.privacy.SensitiveFieldDetector
import com.example.privacy.TranslationConsentManager
import com.example.settings.LanguagePreferences
import com.example.translation.SupportedLanguages
import kotlinx.coroutines.CoroutineScope
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
    private val textEngine: CodexTextEngine? = null,
    val consentManager: TranslationConsentManager = TranslationConsentManager.getInstance(context),
    private val onStateChanged: () -> Unit,
    val onSwitchImeRequested: () -> Unit = {}
) {
    private val engine: CodexTextEngine by lazy { textEngine ?: CodexRuntime.getInstance(context) }

    val clipboardHistory = com.example.clipboard.ClipboardHistory(context)
    val clipboardSuggestion = com.example.clipboard.ClipboardSuggestionController(context, onStateChanged)
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
        if (inputConnection !== ic || currentEditorInfo !== editorInfo) cancelAction()
        this.inputConnection = ic
        this.currentEditorInfo = editorInfo
        if (uiState !is TextActionUiState.Processing) {
            uiState = TextActionUiState.Idle
        }
        clipboardSuggestion.updateEditor(ic, isSensitiveField)
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
        if (uiState is TextActionUiState.Processing) return
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
        startTextAction(TextOperation(), "Correção", "Corrigindo…")
    }

    private fun startTextAction(operation: TextOperation, title: String, processing: String) {
        if (isSensitiveField || uiState is TextActionUiState.Processing) return
        val ic = inputConnection ?: run {
            uiState = TextActionUiState.Error("Não foi possível acessar o texto.")
            onStateChanged(); return
        }
        val editor = currentEditorInfo
        val job = coroutineScope.launch(start=CoroutineStart.LAZY) {
            try {
                engine.ensureRuntimeReady()
                ensureActive()
                if (isSensitiveField || inputConnection !== ic || currentEditorInfo !== editor) return@launch
                if (!engine.loginStatus().connected) {
                    uiState = TextActionUiState.LoginRequired
                    onStateChanged(); return@launch
                }
                ensureActive()
                if (isSensitiveField || inputConnection !== ic || currentEditorInfo !== editor) return@launch
                val extracted = TextExtractor.extract(ic)
                if (extracted == null || extracted.textToCorrect.isBlank()) {
                    uiState = TextActionUiState.Error("Nenhum texto encontrado.")
                    onStateChanged(); return@launch
                }
                uiState = TextActionUiState.Processing(processing, coroutineContext[kotlinx.coroutines.Job]!!)
                onStateChanged()
                val result = engine.processText(extracted.textToCorrect, operation)
                ensureActive()
                if (isSensitiveField || inputConnection !== ic || currentEditorInfo !== editor) return@launch
                uiState = TextActionUiState.Preview(title, extracted.textToCorrect, result, extracted)
                onStateChanged()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                uiState = TextActionUiState.Error(e.message ?: "Não foi possível processar com Codex.")
                onStateChanged()
            }
        }
        uiState = TextActionUiState.Processing("Preparando…", job)
        onStateChanged(); job.start()
    }

    fun openChatGptSettings() {
        context.startActivity(android.content.Intent(context, com.example.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // -------------------------------------------------------------
    // TRANSLATION FLOW
    // -------------------------------------------------------------
    fun requestTranslationPicker() {
        if (uiState is TextActionUiState.Processing) return
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
        if (isSensitiveField || uiState is TextActionUiState.Processing) return
        if (!consentManager.hasAcceptedConsent()) {
            uiState = TextActionUiState.ConsentRequired(TextActionUiState.PendingAction.Translation(targetLanguageCode))
            onStateChanged(); return
        }
        if (languagePreferences.translationLanguages.none { it.languageCode == targetLanguageCode }) {
            requestTranslationPicker()
            return
        }

        consentManager.setLastTranslationLanguageCode(targetLanguageCode)

        val language = SupportedLanguages.find(targetLanguageCode) ?: return
        startTextAction(TextOperation(TextMode.TRANSLATION, language),
            "Tradução · ${language.displayName}", "Traduzindo para ${language.displayName}…")
    }

    // -------------------------------------------------------------
    // SHARED ACTIONS
    // -------------------------------------------------------------
    fun acceptConsentAndProceed(pendingAction: TextActionUiState.PendingAction) {
        if (isSensitiveField) return
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
