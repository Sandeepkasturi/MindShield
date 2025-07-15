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

object DistractionBlockOverlay {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var countdownJob: Job? = null

    fun show(context: Context, appName: String, durationMs: Long, onFinish: () -> Unit) {
        if (overlayView != null) return
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        overlayView = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#CC222244")) // Strong, high-contrast semi-transparent
            val textView = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 24f
                setPadding(60, 200, 60, 200)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.TRANSPARENT)
            }
            addView(textView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            updateCountdown(textView, durationMs)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        windowManager?.addView(overlayView, params)
        countdownJob = CoroutineScope(Dispatchers.Main).launch {
            var remaining = durationMs
            val textView = (overlayView as FrameLayout).getChildAt(0) as TextView
            while (remaining > 0) {
                updateCountdown(textView, remaining)
                delay(1000L)
                remaining -= 1000L
            }
            remove(context)
            onFinish()
        }
    }

    private fun updateCountdown(textView: TextView, remainingMs: Long) {
        val seconds = (remainingMs / 1000) % 60
        val minutes = (remainingMs / 1000) / 60
        textView.text = "App is blocked for $minutes:${seconds.toString().padStart(2, '0')}\nPlease take a short break!"
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
} 