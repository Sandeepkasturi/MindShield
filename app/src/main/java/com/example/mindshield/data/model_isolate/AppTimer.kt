package com.example.mindshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_timers")
data class AppTimer(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val isEnabled: Boolean = true,
    val lastResetDate: Long = System.currentTimeMillis(),
    val currentUsageMinutes: Int = 0,
    val currentUsageSeconds: Int = 0
) 