package com.example.mindshield.ui.activity

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.mindshield.ui.theme.MindShieldTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DistractionOverlayActivity : ComponentActivity() {
    
    private var packageName: String = ""
    private var appName: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val blockDurationMs = 300000L // 5 minutes
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get app details from intent
        packageName = intent.getStringExtra("package_name") ?: ""
        appName = intent.getStringExtra("app_name") ?: "Unknown App"
        
        // Set up window for overlay
        setupOverlayWindow()
        
        setContent {
            MindShieldTheme {
                DistractionOverlayScreen(
                    appName = appName,
                    onTimeUp = { finish() }
                )
            }
        }
        
        // Auto-finish after 5 minutes
        handler.postDelayed({
            finish()
        }, blockDurationMs)
    }
    
    private fun setupOverlayWindow() {
        // Make the activity overlay-style
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // Set window to be transparent
        window.statusBarColor = Color.Transparent.value.toInt()
        window.navigationBarColor = Color.Transparent.value.toInt()
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

@Composable
fun DistractionOverlayScreen(
    appName: String,
    onTimeUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var timeRemaining by remember { mutableStateOf(300) } // 5 minutes in seconds
    var showContent by remember { mutableStateOf(false) }
    
    // Animate content appearance
    LaunchedEffect(Unit) {
        delay(500)
        showContent = true
    }
    
    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
        onTimeUp()
    }
    
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        // Animated background pattern
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(1000)) + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Warning icon with pulse animation
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color.Red.copy(alpha = pulseAlpha),
                            shape = CircleShape
                        )
                        .padding(16.dp),
                    tint = Color.White
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Main message
                Text(
                    text = "App Temporarily Blocked",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "You are now using distracting app for long time,\nkindly visit after 5 minutes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // App name
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Red.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Countdown timer
                Text(
                    text = formatTime(timeRemaining),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Time remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Progress indicator
                LinearProgressIndicator(
                    progress = timeRemaining / 300f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Additional info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "This app will be available again when the timer expires",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
} 