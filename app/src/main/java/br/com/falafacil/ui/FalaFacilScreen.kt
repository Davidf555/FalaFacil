package br.com.falafacil.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.falafacil.FalaFacilUiState
import br.com.falafacil.FalaFacilViewModel
import br.com.falafacil.R
import kotlin.math.ceil

private enum class KeyAction {
    LETTER,
    SPACE,
    ACCENT_MODE,
    NUMBER_MODE,
    ALPHABET_MODE,
    DELETE,
    SPEAK
}

private data class KeyboardKey(
    val id: String,
    val label: String,
    val description: String,
    val action: KeyAction,
    val value: String = ""
)

private enum class KeyboardMode {
    LETTERS,
    ACCENTS,
    NUMBERS
}

@Composable
fun FalaFacilScreen(
    viewModel: FalaFacilViewModel,
    isAppLocked: Boolean,
    lockTaskMessage: String?,
    onToggleLockTask: () -> Unit,
    onDismissLockTaskMessage: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS) }
    var pendingMode by remember { mutableStateOf<KeyboardMode?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var dismissVoiceWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val visibleVoiceWarning = state.voiceUnavailable && !dismissVoiceWarning
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LockTaskButton(isLocked = isAppLocked, onClick = onToggleLockTask)
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = context.getString(R.string.settings) }
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 72.dp else 136.dp)
                .padding(vertical = 2.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = if (isLandscape) 52.dp else 60.dp,
                            top = if (isLandscape) 4.dp else 12.dp,
                            bottom = if (isLandscape) 4.dp else 12.dp
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.panel_hint),
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 19.sp,
                            lineHeight = 24.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF757575)
                        )
                    } else {
                        Text(
                            text = state.text,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
            IconButton(
                onClick = { showHistory = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(if (isLandscape) 48.dp else 56.dp)
                    .semantics { contentDescription = context.getString(R.string.open_history) }
            ) {
                Icon(Icons.Default.History, contentDescription = null)
            }
        }

        QuickPhraseStrip(
            phrases = state.quickPhrases,
            compact = isLandscape,
            onPhraseClick = viewModel::speakPhrase
        )
        val suggestions = wordSuggestions(state.text)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 36.dp else 44.dp)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Button(
                    onClick = { viewModel.completeCurrentWord(suggestion) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = suggestion,
                        fontSize = if (isLandscape) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = if (isLandscape) state.landscapeColumns else 6
                val keys = keyboardKeys(mode, context, state.isSpeaking)
                val rows = ceil(keys.size.toFloat() / columns).toInt()
                val finalRowSize = (keys.size % columns).let { if (it == 0) columns else it }
                val finalRow = keys.takeLast(finalRowSize)
                val scrollableKeys = keys.dropLast(finalRow.size)
                val rowHeight = ((maxHeight - 8.dp * (rows - 1) - 8.dp) / rows)
                    .coerceAtLeast(if (isLandscape) 44.dp else 64.dp)
                Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = true
            ) {
                items(scrollableKeys, key = KeyboardKey::id) { key ->
                    KeyboardKeyButton(
                        key = key,
                        height = rowHeight,
                        onTap = {
                            if (viewModel.onKeyTap(key.id)) {
                                when (key.action) {
                                    KeyAction.LETTER -> viewModel.appendText(key.value)
                                    KeyAction.SPACE -> viewModel.appendText(" ")
                                    KeyAction.ACCENT_MODE -> pendingMode = KeyboardMode.ACCENTS
                                    KeyAction.NUMBER_MODE -> pendingMode = KeyboardMode.NUMBERS
                                    KeyAction.ALPHABET_MODE -> pendingMode = KeyboardMode.LETTERS
                                    KeyAction.DELETE -> viewModel.deleteLastCharacter()
                                    KeyAction.SPEAK -> viewModel.speak()
                                }
                                if (mode == KeyboardMode.ACCENTS && key.action != KeyAction.ACCENT_MODE) {
                                    pendingMode = KeyboardMode.LETTERS
                                }
                                if (state.vibrationEnabled) vibrate(context)
                            }
                        },
                        onTapComplete = {
                            pendingMode?.let {
                                mode = it
                                pendingMode = null
                            }
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                finalRow.forEach { key ->
                    KeyboardKeyButton(
                        key = key,
                        height = rowHeight,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            if (viewModel.onKeyTap(key.id)) {
                                when (key.action) {
                                    KeyAction.LETTER -> viewModel.appendText(key.value)
                                    KeyAction.SPACE -> viewModel.appendText(" ")
                                    KeyAction.ACCENT_MODE -> pendingMode = KeyboardMode.ACCENTS
                                    KeyAction.NUMBER_MODE -> pendingMode = KeyboardMode.NUMBERS
                                    KeyAction.ALPHABET_MODE -> pendingMode = KeyboardMode.LETTERS
                                    KeyAction.DELETE -> viewModel.deleteLastCharacter()
                                    KeyAction.SPEAK -> viewModel.speak()
                                }
                                if (mode == KeyboardMode.ACCENTS && key.action != KeyAction.ACCENT_MODE) {
                                    pendingMode = KeyboardMode.LETTERS
                                }
                                if (state.vibrationEnabled) vibrate(context)
                            }
                        },
                        onTapComplete = {
                            pendingMode?.let {
                                mode = it
                                pendingMode = null
                            }
                        }
                    )
                }
                repeat(columns - finalRow.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                    }
                }
            }
        }
    }

    if (visibleVoiceWarning) {
        AlertDialog(
            onDismissRequest = { dismissVoiceWarning = true },
            title = {
                Text(
                    stringResource(R.string.tts_missing_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.tts_missing_description),
                    fontSize = 18.sp,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dismissVoiceWarning = true
                        viewModel.requestVoiceInstall()
                    },
                    modifier = Modifier.heightIn(min = 64.dp)
                ) {
                    Text(stringResource(R.string.install_voice), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dismissVoiceWarning = true },
                    modifier = Modifier.heightIn(min = 64.dp)
                ) {
                    Text(stringResource(R.string.close), fontSize = 18.sp)
                }
            }
        )
    }

    if (lockTaskMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissLockTaskMessage,
            title = {
                Text(
                    stringResource(R.string.lock_task_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    lockTaskMessage,
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissLockTaskMessage,
                    modifier = Modifier.heightIn(min = 64.dp)
                ) {
                    Text(stringResource(R.string.close), fontSize = 18.sp)
                }
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSpeechRateChanged = viewModel::onSpeechRateChanged,
            onVibrationChanged = viewModel::onVibrationChanged,
            onRepeatIntervalChanged = viewModel::onRepeatIntervalChanged,
            landscapeColumns = state.landscapeColumns,
            quickPhrases = state.quickPhrases,
            onLandscapeColumnsChanged = viewModel::onLandscapeColumnsChanged,
            onQuickPhrasesChanged = viewModel::onQuickPhrasesChanged
        )
    }
    if (showHistory) {
        HistoryDialog(
            phrases = state.recentPhrases,
            onDismiss = { showHistory = false },
            onSelect = { phrase ->
                viewModel.onTextChanged(phrase)
                showHistory = false
            }
        )
    }
}

