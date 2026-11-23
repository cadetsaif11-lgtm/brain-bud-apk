package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.GeneratedFlashcardDraft
import com.example.data.model.GeneratedQuizDraft
import com.example.data.model.GeneratedStudyPack
import com.example.data.model.WeakAreaAnalysisResult
import com.example.data.model.WeakAreaItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "BrainBuddyAiService"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

enum class TutorMode(val label: String, val instruction: String) {
    ELI5("Explain Simply", "Explain concepts in crystal-clear, intuitive everyday language with relatable analogies."),
    SOCRATIC("Socratic Coach", "Guide the student to discover the answer step-by-step using thoughtful leading questions before revealing."),
    EXAM_PREP("Exam Drill", "Focus on high-yield exam definitions, common traps, and concise memory hooks.")
}

class BrainBuddyAiService {

    private val studyPackSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short, descriptive deck title (max 80 chars)"),
            "subject" to Schema.string(description = "Academic subject name (e.g., Biology, Algorithms, History)"),
            "summary" to Schema.string(description = "Concise 3-5 sentence synthesis of the core material"),
            "flashcards" to Schema.array(
                description = "6 to 10 high-yield flashcards",
                items = Schema.obj(
                    properties = mapOf(
                        "front" to Schema.string(description = "Clear question or prompt"),
                        "back" to Schema.string(description = "Accurate, concise answer"),
                        "hint" to Schema.string(description = "Helpful memory cue without giving away the answer"),
                        "conceptTag" to Schema.string(description = "Short 1-3 word subtopic tag")
                    )
                )
            ),
            "questions" to Schema.array(
                description = "5 to 8 multiple-choice quiz questions",
                items = Schema.obj(
                    properties = mapOf(
                        "prompt" to Schema.string(description = "Multiple-choice question"),
                        "options" to Schema.array(
                            description = "Exactly 4 plausible answer choices",
                            items = Schema.string()
                        ),
                        "correctIndex" to Schema.integer(description = "0-based index (0 to 3) of the correct option"),
                        "explanation" to Schema.string(description = "Why the correct answer is right and others are wrong"),
                        "conceptTag" to Schema.string(description = "Short 1-3 word subtopic tag matching flashcards")
                    )
                )
            )
        )
    )

    private val weakAreaSchema = Schema.obj(
        properties = mapOf(
            "overallDiagnosis" to Schema.string(description = "2-3 sentence summary of the student's learning bottlenecks"),
            "weakAreas" to Schema.array(
                description = "Up to 5 prioritized weak areas",
                items = Schema.obj(
                    properties = mapOf(
                        "topic" to Schema.string(description = "Weak concept or subtopic name"),
                        "severity" to Schema.enumeration(
                            values = listOf("High", "Medium", "Low"),
                            description = "Urgency to review"
                        ),
                        "reason" to Schema.string(description = "Why this area appears weak based on misses"),
                        "actionTip" to Schema.string(description = "Concrete study tactic to master it")
                    )
                )
            ),
            "recommendations" to Schema.array(
                description = "3 concise, actionable next study steps",
                items = Schema.string()
            )
        )
    )

    private val studyPackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studyPackSchema
                temperature = 0.4f
            }
        )
    }

    private val weakAreaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = weakAreaSchema
                temperature = 0.3f
            }
        )
    }

    private val tutorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    suspend fun generateStudyPackFromNotes(
        notes: String,
        preferredSubject: String = "",
        attachedImage: Bitmap? = null
    ): Result<GeneratedStudyPack> {
        return try {
            val promptBuilder = buildString {
                appendLine("You are BrainBuddy, an expert cognitive science study designer.")
                appendLine("Convert the provided study material into a structured study pack with:")
                appendLine("1. A clear title, subject, and concise executive summary.")
                appendLine("2. 6-10 atomic flashcards (front, back, hint, conceptTag).")
                appendLine("3. 5-8 conceptual multiple-choice quiz questions with EXACTLY 4 options each and correctIndex between 0 and 3.")
                if (preferredSubject.isNotBlank()) {
                    appendLine("Preferred subject hint: $preferredSubject")
                }
                if (notes.isNotBlank()) {
                    appendLine("\nStudy Notes:\n${notes.take(12000)}")
                } else {
                    appendLine("\nExtract all key notes, concepts, and formulas from the attached study image.")
                }
            }

            val inputContent = content {
                if (attachedImage != null) {
                    image(attachedImage)
                }
                text(promptBuilder)
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                studyPackModel.generateContent(inputContent)
            }
            val jsonText = response.text ?: return Result.failure(
                IllegalStateException("Gemini returned an empty response.")
            )
            Result.success(parseStudyPackJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Study pack generation timed out", e)
            Result.failure(Exception("AI generation timed out after 60s. Please try shorter notes or retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate study pack", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate study pack."))
        }
    }

    suspend fun generateBoosterPackForWeakTopics(
        weakTopics: List<String>,
        contextNotes: String
    ): Result<GeneratedStudyPack> {
        val topicsText = weakTopics.joinToString(", ").ifBlank { "Core Fundamentals" }
        val prompt = """
            Create a targeted Weak-Area Booster Study Pack focusing specifically on these struggling topics: $topicsText.
            Design 6 crisp memory-anchor flashcards and 5 diagnostic multiple-choice questions (with 4 options each) that target common misconceptions in these topics.
            Reference context: ${contextNotes.take(6000)}
        """.trimIndent()
        return generateStudyPackFromNotes(notes = prompt, preferredSubject = "Targeted Review")
    }

    suspend fun analyzeWeakAreas(
        deckSummaries: List<String>,
        missedItemsSummary: List<String>
    ): Result<WeakAreaAnalysisResult> {
        return try {
            val prompt = buildString {
                appendLine("You are BrainBuddy's AI Learning Diagnostician.")
                appendLine("Analyze this student's deck performance and missed concepts to identify weak areas.")
                appendLine("\nDecks & Scores:")
                deckSummaries.forEach { appendLine("- $it") }
                appendLine("\nMissed Flashcards & Quiz Questions:")
                missedItemsSummary.forEach { appendLine("- $it") }
                appendLine("\nReturn up to 5 specific weak areas ranked by severity (High/Medium/Low) and 3 actionable recommendations.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                weakAreaModel.generateContent(prompt)
            }
            val jsonText = response.text ?: return Result.failure(
                IllegalStateException("Gemini returned an empty diagnosis.")
            )
            Result.success(parseWeakAreaJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Weak area analysis timed out", e)
            Result.failure(Exception("Weak area analysis timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze weak areas", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to analyze weak areas."))
        }
    }

    fun streamTutorReply(
        userMessage: String,
        deckContext: String,
        mode: TutorMode,
        historyPairs: List<Pair<String, String>>
    ): Flow<String> {
        val systemPreamble = """
            You are BrainBuddy, a warm, encouraging AI study companion.
            Teaching style: ${mode.label} — ${mode.instruction}
            Keep answers focused, well-structured, and under 220 words unless asked for detail.
            ${if (deckContext.isNotBlank()) "Current study deck context:\n${deckContext.take(4000)}" else ""}
        """.trimIndent()

        val history = buildList {
            add(content("user") { text(systemPreamble) })
            add(content("model") { text("Understood! I'm ready to help you study using the ${mode.label} approach.") })
            historyPairs.takeLast(8).forEach { (role, msg) ->
                add(content(role) { text(msg) })
            }
        }

        val chat = tutorModel.startChat(history = history)
        return chat.sendMessageStream(userMessage)
            .mapNotNull { it.text }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "Tutor streaming failed", error)
                throw error
            }
    }

    private fun parseStudyPackJson(jsonString: String): GeneratedStudyPack {
        val root = JSONObject(jsonString)
        val title = root.optString("title", "Study Pack").trim().take(120).ifBlank { "Study Pack" }
        val subject = root.optString("subject", "General").trim().take(60).ifBlank { "General" }
        val summary = root.optString("summary", "").trim().take(4000)

        val flashcardsArr = root.optJSONArray("flashcards")
        val flashcards = buildList {
            if (flashcardsArr != null) {
                for (i in 0 until flashcardsArr.length()) {
                    val item = flashcardsArr.optJSONObject(i) ?: continue
                    val front = item.optString("front", "").trim().take(1000)
                    val back = item.optString("back", "").trim().take(2000)
                    if (front.isNotEmpty() && back.isNotEmpty()) {
                        add(
                            GeneratedFlashcardDraft(
                                front = front,
                                back = back,
                                hint = item.optString("hint", "").trim().take(500),
                                conceptTag = item.optString("conceptTag", subject).trim().take(80).ifBlank { subject }
                            )
                        )
                    }
                }
            }
        }

        val questionsArr = root.optJSONArray("questions")
        val questions = buildList {
            if (questionsArr != null) {
                for (i in 0 until questionsArr.length()) {
                    val item = questionsArr.optJSONObject(i) ?: continue
                    val prompt = item.optString("prompt", "").trim().take(1000)
                    val rawOptions = item.optJSONArray("options")
                    val parsedOptions = buildList {
                        if (rawOptions != null) {
                            for (j in 0 until rawOptions.length()) {
                                val opt = rawOptions.optString(j, "").trim().take(500)
                                if (opt.isNotEmpty()) add(opt)
                            }
                        }
                    }.take(4).toMutableList()

                    while (parsedOptions.size < 4) {
                        parsedOptions.add("Option ${parsedOptions.size + 1}")
                    }

                    val correctIdx = item.optInt("correctIndex", 0).coerceIn(0, 3)
                    if (prompt.isNotEmpty()) {
                        add(
                            GeneratedQuizDraft(
                                prompt = prompt,
                                options = parsedOptions,
                                correctIndex = correctIdx,
                                explanation = item.optString("explanation", "").trim().take(1500),
                                conceptTag = item.optString("conceptTag", subject).trim().take(80).ifBlank { subject }
                            )
                        )
                    }
                }
            }
        }

        return GeneratedStudyPack(
            title = title,
            subject = subject,
            summary = summary,
            flashcards = flashcards,
            questions = questions
        )
    }

    private fun parseWeakAreaJson(jsonString: String): WeakAreaAnalysisResult {
        val root = JSONObject(jsonString)
        val overall = root.optString("overallDiagnosis", "").trim().take(2000)

        val weakArr = root.optJSONArray("weakAreas")
        val weakAreas = buildList {
            if (weakArr != null) {
                for (i in 0 until weakArr.length()) {
                    val obj = weakArr.optJSONObject(i) ?: continue
                    val topic = obj.optString("topic", "").trim()
                    if (topic.isNotEmpty()) {
                        add(
                            WeakAreaItem(
                                topic = topic,
                                severity = obj.optString("severity", "Medium"),
                                reason = obj.optString("reason", ""),
                                actionTip = obj.optString("actionTip", "")
                            )
                        )
                    }
                }
            }
        }

        val recsArr = root.optJSONArray("recommendations")
        val recs = buildList {
            if (recsArr != null) {
                for (i in 0 until recsArr.length()) {
                    val r = recsArr.optString(i, "").trim()
                    if (r.isNotEmpty()) add(r)
                }
            }
        }.take(10)

        return WeakAreaAnalysisResult(
            overallDiagnosis = overall,
            weakAreas = weakAreas,
            recommendations = recs
        )
    }
}
