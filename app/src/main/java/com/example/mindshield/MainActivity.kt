package com.example.mindshield

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import com.example.mindshield.ui.screen.AnalyticsScreen
import com.example.mindshield.ui.screen.HomeScreen
import com.example.mindshield.ui.screen.SettingsScreen
import com.example.mindshield.ui.theme.MindShieldTheme
import com.example.mindshield.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import androidx.navigation.compose.currentBackStackEntryAsState
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import com.example.mindshield.R
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
import android.content.Context

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            homeViewModel.onPermissionsGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mindshield_prefs", MODE_PRIVATE)
        
        android.util.Log.d("MainActivity", "onCreate called")
        
        checkAndRequestPermissions()
        
        setContent {
            MindShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MindShieldApp(homeViewModel)
                }
            }
        }
        
        // Start distraction detection when monitoring is enabled
        lifecycleScope.launch {
            homeViewModel.isMonitoring.collect { isMonitoring ->
                if (isMonitoring) {
                    homeViewModel.startDistractionDetection(this@MainActivity)
                } else {
                    homeViewModel.stopDistractionDetection(this@MainActivity)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Mark app as foreground
        prefs.edit().putBoolean("is_foreground", true).apply()
        // Check permissions again when returning from settings
        checkAndRequestPermissions()
        
        // Ensure service is running if permissions are granted
        if (areAllPermissionsGranted()) {
            android.util.Log.d("MainActivity", "Permissions granted, starting service")
            startDistractionMonitorService()
        } else {
            android.util.Log.d("MainActivity", "Permissions not granted yet")
        }
    }

    override fun onPause() {
        super.onPause()
        // Mark app as background
        prefs.edit().putBoolean("is_foreground", false).apply()
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.bindMonitoringService(this)
        checkBatteryOptimization()
    }

    override fun onStop() {
        super.onStop()
        homeViewModel.unbindMonitoringService(this)
    }

    private fun checkAndRequestPermissions() {
        // Regular permissions that can be requested through dialog
        val regularPermissions = arrayOf(
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.VIBRATE
        )

        val permissionsToRequest = regularPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }

        // Check for usage stats permission (requires system settings)
        if (!hasUsageStatsPermission()) {
            showUsageStatsPermissionDialog()
            return
        }

        // Check for overlay permission (requires system settings)
        if (!hasOverlayPermission()) {
            showOverlayPermissionDialog()
            return
        }

        // If all permissions are granted, notify the ViewModel and start services
        if (areAllPermissionsGranted()) {
            android.util.Log.d("MainActivity", "All permissions granted, starting service")
            homeViewModel.onPermissionsGranted()
            startDistractionMonitorService()
        } else {
            android.util.Log.d("MainActivity", "Permissions not granted yet")
        }
    }

    private fun startDistractionMonitorService() {
        android.util.Log.d("MainActivity", "Starting DistractionAppMonitorService")
        val intent = Intent(this, com.example.mindshield.service.DistractionAppMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        android.util.Log.d("MainActivity", "DistractionAppMonitorService started")
    }

    private fun areAllPermissionsGranted(): Boolean {
        val regularPermissions = arrayOf(
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.VIBRATE
        )

        val regularPermissionsGranted = regularPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

        val usageStatsGranted = hasUsageStatsPermission()
        val overlayGranted = hasOverlayPermission()
        val allGranted = regularPermissionsGranted && usageStatsGranted && overlayGranted
        
        android.util.Log.d("MainActivity", "Permission check: regular=$regularPermissionsGranted, usage=$usageStatsGranted, overlay=$overlayGranted, all=$allGranted")
        
        return allGranted
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun showUsageStatsPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Usage Access Permission")
            .setMessage("MindShield needs access to usage statistics to monitor which apps you're using and detect potential distractions.\n\nPlease grant 'Usage access' permission in the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_overlay_permission, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
        val dialog = builder.create()

        dialogView.findViewById<ImageView>(R.id.overlay_permission_icon)?.setImageResource(R.drawable.ic_launcher_foreground)
        dialogView.findViewById<TextView>(R.id.overlay_permission_title)?.text = "Enable Distraction Protection"
        dialogView.findViewById<TextView>(R.id.overlay_permission_message)?.text = "MindShield needs permission to display gentle reminders and focus overlays on top of other apps. This helps you take healthy breaks and avoid distractions while using apps like YouTube or social media.\n\nWe never collect or share your screen content."

        dialogView.findViewById<Button>(R.id.overlay_permission_open)?.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.overlay_permission_learn_more)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("How MindShield Uses Overlays")
                .setMessage("MindShield uses overlays to gently remind you when you're spending too much time on distracting apps. Overlays are only shown on your device and never leave your phone. Your privacy is always protected.")
                .setPositiveButton("OK", null)
                .show()
        }
        dialogView.findViewById<Button>(R.id.overlay_permission_cancel)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            showBatteryOptimizationDialog()
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Allow MindShield to Run in Background")
            .setMessage("To ensure MindShield can monitor distractions and protect your focus, please exclude it from battery optimization. Some devices may stop MindShield if battery optimization is enabled.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

@Composable
fun MindShieldApp(homeViewModel: HomeViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = {
                        if (currentRoute != "home") {
                            navController.navigate("home") {
                                launchSingleTop = true
                                popUpTo("home") { inclusive = false }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == "analytics",
                    onClick = {
                        if (currentRoute != "analytics") {
                            navController.navigate("analytics") {
                                launchSingleTop = true
                                popUpTo("home") { inclusive = false }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                    label = { Text("Analytics") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = {
                        if (currentRoute != "settings") {
                            navController.navigate("settings") {
                                launchSingleTop = true
                                popUpTo("home") { inclusive = false }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen(homeViewModel)
            }
            composable("analytics") {
                AnalyticsScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
} 