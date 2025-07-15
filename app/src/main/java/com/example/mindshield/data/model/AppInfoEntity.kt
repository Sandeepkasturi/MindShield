package com.example.mindshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_info")
data class AppInfoEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconUri: String?, // Store icon as URI or resource name
    val category: String, // Store as String for Room compatibility
    val usageTime: Long // in milliseconds
) 