package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Flashcard
import com.example.data.model.MasteryLevel
import com.example.data.model.QuizQuestion
import com.example.data.model.StudyDeck
import com.example.data.model.UiState
import com.example.ui.components.BrainBuddyInlineLoader
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralWeak
import com.example.ui.theme.EmeraldMastered
import java.util.Locale

private enum class DeckTab { FLASHCARDS, QUIZ, SUMMARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deck: StudyDeck,
    flashcardsState: UiState<List<Flashcard>>,
    questionsState: UiState<List<QuizQuestion>>,
    onBack: () -> Unit,
    onUpdateFlashcardMastery: (Flashcard, MasteryLevel, List<Flashcard>) -> Unit,
    onRecordQuizResult: (List<QuizQuestion>, Set<String>, Int) -> Unit,
    onAskBuddyAboutDeck: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
                ttsReady = true
            }
        }
        instance
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    var activeTab by remember { mutableStateOf(DeckTab.FLASHCARDS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = deck.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = deck.subject,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("deck_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAskBuddyAboutDeck) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = "Ask BrainBuddy about this deck",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == DeckTab.FLASHCARDS,
                        onClick = { activeTab = DeckTab.FLASHCARDS },
                        label = { Text("Flashcards") },
                        leadingIcon = { Icon(Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("tab_flashcards")
                    )
                    FilterChip(
                        selected = activeTab == DeckTab.QUIZ,
                        onClick = { activeTab = DeckTab.QUIZ },
                        label = { Text("Quiz") },
                        leadingIcon = { Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("tab_quiz")
                    )
                    FilterChip(
                        selected = activeTab == DeckTab.SUMMARY,
                        onClick = { activeTab = DeckTab.SUMMARY },
                        label = { Text("Summary") },
                        modifier = Modifier.testTag("tab_summary")
                    )
                }

                AnimatedContent(targetState = activeTab, label = "deck_tab_anim") { tab ->
                    when (tab) {
                        DeckTab.FLASHCARDS -> {
                            FlashcardsStudioContent(
                                flashcardsState = flashcardsState,
                                onSpeak = { text ->
                                    if (ttsReady) {
                                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bb_card")
                                    }
                                },
                                onRateMastery = onUpdateFlashcardMastery
                            )
                        }
                        DeckTab.QUIZ -> {
                            QuizStudioContent(
                                questionsState = questionsState,
                                onQuizCompleted = onRecordQuizResult
                            )
                        }
                        DeckTab.SUMMARY -> {
                            SummaryAndNotesContent(deck = deck)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardsStudioContent(
    flashcardsState: UiState<List<Flashcard>>,
    onSpeak: (String) -> Unit,
    onRateMastery: (Flashcard, MasteryLevel, List<Flashcard>) -> Unit
) {
    when (flashcardsState) {
        is UiState.Loading -> BrainBuddyInlineLoader("Loading flashcards...", Modifier.fillMaxSize())
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(flashcardsState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            val cards = flashcardsState.data
            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No flashcards in this deck yet.")
                }
                return
            }

            var currentIndex by remember { mutableIntStateOf(0) }
            val safeIndex = currentIndex.coerceIn(0, cards.lastIndex)
            val card = cards[safeIndex]
            var isFlipped by remember(safeIndex, card.id) { mutableStateOf(false) }
            var showHint by remember(safeIndex, card.id) { mutableStateOf(false) }

            val rotation by animateFloatAsState(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = tween(durationMillis = 420),
                label = "card_flip"
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Card ${safeIndex + 1} of ${cards.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when (card.masteryEnum()) {
                                MasteryLevel.MASTERED -> EmeraldMastered.copy(alpha = 0.16f)
                                MasteryLevel.LEARNING -> AmberGold.copy(alpha = 0.16f)
                                MasteryLevel.NEW -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = card.masteryEnum().name,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (card.masteryEnum()) {
                                    MasteryLevel.MASTERED -> EmeraldMastered
                                    MasteryLevel.LEARNING -> AmberGold
                                    MasteryLevel.NEW -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (safeIndex + 1).toFloat() / cards.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        strokeCap = StrokeCap.Round
                    )
                }

                // 3D Flip Flashcard
                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (rotation <= 90f) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clip(RoundedCornerShape(28.dp))
                            .clickable { isFlipped = !isFlipped }
                            .testTag("flashcard_flip_surface")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .graphicsLayer {
                                    if (rotation > 90f) rotationY = 180f
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = if (rotation <= 90f) "QUESTION • ${card.conceptTag}" else "ANSWER",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onSpeak(if (rotation <= 90f) card.front else card.back)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "Read card aloud"
                                    )
                                }
                            }

                            Text(
                                text = if (rotation <= 90f) card.front else card.back,
                                style = if (rotation <= 90f) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                color = if (rotation <= 90f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                text = "Tap card to flip",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }

                // Hint reveal
                if (card.hint.isNotBlank()) {
                    item {
                        if (showHint) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = AmberGold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = card.hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showHint = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show Hint")
                            }
                        }
                    }
                }

                // Mastery Confidence Actions
                item {
                    Text(
                        text = "How well did you know this?",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onRateMastery(card, MasteryLevel.LEARNING, cards)
                                if (safeIndex < cards.lastIndex) currentIndex = safeIndex + 1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rate_needs_review_button")
                        ) {
                            Text("Needs Review", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                onRateMastery(card, MasteryLevel.MASTERED, cards)
                                if (safeIndex < cards.lastIndex) currentIndex = safeIndex + 1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldMastered,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rate_mastered_button")
                        ) {
                            Text("Mastered", maxLines = 1)
                        }
                    }
                }

                // Navigation controls
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { if (safeIndex > 0) currentIndex = safeIndex - 1 },
                            enabled = safeIndex > 0,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Previous")
                        }
                        OutlinedButton(
                            onClick = { if (safeIndex < cards.lastIndex) currentIndex = safeIndex + 1 },
                            enabled = safeIndex < cards.lastIndex,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizStudioContent(
    questionsState: UiState<List<QuizQuestion>>,
    onQuizCompleted: (List<QuizQuestion>, Set<String>, Int) -> Unit
) {
    when (questionsState) {
        is UiState.Loading -> BrainBuddyInlineLoader("Preparing quiz...", Modifier.fillMaxSize())
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(questionsState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            val questions = questionsState.data
            if (questions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No quiz questions in this deck.")
                }
                return
            }

            var currentIdx by remember { mutableIntStateOf(0) }
            var selectedOption by remember(currentIdx) { mutableStateOf<Int?>(null) }
            var isAnswered by remember(currentIdx) { mutableStateOf(false) }
            var quizFinished by remember { mutableStateOf(false) }
            val missedIds = remember { mutableStateListOf<String>() }
            val startedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

            if (quizFinished) {
                val correctCount = (questions.size - missedIds.distinct().size).coerceAtLeast(0)
                val percent = ((correctCount.toFloat() / questions.size.toFloat()) * 100f).toInt()
                val missedTags = questions.filter { missedIds.contains(it.id) }.map { it.conceptTag }.distinct()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.displayLarge,
                        color = if (percent >= 75) EmeraldMastered else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You answered $correctCount of ${questions.size} correctly",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (missedTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Weak areas flagged: ${missedTags.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            missedIds.clear()
                            currentIdx = 0
                            quizFinished = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retake Quiz")
                    }
                }
                return
            }

            val q = questions[currentIdx.coerceIn(0, questions.lastIndex)]
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Question ${currentIdx + 1} of ${questions.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = q.conceptTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (currentIdx + 1).toFloat() / questions.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        strokeCap = StrokeCap.Round
                    )
                }

                item {
                    Text(
                        text = q.prompt,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(q.options.size) { optIdx ->
                    val optionText = q.options[optIdx]
                    val isCorrectOption = optIdx == q.correctIndex.toInt()
                    val isSelected = selectedOption == optIdx

                    val bgColor = when {
                        !isAnswered && isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isAnswered && isCorrectOption -> EmeraldMastered.copy(alpha = 0.16f)
                        isAnswered && isSelected && !isCorrectOption -> CoralWeak.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !isAnswered) {
                                selectedOption = optIdx
                                isAnswered = true
                                if (!isCorrectOption && !missedIds.contains(q.id)) {
                                    missedIds.add(q.id)
                                }
                            }
                            .testTag("quiz_option_$optIdx")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (isAnswered && isCorrectOption) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Correct", tint = EmeraldMastered)
                            } else if (isAnswered && isSelected) {
                                Icon(Icons.Rounded.Close, contentDescription = "Incorrect", tint = CoralWeak)
                            }
                        }
                    }
                }

                if (isAnswered) {
                    item {
                        if (q.explanation.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = q.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (currentIdx < questions.lastIndex) {
                                    currentIdx++
                                } else {
                                    val mins = (((System.currentTimeMillis() - startedAtMs) / 60000L).toInt()).coerceAtLeast(1)
                                    onQuizCompleted(questions, missedIds.toSet(), mins)
                                    quizFinished = true
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("quiz_next_button")
                        ) {
                            Text(if (currentIdx < questions.lastIndex) "Next Question" else "Finish & Save Score")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryAndNotesContent(deck: StudyDeck) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI Executive Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deck.aiSummary.ifBlank { "No summary generated." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Original Study Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deck.sourceNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
