package com.example.mindshield.data.model

data class AppSession(
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val duration: Long,
    val isDistracting: Boolean
) 