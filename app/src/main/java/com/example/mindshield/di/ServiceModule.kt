package com.example.mindshield.di

import android.content.Context
import com.example.mindshield.util.ScreenCaptureHelper
import com.example.mindshield.util.GeminiContentClassifier
import com.example.mindshield.util.ContentDecisionEngine
import com.example.mindshield.util.DistractionNotificationManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideScreenCaptureHelper(): ScreenCaptureHelper {
        return ScreenCaptureHelper()
    }

    @Provides
    @Singleton
    fun provideGeminiContentClassifier(gson: Gson, settingsRepository: com.example.mindshield.data.repository.SettingsRepository): GeminiContentClassifier {
        return GeminiContentClassifier(gson, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideContentDecisionEngine(@ApplicationContext context: Context): ContentDecisionEngine {
        return ContentDecisionEngine(context)
    }

    @Provides
    @Singleton
    fun provideDistractionNotificationManager(@ApplicationContext context: Context): DistractionNotificationManager {
        return DistractionNotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(settingsDataStore: com.example.mindshield.data.local.SettingsDataStore): com.example.mindshield.data.repository.SettingsRepository {
        return com.example.mindshield.data.repository.SettingsRepository(settingsDataStore)
    }
} 