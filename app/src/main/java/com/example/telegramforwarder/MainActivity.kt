package com.example.telegramforwarder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.telegramforwarder.data.local.UserPreferences
import com.example.telegramforwarder.services.BotService
import com.example.telegramforwarder.ui.screens.HomeScreen
import com.example.telegramforwarder.ui.screens.LogsScreen
import com.example.telegramforwarder.ui.screens.SettingsScreen
import com.example.telegramforwarder.ui.theme.TelegramForwarderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val userPreferences = remember { UserPreferences(context) }
            val themeMode by userPreferences.themeMode.collectAsState(initial = "system")

            // Check if we should start the service on launch (if verified previously)
            // Logic moved to PermissionWrapper to ensure permissions are granted first.

            TelegramForwarderTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionWrapper {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissions = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    var permissionsGranted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        permissionsGranted = allGranted
        if (allGranted) {
             // Start Service if permissions granted
             val intent = Intent(context, BotService::class.java)
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                 context.startForegroundService(intent)
             } else {
                 context.startService(intent)
             }
        } else {
            // Show rationale? For now just proceed but features might break
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }

    // Just show content always, but maybe show a banner if permissions missing?
    // For simplicity, we wrap content but don't block it.

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
             content()
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogs = { navController.navigate("logs") }
            )
        }
        composable("logs") {
            LogsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
