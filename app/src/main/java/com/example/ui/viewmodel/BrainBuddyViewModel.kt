package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.BrainBuddyAiService
import com.example.data.ai.TutorMode
import com.example.data.model.Flashcard
import com.example.data.model.MasteryLevel
import com.example.data.model.QuizQuestion
import com.example.data.model.StudyDeck
import com.example.data.model.StudySession
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WeakAreaAnalysisResult
import com.example.data.repository.StudyRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Generating(val statusLabel: String) : GenerationUiState
    data class Created(val deckId: String) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

sealed interface WeakAreaUiState {
    data object Idle : WeakAreaUiState
    data object Analyzing : WeakAreaUiState
    data class Ready(val result: WeakAreaAnalysisResult) : WeakAreaUiState
    data class Error(val message: String) : WeakAreaUiState
}

data class TutorChatMessage(
    val id: String,
    val isFromUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

data class FocusTimerState(
    val presetMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val focusLabel: String = "Deep Study Session",
    val completedToast: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrainBuddyViewModel(
    private val repository: StudyRepository,
    private val aiService: BrainBuddyAiService,
    private val currentUserId: String,
    private val displayName: String?
) : ViewModel() {

    private companion object {
        const val TAG = "BrainBuddyVM"
        const val STOP_TIMEOUT_MS = 5000L
    }

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, displayName)
        }
    }

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val decksState: StateFlow<UiState<List<StudyDeck>>> = repository.observeDecks(currentUserId)
        .map<List<StudyDeck>, UiState<List<StudyDeck>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing decks", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load study decks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val sessionsState: StateFlow<UiState<List<StudySession>>> = repository.observeSessions(currentUserId)
        .map<List<StudySession>, UiState<List<StudySession>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing sessions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load progress history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _selectedDeckId = MutableStateFlow<String?>(null)
    val selectedDeckId: StateFlow<String?> = _selectedDeckId.asStateFlow()

    val selectedDeckFlashcards: StateFlow<UiState<List<Flashcard>>> = _selectedDeckId
        .flatMapLatest { deckId ->
            if (deckId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeFlashcards(currentUserId, deckId)
                    .map<List<Flashcard>, UiState<List<Flashcard>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        emit(UiState.Error(rootError.message ?: "Failed to load flashcards"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val selectedDeckQuestions: StateFlow<UiState<List<QuizQuestion>>> = _selectedDeckId
        .flatMapLatest { deckId ->
            if (deckId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeQuestions(currentUserId, deckId)
                    .map<List<QuizQuestion>, UiState<List<QuizQuestion>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        emit(UiState.Error(rootError.message ?: "Failed to load quiz questions"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _weakAreaState = MutableStateFlow<WeakAreaUiState>(WeakAreaUiState.Idle)
    val weakAreaState: StateFlow<WeakAreaUiState> = _weakAreaState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // AI Tutor chat state
    private val _tutorMessages = MutableStateFlow<List<TutorChatMessage>>(
        listOf(
            TutorChatMessage(
                id = "welcome",
                isFromUser = false,
                text = "Hi! I'm BrainBuddy. Ask me to clarify any tough concept, test you Socratic-style, or break down a formula."
            )
        )
    )
    val tutorMessages: StateFlow<List<TutorChatMessage>> = _tutorMessages.asStateFlow()

    private val _tutorMode = MutableStateFlow(TutorMode.ELI5)
    val tutorMode: StateFlow<TutorMode> = _tutorMode.asStateFlow()

    private val _isTutorStreaming = MutableStateFlow(false)
    val isTutorStreaming: StateFlow<Boolean> = _isTutorStreaming.asStateFlow()

    // Focus timer state
    private val _focusTimerState = MutableStateFlow(FocusTimerState())
    val focusTimerState: StateFlow<FocusTimerState> = _focusTimerState.asStateFlow()
    private var timerJob: Job? = null

    fun selectDeck(deckId: String) {
        _selectedDeckId.value = deckId
    }

    fun clearGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun generateStudyPackFromNotes(
        notes: String,
        preferredSubject: String,
        attachedImage: Bitmap? = null
    ) {
        if (notes.isBlank() && attachedImage == null) {
            _generationState.value = GenerationUiState.Error("Please paste study notes or attach a photo of your notes.")
            return
        }
        viewModelScope.launch {
            _generationState.value = GenerationUiState.Generating("Extracting key concepts & flashcards...")
            val aiResult = aiService.generateStudyPackFromNotes(
                notes = notes,
                preferredSubject = preferredSubject,
                attachedImage = attachedImage
            )
            aiResult.fold(
                onSuccess = { pack ->
                    _generationState.value = GenerationUiState.Generating("Saving study deck to your library...")
                    val saveResult = repository.saveGeneratedStudyPack(
                        userId = currentUserId,
                        sourceNotes = notes.ifBlank { pack.summary },
                        pack = pack
                    )
                    saveResult.fold(
                        onSuccess = { deckId ->
                            _selectedDeckId.value = deckId
                            _generationState.value = GenerationUiState.Created(deckId)
                        },
                        onFailure = { err ->
                            _generationState.value = GenerationUiState.Error(
                                err.message ?: "Could not save study deck."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _generationState.value = GenerationUiState.Error(
                        err.message ?: "AI failed to convert notes."
                    )
                }
            )
        }
    }

    fun updateFlashcardMastery(
        deckId: String,
        card: Flashcard,
        newMastery: MasteryLevel,
        allCards: List<Flashcard>
    ) {
        viewModelScope.launch {
            val res = repository.updateFlashcardMastery(
                userId = currentUserId,
                deckId = deckId,
                card = card,
                newMastery = newMastery,
                allCardsInDeck = allCards
            )
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.message ?: "Failed to update flashcard mastery."
            }
        }
    }

    fun recordQuizResult(
        deck: StudyDeck,
        questions: List<QuizQuestion>,
        missedQuestionIds: Set<String>,
        durationMinutes: Int
    ) {
        val total = questions.size.coerceAtLeast(1)
        val correct = (total - missedQuestionIds.size).coerceAtLeast(0)
        val scorePercent = ((correct.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
        val missedTags = questions
            .filter { missedQuestionIds.contains(it.id) }
            .map { it.conceptTag }
            .distinct()

        viewModelScope.launch {
            val res = repository.recordQuizCompletion(
                userId = currentUserId,
                deckId = deck.id,
                deckTitle = deck.title,
                scorePercent = scorePercent,
                durationMinutes = durationMinutes,
                answeredQuestions = questions,
                missedQuestionIds = missedQuestionIds,
                weakTags = missedTags
            )
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.message ?: "Failed to save quiz progress."
            }
        }
    }

    fun analyzeWeakAreas() {
        val decks = (decksState.value as? UiState.Success)?.data.orEmpty()
        val sessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        if (decks.isEmpty()) {
            _weakAreaState.value = WeakAreaUiState.Error("Create at least one study deck first so BrainBuddy can analyze your weak areas.")
            return
        }

        val deckSummaries = decks.map { d ->
            val scoreText = if (d.lastScorePercent >= 0) "${d.lastScorePercent}%" else "Not quizzed yet"
            val weakText = if (d.weakConcepts.isNotEmpty()) d.weakConcepts.joinToString(", ") else "None flagged yet"
            "Deck '${d.title}' (${d.subject}): ${d.masteredCount}/${d.flashcardCount} cards mastered, last quiz $scoreText, weak tags: [$weakText]"
        }

        val missedSummary = buildList {
            sessions.flatMap { it.weakTags }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(12)
                .forEach { (tag, count) ->
                    add("Concept '$tag' missed $count time(s) in recent sessions")
                }
            if (isEmpty()) {
                add("Learner has not missed quiz questions yet; infer foundational focus areas from deck topics: ${decks.joinToString { it.title }}")
            }
        }

        viewModelScope.launch {
            _weakAreaState.value = WeakAreaUiState.Analyzing
            val result = aiService.analyzeWeakAreas(deckSummaries, missedSummary)
            result.fold(
                onSuccess = { analysis ->
                    repository.saveWeakAreaAnalysis(currentUserId, analysis)
                    _weakAreaState.value = WeakAreaUiState.Ready(analysis)
                },
                onFailure = { err ->
                    _weakAreaState.value = WeakAreaUiState.Error(err.message ?: "Weak area analysis failed.")
                }
            )
        }
    }

    fun generateWeakAreaBooster() {
        val decks = (decksState.value as? UiState.Success)?.data.orEmpty()
        val profile = (profileState.value as? UiState.Success)?.data
        val analysisTopics = (weakAreaState.value as? WeakAreaUiState.Ready)
            ?.result?.weakAreas?.map { it.topic }.orEmpty()
        val deckWeakTopics = decks.flatMap { it.weakConcepts }.distinct()
        val profileWeakTopics = profile?.weakTopics.orEmpty()
        val combinedWeak = (analysisTopics + deckWeakTopics + profileWeakTopics).distinct().take(6)

        val fallbackNotes = decks.take(3).joinToString("\n\n") { "${it.title}: ${it.aiSummary}" }
        viewModelScope.launch {
            _generationState.value = GenerationUiState.Generating("Building targeted booster drill for your weak areas...")
            val aiResult = aiService.generateBoosterPackForWeakTopics(
                weakTopics = combinedWeak.ifEmpty { decks.map { it.subject }.distinct().take(3) },
                contextNotes = fallbackNotes
            )
            aiResult.fold(
                onSuccess = { pack ->
                    val saveResult = repository.saveGeneratedStudyPack(
                        userId = currentUserId,
                        sourceNotes = "Targeted Weak-Area Drill: ${combinedWeak.joinToString(", ")}",
                        pack = pack
                    )
                    saveResult.fold(
                        onSuccess = { deckId ->
                            _selectedDeckId.value = deckId
                            _generationState.value = GenerationUiState.Created(deckId)
                        },
                        onFailure = { err ->
                            _generationState.value = GenerationUiState.Error(err.message ?: "Failed to save booster deck.")
                        }
                    )
                },
                onFailure = { err ->
                    _generationState.value = GenerationUiState.Error(err.message ?: "Failed to build booster drill.")
                }
            )
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            val res = repository.deleteDeck(currentUserId, deckId)
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.message ?: "Failed to delete deck."
            } else if (_selectedDeckId.value == deckId) {
                _selectedDeckId.value = null
            }
        }
    }

    // AI Tutor methods
    fun setTutorMode(mode: TutorMode) {
        _tutorMode.value = mode
    }

    fun sendTutorMessage(question: String, contextDeck: StudyDeck? = null) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _isTutorStreaming.value) return

        val historySnapshot = _tutorMessages.value.drop(1).map {
            (if (it.isFromUser) "user" else "model") to it.text
        }

        val userMsgId = "u_${System.currentTimeMillis()}"
        val botMsgId = "m_${System.currentTimeMillis() + 1}"

        _tutorMessages.update {
            it + TutorChatMessage(id = userMsgId, isFromUser = true, text = trimmed) +
                TutorChatMessage(id = botMsgId, isFromUser = false, text = "", isStreaming = true)
        }

        val deckContextText = contextDeck?.let {
            "Deck: ${it.title} (${it.subject})\nSummary: ${it.aiSummary}\nNotes excerpt: ${it.sourceNotes.take(2000)}"
        } ?: ""

        viewModelScope.launch {
            _isTutorStreaming.value = true
            val accumulated = StringBuilder()
            try {
                aiService.streamTutorReply(
                    userMessage = trimmed,
                    deckContext = deckContextText,
                    mode = _tutorMode.value,
                    historyPairs = historySnapshot
                ).collect { chunk ->
                    accumulated.append(chunk)
                    val textNow = accumulated.toString()
                    _tutorMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == botMsgId) msg.copy(text = textNow, isStreaming = true) else msg
                        }
                    }
                }
                val finalText = accumulated.toString().ifBlank { "Let's break that down together—could you share a bit more detail?" }
                _tutorMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == botMsgId) msg.copy(text = finalText, isStreaming = false) else msg
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorNote = "Couldn't reach BrainBuddy AI right now: ${e.localizedMessage ?: "Unknown error"}"
                _tutorMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == botMsgId) msg.copy(text = errorNote, isStreaming = false) else msg
                    }
                }
            } finally {
                _isTutorStreaming.value = false
            }
        }
    }

    // Focus Pomodoro Timer controls
    fun selectFocusPreset(minutes: Int) {
        timerJob?.cancel()
        _focusTimerState.update {
            it.copy(
                presetMinutes = minutes,
                remainingSeconds = minutes * 60,
                isRunning = false,
                completedToast = null
            )
        }
    }

    fun updateFocusLabel(label: String) {
        _focusTimerState.update { it.copy(focusLabel = label.take(80)) }
    }

    fun toggleFocusTimer() {
        val current = _focusTimerState.value
        if (current.isRunning) {
            timerJob?.cancel()
            _focusTimerState.update { it.copy(isRunning = false) }
        } else {
            _focusTimerState.update { it.copy(isRunning = true, completedToast = null) }
            timerJob = viewModelScope.launch {
                while (_focusTimerState.value.remainingSeconds > 0 && _focusTimerState.value.isRunning) {
                    delay(1000L)
                    _focusTimerState.update { st ->
                        st.copy(remainingSeconds = (st.remainingSeconds - 1).coerceAtLeast(0))
                    }
                }
                if (_focusTimerState.value.remainingSeconds == 0) {
                    val mins = _focusTimerState.value.presetMinutes
                    val label = _focusTimerState.value.focusLabel.ifBlank { "Focus Session" }
                    _focusTimerState.update {
                        it.copy(
                            isRunning = false,
                            remainingSeconds = mins * 60,
                            completedToast = "Logged $mins min focus session (+${mins * 3} XP)!"
                        )
                    }
                    repository.recordFocusSession(currentUserId, mins, label)
                }
            }
        }
    }

    fun resetFocusTimer() {
        timerJob?.cancel()
        _focusTimerState.update {
            it.copy(
                isRunning = false,
                remainingSeconds = it.presetMinutes * 60,
                completedToast = null
            )
        }
    }

    fun logElapsedFocusNow() {
        val st = _focusTimerState.value
        val elapsedMins = ((st.presetMinutes * 60 - st.remainingSeconds) / 60).coerceAtLeast(1)
        timerJob?.cancel()
        viewModelScope.launch {
            repository.recordFocusSession(
                userId = currentUserId,
                durationMinutes = elapsedMins,
                label = st.focusLabel.ifBlank { "Focus Study" }
            )
            _focusTimerState.update {
                it.copy(
                    isRunning = false,
                    remainingSeconds = it.presetMinutes * 60,
                    completedToast = "Logged $elapsedMins min study session!"
                )
            }
        }
    }

    fun clearFocusToast() {
        _focusTimerState.update { it.copy(completedToast = null) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
