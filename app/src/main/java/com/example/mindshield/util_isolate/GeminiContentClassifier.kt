package com.example.mindshield.util

import android.util.Log
import com.example.mindshield.data.model.ClassificationResult
import com.example.mindshield.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

// --- CORRECTED GEMINI API MODELS ---
// These models match the actual structure of the Gemini API's generateContent endpoint.

data class Part(val text: String)
data class Content(val parts: List<Part>)
data class GenerateContentRequest(val contents: List<Content>)

data class Candidate(val content: Content)
data class GenerateContentResponse(
    val candidates: List<Candidate>?,
    val promptFeedback: PromptFeedback?
)
data class PromptFeedback(val blockReason: String?)

data class ClassificationResponse(
    val classification: String?,
    val confidence: Double?
)

@Singleton
class GeminiContentClassifier @Inject constructor(
    private val gson: Gson,
    private val settingsRepository: SettingsRepository
) {
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    private val defaultApiKey = "AIzaSyCf-d1RX2oITs4ypofPpP8gW_ga8KujWAw"

    private fun buildPrompt(text: String): String {
        val truncatedText = text.take(30000)
        return """
        Analyze the following text and classify it into one of three categories: \"Productive\", \"Unproductive\", or \"Neutral\".
        
        - \"Productive\": Content that is educational, professional, work-related, or focused on learning and development.
        - \"Unproductive\": Content that is purely for entertainment, such as memes, casual social media, short-form videos, or gaming.
        - \"Neutral\": Content that does not clearly fall into the other two categories, like general news or informational browsing.

        Return your answer ONLY as a JSON object with two keys: \"classification\" (string) and \"confidence\" (a double between 0.0 and 1.0).

        Here is the text to analyze:
        
        ```
        $truncatedText
        ```
        
        JSON Response:
        """.trimIndent()
    }

    suspend fun classify(text: String): ClassificationResult = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(text)
        val requestBody = GenerateContentRequest(contents = listOf(Content(parts = listOf(Part(prompt)))))
        val requestBodyJson = gson.toJson(requestBody)
        // Get user API key if set, else use default
        val userApiKey = try { settingsRepository.geminiApiKey.first() } catch (_: Exception) { null }
        val apiKeyToUse = if (!userApiKey.isNullOrBlank()) userApiKey else defaultApiKey
        val url = URL("$baseUrl?key=$apiKeyToUse")
        var connection: HttpURLConnection? = null

        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(requestBodyJson)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                val generateContentResponse = gson.fromJson(responseBody, GenerateContentResponse::class.java)
                val modelResponseText = generateContentResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (modelResponseText == null) {
                    Log.e("GeminiClassifier", "API response was empty or invalid. Block Reason: ${generateContentResponse.promptFeedback?.blockReason}")
                    return@withContext ClassificationResult("Neutral", 0.5)
                }
                val classificationResponse = gson.fromJson(modelResponseText, ClassificationResponse::class.java)
                Log.d("GeminiClassifier", "[AI] FULL Raw AI response (modelResponseText): $modelResponseText")
                ClassificationResult(
                    classification = classificationResponse.classification ?: "Neutral",
                    confidence = classificationResponse.confidence ?: 0.5,
                    modelResponseText = modelResponseText
                )
            } else {
                Log.e("GeminiClassifier", "API Error: HTTP $responseCode - ${connection.responseMessage}")
                ClassificationResult("Neutral", 0.5)
            }
        } catch (e: IOException) {
            Log.e("GeminiClassifier", "Network Error: Failed to connect to API", e)
            ClassificationResult("Neutral", 0.5)
        } catch (e: JsonSyntaxException) {
            Log.e("GeminiClassifier", "JSON Parsing Error: The model returned malformed JSON.", e)
            ClassificationResult("Neutral", 0.5)
        } finally {
            connection?.disconnect()
        }
    }
} 
