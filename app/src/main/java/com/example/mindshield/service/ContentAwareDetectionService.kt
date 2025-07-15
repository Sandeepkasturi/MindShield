package com.example.mindshield.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import android.content.Context
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import java.util.concurrent.ConcurrentHashMap
import android.view.inputmethod.InputMethodManager
import android.text.TextUtils
import java.util.Calendar
import com.example.mindshield.util.DistractionSessionManager
import com.example.mindshield.util.DistractionNotificationHelper
import com.example.mindshield.util.DistractionBlockOverlay
import android.graphics.drawable.BitmapDrawable

class ContentAwareDetectionService : AccessibilityService() {
    
    private val TAG = "ContentAwareDetection"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentApp: String? = null
    private var lastAnalysisTime = 0L
    private val analysisCache = ConcurrentHashMap<String, Boolean>() // packageName -> isDistracting
    
    // Enhanced search analysis state
    private var lastSearchText = ""
    private var searchAnalysisInProgress = false
    private var searchStartTime = 0L
    private val searchTimeoutMs = 30000L // 30 seconds to leave app after search
    private var searchHistory = mutableListOf<String>()
    private var lastSearchTime = 0L
    
    // Enhanced keywords for smarter content analysis
    private val entertainmentKeywords = listOf(
        "entertainment", "funny", "comedy", "music", "gaming", "sports", "movie", "tv show",
        "viral", "trending", "memes", "dance", "prank", "challenge", "reaction", "unboxing",
        "gaming", "stream", "live", "fortnite", "minecraft", "roblox", "tiktok", "instagram",
        "facebook", "snapchat", "twitter", "reddit", "pinterest", "netflix", "disney+", "hulu",
        "amazon prime", "hbo", "youtube music", "spotify", "pandora", "apple music",
        "cat", "dog", "funny", "lol", "laugh", "joke", "prank", "fail", "epic", "amazing",
        "viral", "trending", "popular", "hot", "best", "top", "favorite", "love", "cute",
        "beautiful", "awesome", "incredible", "unbelievable", "shocking", "crazy", "wild",
        "gossip", "celebrity", "hollywood", "star", "famous", "viral video", "funny video",
        "dance challenge", "lip sync", "cover", "remix", "mashup", "parody", "skit",
        "gameplay", "walkthrough", "speedrun", "let's play", "review", "unboxing",
        "haul", "haul video", "shopping", "fashion", "makeup", "beauty", "lifestyle",
        "vlog", "daily vlog", "morning routine", "night routine", "what i eat",
        "food", "recipe", "cooking", "baking", "dessert", "snack", "meal prep",
        "workout", "fitness", "exercise", "gym", "yoga", "meditation", "wellness",
        "travel", "vacation", "trip", "adventure", "explore", "destination", "hotel",
        "restaurant", "food review", "travel vlog", "city tour", "sightseeing"
    )
    
    private val productiveKeywords = listOf(
        "tutorial", "how to", "educational", "learning", "course", "study", "research",
        "documentation", "guide", "manual", "reference", "academic", "professional",
        "work", "business", "productivity", "development", "programming", "coding",
        "design", "architecture", "engineering", "science", "technology", "innovation",
        "news", "current events", "politics", "economics", "finance", "health", "fitness",
        "nutrition", "cooking", "recipes", "diy", "home improvement", "gardening",
        "learn", "teach", "explain", "understand", "improve", "develop", "create",
        "build", "solve", "fix", "help", "support", "guide", "tutorial", "lesson",
        "algorithm", "data structure", "software", "hardware", "database", "api",
        "framework", "library", "tool", "utility", "optimization", "performance",
        "security", "privacy", "encryption", "authentication", "authorization",
        "testing", "debugging", "deployment", "ci/cd", "version control", "git",
        "docker", "kubernetes", "cloud", "aws", "azure", "gcp", "serverless",
        "microservices", "api design", "rest", "graphql", "websocket", "real-time",
        "machine learning", "ai", "artificial intelligence", "data science", "analytics",
        "statistics", "mathematics", "physics", "chemistry", "biology", "medicine",
        "psychology", "philosophy", "history", "geography", "literature", "language",
        "grammar", "vocabulary", "writing", "reading", "comprehension", "critical thinking",
        "problem solving", "logic", "reasoning", "analysis", "synthesis", "evaluation",
        "project management", "agile", "scrum", "kanban", "lean", "six sigma",
        "leadership", "management", "strategy", "planning", "organization", "time management",
        "goal setting", "motivation", "inspiration", "success", "achievement", "growth",
        "development", "improvement", "progress", "advancement", "career", "professional",
        "industry", "market", "trend", "innovation", "disruption", "transformation"
    )

