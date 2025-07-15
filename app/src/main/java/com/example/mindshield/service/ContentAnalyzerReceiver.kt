package com.example.mindshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mindshield.data.model.ActionDecision
import com.example.mindshield.util.ContentDecisionEngine
import com.example.mindshield.util.GeminiContentClassifier
import com.example.mindshield.service.TextExtractorService.Companion.ACTION_EXTRACTED_TEXT
import com.example.mindshield.service.TextExtractorService.Companion.EXTRA_EXTRACTED_TEXT
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ContentAnalyzerReceiver : BroadcastReceiver() {
    @Inject lateinit var classifier: GeminiContentClassifier
    @Inject lateinit var decisionEngine: ContentDecisionEngine

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXTRACTED_TEXT) return
        val text = intent.getStringExtra(EXTRA_EXTRACTED_TEXT) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val classification = classifier.classify(text)
                val action = decisionEngine.decideAction(classification)
                Log.d("ContentAnalyzerReceiver", "Classification: $classification, Action: $action")
                // Broadcast result for notification manager or other listeners
                val resultIntent = Intent(ACTION_CLASSIFICATION_RESULT).apply {
                    putExtra(EXTRA_CLASSIFICATION, classification.classification)
                    putExtra(EXTRA_CONFIDENCE, classification.confidence)
                    putExtra(EXTRA_ACTION, action.name)
                }
                context.sendBroadcast(resultIntent)
            } catch (e: Exception) {
                Log.e("ContentAnalyzerReceiver", "Error in classification pipeline: ${e.message}")
            }
        }
    }

    companion object {
        const val ACTION_CLASSIFICATION_RESULT = "com.example.mindshield.ACTION_CLASSIFICATION_RESULT"
        const val EXTRA_CLASSIFICATION = "classification"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_ACTION = "action_decision"
    }
} 