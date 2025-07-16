package com.example.mindshield.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Keep this import
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mindshield.ui.viewmodel.SettingsViewModel
import com.example.mindshield.ui.viewmodel.AppInfo
import com.example.mindshield.data.model.AppTimer
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
// Removed the duplicate import for Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.Image
import android.graphics.drawable.Drawable
import android.content.Context
import com.example.mindshield.util.ContentAwareServiceManager
import android.net.Uri

// Local data class for settings app info with icon
private data class SettingsAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    var isDistracting: Boolean
)

@Composable
fun AppTimerCard(
    timer: AppTimer,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onUpdateLimit: (Int) -> Unit
) {
    var showLimitDialog by remember { mutableStateOf(false) }
    var tempLimit by remember { mutableStateOf(timer.dailyLimitMinutes) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = timer.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Daily limit: ${timer.dailyLimitMinutes} minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (timer.currentUsageMinutes > 0) {
                        Text(
                            text = "Used today: ${timer.currentUsageMinutes} minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = timer.isEnabled,
                        onCheckedChange = { onToggleEnabled() }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete timer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Limit (minutes)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showLimitDialog = true }) {
                    Text("${timer.dailyLimitMinutes} min")
                }
            }
        }
    }
    
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Set Daily Limit") },
            text = {
                Column {
                    Text("Set daily time limit for ${timer.appName}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = tempLimit.toFloat(),
                        onValueChange = { tempLimit = it.toInt() },
                        valueRange = 1f..160f, // 1 minute to 160 minutes
                        steps = 159
                    )
                    Text("${tempLimit} minutes", style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateLimit(tempLimit)
                        showLimitDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val backgroundColor = Color(0xFF383737)
    val textColor = MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var showAppTimerDialog by remember { mutableStateOf(false) }
    // Content-aware detection toggle
    val contentAwareEnabled by viewModel.contentAwareEnabled.collectAsState()
    val appTimerEnabled by viewModel.appTimerEnabled.collectAsState()
    val appTimers by viewModel.appTimers.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKeyEdit by remember { mutableStateOf(false) }
    // Cache all apps on first composition
    val allApps = remember {
        context.packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                val appName = context.packageManager.getApplicationLabel(it).toString()
                val icon = try { context.packageManager.getApplicationIcon(it.packageName) } catch (e: Exception) { null }
                AppInfo(
                    packageName = it.packageName,
                    appName = appName,
                    isDistracting = uiState.distractingApps.any { d -> d.packageName == it.packageName && d.isDistracting }
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
    val appsWithStatus = allApps.map { app ->
        app.copy(isDistracting = uiState.distractingApps.any { d -> d.packageName == app.packageName && d.isDistracting })
    }
    CompositionLocalProvider(LocalContentColor provides textColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground // Ensure text color is visible on background
            )
            // User Profile
            OutlinedTextField(
                value = uiState.userName,
                onValueChange = { viewModel.updateUserName(it) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    // Use MaterialTheme's color scheme for consistency
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            // Gemini API Key Section
            Spacer(modifier = Modifier.height(8.dp))
            Text("Gemini API Key", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            if (!showApiKeyEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (geminiApiKey.isNotBlank()) "••••••••••••••••••••" else "No API key set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    TextButton(onClick = {
                        apiKeyInput = geminiApiKey
                        showApiKeyEdit = true
                    }) {
                        Text(if (geminiApiKey.isBlank()) "Add" else "Edit")
                    }
                }
            } else {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Enter Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        showApiKeyEdit = false
                    }) { Text("Cancel") }
                    TextButton(onClick = {
                        viewModel.updateGeminiApiKey(apiKeyInput)
                        showApiKeyEdit = false
                    }) { Text("Save") }
                }
            }
            // Monitoring toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Monitoring", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Track app usage and detect distractions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                Switch(
                    checked = uiState.isMonitoringEnabled,
                    onCheckedChange = { viewModel.toggleMonitoring() }
                )
            }
            // Alert delay
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Alert Delay", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("${uiState.alertDelayMinutes} min", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Slider(
                value = uiState.alertDelayMinutes.toFloat(),
                onValueChange = { viewModel.updateAlertDelay(it.toInt()) },
                valueRange = 1f..30f,
                steps = 29
            )
            // Alert sound toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Alert Sound", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = uiState.alertSoundEnabled,
                    onCheckedChange = { viewModel.toggleAlertSound() }
                )
            }
            // Vibration toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Vibration", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = uiState.vibrationEnabled,
                    onCheckedChange = { viewModel.toggleVibration() }
                )
            }
            // Focus mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Focus Mode", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = uiState.focusModeEnabled,
                    onCheckedChange = { viewModel.toggleFocusMode() }
                )
            }
            if (uiState.focusModeEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Focus Duration", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("${uiState.focusModeDuration} min", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
                Slider(
                    value = uiState.focusModeDuration.toFloat(),
                    onValueChange = { viewModel.updateFocusModeDuration(it.toInt()) },
                    valueRange = 15f..120f,
                    steps = 21
                )
            }
            // Distraction Wish List Apps (user-selected distracting apps)
            Text("Distraction Wish List Apps", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = "Add App")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add App to Distraction List")
            }
            Spacer(modifier = Modifier.height(8.dp))
            uiState.distractingApps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.appName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    Switch(
                        checked = app.isDistracting,
                        onCheckedChange = { viewModel.updateAppDistractionStatus(context, app.packageName, !app.isDistracting) }
                    )
                }
            }
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Add App to Distraction List") },
                    text = {
                        Box(Modifier.heightIn(max = 400.dp)) {
                            androidx.compose.foundation.lazy.LazyColumn {
                                items(appsWithStatus) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateAppDistractionStatus(context, app.packageName, true)
                                                showDialog = false
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(app.appName, modifier = Modifier.weight(1f))
                                        if (app.isDistracting) {
                                            Icon(Icons.Default.Check, contentDescription = "Already distracting", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDialog = false }) { Text("Close") }
                    }
                )
            }
            // Content-Aware Detection toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Content-Aware Detection", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = contentAwareEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setContentAwareEnabled(enabled)
                        if (enabled) {
                            // Prompt user to enable accessibility service
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                )
            }
            
            // App Timer Section
            Text("App Timer Settings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Set daily time limits for apps to prevent overuse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            
            // App Timer toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable App Timers", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Set daily limits for app usage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                Switch(
                    checked = appTimerEnabled,
                    onCheckedChange = { viewModel.setAppTimerEnabled(it) }
                )
            }
            
            if (appTimerEnabled) {
                // Add App Timer Button
                Button(
                    onClick = { showAppTimerDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add App Timer")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add App Timer")
                }
                
                // Display existing app timers
                appTimers.forEach { timer ->
                    AppTimerCard(
                        timer = timer,
                        onToggleEnabled = { viewModel.toggleAppTimerEnabled(timer) },
                        onDelete = { viewModel.deleteAppTimer(timer) },
                        onUpdateLimit = { newLimit ->
                            viewModel.updateAppTimer(timer.copy(dailyLimitMinutes = newLimit))
                        }
                    )
                }
            }
            
            // App Timer Selection Dialog
            if (showAppTimerDialog) {
                var selectedLimit by remember { mutableStateOf(60) }
                var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
                
                AlertDialog(
                    onDismissRequest = { showAppTimerDialog = false },
                    title = { Text("Add App Timer") },
                    text = {
                        Column {
                            Text("Select an app to set a daily time limit:")
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(Modifier.heightIn(max = 300.dp)) {
                                LazyColumn {
                                    items(appsWithStatus.filter { app ->
                                        !appTimers.any { it.packageName == app.packageName }
                                    }) { app ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedApp = app
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(app.appName, modifier = Modifier.weight(1f))
                                            if (selectedApp?.packageName == app.packageName) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                            if (selectedApp != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Daily Limit (minutes):")
                                Slider(
                                    value = selectedLimit.toFloat(),
                                    onValueChange = { selectedLimit = it.toInt() },
                                    valueRange = 1f..160f, // 1 minute to 160 minutes
                                    steps = 159
                                )
                                Text("${selectedLimit} minutes", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedApp?.let { app ->
                                    viewModel.addAppTimer(app.packageName, app.appName, selectedLimit)
                                }
                                showAppTimerDialog = false
                            },
                            enabled = selectedApp != null
                        ) {
                            Text("Add Timer")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAppTimerDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Data management
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Export Data", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Button(onClick = { viewModel.exportData() }) {
                    Text("Export")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Clear Data", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Button(onClick = { viewModel.clearData() }) {
                    Text("Clear")
                }
            }
            // About
            Text("About", style = MaterialTheme.typography.bodyLarge)
            Text("MindShield v${uiState.appVersion} (${uiState.buildNumber})", style = MaterialTheme.typography.bodySmall)
            Text("MindShield helps you focus by monitoring your app usage, alerting you to distractions, and providing actionable analytics for digital wellness.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Designed and Developed by ", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Sandeep Kasturi",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sandeepkasturi"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}