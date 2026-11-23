package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MasteryLevel {
    NEW,
    LEARNING,
    MASTERED;

    companion object {
        fun fromRaw(raw: String?): MasteryLevel =
            entries.firstOrNull { it.name == raw } ?: NEW
    }
}

enum class SessionType {
    QUIZ,
    FLASHCARDS,
    FOCUS;

    companion object {
        fun fromRaw(raw: String?): SessionType =
            entries.firstOrNull { it.name == raw } ?: QUIZ
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Learner",
    val totalStudyMinutes: Long = 0L,
    val quizzesCompleted: Long = 0L,
    val cardsMastered: Long = 0L,
    val currentStreakDays: Long = 1L,
    val totalXp: Long = 0L,
    val lastStudiedDate: String = "",
    val weakAreaSummary: String = "",
    val weakTopics: List<String> = emptyList(),
    val studyRecommendations: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyDeck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "General",
    val sourceNotes: String = "",
    val aiSummary: String = "",
    val flashcardCount: Long = 0L,
    val masteredCount: Long = 0L,
    val quizCount: Long = 0L,
    val lastScorePercent: Long = -1L,
    val weakConcepts: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Flashcard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deckId: String = "",
    val front: String = "",
    val back: String = "",
    val hint: String = "",
    val conceptTag: String = "Core Concept",
    val masteryLevel: String = MasteryLevel.NEW.name,
    val reviewCount: Long = 0L,
    val missCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun masteryEnum(): MasteryLevel = MasteryLevel.fromRaw(masteryLevel)
}

@Keep
@IgnoreExtraProperties
data class QuizQuestion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deckId: String = "",
    val prompt: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Long = 0L,
    val explanation: String = "",
    val conceptTag: String = "Core Concept",
    val timesAnswered: Long = 0L,
    val timesMissed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deckId: String = "",
    val deckTitle: String = "",
    val sessionType: String = SessionType.QUIZ.name,
    val scorePercent: Long = 0L,
    val durationMinutes: Long = 0L,
    val weakTags: List<String> = emptyList(),
    val dayKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun sessionEnum(): SessionType = SessionType.fromRaw(sessionType)
}

// Structured outputs from Gemini AI
data class GeneratedFlashcardDraft(
    val front: String,
    val back: String,
    val hint: String,
    val conceptTag: String
)

data class GeneratedQuizDraft(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val conceptTag: String
)

data class GeneratedStudyPack(
    val title: String,
    val subject: String,
    val summary: String,
    val flashcards: List<GeneratedFlashcardDraft>,
    val questions: List<GeneratedQuizDraft>
)

data class WeakAreaItem(
    val topic: String,
    val severity: String,
    val reason: String,
    val actionTip: String
)

data class WeakAreaAnalysisResult(
    val overallDiagnosis: String,
    val weakAreas: List<WeakAreaItem>,
    val recommendations: List<String>
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
