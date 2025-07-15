package com.example.mindshield.data.repository

import com.example.mindshield.data.local.DistractionEventDao
import com.example.mindshield.data.model.DistractionEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistractionEventRepository @Inject constructor(
    private val distractionEventDao: DistractionEventDao
) {
    
    fun getAllDistractionEvents(): Flow<List<DistractionEvent>> {
        return distractionEventDao.getAllDistractionEvents()
    }
    
    suspend fun recordDistractionEvent(
        packageName: String,
        appName: String,
        duration: Long,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val event = DistractionEvent(
            packageName = packageName,
            appName = appName,
            duration = duration,
            timestamp = timestamp
        )
        distractionEventDao.insertDistractionEvent(event)
    }
    
    suspend fun getDistractionEventsForPeriod(startTime: Long, endTime: Long): List<DistractionEvent> {
        return distractionEventDao.getDistractionEventsForPeriod(startTime, endTime)
    }
    
    suspend fun getDistractionEventsByPackage(packageName: String): List<DistractionEvent> {
        return distractionEventDao.getDistractionEventsByPackage(packageName)
    }
    
    suspend fun acknowledgeDistractionEvent(eventId: Long) {
        distractionEventDao.acknowledgeDistractionEvent(eventId)
    }
    
    suspend fun deleteDistractionEvent(event: DistractionEvent) {
        distractionEventDao.deleteDistractionEvent(event)
    }
    
    suspend fun clearAllDistractionEvents() {
        distractionEventDao.clearAllDistractionEvents()
    }
} 