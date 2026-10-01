package com.example.ime

import kotlinx.coroutines.Job

sealed class TextActionUiState {
    object Idle : TextActionUiState()

    data class ConsentRequired(val pendingAction: PendingAction) : TextActionUiState()

    object SelectingLanguage : TextActionUiState()

    data class Processing(
        val statusMessage: String,
        val cancelJob: Job
    ) : TextActionUiState()

    data class Preview(
        val title: String,
        val originalText: String,
        val resultText: String,
        val extractedRange: ExtractedTextRange,
        var isShowingOriginal: Boolean = false
    ) : TextActionUiState()

    data class Error(val message: String) : TextActionUiState()

    sealed class PendingAction {
        object Correction : PendingAction()
        data class Translation(val targetLanguageCode: String) : PendingAction()
    }
}
