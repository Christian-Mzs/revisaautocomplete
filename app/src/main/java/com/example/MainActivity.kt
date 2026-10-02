package com.example

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var isImeEnabled by mutableStateOf(false)
    private var isImeSelected by mutableStateOf(false)

    companion object {
        private const val TAG = "KeyboardCorretorIME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        refreshImeStatus()

        setContent {
            MyApplicationTheme {
                SettingsScreen(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onRefreshImeStatus = { refreshImeStatus() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshImeStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshImeStatus()
        }
    }

    private fun refreshImeStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        var enabled = false
        if (imm != null) {
            val enabledList = imm.enabledInputMethodList
            enabled = enabledList.any {
                it.packageName == packageName || it.serviceName.contains("KeyboardInputMethodService")
            }
        }
        isImeEnabled = enabled

        var selected = false
        try {
            val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            Log.d(TAG, "refreshImeStatus: defaultIme=$defaultIme, packageName=$packageName, isEnabled=$enabled")
            if (defaultIme != null && (defaultIme.contains(packageName) || defaultIme.contains("KeyboardInputMethodService"))) {
                selected = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default IME", e)
            selected = false
        }
        isImeSelected = selected && enabled
    }
}
