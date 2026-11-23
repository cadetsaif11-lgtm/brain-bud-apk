package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.TutorMode
import com.example.data.model.StudyDeck
import com.example.data.model.UiState
import com.example.ui.theme.EmeraldMastered
import com.example.ui.viewmodel.FocusTimerState
import com.example.ui.viewmodel.TutorChatMessage

@Composable
fun BuddyAndFocusScreen(
    decksState: UiState<List<StudyDeck>>,
    tutorMessages: List<TutorChatMessage>,
    tutorMode: TutorMode,
    isTutorStreaming: Boolean,
    focusTimerState: FocusTimerState,
    onSelectTutorMode: (TutorMode) -> Unit,
    onSendTutorMessage: (String, StudyDeck?) -> Unit,
    onSelectFocusPreset: (Int) -> Unit,
    onUpdateFocusLabel: (String) -> Unit,
    onToggleFocusTimer: () -> Unit,
    onResetFocusTimer: () -> Unit,
    onLogElapsedFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSegment by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Study Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("segment_ai_buddy")
                ) {
                    Text("AI Study Buddy")
                }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("segment_focus_timer")
                ) {
                    Text("Focus Timer")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = selectedSegment,
                modifier = Modifier.weight(1f),
                label = "studio_segment"
            ) { seg ->
                if (seg == 0) {
                    AiTutorChatPane(
                        decks = (decksState as? UiState.Success)?.data.orEmpty(),
                        messages = tutorMessages,
                        tutorMode = tutorMode,
                        isStreaming = isTutorStreaming,
                        onSelectMode = onSelectTutorMode,
                        onSendMessage = onSendTutorMessage
                    )
                } else {
                    FocusTimerPane(
                        state = focusTimerState,
                        onSelectPreset = onSelectFocusPreset,
                        onUpdateLabel = onUpdateFocusLabel,
                        onToggle = onToggleFocusTimer,
                        onReset = onResetFocusTimer,
                        onLogNow = onLogElapsedFocus
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTutorChatPane(
    decks: List<StudyDeck>,
    messages: List<TutorChatMessage>,
    tutorMode: TutorMode,
    isStreaming: Boolean,
    onSelectMode: (TutorMode) -> Unit,
    onSendMessage: (String, StudyDeck?) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedDeck by remember { mutableStateOf<StudyDeck?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tutor Mode Selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(TutorMode.entries) { mode ->
                FilterChip(
                    selected = tutorMode == mode,
                    onClick = { onSelectMode(mode) },
                    label = { Text(mode.label) }
                )
            }
        }

        // Optional Deck Context Selector
        if (decks.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedDeck == null,
                        onClick = { selectedDeck = null },
                        label = { Text("All Topics") }
                    )
                }
                items(decks, key = { it.id }) { deck ->
                    FilterChip(
                        selected = selectedDeck?.id == deck.id,
                        onClick = { selectedDeck = deck },
                        label = { Text(deck.title, maxLines = 1) }
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isFromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (msg.isFromUser) 18.dp else 4.dp,
                            bottomEnd = if (msg.isFromUser) 4.dp else 18.dp
                        ),
                        color = if (msg.isFromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (msg.isFromUser) 0.dp else 1.dp,
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Text(
                            text = if (msg.text.isEmpty() && msg.isStreaming) "Thinking..." else msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.isFromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask BrainBuddy a study question...") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tutor_chat_input")
            )
            IconButton(
                onClick = {
                    val toSend = inputText
                    inputText = ""
                    onSendMessage(toSend, selectedDeck)
                },
                enabled = inputText.isNotBlank() && !isStreaming,
                modifier = Modifier
                    .size(50.dp)
                    .testTag("tutor_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send question",
                    tint = if (inputText.isNotBlank() && !isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FocusTimerPane(
    state: FocusTimerState,
    onSelectPreset: (Int) -> Unit,
    onUpdateLabel: (String) -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onLogNow: () -> Unit
) {
    val totalSec = (state.presetMinutes * 60).coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = state.remainingSeconds.toFloat() / totalSec.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "focus_arc"
    )
    val mins = state.remainingSeconds / 60
    val secs = state.remainingSeconds % 60
    val timeText = String.format("%02d:%02d", mins, secs)

    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { preset ->
                FilterChip(
                    selected = state.presetMinutes == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text("${preset}m") }
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isRunning) "Focusing..." else "Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.focusLabel,
                onValueChange = onUpdateLabel,
                label = { Text("Focus topic") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.completedToast != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldMastered.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldMastered)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.completedToast,
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldMastered
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggle,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("focus_toggle_button")
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isRunning) "Pause" else "Start Focus")
                }

                OutlinedButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset timer")
                }

                OutlinedButton(
                    onClick = onLogNow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Log Now", maxLines = 1)
                }
            }
        }
    }
}
