package com.example.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.example.ui.keyboard.KeyboardLayoutView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class KeyboardInputMethodService : InputMethodService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var keyboardController: KeyboardController
    private var keyboardLayoutView: KeyboardLayoutView? = null

    override fun onCreate() {
        super.onCreate()
        keyboardController = KeyboardController(
            context = this,
            coroutineScope = serviceScope,
            onStateChanged = {
                keyboardLayoutView?.render()
            }
        )
    }

    override fun onCreateInputView(): View {
        val layout = KeyboardLayoutView(this, keyboardController)
        keyboardLayoutView = layout
        return layout
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboardController.updateInputConnection(currentInputConnection, attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardController.updateInputConnection(currentInputConnection, info)
        keyboardLayoutView?.render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardLayoutView?.dismissPopup()
        keyboardController.cancelCorrection()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        keyboardLayoutView?.dismissPopup()
        keyboardLayoutView = null
    }
}
