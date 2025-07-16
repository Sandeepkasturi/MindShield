package com.example.mindshield.di

import android.content.Context
import com.example.mindshield.data.local.*
import com.example.mindshield.data.repository.AppInfoRepository
import com.example.mindshield.data.repository.AppTimerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideMindShieldDatabase(@ApplicationContext context: Context): MindShieldDatabase {
        return MindShieldDatabase.getDatabase(context)
    }
    
    @Provides
    @Singleton
    fun provideAppUsageDao(database: MindShieldDatabase): AppUsageDao {
        return database.appUsageDao()
    }
    
    @Provides
    @Singleton
    fun provideDistractionEventDao(database: MindShieldDatabase): DistractionEventDao {
        return database.distractionEventDao()
    }
    
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideAppInfoDao(database: MindShieldDatabase): AppInfoDao {
        return database.appInfoDao()
    }

    @Provides
    @Singleton
    fun provideAppInfoRepository(appInfoDao: AppInfoDao): AppInfoRepository {
        return AppInfoRepository(appInfoDao)
    }
    
    @Provides
    @Singleton
    fun provideAppTimerDao(database: MindShieldDatabase): AppTimerDao {
        return database.appTimerDao()
    }
    
    @Provides
    @Singleton
    fun provideAppTimerRepository(appTimerDao: AppTimerDao): AppTimerRepository {
        return AppTimerRepository(appTimerDao)
    }
} 