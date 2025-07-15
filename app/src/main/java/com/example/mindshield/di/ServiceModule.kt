package com.example.mindshield.di

import android.content.Context
import com.example.mindshield.util.ScreenCaptureHelper
import com.example.mindshield.util.GeminiContentClassifier
import com.example.mindshield.util.ContentDecisionEngine
import com.example.mindshield.util.DistractionNotificationManager
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
    fun provideGeminiContentClassifier(): GeminiContentClassifier {
        return GeminiContentClassifier()
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
} 