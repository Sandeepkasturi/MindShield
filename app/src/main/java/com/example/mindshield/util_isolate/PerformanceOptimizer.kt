package com.example.mindshield.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

object PerformanceOptimizer {
    private const val TAG = "PerformanceOptimizer"
    
    // Background coroutine scope for heavy operations
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Debounce utility for frequent operations
    private val debounceMap = mutableMapOf<String, Job>()
    
    /**
     * Execute a task with debouncing to prevent excessive calls
     */
    fun debounce(key: String, delayMs: Long = 1000L, task: suspend () -> Unit) {
        debounceMap[key]?.cancel()
        debounceMap[key] = backgroundScope.launch {
            delay(delayMs)
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "Error in debounced task: ${e.message}")
            }
        }
    }
    
    /**
     * Execute a task in background with error handling
     */
    fun executeInBackground(task: suspend () -> Unit) {
        backgroundScope.launch {
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "Error in background task: ${e.message}")
            }
        }
    }
    
    /**
     * Check if device is low-end for performance adjustments
     */
    fun isLowEndDevice(context: Context): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        
        return maxMemory < 2048 // Less than 2GB RAM
    }
    
    /**
     * Get optimized polling interval based on device performance
     */
    fun getOptimizedPollingInterval(context: Context): Long {
        return if (isLowEndDevice(context)) {
            15000L // 15 seconds for low-end devices
        } else {
            10000L // 10 seconds for normal devices
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        debounceMap.values.forEach { it.cancel() }
        debounceMap.clear()
        backgroundScope.cancel()
    }
} 