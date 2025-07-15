package com.example.mindshield.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshield.data.model.AppUsage
import com.example.mindshield.data.repository.AppUsageRepository
import com.example.mindshield.data.repository.DistractionEventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    private val distractionEventRepository: DistractionEventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    loadAnalytics()
                    Log.d("AnalyticsViewModel", "Updated analytics data")
                } catch (e: Exception) {
                    Log.e("AnalyticsViewModel", "Error updating analytics data", e)
                }
                kotlinx.coroutines.delay(10_000L) // Update every 10 seconds
            }
        }
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val now = System.currentTimeMillis()
            val weekAgo = now - (7 * 24 * 60 * 60 * 1000)
            val dayAgo = now - (24 * 60 * 60 * 1000)

            val weeklyUsage: List<AppUsage> = appUsageRepository.getAppUsageForPeriod(weekAgo, now)
            val dailyUsage: List<AppUsage> = appUsageRepository.getAppUsageForPeriod(dayAgo, now)
            val weeklyDistractions = distractionEventRepository.getDistractionEventsForPeriod(weekAgo, now)

            val totalUsageMinutes = weeklyUsage.sumOf { usage -> usage.duration }
            val totalDistractions = weeklyDistractions.size

            // Distraction time: sum durations for apps marked as distracting
            val distractionPackages = weeklyUsage.filter { it.isDistracting }.map { it.packageName }.toSet()
            val distractionUsageMinutes = weeklyUsage.filter { distractionPackages.contains(it.packageName) }.sumOf { it.duration }

            val focusScore = calculateFocusScore(totalUsageMinutes, totalDistractions)
            val averageSessionMinutes = calculateAverageSession(weeklyUsage)

            val topDistractingApps = getTopDistractingApps(weeklyUsage, weeklyDistractions)
            val weeklyData = getWeeklyData(weekAgo, now)
            val categoryBreakdown = getCategoryBreakdown(weeklyUsage, weeklyDistractions)

            Log.d("AnalyticsViewModel", "Analytics loaded - Total: ${totalUsageMinutes}m, Distractions: ${totalDistractions}, Focus Score: ${focusScore}%")

            _uiState.value = AnalyticsUiState(
                loading = false,
                totalUsageMinutes = totalUsageMinutes,
                distractionUsageMinutes = distractionUsageMinutes,
                totalDistractions = totalDistractions,
                focusScore = focusScore,
                averageSessionMinutes = averageSessionMinutes,
                topDistractingApps = topDistractingApps,
                weeklyData = weeklyData,
                categoryBreakdown = categoryBreakdown
            )
        }
    }

    private fun calculateFocusScore(totalUsageMinutes: Long, totalDistractions: Int): Int {
        if (totalUsageMinutes == 0L) return 100
        val distractionRate = (totalDistractions.toFloat() / (totalUsageMinutes / 60).toFloat()) * 100
        val baseScore = 100 - distractionRate.toInt()
        return maxOf(0, minOf(100, baseScore))
    }

    private fun calculateAverageSession(usageData: List<AppUsage>): Int {
        if (usageData.isEmpty()) return 0
        val totalSessions = usageData.size
        val totalMinutes = usageData.sumOf { usage: AppUsage -> usage.duration }
        return (totalMinutes / totalSessions).toInt()
    }

    private fun getTopDistractingApps(
        usageData: List<AppUsage>,
        distractionEvents: List<com.example.mindshield.data.model.DistractionEvent>
    ): List<DistractingApp> {
        val distractingApps = usageData.filter { usage -> usage.isDistracting }
        val distractionCounts = distractionEvents.groupBy { event -> event.packageName }
            .mapValues { entry -> entry.value.size }

        return distractingApps.map { app ->
            DistractingApp(
                packageName = app.packageName,
                appName = app.appName,
                usageMinutes = app.duration,
                distractionCount = distractionCounts[app.packageName] ?: 0,
                avgSessionMinutes = (app.duration / maxOf(1, distractionCounts[app.packageName] ?: 1)).toInt()
            )
        }.sortedByDescending { distractingApp -> distractingApp.usageMinutes }.take(10)
    }

    private suspend fun getWeeklyData(startTime: Long, endTime: Long): List<DailyData> {
        val days = mutableListOf<DailyData>()
        val dayInMillis = 24 * 60 * 60 * 1000L
        for (i in 0..6) {
            val dayStart = startTime + (i * dayInMillis)
            val dayEnd = dayStart + dayInMillis
            val dayUsage: List<AppUsage> = appUsageRepository.getAppUsageForPeriod(dayStart, dayEnd)
            val dayDistractions = distractionEventRepository.getDistractionEventsForPeriod(dayStart, dayEnd)
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            days.add(
                DailyData(
                    day = dayNames[i],
                    usageMinutes = dayUsage.sumOf { usage: AppUsage -> usage.duration },
                    distractions = dayDistractions.size
                )
            )
        }
        return days
    }

    private fun getCategoryBreakdown(
        usageData: List<AppUsage>,
        distractionEvents: List<com.example.mindshield.data.model.DistractionEvent>
    ): List<CategoryData> {
        val categories = mapOf(
            "Social Media" to listOf("com.facebook", "com.instagram", "com.twitter", "com.snapchat"),
            "Entertainment" to listOf("com.netflix", "com.spotify", "com.youtube"),
            "Games" to listOf("com.epicgames", "com.activision", "com.ea"),
            "Productivity" to listOf("com.microsoft", "com.google.android.apps.docs", "com.slack")
        )
        return categories.map { (categoryName, packageNames) ->
            val categoryApps = usageData.filter { app ->
                packageNames.any { packageName ->
                    app.packageName.startsWith(packageName)
                }
            }
            val categoryDistractions = distractionEvents.filter { event ->
                packageNames.any { packageName ->
                    event.packageName.startsWith(packageName)
                }
            }
            CategoryData(
                name = categoryName,
                appCount = categoryApps.size,
                totalUsageMinutes = categoryApps.sumOf { app: AppUsage -> app.duration },
                distractionCount = categoryDistractions.size
            )
        }.filter { category: CategoryData -> category.appCount > 0 }.sortedByDescending { category: CategoryData -> category.totalUsageMinutes }
    }

    fun refreshAnalytics() {
        loadAnalytics()
    }
}

data class AnalyticsUiState(
    val loading: Boolean = true,
    val totalUsageMinutes: Long = 0,
    val distractionUsageMinutes: Long = 0,
    val totalDistractions: Int = 0,
    val focusScore: Int = 0,
    val averageSessionMinutes: Int = 0,
    val topDistractingApps: List<DistractingApp> = emptyList(),
    val weeklyData: List<DailyData> = emptyList(),
    val categoryBreakdown: List<CategoryData> = emptyList()
)

data class DistractingApp(
    val packageName: String,
    val appName: String,
    val usageMinutes: Long,
    val distractionCount: Int,
    val avgSessionMinutes: Int
)

data class DailyData(
    val day: String,
    val usageMinutes: Long,
    val distractions: Int
)

data class CategoryData(
    val name: String,
    val appCount: Int,
    val totalUsageMinutes: Long,
    val distractionCount: Int
) 