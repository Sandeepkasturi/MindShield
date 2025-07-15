package com.example.mindshield.data.local

import androidx.room.*
import com.example.mindshield.data.model.AppUsage
import com.example.mindshield.data.model.DistractionEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    
    @Query("SELECT * FROM app_usage ORDER BY timestamp DESC")
    fun getAllAppUsage(): Flow<List<AppUsage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(appUsage: AppUsage)
    
    @Query("SELECT * FROM app_usage WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getAppUsageForPeriod(startTime: Long, endTime: Long): List<AppUsage>
    
    @Query("SELECT * FROM app_usage WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getAppUsageByPackage(packageName: String): List<AppUsage>
    
    @Query("SELECT * FROM app_usage WHERE isDistracting = 1 ORDER BY duration DESC")
    suspend fun getDistractingApps(): List<AppUsage>
    
    @Insert
    suspend fun insertDistractionEvent(event: DistractionEvent)
    
    @Delete
    suspend fun deleteAppUsage(appUsage: AppUsage)
    
    @Query("DELETE FROM app_usage")
    suspend fun clearAllAppUsage()
} 