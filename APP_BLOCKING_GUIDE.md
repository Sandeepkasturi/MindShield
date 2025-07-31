# MindShield App Blocking System Guide

## Overview

The MindShield app blocking system provides comprehensive app usage monitoring and blocking functionality. It combines **UsageStatsManager** for accurate usage tracking and **AccessibilityService** for effective app blocking.

## Key Features

- ✅ **Accurate Usage Tracking**: Uses Android's UsageStatsManager for precise app usage monitoring
- ✅ **Elegant Blocking**: Gently redirects users to home screen when limits are reached
- ✅ **Daily Limits**: Resets usage counters daily at midnight
- ✅ **Gentle Warnings**: Shows non-intrusive notifications at 50% usage
- ✅ **Background Monitoring**: Works even when the app is closed
- ✅ **Permission Management**: Handles all required permissions automatically
- ✅ **User-Friendly**: No jarring overlays or interruptions

## System Architecture

### Core Components

1. **AppUsageTracker** - Interfaces with UsageStatsManager for accurate usage data
2. **AppTimerService** - Manages app timing and blocking logic
3. **AppBlockingService** - AccessibilityService for real-time app monitoring
4. **AppBlockingManager** - High-level API for easy integration
5. **AppBlockingHelper** - Permission management and system checks

### Data Flow

```
App Usage → UsageStatsManager → AppUsageTracker → AppTimerService → AppBlockingService → Block Overlay
```

## Setup Instructions

### 1. Permissions Required

The system requires two main permissions:

#### Usage Stats Permission
```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" 
    tools:ignore="ProtectedPermissions" />
```

#### Accessibility Service Permission
```xml
<uses-permission android:name="android.permission.ACCESSIBILITY_SERVICE" />
```

### 2. AndroidManifest.xml Configuration

Add the AppBlockingService to your manifest:

```xml
<service
    android:name=".service.AppBlockingService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/app_blocking_accessibility_config" />
</service>
```

### 3. Accessibility Service Configuration

Create `res/xml/app_blocking_accessibility_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/app_blocking_accessibility_description"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:settingsActivity="com.example.mindshield.MainActivity" />
```

## Usage Examples

### Basic Setup

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var appBlockingManager: AppBlockingManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize app blocking system
        initializeAppBlocking()
    }
    
    private fun initializeAppBlocking() {
        // Check if app blocking is ready
        if (!appBlockingManager.isReady()) {
            appBlockingManager.requestPermissions()
            return
        }
        
        appBlockingManager.initialize()
    }
}
```

### Adding Apps to Monitor

```kotlin
// Add Instagram with 30-minute daily limit
appBlockingManager.addAppTimer(
    packageName = "com.instagram.android",
    appName = "Instagram",
    dailyLimitMinutes = 30
)

// Add TikTok with 20-minute daily limit
appBlockingManager.addAppTimer(
    packageName = "com.zhiliaoapp.musically",
    appName = "TikTok",
    dailyLimitMinutes = 20
)

// Add YouTube with 60-minute daily limit
appBlockingManager.addAppTimer(
    packageName = "com.google.android.youtube",
    appName = "YouTube",
    dailyLimitMinutes = 60
)
```

### Managing App Limits

```kotlin
// Update an app's daily limit
appBlockingManager.updateAppLimit("com.instagram.android", 15)

// Enable/disable monitoring for an app
appBlockingManager.setAppMonitoringEnabled("com.zhiliaoapp.musically", false)

// Remove an app from monitoring
appBlockingManager.removeAppTimer("com.zhiliaoapp.musically")
```

### Getting Usage Information

```kotlin
// Get current usage for an app (in milliseconds)
val instagramUsage = appBlockingManager.getAppUsage("com.instagram.android")
val usageSeconds = instagramUsage / 1000

// Check if an app is currently blocked
val isBlocked = appBlockingManager.isAppBlocked("com.instagram.android")

