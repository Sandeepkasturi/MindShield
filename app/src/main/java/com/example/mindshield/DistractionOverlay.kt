package com.example.mindshield

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.mindshield.data.model.AppSession

class DistractionOverlay(private val context: Context) {
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var session: AppSession? = null
    private var soundEnabled: Boolean = true
    private var vibrationEnabled: Boolean = true
    
    fun setSession(session: AppSession) {
        this.session = session
    }
    
    fun setSoundEnabled(enabled: Boolean) {
        this.soundEnabled = enabled
    }
    
    fun setVibrationEnabled(enabled: Boolean) {
        this.vibrationEnabled = enabled
    }
    
    fun show() {
        if (overlayView != null) return
        
        overlayView = createOverlayView()
        val params = createWindowParams()
        
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            // Handle permission denied or other errors
        }
    }
    
    fun dismiss() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // Handle errors
            }
            overlayView = null
        }
    }
    
    private fun createOverlayView(): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.distraction_overlay, null)
        
        val titleText = view.findViewById<TextView>(R.id.overlay_title)
        val messageText = view.findViewById<TextView>(R.id.overlay_message)
        val acknowledgeButton = view.findViewById<Button>(R.id.overlay_acknowledge)
        val dismissButton = view.findViewById<Button>(R.id.overlay_dismiss)
        
        session?.let { session ->
            titleText.text = "Distraction Detected!"
            messageText.text = "You've been using ${session.appName} for ${session.duration} minutes"
        }
        
        acknowledgeButton.setOnClickListener {
            // Handle acknowledge action
            dismiss()
        }
        
        dismissButton.setOnClickListener {
            // Handle dismiss action
            dismiss()
        }
        
        return view
    }
    
    private fun createWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP
        }
    }
} 