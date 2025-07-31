package com.example.mindshield

import android.app.Application
import android.content.Context
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import com.example.mindshield.util.PerformanceOptimizer
import com.example.mindshield.util.MemoryManager

@HiltAndroidApp
class MindShieldApplication : Application() {
    
    companion object {
        private const val TAG = "MindShieldApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MindShield Application started")
        
        // Initialize performance optimizations
        initializePerformanceOptimizations()
    }
    
    private fun initializePerformanceOptimizations() {
        // Log device performance info
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val totalMemory = runtime.totalMemory() / (1024 * 1024) // MB
        val freeMemory = runtime.freeMemory() / (1024 * 1024) // MB
        
        Log.d(TAG, "Device Memory - Max: ${maxMemory}MB, Total: ${totalMemory}MB, Free: ${freeMemory}MB")
        // Temporarily disabled to avoid potential circular dependencies
        // Log.d(TAG, "Is Low-End Device: ${PerformanceOptimizer.isLowEndDevice(this)}")
        
        // Start memory monitoring - temporarily disabled
        // MemoryManager.startMemoryMonitoring(this)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning received")
        
        // Force immediate cleanup - temporarily disabled
        // MemoryManager.forceCleanup()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "Memory trim level: $level")
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Force cleanup on memory pressure - temporarily disabled
                // MemoryManager.forceCleanup()
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Stop memory monitoring - temporarily disabled
        // MemoryManager.stopMemoryMonitoring()
        Log.d(TAG, "MindShield Application terminated")
    }
} 