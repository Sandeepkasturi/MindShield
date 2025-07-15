package com.example.mindshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "distraction_events")
data class DistractionEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val duration: Long, // in minutes
    val timestamp: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false
) 