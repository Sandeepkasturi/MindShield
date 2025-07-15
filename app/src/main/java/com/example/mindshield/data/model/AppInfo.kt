package com.example.mindshield.data.model

import android.graphics.drawable.Drawable

enum class AppCategory {
    PRODUCTIVE, UNPRODUCTIVE, NEUTRAL
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val category: AppCategory,
    val usageTime: Long // in milliseconds
) 