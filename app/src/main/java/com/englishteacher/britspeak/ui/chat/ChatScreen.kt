package com.englishteacher.britspeak.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishteacher.britspeak.ui.avatar.DigitalHumanAvatar
import com.englishteacher.britspeak.ui.avatar.avatarPortraitFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    topicId: String?,
    sessionId: String?,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var micGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted = granted
            if (granted) viewModel.startConversation()
        }

    // Tap-the-avatar → pick a photo → save it locally → re-render as the animated character.
    var avatarVersion by remember { mutableIntStateOf(0) }
    val avatarPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    avatarPortraitFile(context).outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            }.getOrDefault(false)
                        }
                    if (ok) {
                        avatarVersion++
                    } else {
                        snackbarHostState.showSnackbar("图片保存失败，请重试")
                    }
                }
            }
        }

    LaunchedEffect(topicId, sessionId) {
        when {
            sessionId != null -> viewModel.openSession(sessionId)
            topicId != null -> viewModel.startOnTopic(topicId)
            else -> viewModel.resumeOrStartDaily()
        }
        viewModel.refresh()
    }

    LaunchedEffect(state.session?.messages?.size) {
        val count = state.session?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.topicTitle.ifBlank { "Practice with Emma" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )

            DigitalHumanAvatar(
                mood = state.avatarMood,
                modifier = Modifier.padding(vertical = 8.dp),
                imageVersion = avatarVersion,
                onClick = {
                    avatarPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(state.session?.messages.orEmpty(), key = { it.id }) { message ->
                    MessageBubble(message = message, feedbackLanguage = state.feedbackLanguage)
                }
                state.lastRepeatScore?.let { score ->
                    item {
                        Box(modifier = Modifier.padding(12.dp)) {
                            RepeatScorePanel(score = score, onDismiss = viewModel::dismissRepeatScore)
                        }
                    }
                }
            }

            if (state.partialTranscript.isNotBlank()) {
                Text(
                    text = "“${state.partialTranscript}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }

            if (state.sttLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(6.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "  离线语音模型首次加载中，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!state.hasApiKey) {
                Text(
                    text = "Add your API key in Settings to start chatting.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }

            // Optional bottom subtitles.
            if (state.subtitlesEnabled && state.subtitle.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            ControlBar(
                state = state,
                onStartConversation = {
                    if (micGranted) {
                        viewModel.startConversation()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopConversation = viewModel::stopConversation,
                onRepeat = {
                    if (micGranted) viewModel.startRepeat() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ControlBar(
    state: ChatUiState,
    onStartConversation: () -> Unit,
    onStopConversation: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Correction-language and subtitle toggles live in Settings — the bar stays clear of
        // anything that could be mistaken for (or crowd) the talk button. This spacer balances
        // the repeat button so the mic stays visually centred.
        Spacer(Modifier.size(48.dp))

        // The mic is a single toggle: tap to start hands-free continuous conversation, tap again
        // to stop. While active it stays a red "stop" button regardless of listen/think/speak.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.conversationActive) {
                FilledIconButton(
                    onClick = onStopConversation,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "停止对话", modifier = Modifier.size(34.dp))
                }
                Text(
                    text =
                        when (state.phase) {
                            ChatPhase.LISTENING -> "聆听中… 点击停止"
                            ChatPhase.THINKING -> "思考中…"
                            ChatPhase.SPEAKING -> "回答中…"
                            else -> "点击停止对话"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                FilledIconButton(
                    onClick = onStartConversation,
                    enabled = state.canSpeak,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "开始连续对话", modifier = Modifier.size(34.dp))
                }
                Text(
                    text = "点击开始对话",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (state.pendingRepeat != null) {
            // Labelled so it is clearly the optional "repeat after me" drill, not the talk button.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onRepeat, enabled = state.canSpeak) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = "跟读练习")
                }
                Text(
                    text = "跟读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
