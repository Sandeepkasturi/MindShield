package com.example.mindshield.data.local

import androidx.room.*
import com.example.mindshield.data.model.AppTimer
import kotlinx.coroutines.flow.Flow

@Dao
interface AppTimerDao {
    
    @Query("SELECT * FROM app_timers ORDER BY appName ASC")
    fun getAllAppTimers(): Flow<List<AppTimer>>
    
    @Query("SELECT * FROM app_timers WHERE packageName = :packageName")
    suspend fun getAppTimer(packageName: String): AppTimer?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppTimer(appTimer: AppTimer)
    
    @Update
    suspend fun updateAppTimer(appTimer: AppTimer)
    
    @Delete
    suspend fun deleteAppTimer(appTimer: AppTimer)
    
    @Query("DELETE FROM app_timers WHERE packageName = :packageName")
    suspend fun deleteAppTimerByPackage(packageName: String)
    
    @Query("SELECT * FROM app_timers WHERE isEnabled = 1")
    suspend fun getEnabledAppTimers(): List<AppTimer>
    
    @Query("UPDATE app_timers SET currentUsageMinutes = currentUsageMinutes + :additionalMinutes WHERE packageName = :packageName")
    suspend fun incrementUsage(packageName: String, additionalMinutes: Int)
    
    @Query("UPDATE app_timers SET currentUsageSeconds = currentUsageSeconds + :additionalSeconds WHERE packageName = :packageName")
    suspend fun incrementUsageSeconds(packageName: String, additionalSeconds: Int)
    
    @Query("UPDATE app_timers SET currentUsageMinutes = 0, currentUsageSeconds = 0, lastResetDate = :resetDate WHERE packageName = :packageName")
    suspend fun resetUsage(packageName: String, resetDate: Long)
    
    @Query("UPDATE app_timers SET currentUsageSeconds = :usageSeconds, currentUsageMinutes = :usageSeconds / 60 WHERE packageName = :packageName")
    suspend fun resetUsageToSeconds(packageName: String, usageSeconds: Int)
} 