package com.example.mindshield.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import com.example.mindshield.data.local.AppUsageDao
import com.example.mindshield.data.model.AppUsage
import com.example.mindshield.data.model.AppInfo
import com.example.mindshield.data.model.AppCategory
import com.example.mindshield.util.DistractionAppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import android.content.pm.PackageManager
import android.util.Log
import com.example.mindshield.data.model.DistractionEvent

@Singleton
class AppUsageRepository @Inject constructor(
    private val appUsageDao: AppUsageDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppUsageRepository"
    }

    // Static mapping for demo; can be made user-configurable
    private val productiveApps = setOf(
        "com.google.android.apps.docs", "com.microsoft.office.word", "com.microsoft.teams", "com.google.android.keep", "com.todoist", "com.evernote", "com.google.android.apps.classroom", "com.adobe.scan.android"
    )
    private val unproductiveApps = setOf(
        "com.facebook.katana", "com.instagram.android", "com.twitter.android", "com.snapchat.android", "com.whatsapp", "com.discord", "com.reddit.frontpage", "com.zhiliaoapp.musically", "com.google.android.youtube", "com.netflix.mediaclient", "com.spotify.music", "com.epicgames.fortnite", "com.activision.callofduty.shooter", "com.ea.gp.apexlegendsmobilefps", "com.roblox.client", "com.mojang.minecraftpe", "com.nianticlabs.pokemongo", "com.supercell.clashofclans", "com.supercell.clashroyale", "com.king.candycrushsaga"
    )

    private fun getCategory(packageName: String): AppCategory = when {
        productiveApps.contains(packageName) -> AppCategory.PRODUCTIVE
        unproductiveApps.contains(packageName) -> AppCategory.UNPRODUCTIVE
        DistractionAppRepository.isDistractingApp(context, packageName) -> AppCategory.UNPRODUCTIVE
        else -> AppCategory.NEUTRAL
    }

    private fun getAppIcon(packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) { null }

    private fun getAppName(packageName: String): String = try {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    } catch (e: Exception) {
        packageName.split(".").last().replaceFirstChar { it.uppercase() }
    }

    private fun getTodayUsageStats(): List<android.app.usage.UsageStats> {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = getStartOfDay()
            
            Log.d(TAG, "Fetching usage stats from $startTime to $endTime")
            
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            
            Log.d(TAG, "Found ${usageStats.size} apps with usage data")
            usageStats.filter { it.totalTimeInForeground > 0 }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching usage stats", e)
            emptyList()
        }
    }

    private fun getStartOfDay(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    suspend fun getRecentApps(limit: Int = 10): List<AppInfo> {
        val usageStats = getTodayUsageStats()
        return usageStats
            .sortedByDescending { it.lastTimeUsed }
            .take(limit)
            .map { stats ->
                AppInfo(
                    packageName = stats.packageName,
                    appName = getAppName(stats.packageName),
                    icon = getAppIcon(stats.packageName),
                    category = getCategory(stats.packageName),
                    usageTime = stats.totalTimeInForeground
                )
            }
    }

    suspend fun getTopApps(limit: Int = 5): List<AppInfo> {
        val usageStats = getTodayUsageStats()
        return usageStats
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { stats ->
                AppInfo(
                    packageName = stats.packageName,
                    appName = getAppName(stats.packageName),
                    icon = getAppIcon(stats.packageName),
                    category = getCategory(stats.packageName),
                    usageTime = stats.totalTimeInForeground
                )
            }
    }

    suspend fun getTodayOverview(): TodayOverview {
        val usageStats = getTodayUsageStats()
        
        val productive = usageStats.filter { getCategory(it.packageName) == AppCategory.PRODUCTIVE }
        val unproductive = usageStats.filter { getCategory(it.packageName) == AppCategory.UNPRODUCTIVE }
        val neutral = usageStats.filter { getCategory(it.packageName) == AppCategory.NEUTRAL }
        
        val productiveTime = productive.sumOf { it.totalTimeInForeground }
        val unproductiveTime = unproductive.sumOf { it.totalTimeInForeground }
        val neutralTime = neutral.sumOf { it.totalTimeInForeground }
        
        Log.d(TAG, "Today Overview - Productive: ${productiveTime/1000/60}m, Unproductive: ${unproductiveTime/1000/60}m, Neutral: ${neutralTime/1000/60}m")
        
        return TodayOverview(
            productiveTime = productiveTime,
            unproductiveTime = unproductiveTime,
            neutralTime = neutralTime,
            productiveApps = productive.map { it.packageName },
            unproductiveApps = unproductive.map { it.packageName },
            neutralApps = neutral.map { it.packageName }
        )
    }

    fun getRealTimeStats(): Flow<RealTimeStats> = flow {
        while (true) {
            val usageStats = getTodayUsageStats()
            val totalUsageTime = usageStats.sumOf { it.totalTimeInForeground }
            val distractingApps = usageStats.filter { getCategory(it.packageName) == AppCategory.UNPRODUCTIVE }
            val distractionTime = distractingApps.sumOf { it.totalTimeInForeground }
            
            Log.d(TAG, "Real-time stats - Total: ${totalUsageTime/1000/60}m, Distractions: ${distractingApps.size}")
            
            emit(RealTimeStats(totalUsageTime, distractingApps.size))
            kotlinx.coroutines.delay(10_000L) // Update every 10 seconds
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getAppUsageForPeriod(startTime: Long, endTime: Long): List<AppUsage> {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            
            usageStats.filter { it.totalTimeInForeground > 0 }.map { stats ->
                AppUsage(
                    packageName = stats.packageName,
                    appName = getAppName(stats.packageName),
                    startTime = stats.firstTimeStamp,
                    endTime = stats.lastTimeUsed,
                    duration = stats.totalTimeInForeground / 60_000L, // Convert to minutes
                    timestamp = stats.lastTimeUsed,
                    isDistracting = getCategory(stats.packageName) == AppCategory.UNPRODUCTIVE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching app usage for period", e)
            emptyList()
        }
    }

    suspend fun insertAppUsage(appUsage: AppUsage) {
        appUsageDao.insertAppUsage(appUsage)
    }

    suspend fun logBlockEvent(packageName: String, appName: String, duration: Long) {
        val event = DistractionEvent(
            packageName = packageName,
            appName = appName,
            duration = duration,
            acknowledged = true
        )
        appUsageDao.insertDistractionEvent(event)
    }

    data class TodayOverview(
        val productiveTime: Long,
        val unproductiveTime: Long,
        val neutralTime: Long,
        val productiveApps: List<String>,
        val unproductiveApps: List<String>,
        val neutralApps: List<String>
    )

    data class RealTimeStats(
        val usageTime: Long,
        val distractions: Int
    )
} 