// Force check if an app should be blocked
appBlockingManager.forceCheckApp("com.instagram.android")
```

### Resetting Usage

```kotlin
// Reset usage for a specific app
appBlockingManager.resetAppUsage("com.instagram.android")
```

## API Reference

### AppBlockingManager

#### Core Methods

- `initialize()` - Initialize the app blocking system
- `isReady()` - Check if all permissions are granted
- `requestPermissions()` - Request missing permissions
- `getMissingPermissions()` - Get list of missing permissions

#### App Management

- `addAppTimer(packageName, appName, dailyLimitMinutes)` - Add app to monitoring
- `removeAppTimer(packageName)` - Remove app from monitoring
- `updateAppLimit(packageName, newLimitMinutes)` - Update daily limit
- `setAppMonitoringEnabled(packageName, enabled)` - Enable/disable monitoring

#### Usage Tracking

- `getAppUsage(packageName)` - Get current usage time
- `resetAppUsage(packageName)` - Reset usage counter
- `isAppBlocked(packageName)` - Check if app is blocked
- `forceCheckApp(packageName)` - Force check and block if needed

#### Data Access

- `getAllMonitoredApps()` - Get all monitored apps
- `getEnabledMonitoredApps()` - Get enabled monitored apps

### AppUsageTracker

#### Usage Methods

- `getAppUsageTime(context, packageName)` - Get daily usage for app
- `getAppUsageTimeRange(context, packageName, startTime, endTime)` - Get usage for time range
- `getCurrentActiveApp(context)` - Get currently active app
- `getAppsUsedToday(context)` - Get all apps used today

### AppBlockingHelper

#### Permission Methods

- `checkAllPermissions(context)` - Check if all permissions granted
- `requestAllPermissions(context)` - Request all permissions
- `getMissingPermissions(context)` - Get missing permissions
- `isAccessibilityServiceEnabled(context)` - Check accessibility service
- `isAppBlockingReady(context)` - Check if system is ready

## User Experience

### Permission Requests

The system automatically handles permission requests:

1. **Usage Stats Permission**: Opens system settings for usage access
2. **Accessibility Service**: Opens accessibility settings
3. **Overlay Permission**: Opens overlay permission settings

### Blocking Behavior

When an app reaches its time limit:

1. **Immediate Block**: App is blocked as soon as it's opened
2. **Elegant Redirect**: User is gently sent to home screen using AccessibilityService
3. **No Intrusive Overlays**: Clean, non-jarring user experience
4. **Persistent Block**: App remains blocked until midnight reset

### User Experience

- **Gentle Blocking**: Uses `performGlobalAction(GLOBAL_ACTION_HOME)` for smooth transitions
- **Half-time Warning**: Gentle notification at 50% of daily limit
- **No Interruptions**: No full-screen overlays or jarring popups
- **Seamless Integration**: Works like a natural part of the Android system

## Troubleshooting

### Common Issues

#### App Not Being Blocked

1. Check if permissions are granted:
   ```kotlin
   val missingPermissions = appBlockingManager.getMissingPermissions()
   ```

2. Verify accessibility service is enabled:
   ```kotlin
   val isEnabled = AppBlockingHelper.isAccessibilityServiceEnabled(context)
   ```

3. Check if app is in monitored list:
   ```kotlin
   val monitoredApps = appBlockingManager.getAllMonitoredApps()
   ```

#### Usage Not Accurate

1. Sync with system usage:
   ```kotlin
   appBlockingManager.forceCheckApp(packageName)
   ```

2. Check UsageStatsManager permission:
   ```kotlin
   val hasPermission = UsageStatsPermissionHelper.hasUsageStatsPermission(context)
   ```

#### Service Not Starting

1. Check battery optimization settings
2. Verify foreground service permissions
3. Ensure app is not being killed by system

### Debug Logging

Enable debug logging to troubleshoot issues:

```kotlin
// Check system status
Log.d("AppBlocking", "System ready: ${appBlockingManager.isReady()}")
Log.d("AppBlocking", "Missing permissions: ${appBlockingManager.getMissingPermissions()}")

// Check app status
Log.d("AppBlocking", "App usage: ${appBlockingManager.getAppUsage(packageName)}")
Log.d("AppBlocking", "App blocked: ${appBlockingManager.isAppBlocked(packageName)}")
```

## Best Practices

### Performance

1. **Periodic Checks**: System checks every 30 seconds for efficiency
2. **Background Processing**: Uses coroutines for non-blocking operations
3. **Memory Management**: Properly manages service lifecycle

### User Experience

1. **Clear Permissions**: Explain why each permission is needed
2. **Graceful Degradation**: System works even with partial permissions
3. **Customizable Limits**: Allow users to set their own limits
4. **Visual Feedback**: Clear blocking overlays with helpful messages

### Security

1. **Local Storage**: All data stored locally on device
2. **No Data Collection**: System doesn't collect or transmit usage data
3. **Permission Scoping**: Only requests necessary permissions

## Integration with Existing Code

The app blocking system is designed to work alongside your existing distraction detection system. It can be integrated into:

- **Settings Screen**: Add app timer management UI
- **Analytics Screen**: Show usage statistics
- **Home Screen**: Display current app status
- **Notification System**: Send usage alerts

## Example Integration

```kotlin
// In your SettingsViewModel
class SettingsViewModel @Inject constructor(
    private val appBlockingManager: AppBlockingManager
) : ViewModel() {
    
    val monitoredApps = appBlockingManager.getAllMonitoredApps()
    
    fun addAppTimer(packageName: String, appName: String, limitMinutes: Int) {
        appBlockingManager.addAppTimer(packageName, appName, limitMinutes)
    }
    
    fun updateAppLimit(packageName: String, newLimit: Int) {
        appBlockingManager.updateAppLimit(packageName, newLimit)
    }
}
```

This comprehensive app blocking system provides everything you need to implement effective app usage monitoring and blocking in your MindShield app! 