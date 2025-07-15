package com.example.mindshield.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenCaptureHelper {
    
    companion object {
        private const val TAG = "ScreenCaptureHelper"
        private const val VIRTUAL_DISPLAY_NAME = "MindShield_ScreenCapture"
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    suspend fun captureScreen(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            
            screenWidth = display.width
            screenHeight = display.height
            screenDensity = context.resources.displayMetrics.densityDpi
            
            // For now, return null as we need MediaProjection permission
            // This would be implemented with proper screen capture permissions
            Log.d(TAG, "Screen capture requested but requires MediaProjection permission")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
            null
        }
    }
    
    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
    }
    
    private fun createVirtualDisplay() {
        mediaProjection?.let { projection ->
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            )
            
            virtualDisplay = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        }
    }
    
    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
} 