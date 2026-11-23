package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Flashcard
import com.example.data.model.GeneratedStudyPack
import com.example.data.model.MasteryLevel
import com.example.data.model.QuizQuestion
import com.example.data.model.SessionType
import com.example.data.model.StudyDeck
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.data.model.WeakAreaAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun todayKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date())
    }

    private fun yesterdayKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(System.currentTimeMillis() - 86_400_000L))
    }

    suspend fun ensureUserProfile(userId: String, displayName: String?): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(userId)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName?.trim()?.take(100)?.ifBlank { "Learner" } ?: "Learner"
                val payload = mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "totalStudyMinutes" to 0L,
                    "quizzesCompleted" to 0L,
                    "cardsMastered" to 0L,
                    "currentStreakDays" to 1L,
                    "totalXp" to 0L,
                    "lastStudiedDate" to todayKey(),
                    "weakAreaSummary" to "",
                    "weakTopics" to emptyList<String>(),
                    "studyRecommendations" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDecks(userId: String): Flow<List<StudyDeck>> = flow {
        val path = "users/$userId/decks"
        emitAll(
            db.collection("users").document(userId).collection("decks")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyDeck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFlashcards(userId: String, deckId: String): Flow<List<Flashcard>> = flow {
        val path = "users/$userId/decks/$deckId/flashcards"
        emitAll(
            db.collection("users").document(userId)
                .collection("decks").document(deckId)
                .collection("flashcards")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Flashcard::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeQuestions(userId: String, deckId: String): Flow<List<QuizQuestion>> = flow {
        val path = "users/$userId/decks/$deckId/questions"
        emitAll(
            db.collection("users").document(userId)
                .collection("decks").document(deckId)
                .collection("questions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        QuizQuestion::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<StudySession>> = flow {
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudySession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveGeneratedStudyPack(
        userId: String,
        sourceNotes: String,
        pack: GeneratedStudyPack
    ): Result<String> {
        var docPath = "users/$userId/decks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val userRef = db.collection("users").document(userId)
            val deckRef = userRef.collection("decks").document()
            val deckId = deckRef.id
            docPath = deckRef.path

            val safeNotes = sourceNotes.trim().ifBlank { pack.summary }.take(15000).ifBlank { "AI Study Notes" }
            val safeTitle = pack.title.trim().take(120).ifBlank { "Untitled Deck" }
            val safeSubject = pack.subject.trim().take(60).ifBlank { "General" }
            val safeSummary = pack.summary.trim().take(4000)

            val cardsToWrite = pack.flashcards.take(50)
            val questionsToWrite = pack.questions.take(30)

            val batch = db.batch()
            val deckPayload = mapOf(
                "userId" to userId,
                "title" to safeTitle,
                "subject" to safeSubject,
                "sourceNotes" to safeNotes,
                "aiSummary" to safeSummary,
                "flashcardCount" to cardsToWrite.size.toLong(),
                "masteredCount" to 0L,
                "quizCount" to questionsToWrite.size.toLong(),
                "lastScorePercent" to -1L,
                "weakConcepts" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(deckRef, deckPayload)

            cardsToWrite.forEach { card ->
                val cardRef = deckRef.collection("flashcards").document()
                val cardPayload = mapOf(
                    "userId" to userId,
                    "deckId" to deckId,
                    "front" to card.front.trim().take(1000).ifBlank { "Question" },
                    "back" to card.back.trim().take(2000).ifBlank { "Answer" },
                    "hint" to card.hint.trim().take(500),
                    "conceptTag" to card.conceptTag.trim().take(80).ifBlank { safeSubject },
                    "masteryLevel" to MasteryLevel.NEW.name,
                    "reviewCount" to 0L,
                    "missCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(cardRef, cardPayload)
            }

            questionsToWrite.forEach { q ->
                val qRef = deckRef.collection("questions").document()
                val cleanOptions = q.options.map { it.trim().take(500).ifBlank { "Option" } }.take(4).toMutableList()
                while (cleanOptions.size < 4) cleanOptions.add("Option ${cleanOptions.size + 1}")
                val qPayload = mapOf(
                    "userId" to userId,
                    "deckId" to deckId,
                    "prompt" to q.prompt.trim().take(1000).ifBlank { "Question" },
                    "options" to cleanOptions,
                    "correctIndex" to q.correctIndex.coerceIn(0, 3).toLong(),
                    "explanation" to q.explanation.trim().take(1500),
                    "conceptTag" to q.conceptTag.trim().take(80).ifBlank { safeSubject },
                    "timesAnswered" to 0L,
                    "timesMissed" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(qRef, qPayload)
            }

            batch.commit().await()
            awardActivityXp(userId, xpDelta = 25L, minutesDelta = 2L)
            Result.success(deckId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateFlashcardMastery(
        userId: String,
        deckId: String,
        card: Flashcard,
        newMastery: MasteryLevel,
        allCardsInDeck: List<Flashcard>
    ): Result<Unit> {
        val cardPath = "users/$userId/decks/$deckId/flashcards/${card.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(deckId.isNotBlank() && card.id.isNotBlank())

            val wasMastered = card.masteryEnum() == MasteryLevel.MASTERED
            val isNowMastered = newMastery == MasteryLevel.MASTERED
            val isMiss = newMastery == MasteryLevel.LEARNING

            val deckRef = db.collection("users").document(userId).collection("decks").document(deckId)
            val cardRef = deckRef.collection("flashcards").document(card.id)

            val updatedCards = allCardsInDeck.map {
                if (it.id == card.id) {
                    it.copy(
                        masteryLevel = newMastery.name,
                        reviewCount = it.reviewCount + 1L,
                        missCount = it.missCount + if (isMiss) 1L else 0L
                    )
                } else it
            }
            val newMasteredCount = updatedCards.count { it.masteryEnum() == MasteryLevel.MASTERED }.toLong()
            val weakTags = updatedCards
                .filter { it.masteryEnum() == MasteryLevel.LEARNING || it.missCount > 0L }
                .map { it.conceptTag }
                .distinct()
                .take(20)

            val batch = db.batch()
            batch.update(
                cardRef,
                mapOf(
                    "masteryLevel" to newMastery.name,
                    "reviewCount" to (card.reviewCount + 1L),
                    "missCount" to (card.missCount + if (isMiss) 1L else 0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                deckRef,
                mapOf(
                    "masteredCount" to newMasteredCount,
                    "weakConcepts" to weakTags,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            val masteredDelta = when {
                !wasMastered && isNowMastered -> 1L
                wasMastered && !isNowMastered -> -1L
                else -> 0L
            }
            val xpDelta = if (isNowMastered) 15L else 5L
            val profileRef = db.collection("users").document(userId)
            val profileSnap = profileRef.get().await()
            if (profileSnap.exists()) {
                val currentMastered = (profileSnap.getLong("cardsMastered") ?: 0L) + masteredDelta
                profileRef.update(
                    mapOf(
                        "cardsMastered" to currentMastered.coerceAtLeast(0L),
                        "totalXp" to FieldValue.increment(xpDelta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, cardPath)
            Result.failure(e)
        }
    }

    suspend fun recordQuizCompletion(
        userId: String,
        deckId: String,
        deckTitle: String,
        scorePercent: Int,
        durationMinutes: Int,
        answeredQuestions: List<QuizQuestion>,
        missedQuestionIds: Set<String>,
        weakTags: List<String>
    ): Result<Unit> {
        val deckPath = "users/$userId/decks/$deckId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(deckId.isNotBlank())

            val userRef = db.collection("users").document(userId)
            val deckRef = userRef.collection("decks").document(deckId)
            val sessionRef = userRef.collection("sessions").document()
            val boundedWeakTags = weakTags.distinct().take(20)

            val batch = db.batch()
            answeredQuestions.forEach { q ->
                val qRef = deckRef.collection("questions").document(q.id)
                val missed = if (missedQuestionIds.contains(q.id)) 1L else 0L
                batch.update(
                    qRef,
                    mapOf(
                        "timesAnswered" to (q.timesAnswered + 1L),
                        "timesMissed" to (q.timesMissed + missed),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                deckRef,
                mapOf(
                    "quizCount" to FieldValue.increment(1L),
                    "lastScorePercent" to scorePercent.coerceIn(0, 100).toLong(),
                    "weakConcepts" to boundedWeakTags,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                sessionRef,
                mapOf(
                    "userId" to userId,
                    "deckId" to deckId,
                    "deckTitle" to deckTitle.take(120).ifBlank { "Quiz" },
                    "sessionType" to SessionType.QUIZ.name,
                    "scorePercent" to scorePercent.coerceIn(0, 100).toLong(),
                    "durationMinutes" to durationMinutes.coerceIn(1, 600).toLong(),
                    "weakTags" to boundedWeakTags,
                    "dayKey" to todayKey(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            val xpEarned = (scorePercent / 2 + 20).toLong()
            awardActivityXp(
                userId = userId,
                xpDelta = xpEarned,
                minutesDelta = durationMinutes.coerceAtLeast(1).toLong(),
                quizzesDelta = 1L
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, deckPath)
            Result.failure(e)
        }
    }

    suspend fun recordFocusSession(
        userId: String,
        durationMinutes: Int,
        label: String
    ): Result<Unit> {
        val path = "users/$userId/sessions"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val userRef = db.collection("users").document(userId)
            val sessionRef = userRef.collection("sessions").document()

            sessionRef.set(
                mapOf(
                    "userId" to userId,
                    "deckId" to "",
                    "deckTitle" to label.trim().take(120).ifBlank { "Deep Focus" },
                    "sessionType" to SessionType.FOCUS.name,
                    "scorePercent" to 100L,
                    "durationMinutes" to durationMinutes.coerceIn(1, 600).toLong(),
                    "weakTags" to emptyList<String>(),
                    "dayKey" to todayKey(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            awardActivityXp(
                userId = userId,
                xpDelta = (durationMinutes * 3L).coerceAtLeast(15L),
                minutesDelta = durationMinutes.coerceAtLeast(1).toLong()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveWeakAreaAnalysis(
        userId: String,
        analysis: WeakAreaAnalysisResult
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            db.collection("users").document(userId).update(
                mapOf(
                    "weakAreaSummary" to analysis.overallDiagnosis.take(2000),
                    "weakTopics" to analysis.weakAreas.map { it.topic }.take(20),
                    "studyRecommendations" to analysis.recommendations.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDeck(userId: String, deckId: String): Result<Unit> {
        val path = "users/$userId/decks/$deckId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val deckRef = db.collection("users").document(userId).collection("decks").document(deckId)
            val cards = deckRef.collection("flashcards").get().await()
            val questions = deckRef.collection("questions").get().await()
            val batch = db.batch()
            cards.documents.forEach { batch.delete(it.reference) }
            questions.documents.forEach { batch.delete(it.reference) }
            batch.delete(deckRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun awardActivityXp(
        userId: String,
        xpDelta: Long,
        minutesDelta: Long,
        quizzesDelta: Long = 0L
    ) {
        val profileRef = db.collection("users").document(userId)
        val snap = profileRef.get().await()
        if (!snap.exists()) return
        val lastDate = snap.getString("lastStudiedDate") ?: ""
        val currentStreak = snap.getLong("currentStreakDays") ?: 1L
        val today = todayKey()
        val updatedStreak = when (lastDate) {
            today -> currentStreak
            yesterdayKey() -> currentStreak + 1L
            else -> 1L
        }
        profileRef.update(
            mapOf(
                "totalXp" to FieldValue.increment(xpDelta),
                "totalStudyMinutes" to FieldValue.increment(minutesDelta),
                "quizzesCompleted" to FieldValue.increment(quizzesDelta),
                "currentStreakDays" to updatedStreak,
                "lastStudiedDate" to today,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
