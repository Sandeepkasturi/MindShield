package com.example.mindshield.data.local

import androidx.room.*
import com.example.mindshield.data.model.DistractionEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface DistractionEventDao {
    
    @Query("SELECT * FROM distraction_events ORDER BY timestamp DESC")
    fun getAllDistractionEvents(): Flow<List<DistractionEvent>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDistractionEvent(event: DistractionEvent)
    
    @Query("SELECT * FROM distraction_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getDistractionEventsForPeriod(startTime: Long, endTime: Long): List<DistractionEvent>
    
    @Query("SELECT * FROM distraction_events WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getDistractionEventsByPackage(packageName: String): List<DistractionEvent>
    
    @Query("UPDATE distraction_events SET acknowledged = 1 WHERE id = :eventId")
    suspend fun acknowledgeDistractionEvent(eventId: Long)
    
    @Delete
    suspend fun deleteDistractionEvent(event: DistractionEvent)
    
    @Query("DELETE FROM distraction_events")
    suspend fun clearAllDistractionEvents()
} 