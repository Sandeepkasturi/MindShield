package com.example.mindshield.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mindshield.ui.component.MetricCard
import com.example.mindshield.data.model.AppSession
import com.example.mindshield.data.model.AppCategory
import com.example.mindshield.data.model.AppInfo
import com.example.mindshield.ui.viewmodel.HomeViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.mindshield.util.OverlayPermissionHelper
import androidx.core.graphics.drawable.toBitmap
import com.example.mindshield.util.UsageStatsPermissionHelper
import android.content.pm.PackageManager

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val recentApps by viewModel.recentApps.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val todayOverview by viewModel.todayOverview.collectAsState()
    val realTimeStats by viewModel.realTimeStats.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val currentAppInfo by viewModel.currentAppInfo.collectAsState()
    val userName by viewModel.userName.collectAsState()

    val context = LocalContext.current
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showUsageStatsDialog by remember { mutableStateOf(false) }
    var checkedOnce by remember { mutableStateOf(false) }

    // Start real-time app usage polling when the screen is displayed
    LaunchedEffect(Unit) {
        viewModel.startRecentAppsPolling(context)
    }

    // Check usage stats permission
    LaunchedEffect(Unit) {
        if (!checkedOnce && !UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            showUsageStatsDialog = true
            checkedOnce = true
        }
    }

    // Launcher to handle result from settings
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (OverlayPermissionHelper.hasOverlayPermission(context)) {
            showOverlayDialog = false
        } else {
            showOverlayDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!checkedOnce && !OverlayPermissionHelper.hasOverlayPermission(context)) {
            showOverlayDialog = true
            checkedOnce = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Personalized greeting
            if (userName.isNotBlank()) {
                Text(
                    text = getGreetingForTime(userName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        item {
            MonitoringStatusCard(
                isMonitoring = isMonitoring,
                onToggleMonitoring = { viewModel.toggleMonitoring() }
            )
        }

        item {
            if (currentSession != null) {
                DistractionAlertCard(
                    session = currentSession!!,
                    userName = userName,
                    onAcknowledge = { viewModel.acknowledgeDistraction() },
                    onDismiss = { viewModel.dismissDistraction() }
                )
            }
        }

        // Real-time stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Usage Time",
                    value = realTimeStats?.let { formatMillisToHourMin(it.usageTime) } ?: "-",
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                MetricCard(
                    title = "Distractions",
                    value = realTimeStats?.distractions?.toString() ?: "-",
                    icon = Icons.Default.Warning,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Recents
        item {
            Text(
                text = "Recent Apps",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (currentAppInfo != null) {
            item {
                AppInfoRow(currentAppInfo!!, isCurrent = true)
            }
        }
        items(recentApps.filter { it.packageName != currentAppInfo?.packageName }) { app ->
            AppInfoRow(app)
        }

        // Top Apps
        item {
            Text(
                text = "Top Apps",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        items(topApps) { app ->
            AppInfoRow(app)
        }

        // Today's Overview
        item {
            Text(
                text = "Today's Overview",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            todayOverview?.let { overview ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewCategoryList("Productive", overview.productiveApps, AppCategory.PRODUCTIVE)
                    OverviewCategoryList("Unproductive", overview.unproductiveApps, AppCategory.UNPRODUCTIVE)
                    OverviewCategoryList("Neutral", overview.neutralApps, AppCategory.NEUTRAL)
                }
            }
        }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Display Over Other Apps") },
            text = {
                Text("MindShield needs permission to show distraction alerts over other apps.\n\nPlease enable 'Display over other apps' permission in the next screen.")
            },
            confirmButton = {
                TextButton(onClick = {
                    overlayPermissionLauncher.launch(
                        OverlayPermissionHelper.createOverlayPermissionIntent(context)
                    )
                }) { Text("OPEN SETTINGS") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) { Text("CANCEL") }
            }
        )
    }

    if (showUsageStatsDialog) {
        AlertDialog(
            onDismissRequest = { showUsageStatsDialog = false },
            title = { Text("Usage Access Permission") },
            text = {
                Text("MindShield needs permission to access usage statistics to show your real-time app usage data.\n\nPlease enable 'Usage access' permission in the next screen.")
            },
            confirmButton = {
                TextButton(onClick = {
                    UsageStatsPermissionHelper.requestUsageStatsPermission(context)
                    showUsageStatsDialog = false
                }) { Text("OPEN SETTINGS") }
            },
            dismissButton = {
                TextButton(onClick = { showUsageStatsDialog = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
fun MonitoringStatusCard(
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isMonitoring) "Monitoring Active" else "Monitoring Inactive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isMonitoring)
                        "MindShield is protecting your focus"
                    else
                        "Tap to start monitoring",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isMonitoring,
                onCheckedChange = { onToggleMonitoring() }
            )
        }
    }
}

@Composable
fun DistractionAlertCard(
    session: AppSession,
    userName: String = "",
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Distraction Detected!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val alertName = if (userName.isNotBlank()) userName else "there"
            Text(
                text = "Hey $alertName, you opened ${session.appName}, which is a distracting app.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Acknowledge")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun formatMillisToHourMin(millis: Long): String {
    val totalMinutes = millis / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun AppInfoRow(app: AppInfo, isCurrent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(formatMillisToHourMin(app.usageTime), style = MaterialTheme.typography.bodyMedium)
        if (isCurrent) {
            Text("  (Now)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategoryChip(category: AppCategory) {
    val color = when (category) {
        AppCategory.PRODUCTIVE -> MaterialTheme.colorScheme.primary
        AppCategory.UNPRODUCTIVE -> MaterialTheme.colorScheme.error
        AppCategory.NEUTRAL -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = category.name.lowercase().replaceFirstChar { it.uppercase() },
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun OverviewCategoryRow(title: String, time: Long, apps: List<String>, category: AppCategory) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(formatMillisToHourMin(time), modifier = Modifier.weight(1f))
        CategoryChip(category)
    }
    if (apps.isNotEmpty()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { packageName ->
                val pm = context.packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
                val appIcon = try {
                    pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
                } catch (e: Exception) { null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = appName,
                            modifier = Modifier.size(20.dp) // 5dp is too small, 20dp is standard
                        )
                    } else {
                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(appName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun getGreetingForTime(userName: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Hello"
    }
    return if (userName.isNotBlank()) "$greeting $userName" else greeting
}

@Composable
fun OverviewCategoryList(title: String, apps: List<String>, category: AppCategory) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        if (apps.isEmpty()) {
            Text("No apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            apps.forEach { packageName ->
                val pm = context.packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) { packageName }
                val appIcon = try {
                    pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
                } catch (e: Exception) { null }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    if (appIcon != null) {
                        Image(bitmap = appIcon, contentDescription = appName, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(appName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
} 