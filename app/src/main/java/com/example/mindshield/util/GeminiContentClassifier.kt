package com.example.mindshield.util

import com.example.mindshield.data.model.ClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// Gemini API request/response models
// Make these public

data class GeminiRequest(val prompt: String)
data class GeminiResponse(val classification: String, val confidence: Double)

// Retrofit interface for Gemini API
interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:classifyContent")
    suspend fun classifyContentWithKey(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

@Singleton
class GeminiContentClassifier @Inject constructor() {
    private val baseUrl = "https://generativelanguage.googleapis.com/"
    private val apiKey = "YOUR_API_KEY"

    private val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    private fun buildPrompt(text: String): String =
        """
        Classify the following content as one of three categories:\n\n" +
        "Productive: Educational, Professional, Work-related, Learning, Technology, Development.\n" +
        "Unproductive: Entertainment, Memes, Casual Browsing, Short-form Video Content, Gaming.\n" +
        "Neutral: General browsing, News, Information that doesn't fall strictly in either category.\n\n" +
        "Content:\n$text\n" +
        """

    suspend fun classify(text: String): ClassificationResult = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(text)
        val request = GeminiRequest(prompt)
        val response = api.classifyContentWithKey(apiKey, request)
        ClassificationResult(
            classification = response.classification,
            confidence = response.confidence
        )
    }
} 
