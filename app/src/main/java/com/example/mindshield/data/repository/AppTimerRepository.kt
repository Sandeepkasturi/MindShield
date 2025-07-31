package com.example.mindshield.data.repository

import com.example.mindshield.data.local.AppTimerDao
import com.example.mindshield.data.model.AppTimer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTimerRepository @Inject constructor(
    private val appTimerDao: AppTimerDao
) {
    
    fun getAllAppTimers(): Flow<List<AppTimer>> = appTimerDao.getAllAppTimers()
    
    suspend fun getAppTimer(packageName: String): AppTimer? = appTimerDao.getAppTimer(packageName)
    
    suspend fun insertAppTimer(appTimer: AppTimer) = appTimerDao.insertAppTimer(appTimer)
    
    suspend fun updateAppTimer(appTimer: AppTimer) = appTimerDao.updateAppTimer(appTimer)
    
    suspend fun deleteAppTimer(appTimer: AppTimer) = appTimerDao.deleteAppTimer(appTimer)
    
    suspend fun deleteAppTimerByPackage(packageName: String) = appTimerDao.deleteAppTimerByPackage(packageName)
    
    suspend fun getEnabledAppTimers(): List<AppTimer> = appTimerDao.getEnabledAppTimers()
    
    suspend fun incrementUsage(packageName: String, additionalMinutes: Int) = 
        appTimerDao.incrementUsage(packageName, additionalMinutes)
    
    suspend fun incrementUsageSeconds(packageName: String, additionalSeconds: Int) = 
        appTimerDao.incrementUsageSeconds(packageName, additionalSeconds)
    
    suspend fun resetUsage(packageName: String, resetDate: Long) = 
        appTimerDao.resetUsage(packageName, resetDate)
    
    suspend fun resetUsageToSeconds(packageName: String, usageSeconds: Int) = 
        appTimerDao.resetUsageToSeconds(packageName, usageSeconds)
    
    suspend fun checkAndResetDailyUsage() {
        val enabledTimers = getEnabledAppTimers()
        val currentDate = System.currentTimeMillis()
        val startOfDay = getStartOfDay()
        
        enabledTimers.forEach { timer ->
            if (timer.lastResetDate < startOfDay) {
                resetUsage(timer.packageName, currentDate)
            }
        }
    }
    
    suspend fun isAppBlocked(packageName: String): Boolean {
        val timer = getAppTimer(packageName) ?: return false
        if (!timer.isEnabled) return false
        
        // Check if we need to reset daily usage
        val currentDate = System.currentTimeMillis()
        val startOfDay = getStartOfDay()
        if (timer.lastResetDate < startOfDay) {
            resetUsage(packageName, currentDate)
            return false
        }
        // Block if current usage in seconds >= daily limit in seconds
        return (timer.currentUsageSeconds ?: 0) >= (timer.dailyLimitMinutes * 60)
    }
    
    private fun getStartOfDay(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
} 