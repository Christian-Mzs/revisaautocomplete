package com.example.ime

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.example.ui.keyboard.KeyboardLayoutView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class KeyboardInputMethodService : android.inputmethodservice.InputMethodService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var keyboardController: KeyboardController
    private var keyboardLayoutView: KeyboardLayoutView? = null

    private val clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        if (!keyboardController.isSensitiveField) {
            keyboardController.clipboardHistory.capture()
            keyboardLayoutView?.refreshClipboard()
        }
    }

    companion object {
        private const val TAG = "KeyboardCorretorIME"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: InputMethodService initialized")

        keyboardController = KeyboardController(
            context = this,
            coroutineScope = serviceScope,
            onStateChanged = {
                keyboardLayoutView?.render()
            },
            onSwitchImeRequested = {
                switchToNextIme()
            }
        )
        keyboardController.clipboardHistory.start(clipboardListener)
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView: Creating keyboard view hierarchy")
        val layout = KeyboardLayoutView(this, keyboardController)
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        keyboardLayoutView = layout
        return layout
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // A held gesture must never commit into a newly connected editor.
        keyboardLayoutView?.dismissPopup()
        InputMetrics.reset()
        Log.d(TAG, "onStartInput: pkg=${attribute?.packageName}, inputType=${attribute?.inputType}, restarting=$restarting")
        keyboardController.updateInputConnection(currentInputConnection, attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView: pkg=${info?.packageName}, inputType=${info?.inputType}, restarting=$restarting")
        keyboardController.updateInputConnection(currentInputConnection, info)
        keyboardController.setMode(KeyboardMode.LETTERS)
        keyboardLayoutView?.resetNavigation()
        if (!keyboardController.isSensitiveField) keyboardController.clipboardHistory.capture()
        keyboardLayoutView?.render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView: finishingInput=$finishingInput")
        keyboardLayoutView?.dismissPopup()
        InputMetrics.finishSession()
        keyboardController.cancelAction()
        keyboardController.setMode(KeyboardMode.LETTERS)
        keyboardLayoutView?.resetNavigation()
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        // CRITICAL FOR EMULATORS & EXTERNAL KEYBOARD DETECTIONS:
        // On emulators (like AI Studio preview), Android detects the host machine's hardware
        // keyboard and by default sets onEvaluateInputViewShown() to false, hiding the soft keyboard.
        // Returning true ensures the soft keyboard is displayed whenever an input field is focused.
        Log.d(TAG, "onEvaluateInputViewShown: forcing true so soft keyboard is visible on emulators")
        return true
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        Log.d(TAG, "onShowInputRequested: flags=$flags, configChange=$configChange")
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Prevent distracting fullscreen extract edit box on landscape/tablets
        return false
    }

    private fun switchToNextIme() {
        Log.d(TAG, "switchToNextIme: Attempting to switch or show picker")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (shouldOfferSwitchingToNextInputMethod()) {
                    switchToNextInputMethod(false)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "switchToNextInputMethod failed, falling back to picker", e)
            }
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: IME service shutting down")
        keyboardController.clipboardHistory.stop(clipboardListener)
        serviceScope.cancel()
        keyboardLayoutView?.dismissPopup()
        keyboardLayoutView = null
    }
}
