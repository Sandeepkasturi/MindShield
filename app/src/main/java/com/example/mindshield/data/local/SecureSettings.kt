package com.example.mindshield.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64
import java.security.SecureRandom

@Singleton
class SecureSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKeyAlias: String = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
    }

    val geminiApiKeyFlow: Flow<String> = callbackFlow {
        trySend(prefs.getString(KEY_GEMINI_API, "") ?: "")
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_GEMINI_API) {
                trySend(sp.getString(KEY_GEMINI_API, "") ?: "")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key).apply()
    }

    fun getOrCreateDbPassphrase(): ByteArray {
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null && existing.isNotEmpty()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
        return bytes
    }
}