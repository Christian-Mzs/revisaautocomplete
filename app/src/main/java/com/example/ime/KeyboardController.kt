package com.example.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.correction.CorrectionResult
import com.example.correction.CorrectionService
import com.example.privacy.SensitiveFieldDetector
import com.example.privacy.TranslationConsentManager
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

sealed class CorrectionUiState {
    object Idle : CorrectionUiState()
    object ConsentRequired : CorrectionUiState()
    data class Correcting(val cancelJob: Job) : CorrectionUiState()
    data class Preview(
        val result: CorrectionResult,
        val extractedSentence: ExtractedSentence,
        var isShowingOriginal: Boolean = false
    ) : CorrectionUiState()
    data class Error(val message: String) : CorrectionUiState()
}

class KeyboardController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val correctionService: CorrectionService = CorrectionService(),
    private val consentManager: TranslationConsentManager = TranslationConsentManager.getInstance(context),
    private val onStateChanged: () -> Unit,
    val onSwitchImeRequested: () -> Unit = {}
) {

    var currentMode: KeyboardMode = KeyboardMode.LETTERS
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var correctionUiState: CorrectionUiState = CorrectionUiState.Idle
        private set

    private var inputConnection: InputConnection? = null
    private var currentEditorInfo: EditorInfo? = null

    private var lastShiftPressTime: Long = 0

    val isSensitiveField: Boolean
        get() = SensitiveFieldDetector.isSensitive(currentEditorInfo)

    fun updateInputConnection(ic: InputConnection?, editorInfo: EditorInfo?) {
        this.inputConnection = ic
        this.currentEditorInfo = editorInfo
        // If current state was preview or error, reset to idle when focus changes
        if (correctionUiState !is CorrectionUiState.Correcting) {
            correctionUiState = CorrectionUiState.Idle
        }
        onStateChanged()
    }

    fun handleCharacter(char: String) {
        val textToInsert = when (shiftState) {
            ShiftState.CAPS_LOCK, ShiftState.ON -> char.uppercase()
            ShiftState.OFF -> char.lowercase()
        }

        inputConnection?.commitText(textToInsert, 1)

        // If shift was ON (single character uppercase), revert to OFF
        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
            onStateChanged()
        }
    }

    fun handleDirectCharacter(char: String) {
        inputConnection?.commitText(char, 1)
    }

    fun handleBackspace() {
        val ic = inputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    fun handleSpace() {
        inputConnection?.commitText(" ", 1)
    }

    fun handleEnter() {
        val ic = inputConnection ?: return
        val editorInfo = currentEditorInfo

        if (editorInfo != null) {
            val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }

        // Default newline
        ic.commitText("\n", 1)
    }

    fun toggleShift() {
        val now = System.currentTimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> {
                // If tapped twice quickly (within 400ms), lock caps
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

    fun requestCorrection() {
        if (isSensitiveField) {
            correctionUiState = CorrectionUiState.Error("Correção desativada em campos de senha.")
            onStateChanged()
            return
        }

        // Privacy check
        if (!consentManager.hasAcceptedConsent()) {
            correctionUiState = CorrectionUiState.ConsentRequired
            onStateChanged()
            return
        }

        executeCorrection()
    }

    fun acceptConsentAndCorrect() {
        consentManager.setConsentAccepted(true)
        executeCorrection()
    }

    fun declineConsent() {
        correctionUiState = CorrectionUiState.Idle
        onStateChanged()
    }

    private fun executeCorrection() {
        val ic = inputConnection
        if (ic == null) {
            correctionUiState = CorrectionUiState.Error("Não foi possível acessar o texto.")
            onStateChanged()
            return
        }

        val extracted = SentenceExtractor.extract(ic)
        if (extracted == null || extracted.textToCorrect.isBlank()) {
            correctionUiState = CorrectionUiState.Error("Nenhum texto encontrado para corrigir.")
            onStateChanged()
            return
        }

        val isDiag = consentManager.isDiagnosticModeEnabled()

        val job = coroutineScope.launch {
            val result = correctionService.correct(
                originalText = extracted.textToCorrect,
                includeDiagnostics = isDiag
            )

            withContext(Dispatchers.Main) {
                if (result.isSuccess && !result.correctedText.isNullOrBlank()) {
                    correctionUiState = CorrectionUiState.Preview(
                        result = result,
                        extractedSentence = extracted,
                        isShowingOriginal = false
                    )
                } else {
                    val errMsg = result.errorMessage ?: "Não foi possível corrigir agora."
                    correctionUiState = CorrectionUiState.Error(errMsg)
                }
                onStateChanged()
            }
        }

        correctionUiState = CorrectionUiState.Correcting(job)
        onStateChanged()
    }

    fun cancelCorrection() {
        if (correctionUiState is CorrectionUiState.Correcting) {
            (correctionUiState as CorrectionUiState.Correcting).cancelJob.cancel()
        }
        correctionUiState = CorrectionUiState.Idle
        onStateChanged()
    }

    fun applyCorrection() {
        val state = correctionUiState as? CorrectionUiState.Preview ?: return
        val corrected = state.result.correctedText ?: return

        TextReplacementController.replace(
            inputConnection = inputConnection,
            extractedSentence = state.extractedSentence,
            correctedText = corrected
        )

        correctionUiState = CorrectionUiState.Idle
        onStateChanged()
    }

    fun togglePreviewOriginal() {
        val state = correctionUiState as? CorrectionUiState.Preview ?: return
        state.isShowingOriginal = !state.isShowingOriginal
        onStateChanged()
    }

    fun dismissError() {
        correctionUiState = CorrectionUiState.Idle
        onStateChanged()
    }
}
