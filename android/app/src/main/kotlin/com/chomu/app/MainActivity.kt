package com.chomu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.chomu.app.ui.HomeScreen
import com.chomu.app.ui.SettingsScreen
import com.chomu.app.vm.ChatViewModel

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF7B61FF),
                    background = Color(0xFF0A0A0F),
                    surface = Color(0xFF131318),
                )
            ) {
                var screen by remember { mutableStateOf("home") }
                when (screen) {
                    "home" -> HomeScreen(vm = vm, onSettings = { screen = "settings" })
                    "settings" -> SettingsScreen(vm = vm, onBack = { screen = "home" })
                }
            }
        }
    }
}
