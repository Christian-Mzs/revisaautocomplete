package com.example

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var isImeEnabled by mutableStateOf(false)
    private var isImeSelected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

    private fun refreshImeStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm != null) {
            val enabledList = imm.enabledInputMethodList
            isImeEnabled = enabledList.any { it.packageName == packageName }
        }

        try {
            val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            isImeSelected = defaultIme != null && defaultIme.contains(packageName)
        } catch (e: Exception) {
            isImeSelected = false
        }
    }
}
