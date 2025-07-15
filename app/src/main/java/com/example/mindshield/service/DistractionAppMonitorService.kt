package com.example.mindshield.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.mindshield.R
import com.example.mindshield.util.DistractionAppRepository
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.provider.Settings
import android.net.Uri
import android.util.Log
import com.example.mindshield.data.local.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.CountDownTimer
import android.widget.FrameLayout
import android.widget.TextView
import android.view.ViewGroup
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.PixelCopy
import android.view.SurfaceView
import android.preference.PreferenceManager
import android.graphics.drawable.BitmapDrawable
import android.view.WindowManager as AndroidWindowManager

class DistractionAppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage: String? = null
    private var overlayView: View? = null
    private val TAG = "DistractionMonitor"
    
    // Content-aware detection state
    private var isContentAwareEnabled = false
    private var contentAnalysisInProgress = false
    private var lastContentAnalysisResult = false // true = distracting content detected
    private var lastAnalysisTime = 0L

    // Enhanced search detection state
    private var searchAlertView: View? = null
    private var suspensionTimers = mutableMapOf<String, Runnable>() // packageName -> timer
    private var suspendedApps = mutableSetOf<String>()

    // Blur overlay state
    private var blurOverlayView: View? = null
    private var blurTimer: CountDownTimer? = null
    private var blurTimeLeft: Long = 0L
    private var blurTargetPackage: String? = null

    // --- Overlay notification and blur timer state ---
    private var overlayNotificationView: View? = null
    private var overlayNotificationTimer: CountDownTimer? = null
    private var overlayNotificationTimeLeft: Long = 0L
    private var blurOverlayActive = false
    private var blurOverlayPaused = false
    private var blurOverlayRemaining: Long = 0L
    private var blurOverlayTimer: CountDownTimer? = null
    private var blurOverlayTargetPackage: String? = null

    // Distraction blur overlay state
    private var distractionBlurOverlayView: View? = null
    private var distractionBlurOverlayTimer: CountDownTimer? = null
    private var distractionBlurOverlayActive = false
    private var distractionBlurOverlayTargetPackage: String? = null
    private var distractionBlurOverlayRemaining: Long = 120_000L // 2 minutes
    private var distractionBlurOverlayPaused = false

    // Persistent blur session state
    private val BLUR_SESSION_PREF = "blur_session_pref"
    private val BLUR_SESSION_START = "blur_session_start"
    private val BLUR_SESSION_DURATION = "blur_session_duration"
    private val BLUR_SESSION_ACTIVE = "blur_session_active"
    private val BLUR_SESSION_PACKAGE = "blur_session_package"

    // Track last notification dismissal time per app
    private val lastDismissalTimestamps = mutableMapOf<String, Long>()

    // Broadcast receiver for content analysis results
    private val contentAnalysisReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast: ${intent?.action}")
            when (intent?.action) {
                "com.example.mindshield.CONTENT_ANALYSIS_RESULT" -> {
                    val isDistracting = intent.getBooleanExtra("isDistracting", false)
                    val packageName = intent.getStringExtra("packageName")
                    Log.d(TAG, "Received content analysis result: isDistracting=$isDistracting, package=$packageName")
                    updateContentAnalysisResult(isDistracting)
                    lastAnalysisTime = System.currentTimeMillis()
                }
                "com.example.mindshield.CONTENT_ANALYSIS_PROGRESS" -> {
                    val inProgress = intent.getBooleanExtra("inProgress", false)
                    Log.d(TAG, "Received content analysis progress: inProgress=$inProgress")
                    setContentAnalysisInProgress(inProgress)
                }
                "com.example.mindshield.DISTRACTING_SEARCH_DETECTED" -> {
                    val searchText = intent.getStringExtra("searchText") ?: ""
                    val entertainmentScore = intent.getIntExtra("entertainmentScore", 0)
                    val productiveScore = intent.getIntExtra("productiveScore", 0)
                    val packageName = intent.getStringExtra("packageName")
                    
                    Log.d(TAG, "Received distracting search: $searchText (E:$entertainmentScore, P:$productiveScore)")
                    handleDistractingSearch(searchText, entertainmentScore, productiveScore, packageName)
                }
            }
        }
    }

    private val distractingContentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.mindshield.DISTRACTING_CONTENT_DETECTED") {
                val packageName = intent.getStringExtra("packageName") ?: return
                handleDistractingContent(packageName)
            }
        }
    }

    // Notification action constants
    companion object {
        const val ACTION_YOUTUBE_DISMISS = "com.example.mindshield.action.YOUTUBE_DISMISS"
        const val ACTION_YOUTUBE_STOP = "com.example.mindshield.action.YOUTUBE_STOP"
        const val ACTION_DISTRACTION_DISMISS = "com.example.mindshield.action.DISTRACTION_DISMISS"
        const val YOUTUBE_NOTIFICATION_ID = 1002
        fun getForegroundApp(context: Context): String? {
            return try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 5000 // Check last 5 seconds instead of 10
                val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
                var lastApp: String? = null
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastApp = event.packageName
                    }
                }
                lastApp
            } catch (e: Exception) {
                Log.e("DistractionMonitor", "Error getting foreground app: ${e.message}")
                null
            }
        }
    }

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_YOUTUBE_DISMISS -> {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(YOUTUBE_NOTIFICATION_ID)
                }
                ACTION_YOUTUBE_STOP -> {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(YOUTUBE_NOTIFICATION_ID)
                    showOverlayAlert("YouTube")
                }
                ACTION_DISTRACTION_DISMISS -> {
                    val packageName = intent.getStringExtra("packageName") ?: return
                    lastDismissalTimestamps[packageName] = System.currentTimeMillis()
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(packageName.hashCode())
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DistractionAppMonitorService created")
        startForegroundService()
        handler.post(checkRunnable)
        checkContentAwareServiceStatus()
        registerContentAnalysisReceiver()
        // Register notification action receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_YOUTUBE_DISMISS)
            addAction(ACTION_YOUTUBE_STOP)
            addAction(ACTION_DISTRACTION_DISMISS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationActionReceiver, filter)
        }
        val filterDistractingContent = IntentFilter("com.example.mindshield.DISTRACTING_CONTENT_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(distractingContentReceiver, filterDistractingContent, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(distractingContentReceiver, filterDistractingContent)
        }
        resumePersistentBlurOverlayIfNeeded()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called - restarting service to maintain monitoring")
        val restartIntent = Intent(applicationContext, DistractionAppMonitorService::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
    private fun registerContentAnalysisReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.example.mindshield.CONTENT_ANALYSIS_RESULT")
            addAction("com.example.mindshield.CONTENT_ANALYSIS_PROGRESS")
            addAction("com.example.mindshield.DISTRACTING_SEARCH_DETECTED")
        }
        
        // Use different registration methods based on API level
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // For Android 8.0+ (API 26+), use RECEIVER_NOT_EXPORTED flag
                registerReceiver(contentAnalysisReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                Log.d(TAG, "Registered broadcast receiver with RECEIVER_NOT_EXPORTED flag")
            }
            else -> {
                // For older Android versions, use regular registerReceiver
                registerReceiver(contentAnalysisReceiver, filter)
                Log.d(TAG, "Registered broadcast receiver without flag (API < 26)")
            }
        }
        Log.d(TAG, "Content analysis broadcast receiver registered")
    }

    private fun checkContentAwareServiceStatus() {
        // Check if content-aware detection service is enabled
        try {
            val contentAwareService = Intent(this, Class.forName("com.example.mindshield.service.ContentAwareDetectionService"))
            isContentAwareEnabled = isServiceRunning(contentAwareService)
            Log.d(TAG, "Content-aware detection enabled: $isContentAwareEnabled")
            
            // Also check if this service is running
            val thisService = Intent(this, DistractionAppMonitorService::class.java)
            val isThisServiceRunning = isServiceRunning(thisService)
            Log.d(TAG, "DistractionAppMonitorService running: $isThisServiceRunning")
            
            // If content-aware service is not running, try to start it
            if (!isContentAwareEnabled) {
                Log.d(TAG, "Content-aware service not running, attempting to start...")
                try {
                    ContentAwareDetectionService.start(this)
                    // Wait a bit and check again
                    handler.postDelayed({
                        isContentAwareEnabled = isServiceRunning(contentAwareService)
                        Log.d(TAG, "Content-aware detection status after start attempt: $isContentAwareEnabled")
                    }, 2000)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start content-aware service: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "Content-aware detection service not found, using basic detection: ${e.message}")
            isContentAwareEnabled = false
        }
    }

    private fun isServiceRunning(intent: Intent): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { 
            it.service.className == intent.component?.className 
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                val foregroundApp = DistractionAppMonitorService.getForegroundApp(this@DistractionAppMonitorService)
                val distractionApps = DistractionAppRepository.getAllDistractionApps(this@DistractionAppMonitorService)
                
                // Only log if there's a change to reduce log spam
                if (lastPackage != foregroundApp) {
                    Log.d(TAG, "Foreground app changed: $foregroundApp")
                }
                
                if (foregroundApp != null && distractionApps.contains(foregroundApp)) {
                    // Special case: Instagram - show only notification, no overlay
                    if (foregroundApp == "com.instagram.android") {
                        showInstagramNotification()
                        // Remove any overlay if present
                        if (distractionBlurOverlayActive) removeDistractionBlurOverlay()
                        lastNotifiedPackage = foregroundApp
                    } else if (!isContentAwareApp(foregroundApp)) {
                        // 5-minute grace period after notification dismissal
                        val lastDismissed = lastDismissalTimestamps[foregroundApp] ?: 0L
                        val now = System.currentTimeMillis()
                        if (now - lastDismissed >= 5 * 60 * 1000L) {
                            if (lastNotifiedPackage != foregroundApp) {
                                showImmediateDistractionNotification(foregroundApp)
                                lastNotifiedPackage = foregroundApp
                            }
                        }
                        // Show full-screen blur overlay for other non-content-aware apps
                        if (!distractionBlurOverlayActive) {
                            showDistractionBlurOverlay(foregroundApp)
                        } else if (distractionBlurOverlayPaused) {
                            resumeDistractionBlurOverlayTimer()
                        }
                    }
                    // Remove overlay and pause timer if user leaves the app (within 2 seconds)
                    if (distractionBlurOverlayActive && foregroundApp != distractionBlurOverlayTargetPackage) {
                        pauseDistractionBlurOverlayTimer()
                        handler.postDelayed({
                            removeDistractionBlurOverlay()
                        }, 2000) // Remove overlay within 2 seconds
                    }
                    // --- YouTube-specific overlay logic ---
                    if (foregroundApp == "com.google.android.youtube") {
                        // Do NOT show notification or overlay here; only show after AI/content-aware result is 'distracting'
                        if (!youtubeBlurOverlayActive && youtubeBlurOverlayRemaining > 0L) {
                            showYouTubeBlurOverlay(youtubeBlurOverlayRemaining)
                        } else if (youtubeBlurOverlayPaused) {
                            resumeYouTubeBlurOverlayTimer()
                        }
                    } else {
                        if (youtubeBlurOverlayActive) {
                            pauseYouTubeBlurOverlayTimer()
                            removeYouTubeBlurOverlay()
                        }
                    }
                } else {
                    if (overlayView != null) {
                        removeOverlayAlert()
                    }
                    if (searchAlertView != null) {
                        removeSearchAlert()
                    }
                }
                lastPackage = foregroundApp
                // --- Overlay/timer pause/resume logic ---
                if (blurOverlayActive && blurOverlayTargetPackage != null) {
                    if (foregroundApp != blurOverlayTargetPackage) {
                        onAppLeftOrBackgrounded()
                    } else {
                        if (blurOverlayPaused) onAppResumed(blurOverlayTargetPackage!!)
                    }
                }
                if (overlayNotificationView != null && blurOverlayTargetPackage != null) {
                    if (foregroundApp != blurOverlayTargetPackage) {
                        onAppLeftOrBackgrounded()
                    } else {
                        onAppResumed(blurOverlayTargetPackage!!)
                    }
                }
                lastPackage = foregroundApp
                // --- YouTube-specific overlay logic ---
                if (foregroundApp == "com.google.android.youtube") {
                    if (!youtubeBlurOverlayActive && youtubeBlurOverlayRemaining > 0L) {
                        showYouTubeBlurOverlay(youtubeBlurOverlayRemaining)
                    } else if (youtubeBlurOverlayPaused) {
                        resumeYouTubeBlurOverlayTimer()
                    }
                } else {
                    if (youtubeBlurOverlayActive) {
                        pauseYouTubeBlurOverlayTimer()
                        removeYouTubeBlurOverlay()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkRunnable: ${e.message}")
            }
            // Increase polling interval to reduce CPU usage
            handler.postDelayed(this, 2000) // Check every 2 seconds instead of 500ms
        }
    }

    private fun shouldShowAlert(packageName: String): Boolean {
        Log.d(TAG, "shouldShowAlert called for: $packageName")
        Log.d(TAG, "isContentAwareEnabled: $isContentAwareEnabled")
        Log.d(TAG, "contentAnalysisInProgress: $contentAnalysisInProgress")
        Log.d(TAG, "lastContentAnalysisResult: $lastContentAnalysisResult")
        
        // For all content-aware apps (including YouTube), require content-aware analysis
        if (isContentAwareApp(packageName)) {
            Log.d(TAG, "$packageName is a content-aware app")
                if (!isContentAwareEnabled) {
                    Log.d(TAG, "Content-aware detection disabled, suppressing alert for $packageName")
                    return false
                }
                if (contentAnalysisInProgress) {
                    Log.d(TAG, "Content analysis in progress for $packageName, waiting...")
                    return false
                }
                if (lastContentAnalysisResult) {
                    Log.d(TAG, "Content analysis confirmed distracting content in $packageName")
                    return true
                } else {
                    Log.d(TAG, "Content analysis found productive content in $packageName, suppressing alert")
                    return false
            }
        }
        
        // For other distraction apps, show alert immediately
        Log.d(TAG, "Non-content-aware app $packageName, showing alert immediately")
        return true
    }

    private fun isContentAwareApp(packageName: String): Boolean {
        val contentAwareApps = listOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx", // Edge
            "com.opera.browser",
            "com.sec.android.app.browser", // Samsung Internet
            "com.android.browser"
        )
        return contentAwareApps.contains(packageName)
    }

    // --- Content-aware detection result handler ---
    fun updateContentAnalysisResult(isDistracting: Boolean) {
        lastContentAnalysisResult = isDistracting
        contentAnalysisInProgress = false
        Log.d(TAG, "Content analysis result updated: isDistracting=$isDistracting")
        val foregroundApp = DistractionAppMonitorService.getForegroundApp(this)
        if (isContentAwareApp(foregroundApp ?: "") && isDistracting) {
            // Step 1: Show notification only
            showContentAwareDistractionNotification(foregroundApp!!)
            // Step 2: After 1 minute, show persistent blur overlay for 2 minutes
            handler.postDelayed({
                startPersistentBlurOverlay(foregroundApp!!, 120_000L)
            }, 60_000L)
        }
    }

    private fun showContentAwareDistractionNotification(packageName: String) {
        val appName = DistractionAppRepository.getAppName(this, packageName)
        val channelId = "content_aware_distraction_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Content-Aware Distraction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(this, com.example.mindshield.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Distracting Content Detected!")
            .setContentText("You are consuming distracting content in $appName. Take a break!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(packageName.hashCode(), notification)
    }

    // Method to be called by ContentAwareDetectionService when analysis starts
    fun setContentAnalysisInProgress(inProgress: Boolean) {
        contentAnalysisInProgress = inProgress
        Log.d(TAG, "Content analysis in progress: $inProgress")
    }

    private fun startForegroundService() {
        val channelId = "distraction_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Distraction Monitor", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MindShield Monitoring")
            .setContentText("Monitoring distraction apps...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        Log.d(TAG, "Foreground service started")
    }

    private fun showOverlayAlert(appName: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "No overlay permission, requesting...")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }
        if (overlayView != null) {
            Log.d(TAG, "Overlay already showing")
            return // Already showing
        }

        Log.d(TAG, "Creating overlay for: $appName")
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_alert, null)

        val title = overlayView!!.findViewById<TextView>(R.id.overlay_title)
        val message = overlayView!!.findViewById<TextView>(R.id.overlay_message)
        val dismiss = overlayView!!.findViewById<Button>(R.id.overlay_dismiss)

        title.text = "Distraction Alert!"
        // Fetch user name from SettingsDataStore and update message
        val settingsDataStore = SettingsDataStore(applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            val name = withContext(Dispatchers.IO) {
                settingsDataStore.userName.firstOrNull() ?: ""
            }
            val alertName = if (name.isNotBlank()) name else "there"
            message.text = "Hey $alertName, you opened $appName, which is a distracting app."
        }

        dismiss.setOnClickListener {
            Log.d(TAG, "Overlay dismissed by user")
            removeOverlayAlert()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(overlayView, params)
        Log.d(TAG, "Overlay displayed successfully")
    }

    private fun removeOverlayAlert() {
        overlayView?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
            overlayView = null
            Log.d(TAG, "Overlay removed")
        }
    }

    // Method to handle distracting search detection
    private fun handleDistractingSearch(searchText: String, entertainmentScore: Int, productiveScore: Int, packageName: String?) {
        val appName = packageName?.let { DistractionAppRepository.getAppName(this, it) } ?: "Unknown App"
        
        // Cancel any existing suspension timer for this app
        packageName?.let { cancelSuspensionTimer(it) }
        
        // Show immediate notification for distracting search
        showDistractingSearchAlert(searchText, entertainmentScore, productiveScore, appName, packageName)
        
        // Start suspension timer
        startSuspensionTimer(packageName, appName)
    }
    
    private fun showDistractingSearchAlert(searchText: String, entertainmentScore: Int, productiveScore: Int, appName: String, packageName: String?) {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "No overlay permission for search alert")
            return
        }
        
        // Remove any existing search alert
        removeSearchAlert()
        
        Log.d(TAG, "Showing distracting search alert for: $searchText")
        
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        searchAlertView = inflater.inflate(R.layout.overlay_alert, null)
        
        val title = searchAlertView!!.findViewById<TextView>(R.id.overlay_title)
        val message = searchAlertView!!.findViewById<TextView>(R.id.overlay_message)
        val dismiss = searchAlertView!!.findViewById<Button>(R.id.overlay_dismiss)
        
        title.text = "Distracting Search Detected!"
        
        // Fetch user name and create personalized message
        val settingsDataStore = SettingsDataStore(applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            val name = withContext(Dispatchers.IO) {
                settingsDataStore.userName.firstOrNull() ?: ""
            }
            val alertName = if (name.isNotBlank()) name else "there"
            message.text = "Hey $alertName, you searched for '$searchText' in $appName.\n\nThis appears to be distracting content (Entertainment: $entertainmentScore, Productive: $productiveScore).\n\nYou have 30 seconds to leave this app or it will be suspended."
        }
        
        dismiss.setOnClickListener {
            Log.d(TAG, "Search alert dismissed by user")
            removeSearchAlert()
            // Cancel suspension timer when user dismisses
            packageName?.let { cancelSuspensionTimer(it) }
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(searchAlertView, params)
        
        // Start countdown display
        startSearchCountdown(packageName, appName)
    }
    
    private fun startSearchCountdown(packageName: String?, appName: String) {
        var timeLeft = 30 // 30 seconds countdown
        
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (timeLeft > 0) {
                    // Update the message with countdown
                    searchAlertView?.findViewById<TextView>(R.id.overlay_message)?.let { messageView ->
                        val settingsDataStore = SettingsDataStore(applicationContext)
                        CoroutineScope(Dispatchers.Main).launch {
                            val name = withContext(Dispatchers.IO) {
                                settingsDataStore.userName.firstOrNull() ?: ""
                            }
                            val alertName = if (name.isNotBlank()) name else "there"
                            messageView.text = "Hey $alertName, you have $timeLeft seconds to leave $appName or it will be suspended."
                        }
                    }
                    timeLeft--
                    handler.postDelayed(this, 1000) // Update every second
                } else {
                    // Time's up - suspend the app
                    Log.d(TAG, "Search countdown expired for $appName")
                    packageName?.let { suspendApp(it, appName) }
                }
            }
        }
        
        // Store the countdown runnable for potential cancellation
        packageName?.let { suspensionTimers[it] = countdownRunnable }
        
        // Start the countdown
        handler.post(countdownRunnable)
    }
    
    private fun removeSearchAlert() {
        searchAlertView?.let {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
                searchAlertView = null
                Log.d(TAG, "Search alert removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing search alert: ${e.message}")
            }
        }
    }
    
    private fun cancelSuspensionTimer(packageName: String) {
        suspensionTimers[packageName]?.let { runnable ->
            handler.removeCallbacks(runnable)
            suspensionTimers.remove(packageName)
            Log.d(TAG, "Suspension timer cancelled for $packageName")
        }
    }
    
    private fun startSuspensionTimer(packageName: String?, appName: String) {
        // Cancel any existing timer for this app
        packageName?.let { cancelSuspensionTimer(it) }
        
        val suspensionRunnable = Runnable {
            Log.d(TAG, "Suspension timer expired for $appName")
            packageName?.let { suspendApp(it, appName) }
        }
        
        // Store the timer for potential cancellation
        packageName?.let { suspensionTimers[it] = suspensionRunnable }
        
        // Start the timer (30 seconds)
        handler.postDelayed(suspensionRunnable, 30000)
        
        Log.d(TAG, "Suspension timer started for $appName (30 seconds)")
    }
    
    private fun suspendApp(packageName: String, appName: String) {
        try {
            // Remove the app from suspended apps if it was previously suspended
            suspendedApps.remove(packageName)
            
            // Add to suspended apps
            suspendedApps.add(packageName)
            
            // Show suspension notification
            showSuspensionNotification(packageName, appName)
            
            // Log the suspension
            Log.d(TAG, "App suspended: $appName ($packageName)")
            
            // Remove any existing alerts
            removeSearchAlert()
            removeOverlayAlert()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error suspending app $appName: ${e.message}")
        }
    }
    
    private fun showSuspensionNotification(packageName: String, appName: String) {
        // Create a notification to inform user that app is being suspended
        val channelId = "suspension_notification"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Suspension", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Suspended")
            .setContentText("$appName has been suspended due to distracting content")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
        
        Log.d(TAG, "Suspension notification sent for $appName")
    }
    
    // Method to check if an app is suspended
    fun isAppSuspended(packageName: String): Boolean {
        return suspendedApps.contains(packageName)
    }
    
    // Method to unsuspend an app
    fun unsuspendApp(packageName: String) {
        suspendedApps.remove(packageName)
        Log.d(TAG, "App unsuspended: $packageName")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "DistractionAppMonitorService destroyed")
        // Remove all handler callbacks (full sweep)
        handler.removeCallbacksAndMessages(null)
        removeOverlayAlert()
        removeSearchAlert()
        // Cancel all suspension timers
        suspensionTimers.values.forEach { runnable ->
            handler.removeCallbacks(runnable)
        }
        suspensionTimers.clear()
        try {
            unregisterReceiver(contentAnalysisReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: "+e.message)
        }
        try {
            unregisterReceiver(notificationActionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering notification receiver: "+e.message)
        }
        try {
            unregisterReceiver(distractingContentReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering distracting content receiver: "+e.message)
        }
        // Clean up resources
        cleanupResources()
        super.onDestroy()
    }
    
    private fun cleanupResources() {
        // Clear any cached data
        lastPackage = null
        contentAnalysisInProgress = false
        lastContentAnalysisResult = false
        isContentAwareEnabled = false
        // Clear search-related data
        searchAlertView = null
        overlayView = null
        suspensionTimers.clear()
        suspendedApps.clear()
        // Force garbage collection for this service
        System.gc()
        Log.d(TAG, "DistractionAppMonitorService resources cleaned up")
    }

    // --- Overlay notification and blur timer state ---
    private fun showCustomOverlayNotification(appName: String, userName: String, packageName: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayNotificationView != null) return
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayNotificationView = inflater.inflate(R.layout.overlay_alert, null)
        val title = overlayNotificationView!!.findViewById<TextView>(R.id.overlay_title)
        val message = overlayNotificationView!!.findViewById<TextView>(R.id.overlay_message)
        val dismiss = overlayNotificationView!!.findViewById<Button>(R.id.overlay_dismiss)
        title.text = "Take a Break!"
        message.text = "Hey $userName, I noticed you've been spending some time on $appName. Sometimes, taking a break can be really refreshing! Maybe try stepping away for a little while and come back later feeling refreshed. You deserve it! 😊"
        dismiss.text = "Dismiss"
        dismiss.setOnClickListener {
            removeCustomOverlayNotification()
            startOverlayNotificationTimer(packageName, appName, userName)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(overlayNotificationView, params)
    }
    private fun removeCustomOverlayNotification() {
        overlayNotificationView?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
            overlayNotificationView = null
        }
    }
    private fun startOverlayNotificationTimer(packageName: String, appName: String, userName: String) {
        overlayNotificationTimeLeft = 60_000L
        overlayNotificationTimer = object : CountDownTimer(overlayNotificationTimeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                overlayNotificationTimeLeft = millisUntilFinished
            }
            override fun onFinish() {
                showShortNotification(userName)
                handler.postDelayed({
                    showBlurOverlayWithTimer(packageName, appName)
                }, 5000)
            }
        }.start()
    }
    private fun pauseOverlayNotificationTimer() {
        overlayNotificationTimer?.cancel()
    }
    private fun resumeOverlayNotificationTimer(packageName: String, appName: String, userName: String) {
        startOverlayNotificationTimer(packageName, appName, userName)
    }
    // --- Short notification after 1 minute ---
    private fun showShortNotification(userName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mindshield_short_notification"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "MindShield Short", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MindShield")
            .setContentText("Hey $userName you are consuming the distracting content.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(2001, notification)
        handler.postDelayed({ notificationManager.cancel(2001) }, 5000)
    }
    // --- Blur overlay with timer ---
    private fun showBlurOverlayWithTimer(packageName: String, appName: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (blurOverlayView != null) return
        blurOverlayActive = true
        blurOverlayPaused = false
        blurOverlayRemaining = 120_000L
        blurOverlayTargetPackage = packageName
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        blurOverlayView = inflater.inflate(R.layout.overlay_blur_timer, null)
        val timerText = blurOverlayView!!.findViewById<TextView>(R.id.blur_overlay_timer)

        // Apply blur or dim based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurOverlayView!!.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
        } else {
            blurOverlayView!!.setBackgroundColor(Color.parseColor("#B3000000")) // 70% black dim
        }

        // Block interaction
        blurOverlayView!!.isClickable = true
        blurOverlayView!!.isFocusable = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(blurOverlayView, params)
        blurOverlayTimer = object : CountDownTimer(blurOverlayRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                blurOverlayRemaining = millisUntilFinished
                val min = (millisUntilFinished / 1000) / 60
                val sec = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("%d:%02d", min, sec)
            }
            override fun onFinish() {
                removeBlurOverlay()
            }
        }.start()
    }
    private fun removeBlurOverlay() {
        blurOverlayView?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
            blurOverlayView = null
        }
        blurOverlayActive = false
        blurOverlayPaused = false
        blurOverlayRemaining = 0L
        blurOverlayTimer?.cancel()
        blurOverlayTimer = null
        blurOverlayTargetPackage = null
    }
    private fun pauseBlurOverlayTimer() {
        blurOverlayTimer?.cancel()
        blurOverlayPaused = true
    }
    private fun resumeBlurOverlayTimer() {
        if (blurOverlayActive && blurOverlayPaused && blurOverlayRemaining > 0L) {
            val timerText = blurOverlayView?.findViewById<TextView>(R.id.blur_overlay_timer)
            blurOverlayTimer = object : CountDownTimer(blurOverlayRemaining, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    blurOverlayRemaining = millisUntilFinished
                    val min = (millisUntilFinished / 1000) / 60
                    val sec = (millisUntilFinished / 1000) % 60
                    timerText?.text = String.format("%d:%02d", min, sec)
                }
                override fun onFinish() {
                    removeBlurOverlay()
                }
            }.start()
            blurOverlayPaused = false
        }
    }
    // --- App focus detection ---
    private fun isAppInForeground(targetPackage: String): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = am.runningAppProcesses ?: return false
        val foregroundProcess = runningAppProcesses.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        return foregroundProcess?.processName == targetPackage
    }
    // --- Main integration point: call this when distracting content is detected ---
    private fun handleDistractingContent(packageName: String) {
        val appName = DistractionAppRepository.getAppName(this, packageName)
        val settingsDataStore = SettingsDataStore(applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            val name = withContext(Dispatchers.IO) {
                settingsDataStore.userName.firstOrNull() ?: "there"
            }
            showCustomOverlayNotification(appName, name, packageName)
        }
    }
    // --- Callbacks for app focus changes (should be called from polling/check loop) ---
    private fun onAppLeftOrBackgrounded() {
        // Remove overlays and pause timers
        removeCustomOverlayNotification()
        pauseOverlayNotificationTimer()
        if (blurOverlayActive) pauseBlurOverlayTimer()
    }
    private fun onAppResumed(targetPackage: String) {
        // Resume timers/overlays if needed
        if (overlayNotificationView != null) {
            val appName = DistractionAppRepository.getAppName(this, targetPackage)
            val settingsDataStore = SettingsDataStore(applicationContext)
            CoroutineScope(Dispatchers.Main).launch {
                val name = withContext(Dispatchers.IO) {
                    settingsDataStore.userName.firstOrNull() ?: "there"
                }
                resumeOverlayNotificationTimer(targetPackage, appName, name)
            }
        }
        if (blurOverlayActive && blurOverlayPaused) resumeBlurOverlayTimer()
    }

    private var lastNotifiedPackage: String? = null

    private fun showImmediateDistractionNotification(packageName: String) {
        val appName = DistractionAppRepository.getAppName(this, packageName)
        val channelId = "distraction_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Distraction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(this, com.example.mindshield.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Dismiss action
        val dismissIntent = Intent(ACTION_DISTRACTION_DISMISS).apply {
            putExtra("packageName", packageName)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this, packageName.hashCode(), dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Take a Break!")
            .setContentText("You are using $appName for a while. Consider taking a break.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPendingIntent)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private fun showDistractionBlurOverlay(packageName: String) {
        if (distractionBlurOverlayActive) return
        val appName = DistractionAppRepository.getAppName(this, packageName)
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val overlayView = inflater.inflate(R.layout.overlay_blur_timer, null) as FrameLayout
        val blurBgView = overlayView.findViewById<View>(R.id.blur_background_view)
        val messageText = overlayView.findViewById<TextView>(R.id.blur_overlay_message)
        val timerText = overlayView.findViewById<TextView>(R.id.blur_overlay_timer)
        messageText.text = "Take a Break!\nYou are using $appName for a while. Consider taking a break."
        // Apply blur or dim based on Android version (to background only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurBgView.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
        } else {
            blurBgView.setBackgroundColor(Color.parseColor("#B3000000")) // 70% black dim
        }
        overlayView.isClickable = true
        overlayView.isFocusable = true
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(overlayView, params)
        distractionBlurOverlayView = overlayView
        distractionBlurOverlayActive = true
        distractionBlurOverlayTargetPackage = packageName
        distractionBlurOverlayPaused = false
        // Timer logic
        startDistractionBlurOverlayTimer(timerText)
    }
    private fun startDistractionBlurOverlayTimer(timerText: TextView?) {
        distractionBlurOverlayTimer?.cancel()
        distractionBlurOverlayTimer = object : CountDownTimer(distractionBlurOverlayRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                distractionBlurOverlayRemaining = millisUntilFinished
                val min = (millisUntilFinished / 1000) / 60
                val sec = (millisUntilFinished / 1000) % 60
                timerText?.text = String.format("%d:%02d", min, sec)
            }
            override fun onFinish() {
                removeDistractionBlurOverlay()
            }
        }.start()
    }
    private fun removeDistractionBlurOverlay() {
        distractionBlurOverlayView?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
        }
        distractionBlurOverlayView = null
        distractionBlurOverlayActive = false
        distractionBlurOverlayTargetPackage = null
        distractionBlurOverlayTimer?.cancel()
        distractionBlurOverlayTimer = null
        distractionBlurOverlayRemaining = 120_000L
        distractionBlurOverlayPaused = false
    }
    private fun pauseDistractionBlurOverlayTimer() {
        distractionBlurOverlayTimer?.cancel()
        distractionBlurOverlayPaused = true
    }
    private fun resumeDistractionBlurOverlayTimer() {
        if (distractionBlurOverlayActive && distractionBlurOverlayPaused && distractionBlurOverlayRemaining > 0L) {
            val timerText = distractionBlurOverlayView?.findViewById<TextView>(R.id.blur_overlay_timer)
            startDistractionBlurOverlayTimer(timerText)
            distractionBlurOverlayPaused = false
        }
    }

    private fun showInstagramNotification() {
        val channelId = "distraction_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Distraction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(this, com.example.mindshield.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Instagram Opened")
            .setContentText("You are using Instagram.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify("com.instagram.android".hashCode(), notification)
    }

    private fun startPersistentBlurOverlay(packageName: String, durationMillis: Long = 120_000L) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val startTime = System.currentTimeMillis()
        prefs.edit()
            .putLong(BLUR_SESSION_START, startTime)
            .putLong(BLUR_SESSION_DURATION, durationMillis)
            .putBoolean(BLUR_SESSION_ACTIVE, true)
            .putString(BLUR_SESSION_PACKAGE, packageName)
            .apply()
        showPersistentBlurOverlay(packageName, startTime, durationMillis)
    }

    private fun showPersistentBlurOverlay(packageName: String, startTime: Long, durationMillis: Long) {
        removeDistractionBlurOverlay()
        val appName = DistractionAppRepository.getAppName(this, packageName)
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val overlayView = inflater.inflate(R.layout.overlay_blur_timer, null) as FrameLayout
        val blurBgView = overlayView.findViewById<View>(R.id.blur_background_view)
        val messageText = overlayView.findViewById<TextView>(R.id.blur_overlay_message)
        val timerText = overlayView.findViewById<TextView>(R.id.blur_overlay_timer)
        messageText.text = "Take a Break!\nYou are using $appName for a while. Consider taking a break."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            captureAndBlurScreen(blurBgView)
        } else {
            blurBgView.setBackgroundColor(Color.parseColor("#B3000000"))
        }
        overlayView.isClickable = true
        overlayView.isFocusable = true
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(overlayView, params)
        distractionBlurOverlayView = overlayView
        distractionBlurOverlayActive = true
        distractionBlurOverlayTargetPackage = packageName
        distractionBlurOverlayPaused = false
        // Timer logic: always calculate remaining time based on startTime
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (durationMillis - elapsed).coerceAtLeast(0L)
        distractionBlurOverlayRemaining = remaining
        startDistractionBlurOverlayTimer(timerText)
    }

    private fun captureAndBlurScreen(targetView: View) {
        // Try to capture a screenshot of the current window and apply a strong blur
        val window = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rootView = window?.let { (it as? Activity)?.window?.decorView?.rootView }
        if (rootView != null) {
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)
            val blurEffect = RenderEffect.createBlurEffect(80f, 80f, Shader.TileMode.CLAMP)
            targetView.background = BitmapDrawable(resources, bitmap)
            targetView.setRenderEffect(blurEffect)
        } else {
            targetView.setBackgroundColor(Color.parseColor("#B3000000"))
        }
    }

    private fun resumePersistentBlurOverlayIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val active = prefs.getBoolean(BLUR_SESSION_ACTIVE, false)
        if (!active) return
        val startTime = prefs.getLong(BLUR_SESSION_START, 0L)
        val duration = prefs.getLong(BLUR_SESSION_DURATION, 120_000L)
        val packageName = prefs.getString(BLUR_SESSION_PACKAGE, null) ?: return
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (duration - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) {
            showPersistentBlurOverlay(packageName, startTime, duration)
        } else {
            clearPersistentBlurSession()
        }
    }

    private fun clearPersistentBlurSession() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putBoolean(BLUR_SESSION_ACTIVE, false)
            .remove(BLUR_SESSION_START)
            .remove(BLUR_SESSION_DURATION)
            .remove(BLUR_SESSION_PACKAGE)
            .apply()
        removeDistractionBlurOverlay()
    }

    private var youtubeBlurOverlayActive = false
    private var youtubeBlurOverlayPaused = false
    private var youtubeBlurOverlayRemaining: Long = 120_000L
    private var youtubeBlurOverlayStartTime: Long = 0L
    private var youtubeBlurOverlayView: View? = null
    private var youtubeBlurOverlayTimer: CountDownTimer? = null

    private fun showYouTubeBlurOverlay(remaining: Long = 120_000L) {
        removeYouTubeBlurOverlay()
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val overlayView = inflater.inflate(R.layout.overlay_blur_timer, null) as FrameLayout
        val blurBgView = overlayView.findViewById<View>(R.id.blur_background_view)
        val messageText = overlayView.findViewById<TextView>(R.id.blur_overlay_message)
        val timerText = overlayView.findViewById<TextView>(R.id.blur_overlay_timer)
        messageText.text = "Take a Break!\nYou are using YouTube for a while. Consider taking a break."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            captureAndBlurScreen(blurBgView)
        } else {
            blurBgView.setBackgroundColor(Color.parseColor("#B3000000"))
        }
        overlayView.isClickable = true
        overlayView.isFocusable = true
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(overlayView, params)
        youtubeBlurOverlayView = overlayView
        youtubeBlurOverlayActive = true
        youtubeBlurOverlayPaused = false
        youtubeBlurOverlayStartTime = System.currentTimeMillis()
        youtubeBlurOverlayRemaining = remaining
        youtubeBlurOverlayTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                youtubeBlurOverlayRemaining = millisUntilFinished
                val min = (millisUntilFinished / 1000) / 60
                val sec = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("%d:%02d", min, sec)
            }
            override fun onFinish() {
                removeYouTubeBlurOverlay()
            }
        }.start()
    }
    private fun removeYouTubeBlurOverlay() {
        youtubeBlurOverlayView?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
        }
        youtubeBlurOverlayView = null
        youtubeBlurOverlayActive = false
        youtubeBlurOverlayPaused = false
        youtubeBlurOverlayTimer?.cancel()
        youtubeBlurOverlayTimer = null
    }
    private fun pauseYouTubeBlurOverlayTimer() {
        youtubeBlurOverlayTimer?.cancel()
        youtubeBlurOverlayPaused = true
    }
    private fun resumeYouTubeBlurOverlayTimer() {
        if (youtubeBlurOverlayActive && youtubeBlurOverlayPaused && youtubeBlurOverlayRemaining > 0L) {
            showYouTubeBlurOverlay(youtubeBlurOverlayRemaining)
            youtubeBlurOverlayPaused = false
        }
    }
} 