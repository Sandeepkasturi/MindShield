package com.example.mindshield.util

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mindshield.data.model.ActionDecision
import com.example.mindshield.data.model.ClassificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "decision_engine_prefs")

@Singleton
class ContentDecisionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val LAST_ACTION_TIME_KEY = longPreferencesKey("last_action_time")
        private const val ALERT_DEBOUNCE_MS = 10 * 60 * 1000L // 10 minutes
    }

    suspend fun decideAction(classificationResult: ClassificationResult): ActionDecision = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prefs = context.dataStore.data.first()
        val lastActionTime = prefs[LAST_ACTION_TIME_KEY] ?: 0L
        val timeSinceLast = now - lastActionTime

        return@withContext when {
            classificationResult.classification.equals("unproductive", ignoreCase = true) &&
                    classificationResult.confidence > 0.7 &&
                    timeSinceLast > ALERT_DEBOUNCE_MS -> {
                // Persist new lastActionTime
                context.dataStore.edit { it[LAST_ACTION_TIME_KEY] = now }
                ActionDecision.ALERT
            }
            classificationResult.classification.equals("productive", ignoreCase = true) ||
                    classificationResult.classification.equals("neutral", ignoreCase = true) -> {
                ActionDecision.NONE
            }
            else -> ActionDecision.NONE
        }
    }
} 