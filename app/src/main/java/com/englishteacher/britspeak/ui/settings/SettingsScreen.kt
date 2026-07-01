package com.englishteacher.britspeak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishteacher.core.ai.LlmProvider
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // --- Model provider (API format) ---
        Text(
            "Model provider",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LlmProvider.entries.forEach { provider ->
                FilterChip(
                    selected = state.provider == provider,
                    onClick = { viewModel.setProvider(provider) },
                    label = {
                        Text(if (provider == LlmProvider.ANTHROPIC) "Anthropic" else "OpenAI-compatible")
                    },
                )
            }
        }
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::setBaseUrl,
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.model,
            onValueChange = viewModel::setModel,
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            text =
                if (state.provider == LlmProvider.ANTHROPIC) {
                    "Anthropic Messages API, e.g. claude-opus-4-8."
                } else {
                    "Any OpenAI-compatible endpoint (OpenAI, DeepSeek, Qwen, Kimi, a local server…)."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )

        // --- API key ---
        Text(
            "API key",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
        )
        Text(
            if (state.hasApiKey) "A key is saved (hidden for security)." else "No key saved yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.hasApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(if (state.provider == LlmProvider.ANTHROPIC) "sk-ant-…" else "sk-…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.saveApiKey(keyInput)
                    keyInput = ""
                },
                enabled = keyInput.isNotBlank(),
            ) { Text("Save key") }
            if (state.hasApiKey) {
                TextButton(onClick = viewModel::clearApiKey) { Text("Remove") }
            }
        }
        if (state.savedFlash) {
            Text("Saved.", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }

        // --- Subtitles ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Show subtitles", style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.subtitlesEnabled, onCheckedChange = viewModel::setSubtitlesEnabled)
        }

        // --- Correction language ---
        Text(
            "Correction language",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = state.feedbackLanguage == lang,
                    onClick = { viewModel.setFeedbackLanguage(lang) },
                    label = { Text(if (lang == FeedbackLanguage.ENGLISH) "English" else "中文") },
                )
            }
        }

        // --- Proficiency ---
        Text(
            "Your level",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProficiencyLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.proficiency == level,
                    onClick = { viewModel.setProficiency(level) },
                    label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Text(
            "Speech recognition runs fully offline on-device (Vosk). Your API key is stored " +
                "encrypted on this device only. For production, route requests through your own " +
                "backend proxy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}
