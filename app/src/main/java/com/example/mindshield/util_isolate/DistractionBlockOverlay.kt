package com.example.mindshield.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.*
import android.view.ViewGroup
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader

object DistractionBlockOverlay {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var countdownJob: Job? = null

    fun show(context: Context, appName: String, duration: Long, onDismiss: () -> Unit, message: String? = null, color: Int? = null) {
        try {
            if (overlayView != null) return
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(
                context.resources.getIdentifier("overlay_blur_timer", "layout", context.packageName),
                null
            )
            val blurView = overlayView!!.findViewById<View>(context.resources.getIdentifier("blur_background_view", "id", context.packageName))
            val rootView = (context as? Activity)?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
            if (color != null) {
                blurView.setBackgroundColor(color)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurView.setRenderEffect(
                    RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                )
            } else {
                blurView.setBackgroundColor(Color.parseColor("#B3FFFFFF")) // 70% white fallback
            }
            val messageView = overlayView!!.findViewById<TextView>(context.resources.getIdentifier("blur_overlay_message", "id", context.packageName))
            val timerView = overlayView!!.findViewById<TextView>(context.resources.getIdentifier("blur_overlay_timer", "id", context.packageName))
            if (message != null) messageView.text = message
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            windowManager?.addView(overlayView, params)
            Handler(Looper.getMainLooper()).postDelayed({ remove(context) }, 35_000)
            countdownJob = CoroutineScope(Dispatchers.Main).launch {
                var remaining = duration
                while (remaining > 0) {
                    updateCountdown(timerView, remaining)
                    delay(1000L)
                    remaining -= 1000L
                }
                remove(context)
                onDismiss()
            }
            overlayView!!.setOnClickListener {
                remove(context)
                onDismiss()
            }
        } catch (e: Exception) {
            android.util.Log.e("DistractionBlockOverlay", "Error showing overlay: ", e)
            onDismiss()
        }
    }

    private fun updateCountdown(textView: TextView, remainingMs: Long) {
        val seconds = (remainingMs / 1000) % 60
        val minutes = (remainingMs / 1000) / 60
        textView.text = String.format("%d:%02d", minutes, seconds)
    }

    fun remove(context: Context) {
        countdownJob?.cancel()
        countdownJob = null
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    fun dismiss(context: Context) {
        try {
            // Existing logic to dismiss overlay
            remove(context)
        } catch (e: Exception) {
            android.util.Log.e("DistractionBlockOverlay", "Error dismissing overlay: ", e)
        }
    }
} 