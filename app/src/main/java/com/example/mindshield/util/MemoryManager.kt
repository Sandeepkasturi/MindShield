package com.example.mindshield.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

object MemoryManager {
    private const val TAG = "MemoryManager"
    
    // Memory thresholds
    private const val WARNING_THRESHOLD = 70 // 70% memory usage
    private const val CRITICAL_THRESHOLD = 85 // 85% memory usage
    
    private var memoryMonitorJob: Job? = null
    
    /**
     * Start memory monitoring
     */
    fun startMemoryMonitoring(context: Context) {
        memoryMonitorJob?.cancel()
        memoryMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkMemoryUsage()
                    delay(60000) // Check every minute
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring memory: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Memory monitoring started")
    }
    
    /**
     * Stop memory monitoring
     */
    fun stopMemoryMonitoring() {
        memoryMonitorJob?.cancel()
        memoryMonitorJob = null
        Log.d(TAG, "Memory monitoring stopped")
    }
    
    /**
     * Check current memory usage
     */
    private fun checkMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // MB
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val memoryUsagePercent = (usedMemory * 100) / maxMemory
        
        Log.d(TAG, "Memory Usage: ${usedMemory}MB / ${maxMemory}MB (${memoryUsagePercent}%)")
        
        when {
            memoryUsagePercent >= CRITICAL_THRESHOLD -> {
                Log.w(TAG, "CRITICAL: High memory usage detected (${memoryUsagePercent}%)")
                performCriticalCleanup()
            }
            memoryUsagePercent >= WARNING_THRESHOLD -> {
                Log.w(TAG, "WARNING: Elevated memory usage detected (${memoryUsagePercent}%)")
                performWarningCleanup()
            }
        }
    }
    
    /**
     * Perform cleanup when memory usage is high
     */
    private fun performWarningCleanup() {
        // Clean up performance optimizer
        PerformanceOptimizer.cleanup()
        
        // Force garbage collection
        System.gc()
        
        Log.d(TAG, "Warning cleanup completed")
    }
    
    /**
     * Perform aggressive cleanup when memory usage is critical
     */
    private fun performCriticalCleanup() {
        // Clean up performance optimizer
        PerformanceOptimizer.cleanup()
        
        // Force multiple garbage collections
        repeat(3) {
            System.gc()
            Thread.sleep(100) // Small delay between GC calls
        }
        
        Log.d(TAG, "Critical cleanup completed")
    }
    
    /**
     * Get current memory usage info
     */
    fun getMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // MB
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val memoryUsagePercent = (usedMemory * 100) / maxMemory
        
        return "Memory: ${usedMemory}MB / ${maxMemory}MB (${memoryUsagePercent}%)"
    }
    
    /**
     * Force immediate cleanup
     */
    fun forceCleanup() {
        Log.d(TAG, "Forcing immediate cleanup")
        performWarningCleanup()
    }
} 