    private var lastDistractingSearch: String? = null
    private var wasWarnedForCurrentSession: Boolean = false
    private var lastYouTubeOpenTime = 0L
    private var lastYouTubeForegroundCheck = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "ContentAwareDetectionService connected")
        
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
        
        Log.d(TAG, "Accessibility service info set: ${info.eventTypes}")
        
        startContentMonitoring()
        startEnhancedSearchMonitoring()
        
        // Send a test broadcast to verify the service is working
        try {
            val testIntent = Intent("com.example.mindshield.CONTENT_ANALYSIS_PROGRESS")
            testIntent.putExtra("inProgress", false)
            sendBroadcast(testIntent)
            Log.d(TAG, "Test broadcast sent to verify service is working")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test broadcast: ${e.message}")
        }
    }

    private fun startContentMonitoring() {
        scope.launch {
            while (isActive) {
                val foregroundApp = getForegroundApp()
                Log.d(TAG, "Checking foreground app: $foregroundApp")
                
                if (foregroundApp != null && isContentAwareApp(foregroundApp)) {
                    Log.d(TAG, "Content-aware app detected: $foregroundApp")
                    if (currentApp != foregroundApp) {
                        Log.d(TAG, "App changed from $currentApp to $foregroundApp")
                        currentApp = foregroundApp
                        if (foregroundApp == "com.google.android.youtube") {
                            lastYouTubeOpenTime = System.currentTimeMillis()
                            analyzeContent(foregroundApp) // Immediate extraction
                            // Launch delayed extraction after 10 seconds
                            scope.launch {
                                delay(10_000)
                                // Only extract again if YouTube is still foreground
                                val stillForeground = getForegroundApp() == "com.google.android.youtube"
                                if (stillForeground) {
                                    Log.d(TAG, "10s after YouTube open: extracting content again.")
                                    analyzeContent("com.google.android.youtube")
                                } else {
                                    Log.d(TAG, "10s after YouTube open: YouTube not foreground, skipping extraction.")
                                }
                            }
                        } else {
                            analyzeContent(foregroundApp)
                        }
                    } else {
                        // For YouTube, analyze more frequently to catch video changes
                        if (foregroundApp == "com.google.android.youtube") {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastAnalysisTime > 3000) { // Check every 3 seconds for YouTube
                                Log.d(TAG, "Re-analyzing YouTube content...")
                                analyzeContent(foregroundApp)
                                lastAnalysisTime = currentTime
                            }
                        } else {
                            Log.d(TAG, "Same app, no analysis needed")
                        }
                    }
                } else {
                    if (currentApp != null) {
                        Log.d(TAG, "App changed from $currentApp to non-content-aware app")
                        currentApp = null
                    }
                }
                delay(2000) // Check every 2 seconds instead of 5 seconds
            }
        }
    }

    private fun startEnhancedSearchMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null && currentApp == "com.google.android.youtube") {
                        val searchCandidates = mutableListOf<String>()
                        fun traverse(node: AccessibilityNodeInfo?) {
                            if (node == null) return
                            val text = node.text?.toString() ?: ""
                            val desc = node.contentDescription?.toString() ?: ""
                            if ((text.isNotBlank() && text.length > 2) || (desc.isNotBlank() && desc.length > 2)) {
                                // Heuristic: likely a search if contains 'search', 'find', or is in the search bar
                                if (text.contains("search", true) || desc.contains("search", true) ||
                                    text.contains("find", true) || desc.contains("find", true) ||
                                    node.className?.contains("EditText") == true || node.className?.contains("TextView") == true) {
                                    searchCandidates.add(text.ifBlank { desc })
                                }
                            }
                            for (i in 0 until node.childCount) {
                                traverse(node.getChild(i))
                            }
                        }
                        traverse(rootNode)
                        if (searchCandidates.isNotEmpty()) {
                            val candidate = searchCandidates.last() // Most recent
                            if (candidate != lastSearchText) {
                                Log.d(TAG, "[YouTube] Search candidate detected: $candidate")
                                lastSearchText = candidate
                                searchStartTime = System.currentTimeMillis()
                                searchAnalysisInProgress = true
                                analyzeSearchContent(candidate)
                            }
                        }
                    } else {
                        // Fallback: original logic for other apps
                        val currentFocusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (currentFocusedNode != null && currentFocusedNode.text != null) {
                            val currentText = currentFocusedNode.text.toString()
                            if (currentText != lastSearchText && currentText.isNotBlank() && currentText.length > 2) {
                                lastSearchText = currentText
                                searchStartTime = System.currentTimeMillis()
                                searchAnalysisInProgress = true
                                Log.d(TAG, "Search text detected: ${lastSearchText.take(50)}...")
                                analyzeSearchContent(lastSearchText)
                            }
                        } else if (searchAnalysisInProgress && System.currentTimeMillis() - searchStartTime > searchTimeoutMs) {
                            Log.d(TAG, "Search session timed out. App left after search.")
                            searchAnalysisInProgress = false
                            lastSearchText = ""
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in search monitoring: ${e.message}")
                }
                delay(500) // Check every 500ms for focused node changes
            }
        }
    }

    private fun analyzeSearchContent(searchText: String) {
        scope.launch {
            try {
                Log.d(TAG, "Analyzing search content: $searchText")
                
                // Add to search history for pattern analysis
                searchHistory.add(searchText)
                if (searchHistory.size > 10) {
                    searchHistory.removeAt(0)
                }
                
                // Analyze the search text for entertainment vs productive keywords
                val entertainmentScore = calculateEntertainmentScore(searchText)
                val productiveScore = calculateProductiveScore(searchText)
                
                // Enhanced analysis: check for search patterns
                val patternScore = analyzeSearchPatterns(searchText)
                val timeBasedScore = analyzeTimeBasedBehavior()
                
                val totalEntertainmentScore = entertainmentScore + patternScore + timeBasedScore
                
                Log.d(TAG, "Search analysis - Entertainment: $entertainmentScore, Productive: $productiveScore, Pattern: $patternScore, Time: $timeBasedScore")
                
                val isDistracting = totalEntertainmentScore > productiveScore && totalEntertainmentScore > 1
                
                // --- NEW LOGIC: If YouTube is open and search is distracting, trigger new overlay flow ---
                if (currentApp == "com.google.android.youtube" && isDistracting) {
                    // Instead of showing notification/overlay here, call monitor service to handle
                    val intent = Intent("com.example.mindshield.DISTRACTING_CONTENT_DETECTED")
                    intent.setPackage(packageName)
                    intent.putExtra("packageName", currentApp)
                    sendBroadcast(intent)
                }
                // --- END NEW LOGIC ---
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing search content: ${e.message}")
            }
        }
    }

    private fun showDistractionOverlay() {
        // Block YouTube for 30 seconds
        DistractionBlockOverlay.show(this, "YouTube", 30_000L) {
            // After timer, reset warning state so user gets a fresh warning next time
            wasWarnedForCurrentSession = false
            lastDistractingSearch = null
        }
    }

    private fun analyzeSearchPatterns(searchText: String): Int {
        var patternScore = 0
        
        // Check for repeated searches (addiction patterns)
        val recentSearches = searchHistory.takeLast(5)
        val uniqueSearches = recentSearches.toSet()
        if (recentSearches.size > 3 && uniqueSearches.size < recentSearches.size * 0.6) {
            patternScore += 2 // Repeated searches indicate potential addiction
        }
        
        // Check for quick successive searches
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSearchTime < 5000) { // Less than 5 seconds between searches
            patternScore += 1
        }
        lastSearchTime = currentTime
        
        // Check for entertainment-focused search patterns
        val entertainmentPatterns = listOf(
            "funny", "viral", "trending", "best", "top", "popular", "hot",
            "latest", "new", "trending", "viral", "famous", "celebrity"
        )
        
        entertainmentPatterns.forEach { pattern ->
            if (searchText.contains(pattern, ignoreCase = true)) {
                patternScore += 1
            }
        }
        
        return patternScore
    }

    private fun analyzeTimeBasedBehavior(): Int {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        var timeScore = 0
        
        // Higher score for entertainment searches during work hours (9 AM - 5 PM)
        if (currentHour in 9..17) {
            timeScore += 1
        }
        
        // Higher score for late night entertainment searches (10 PM - 6 AM)
        if (currentHour >= 22 || currentHour <= 6) {
            timeScore += 1
        }
        
        return timeScore
    }

    private fun notifyDistractingSearch(searchText: String, entertainmentScore: Int, productiveScore: Int) {
        try {
            // Send broadcast for immediate distraction notification
            val intent = Intent("com.example.mindshield.DISTRACTING_SEARCH_DETECTED")
            intent.putExtra("searchText", searchText)
            intent.putExtra("entertainmentScore", entertainmentScore)
            intent.putExtra("productiveScore", productiveScore)
            intent.putExtra("packageName", currentApp)
            sendBroadcast(intent)
            
            Log.d(TAG, "Distracting search broadcast sent for: $searchText")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending distracting search broadcast: ${e.message}")
        }
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

    private fun getForegroundApp(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 5000
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
            Log.e(TAG, "Error getting foreground app: ${e.message}")
            null
        }
    }

    private fun analyzeContent(packageName: String) {
        scope.launch {
            try {
                // Notify DistractionAppMonitorService that analysis is starting
                notifyAnalysisInProgress(true)
                Log.d(TAG, "Starting content analysis for: $packageName")
                // Wait a bit for the app to fully load
                delay(1000)
                val isDistracting = when (packageName) {
                    "com.google.android.youtube" -> analyzeYouTubeContent()
                    else -> analyzeBrowserContent()
                }
                // Cache the result
                analysisCache[packageName] = isDistracting
                // Notify DistractionAppMonitorService of the result
                notifyAnalysisResult(isDistracting)
                Log.d(TAG, "Content analysis complete for $packageName: isDistracting=$isDistracting")
                // --- FINAL LOGIC: For YouTube, never start session or notification on open ---
                if (packageName == "com.google.android.youtube") {
                    DistractionSessionManager.resetSession() // Always reset session for YouTube open
                } else if (isDistracting) {
                    DistractionSessionManager.onAppOpened(this@ContentAwareDetectionService, packageName)
                } else {
                    DistractionSessionManager.resetSession()
                }
                // --- END FINAL LOGIC ---
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing content: ${e.message}")
                notifyAnalysisResult(false) // Default to non-distracting on error
            } finally {
                notifyAnalysisInProgress(false)
            }
        }
    }

    private fun analyzeYouTubeContent(): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "YouTube analysis failed: rootInActiveWindow is null")
            return false
        }
        
        Log.d(TAG, "Starting YouTube content analysis...")
        
        // Extract text from various YouTube elements
        val textContent = extractTextFromNode(rootNode)
        Log.d(TAG, "Extracted text content: ${textContent.take(500)}...") // Log more content for debugging
        Log.d(TAG, "YouTube text content length: ${textContent.length}")
        
        // Analyze the content
        val entertainmentScore = calculateEntertainmentScore(textContent)
        val productiveScore = calculateProductiveScore(textContent)
        
        Log.d(TAG, "YouTube analysis - Entertainment: $entertainmentScore, Productive: $productiveScore")
        
        // More aggressive detection for YouTube - any entertainment content is likely distracting
        val isDistracting = when {
            entertainmentScore > 0 && entertainmentScore > productiveScore -> {
                Log.d(TAG, "YouTube: Entertainment score higher than productive")
                true
            }
            entertainmentScore > 1 -> {
                Log.d(TAG, "YouTube: High entertainment score detected")
                true
            }
            textContent.contains("funny", ignoreCase = true) || 
            textContent.contains("comedy", ignoreCase = true) ||
            textContent.contains("laugh", ignoreCase = true) ||
            textContent.contains("humor", ignoreCase = true) ||
            textContent.contains("joke", ignoreCase = true) ||
            textContent.contains("prank", ignoreCase = true) ||
            textContent.contains("viral", ignoreCase = true) ||
            textContent.contains("trending", ignoreCase = true) -> {
                Log.d(TAG, "YouTube: Direct entertainment keywords found")
                true
            }
            // For YouTube, if we can't determine content type, assume it's distracting
            textContent.isBlank() || textContent.length < 50 -> {
                Log.d(TAG, "YouTube: Limited text content, assuming distracting")
                true
            }
            else -> {
                Log.d(TAG, "YouTube: No clear entertainment content detected, but showing alert anyway")
                true // Default to distracting for YouTube
            }
        }
        
        Log.d(TAG, "YouTube analysis result: isDistracting=$isDistracting")
        return isDistracting
    }

    private fun analyzeBrowserContent(): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Browser analysis failed: rootInActiveWindow is null")
            return false
        }
        
        Log.d(TAG, "Starting browser content analysis...")
        
        // Extract text from browser elements
        val textContent = extractTextFromNode(rootNode)
        Log.d(TAG, "Extracted text content: ${textContent.take(200)}...") // Log first 200 chars
        
        // Analyze the content
        val entertainmentScore = calculateEntertainmentScore(textContent)
        val productiveScore = calculateProductiveScore(textContent)
        
        Log.d(TAG, "Browser analysis - Entertainment: $entertainmentScore, Productive: $productiveScore")
        
        // If entertainment score is significantly higher than productive, it's distracting
        val isDistracting = entertainmentScore > productiveScore && entertainmentScore > 3
        Log.d(TAG, "Browser analysis result: isDistracting=$isDistracting")
        
        return isDistracting
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        
        val text = StringBuilder()
        
        // Get text from current node
        node.text?.let { text.append(it.toString().lowercase()) }
        node.contentDescription?.let { text.append(" ").append(it.toString().lowercase()) }
        
        // Recursively get text from children
        for (i in 0 until node.childCount) {
            text.append(" ").append(extractTextFromNode(node.getChild(i)))
        }
        
        return text.toString()
    }

    private fun calculateEntertainmentScore(text: String): Int {
        var score = 0
        entertainmentKeywords.forEach { keyword ->
            if (text.contains(keyword, ignoreCase = true)) {
                score++
            }
        }
        return score
    }

    private fun calculateProductiveScore(text: String): Int {
        var score = 0
        productiveKeywords.forEach { keyword ->
            if (text.contains(keyword, ignoreCase = true)) {
                score++
            }
        }
        return score
    }

    private fun notifyAnalysisResult(isDistracting: Boolean) {
        try {
            // Send broadcast to DistractionAppMonitorService
            val intent = Intent("com.example.mindshield.CONTENT_ANALYSIS_RESULT")
            intent.putExtra("isDistracting", isDistracting)
            intent.putExtra("packageName", currentApp)
            sendBroadcast(intent)
            
            Log.d(TAG, "Broadcast sent: isDistracting=$isDistracting")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending analysis result: ${e.message}")
        }
    }

    private fun notifyAnalysisInProgress(inProgress: Boolean) {
        try {
            val intent = Intent("com.example.mindshield.CONTENT_ANALYSIS_PROGRESS")
            intent.putExtra("inProgress", inProgress)
            intent.putExtra("packageName", currentApp)
            sendBroadcast(intent)
            
            Log.d(TAG, "Analysis progress broadcast: inProgress=$inProgress")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending analysis progress: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (currentApp == "com.google.android.youtube" && event != null) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val node = event.source
                    // Heuristic: search icon/button click
                    if (node != null && (node.contentDescription?.contains("search", true) == true ||
                                         node.className?.contains("ImageButton") == true)) {
                        val searchText = findSearchBarText(rootInActiveWindow)
                        if (!searchText.isNullOrBlank() && searchText != lastSearchText) {
                            Log.d(TAG, "[YouTube] Search submitted via icon: $searchText")
                            lastSearchText = searchText
                            analyzeSearchContent(searchText)
                        }
                    }
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // Optionally, detect IME_ACTION_SEARCH or Enter key
                    if (event.action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY ||
                        event.action == AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) {
                        val searchText = findSearchBarText(rootInActiveWindow)
                        if (!searchText.isNullOrBlank() && searchText != lastSearchText) {
                            Log.d(TAG, "[YouTube] Search submitted via keyboard: $searchText")
                            lastSearchText = searchText
                            analyzeSearchContent(searchText)
                        }
                    }
                }
            }
        }
        event?.let {
            try {
                when (it.eventType) {
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                        // Capture text changes in search fields
                        val source = it.source
                        if (source != null && source.isFocused) {
                            val text = source.text?.toString() ?: ""
                            if (text.isNotBlank() && text.length > 2 && text != lastSearchText) {
                                lastSearchText = text
                                searchStartTime = System.currentTimeMillis()
                                searchAnalysisInProgress = true
                                Log.d(TAG, "Text change detected in search: ${text.take(50)}...")
                                analyzeSearchContent(text)
                            } else {
                                // Text doesn't meet criteria for analysis
                            }
                        } else {
                            // Source is null or not focused
                        }
                    }
                    AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                        // Monitor when search fields get focus
                        val source = it.source
                        if (source != null && source.isEditable) {
                            Log.d(TAG, "Search field focused")
                        } else {
                            // Source is null or not editable
                        }
                    }
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                        // Handle content changes for real-time analysis
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAnalysisTime > 2000) { // Reduced debounce for faster response
                            currentApp?.let { app ->
                                if (isContentAwareApp(app)) {
                                    Log.d(TAG, "Content change detected, analyzing: $app")
                                    analyzeContent(app)
                                    lastAnalysisTime = currentTime
                                } else {
                                    // App is not content-aware
                                    Log.d(TAG, "Content change in non-content-aware app: $app")
                                }
                            } ?: run {
                                // currentApp is null
                                Log.d(TAG, "Content change detected but no current app")
                            }
                        } else {
                            // Analysis debounced
                            Log.d(TAG, "Content analysis debounced (${currentTime - lastAnalysisTime}ms since last)")
                        }
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        // Monitor clicks on search-related elements
                        val source = it.source
                        if (source != null) {
                            val text = source.text?.toString() ?: ""
                            val contentDesc = source.contentDescription?.toString() ?: ""
                            
                            // Check if clicked element is search-related
                            if (text.contains("search", ignoreCase = true) || 
                                contentDesc.contains("search", ignoreCase = true) ||
                                text.contains("google", ignoreCase = true) ||
                                contentDesc.contains("google", ignoreCase = true)) {
                                Log.d(TAG, "Search element clicked: $text")
                            } else {
                                // Clicked element is not search-related
                            }
                        } else {
                            // Source is null
                        }
                    }
                    else -> {
                        // Handle other event types if needed
                        Log.d(TAG, "Other accessibility event: ${it.eventType}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling accessibility event: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "ContentAwareDetectionService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        
        // Clean up resources
        cleanupResources()
        Log.d(TAG, "ContentAwareDetectionService destroyed")
    }
    
    private fun cleanupResources() {
        // Clear cached data
        currentApp = null
        lastAnalysisTime = 0L
        analysisCache.clear()
        searchHistory.clear()
        
        // Force garbage collection
        System.gc()
        Log.d(TAG, "ContentAwareDetectionService resources cleaned up")
    }

    private fun findSearchBarText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        if (node.className?.contains("EditText") == true && node.text?.isNotBlank() == true) {
            return node.text.toString()
        }
        for (i in 0 until node.childCount) {
            val result = findSearchBarText(node.getChild(i))
            if (!result.isNullOrBlank()) return result
        }
        return null
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ContentAwareDetectionService::class.java)
            context.startService(intent)
            Log.d("ContentAwareDetection", "ContentAwareDetectionService start requested")
        }

        fun stop(context: Context) {
            val intent = Intent(context, ContentAwareDetectionService::class.java)
            context.stopService(intent)
            Log.d("ContentAwareDetection", "ContentAwareDetectionService stop requested")
        }

        fun isContentAwareServiceEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val isRunning = manager.getRunningServices(Integer.MAX_VALUE).any { 
                it.service.className == ContentAwareDetectionService::class.java.name 
            }
            Log.d("ContentAwareDetection", "ContentAwareDetectionService running: $isRunning")
            return isRunning
        }
        
        // Manual trigger for testing
        fun triggerContentAnalysis(context: Context, packageName: String) {
            val intent = Intent("com.example.mindshield.MANUAL_CONTENT_ANALYSIS")
            intent.putExtra("packageName", packageName)
            context.sendBroadcast(intent)
            Log.d("ContentAwareDetection", "Manual content analysis triggered for: $packageName")
        }
    }
} 