@Composable
private fun LockTaskButton(isLocked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val borderColor = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier
            .size(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(if (isLocked) 3.dp else 1.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = context.getString(
                        if (isLocked) R.string.unlock_application else R.string.lock_application
                    )
                }
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun QuickPhraseStrip(
    phrases: List<String>,
    compact: Boolean,
    onPhraseClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 36.dp else 64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(phrases) { phrase ->
            Button(
                onClick = { onPhraseClick(phrase) },
                modifier = Modifier.heightIn(min = if (compact) 32.dp else 56.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 2.dp else 8.dp)
            ) {
                Text(
                    text = phrase,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun wordSuggestions(text: String): List<String> {
    val words = listOf(
        "a", "agora", "ajuda", "alguém", "amigo", "amor", "as", "bem", "bom", "com",
        "comer", "como", "de", "desculpa", "dia", "do", "dor", "e", "ela", "ele", "em",
        "eu", "estou", "favor", "fome", "foi", "frio", "gostaria", "hoje", "mais", "mal",
        "me", "meu", "minha", "muito", "não", "no", "obrigado", "oi", "onde", "os",
        "para", "por", "preciso", "que", "quero", "sim", "sinto", "sua", "tenho", "um",
        "uma", "você", "vou", "água"
    )
    val currentWord = text.substringAfterLast(' ').lowercase()
    if (currentWord.isBlank()) return listOf("eu", "não", "sim")
    val matches = words.filter { it.startsWith(currentWord) && it != currentWord }.take(3)
    return (matches + words.filterNot(matches::contains).take(3 - matches.size)).take(3)
}

@Composable
private fun KeyboardKeyButton(
    key: KeyboardKey,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onTapComplete: () -> Unit
) {
    var pressed by remember(key.id) { mutableStateOf(false) }
    val latestOnTap by rememberUpdatedState(onTap)
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when (key.action) {
        KeyAction.DELETE -> androidx.compose.ui.graphics.Color(0xFFC62828)
        KeyAction.SPEAK -> androidx.compose.ui.graphics.Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (key.action == KeyAction.DELETE || key.action == KeyAction.SPEAK) 3.dp else 1.dp

    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val scale = if (pressed) 0.96f else 1f
                scaleX = scale
                scaleY = scale
            }
            .shadow(3.dp, shape)
            .clip(shape)
            .background(if (pressed) androidx.compose.ui.graphics.Color(0xFFE0E0E0) else androidx.compose.ui.graphics.Color.White)
            .border(borderWidth, borderColor, shape)
            .pointerInput(key.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    latestOnTap()
                    while (true) {
                        val event = awaitPointerEvent()
                        val allPointersUp = event.changes.none { it.pressed }
                        event.changes.forEach { change ->
                            if (change.pressed) change.consume()
                        }
                        if (allPointersUp) break
                    }
                    pressed = false
                    onTapComplete()
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = key.description
                onClick {
                    latestOnTap()
                    onTapComplete()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (key.action) {
            KeyAction.DELETE -> KeyWithIcon(
                icon = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, modifier = Modifier.size(26.dp)) },
                label = stringResource(R.string.delete_short)
            )
            KeyAction.SPEAK -> KeyWithIcon(
                icon = {
                    if (key.label == stringResource(R.string.stop_short)) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                },
                label = key.label
            )
            KeyAction.SPACE -> KeyWithIcon(
                icon = {
                    Text(
                        text = "␣",
                        fontSize = 26.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                label = key.label
            )
            else -> Text(
                text = key.label,
                fontSize = if (key.label.length > 3) 26.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun KeyWithIcon(icon: @Composable () -> Unit, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VoiceWarning(onInstall: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color(0xFFFFF3E0),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFFB45309))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tts_missing_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF7C2D12)
            )
            Text(
                text = stringResource(R.string.tts_missing_description),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = androidx.compose.ui.graphics.Color(0xFF431407)
            )
            Button(onClick = onInstall, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.install_voice), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    state: FalaFacilUiState,
    onDismiss: () -> Unit,
    onSpeechRateChanged: (Float) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onRepeatIntervalChanged: (Long) -> Unit,
    landscapeColumns: Int,
    quickPhrases: List<String>,
    onLandscapeColumnsChanged: (Int) -> Unit,
    onQuickPhrasesChanged: (List<String>) -> Unit
) {
    var phraseEditor by remember(quickPhrases) { mutableStateOf(quickPhrases.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings), fontSize = 26.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.keyboard_columns), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoice(stringResource(R.string.columns_eight), landscapeColumns == 8) { onLandscapeColumnsChanged(8) }
                    SettingChoice(stringResource(R.string.columns_eleven), landscapeColumns == 11) { onLandscapeColumnsChanged(11) }
                }
                Text(stringResource(R.string.speech_speed), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoice(stringResource(R.string.slow), state.speechRate == 0.7f) { onSpeechRateChanged(0.7f) }
                    SettingChoice(stringResource(R.string.normal), state.speechRate == 1f) { onSpeechRateChanged(1f) }
                    SettingChoice(stringResource(R.string.fast), state.speechRate == 1.3f) { onSpeechRateChanged(1.3f) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.vibration), modifier = Modifier.weight(1f), fontSize = 18.sp)
                    Switch(checked = state.vibrationEnabled, onCheckedChange = onVibrationChanged)
                }
                Text(stringResource(R.string.repeat_interval), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoice(stringResource(R.string.repeat_off), state.repeatIntervalMs == 0L) { onRepeatIntervalChanged(0L) }
                    SettingChoice(stringResource(R.string.repeat_300), state.repeatIntervalMs == 300L) { onRepeatIntervalChanged(300L) }
                    SettingChoice(stringResource(R.string.repeat_600), state.repeatIntervalMs == 600L) { onRepeatIntervalChanged(600L) }
                }
                Text(stringResource(R.string.quick_phrases), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = phraseEditor,
                    onValueChange = { phraseEditor = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text(stringResource(R.string.quick_phrases_hint)) },
                    supportingText = { Text(stringResource(R.string.quick_phrases_support)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onQuickPhrasesChanged(phraseEditor.lineSequence().take(5).toList())
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.save), fontSize = 18.sp)
            }
        }
    )
}

@Composable
private fun RowScope.SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HistoryDialog(phrases: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history), fontSize = 26.sp, fontWeight = FontWeight.Bold) },
        text = {
            if (phrases.isEmpty()) {
                Text(stringResource(R.string.history_empty), fontSize = 18.sp)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(phrases.size) { index ->
                        Button(
                            onClick = { onSelect(phrases[index]) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = phrases[index],
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 18.sp,
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.close), fontSize = 18.sp)
            }
        }
    )
}

private fun keyboardKeys(mode: KeyboardMode, context: Context, isSpeaking: Boolean): List<KeyboardKey> {
    fun letter(value: String, id: String = value.lowercase()): KeyboardKey = KeyboardKey(
        id = id,
        label = value,
        description = context.getString(R.string.key_letter_description, value),
        action = KeyAction.LETTER,
        value = value
    )

    val space = KeyboardKey("space", context.getString(R.string.space), context.getString(R.string.key_space_description), KeyAction.SPACE)
    val delete = KeyboardKey("delete", context.getString(R.string.delete_short), context.getString(R.string.key_delete_description), KeyAction.DELETE)
    val speak = KeyboardKey(
        "speak",
        context.getString(if (isSpeaking) R.string.stop_short else R.string.speak_short),
        context.getString(if (isSpeaking) R.string.key_stop_description else R.string.key_speak_description),
        KeyAction.SPEAK
    )
    val alphabet = KeyboardKey("alphabet", "ABC", context.getString(R.string.key_alphabet_description), KeyAction.ALPHABET_MODE)

    return when (mode) {
        KeyboardMode.LETTERS -> ('A'..'Z').map { letter(it.toString()) } + listOf(
            letter("Ç"),
            space,
            KeyboardKey("accents", "ãéô", context.getString(R.string.key_accents_description), KeyAction.ACCENT_MODE),
            KeyboardKey("numbers", "123", context.getString(R.string.key_numbers_description), KeyAction.NUMBER_MODE),
            delete,
            speak
        )
        KeyboardMode.ACCENTS -> listOf("ã", "â", "á", "à", "é", "ê", "í", "ó", "ô", "õ", "ú").map { letter(it) } +
            listOf(alphabet, space, delete, speak)
        KeyboardMode.NUMBERS -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", ",", ".", "?", "!", ":", ";", "-", "(", ")", "/", "%", "R$")
            .map { value ->
                val description = when {
                    value == "R$" -> context.getString(R.string.key_currency_description)
                    value.first().isDigit() -> context.getString(R.string.key_number_description, value)
                    else -> context.getString(R.string.key_symbol_description, value)
                }
                letter(value, "number_$value").copy(description = description)
            } + listOf(space, alphabet, delete, speak)
    }
}

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(25)
    }
}
