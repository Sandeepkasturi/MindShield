package com.example.mindshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage")
data class AppUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long, // in minutes
    val isDistracting: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) 