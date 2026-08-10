package com.chomu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chomu.app.vm.ChatViewModel

private val Bg = Color(0xFF0A0A0F)
private val Accent = Color(0xFF7B61FF)
private val TextPrimary = Color(0xFFE8E8F0)
private val TextMuted = Color(0xFF888898)
private val CardBg = Color(0xCC131318)

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var apiKey by remember { mutableStateOf(vm.prefs.nvidiaApiKey) }
    var model by remember { mutableStateOf(vm.prefs.nvidiaModel) }
    var name by remember { mutableStateOf(vm.prefs.companionName) }
    var vrmUrl by remember { mutableStateOf(vm.prefs.vrmUrl) }
    var showKey by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf("") }

    val models = listOf(
        "meta/llama-3.1-70b-instruct" to "Llama 3.1 70B (best quality)",
        "meta/llama-3.1-8b-instruct" to "Llama 3.1 8B (faster)",
        "nvidia/nemotron-mini-4b-instruct" to "Nemotron 4B (lightest)",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Bg).systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = TextPrimary)
                }
                Text("Settings", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Companion name
            Card("Companion Name") {
                ChomuField(
                    value = name, onValue = { name = it },
                    placeholder = "e.g. Mia"
                )
            }

            // API key
            Card("NVIDIA API Key") {
                ChomuField(
                    value = apiKey, onValue = { apiKey = it },
                    placeholder = "nvapi-...",
                    visual = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboard = KeyboardType.Password,
                    trailing = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show", color = Accent, fontSize = 12.sp)
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text("Get free key: build.nvidia.com/explore", color = TextMuted, fontSize = 11.sp)
            }

            // Model
            Card("AI Model") {
                models.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = model == value,
                            onClick = { model = value },
                            colors = RadioButtonDefaults.colors(selectedColor = Accent)
                        )
                        Text(label, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            // VRM model URL
            Card("3D Model URL (optional)") {
                ChomuField(
                    value = vrmUrl, onValue = { vrmUrl = it },
                    placeholder = "https://...model.vrm"
                )
                Spacer(Modifier.height(4.dp))
                Text("Leave empty to use default model. Supports .vrm and .glb URLs.", color = TextMuted, fontSize = 11.sp)
            }

            // Save
            Button(
                onClick = {
                    vm.prefs.nvidiaApiKey = apiKey.trim()
                    vm.prefs.nvidiaModel = model
                    vm.prefs.companionName = name.trim().ifBlank { "Mia" }
                    vm.prefs.vrmUrl = vrmUrl.trim()
                    if (vrmUrl.isNotBlank()) vm.loadVrmModel(vrmUrl.trim())
                    savedMsg = "Saved!"
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(savedMsg.ifBlank { "Save" }, color = Color.White, fontSize = 15.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = TextMuted, fontSize = 11.sp)
        content()
    }
}

@Composable
private fun ChomuField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    visual: VisualTransformation = VisualTransformation.None,
    keyboard: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 13.sp) },
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = trailing,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedContainerColor = Color(0xFF0D0D18), unfocusedContainerColor = Color(0xFF0D0D18),
            focusedBorderColor = Accent.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
            cursorColor = Accent,
        ),
        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp)
    )
}
