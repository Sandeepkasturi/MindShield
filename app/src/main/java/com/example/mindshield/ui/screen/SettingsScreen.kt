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
    // Content-aware detection toggle
    val contentAwareEnabled by viewModel.contentAwareEnabled.collectAsState()
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