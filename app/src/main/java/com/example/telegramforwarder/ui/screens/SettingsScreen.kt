package com.example.telegramforwarder.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegramforwarder.data.local.UserPreferences
import com.example.telegramforwarder.data.remote.TelegramRepository
import com.example.telegramforwarder.services.BotService
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { UserPreferences(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val botToken by preferences.botToken.collectAsState(initial = "")
    val chatId by preferences.chatId.collectAsState(initial = "")
    val geminiApiKeys by preferences.geminiApiKeys.collectAsState(initial = emptyList())
    val isSmsEnabled by preferences.isSmsEnabled.collectAsState(initial = true)
    val isEmailEnabled by preferences.isEmailEnabled.collectAsState(initial = true)

    // New Features
    val isMissedCallEnabled by preferences.isMissedCallEnabled.collectAsState(initial = false)
    val isBatteryNotifyEnabled by preferences.isBatteryNotifyEnabled.collectAsState(initial = false)
    val batteryLowThreshold by preferences.batteryLowThreshold.collectAsState(initial = 20f)
    val batteryHighThreshold by preferences.batteryHighThreshold.collectAsState(initial = 90f)
    val isBotPollingEnabled by preferences.isBotPollingEnabled.collectAsState(initial = false)

    var tokenInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var newGeminiKeyInput by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }

    // Initialize inputs with stored values
    LaunchedEffect(botToken, chatId) {
        if (botToken != null) tokenInput = botToken!!
        if (chatId != null) chatInput = chatId!!
    }

    // Trigger BotService when verification is confirmed (or whenever preferences change that might enable it)
    // Actually, we should trigger it after "Save & Verify" succeeds.

    // Animation state
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        // Handle keyboard overlap
        contentWindowInsets = WindowInsets.ime
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // Extra padding for scroll
            ) {
                // --- Forwarding Options ---
                item {
                    AnimatedEntry(visibleState, 0) {
                        SettingsSectionTitle("Forwarding Options")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 100) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsSwitchCard(
                                title = "Forward SMS",
                                subtitle = "Intercept and forward incoming SMS",
                                icon = Icons.Default.Sms,
                                checked = isSmsEnabled,
                                onCheckedChange = { scope.launch { preferences.setSmsEnabled(it) } }
                            )
                            SettingsSwitchCard(
                                title = "Forward Emails",
                                subtitle = "Intercept and forward Gmail notifications",
                                icon = Icons.Default.Email,
                                checked = isEmailEnabled,
                                onCheckedChange = { scope.launch { preferences.setEmailEnabled(it) } }
                            )
                             SettingsSwitchCard(
                                title = "Missed Call Notifications",
                                subtitle = "Notify when a call is missed or rejected",
                                icon = Icons.Default.PhoneMissed,
                                checked = isMissedCallEnabled,
                                onCheckedChange = { scope.launch { preferences.setMissedCallEnabled(it) } }
                            )
                        }
                    }
                }

                // --- Battery Options ---
                 item {
                    AnimatedEntry(visibleState, 150) {
                        SettingsSectionTitle("Battery Monitoring")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 180) {
                         Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsSwitchCard(
                                title = "Battery Alerts",
                                subtitle = "Notify on low/high battery levels",
                                icon = Icons.Default.BatteryAlert,
                                checked = isBatteryNotifyEnabled,
                                onCheckedChange = { scope.launch { preferences.setBatteryNotifyEnabled(it) } }
                            )

                            if (isBatteryNotifyEnabled) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Low Battery Threshold: ${batteryLowThreshold.toInt()}%", fontWeight = FontWeight.SemiBold)
                                        Slider(
                                            value = batteryLowThreshold,
                                            onValueChange = { scope.launch { preferences.setBatteryLowThreshold(it) } },
                                            valueRange = 0f..50f,
                                            steps = 49
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text("High Battery Threshold: ${batteryHighThreshold.toInt()}%", fontWeight = FontWeight.SemiBold)
                                        Slider(
                                            value = batteryHighThreshold,
                                            onValueChange = { scope.launch { preferences.setBatteryHighThreshold(it) } },
                                            valueRange = 50f..100f,
                                            steps = 49
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Gemini Configuration ---
                item {
                    AnimatedEntry(visibleState, 200) {
                        SettingsSectionTitle("Gemini AI Configuration")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 250) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "API Keys",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Add multiple keys for redundancy. The system will rotate through them if one fails.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // List of existing keys
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    geminiApiKeys.forEachIndexed { index, key ->
                                        GeminiKeyItem(
                                            index = index,
                                            key = key,
                                            onDelete = {
                                                val newList = geminiApiKeys.toMutableList().apply { removeAt(index) }
                                                scope.launch { preferences.saveGeminiKeys(newList) }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Add new key
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BeautifulTextField(
                                        value = newGeminiKeyInput,
                                        onValueChange = { newGeminiKeyInput = it },
                                        label = "New Gemini API Key",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (newGeminiKeyInput.isNotBlank()) {
                                                val newList = geminiApiKeys + newGeminiKeyInput.trim()
                                                scope.launch { preferences.saveGeminiKeys(newList) }
                                                newGeminiKeyInput = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Telegram Configuration ---
                item {
                    AnimatedEntry(visibleState, 300) {
                        SettingsSectionTitle("Telegram Configuration")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 400) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SettingsSwitchCard(
                                title = "Remote Control Bot",
                                subtitle = "Allow sending SMS via Telegram commands (consumes battery)",
                                icon = Icons.Default.SmartToy,
                                checked = isBotPollingEnabled,
                                onCheckedChange = { scope.launch { preferences.setBotPollingEnabled(it) } }
                            )

                            BeautifulTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it },
                                label = "Bot Token"
                            )
                            BeautifulTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                label = "Chat ID"
                            )
                        }
                    }
                }

                item {
                    AnimatedEntry(visibleState, 500) {
                        Button(
                            onClick = {
                                scope.launch {
                                    preferences.saveBotToken(tokenInput)
                                    preferences.saveChatId(chatInput)

                                    isTestingConnection = true
                                    val response = TelegramRepository.sendMessage(
                                        botToken = tokenInput.trim(),
                                        chatId = chatInput.trim(),
                                        message = "DONE"
                                    )
                                    isTestingConnection = false

                                    if (response.success) {
                                        snackbarHostState.showSnackbar("Saved & Verified!")

                                        // Start Polling Service on successful verify
                                        val serviceIntent = Intent(context, BotService::class.java)
                                        context.startForegroundService(serviceIntent)

                                    } else {
                                        snackbarHostState.showSnackbar("Failed: ${response.message}")
                                    }
                                }
                            },
                            enabled = !isTestingConnection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save & Verify Connection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // --- Appearance ---
                item {
                    AnimatedEntry(visibleState, 550) {
                        SettingsSectionTitle("Appearance")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 580) {
                        val themeMode by preferences.themeMode.collectAsState(initial = "system")
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ThemeOption(
                                        title = "System",
                                        icon = Icons.Default.PhoneAndroid,
                                        isSelected = themeMode == "system",
                                        onClick = { scope.launch { preferences.saveThemeMode("system") } },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ThemeOption(
                                        title = "Light",
                                        icon = Icons.Default.LightMode,
                                        isSelected = themeMode == "light",
                                        onClick = { scope.launch { preferences.saveThemeMode("light") } },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ThemeOption(
                                        title = "Dark",
                                        icon = Icons.Default.DarkMode,
                                        isSelected = themeMode == "dark",
                                        onClick = { scope.launch { preferences.saveThemeMode("dark") } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Statistics ---
                item {
                    AnimatedEntry(visibleState, 575) {
                        SettingsSectionTitle("Statistics")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 590) {
                        val database = remember { com.example.telegramforwarder.data.local.AppDatabase.getDatabase(context) }
                        val totalMessages by database.messageDao().getMessageCount().collectAsState(initial = 0)
                        val smsCount by database.messageDao().getMessageCountByType("SMS").collectAsState(initial = 0)
                        val emailCount by database.messageDao().getMessageCountByType("EMAIL").collectAsState(initial = 0)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Message Statistics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatisticCard(
                                        label = "Total",
                                        value = totalMessages.toString(),
                                        icon = Icons.Default.Message,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatisticCard(
                                        label = "SMS",
                                        value = smsCount.toString(),
                                        icon = Icons.Default.Sms,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatisticCard(
                                        label = "Email",
                                        value = emailCount.toString(),
                                        icon = Icons.Default.Email,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Diagnostics ---
                item {
                    AnimatedEntry(visibleState, 600) {
                        SettingsSectionTitle("Diagnostics")
                    }
                }

                item {
                    AnimatedEntry(visibleState, 700) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToLogs() },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Notes, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("System Logs", fontWeight = FontWeight.Bold)
                                    Text("View debugging logs", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }

                item {
                    AnimatedEntry(visibleState, 750) {
                        val database = remember { com.example.telegramforwarder.data.local.AppDatabase.getDatabase(context) }
                        var showClearDialog by remember { mutableStateOf(false) }
                        
                        if (showClearDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showClearDialog = false },
                                title = { Text("Clear Messages") },
                                text = { Text("This will delete all stored messages. This action cannot be undone.") },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            scope.launch {
                                                database.messageDao().deleteAllMessages()
                                                snackbarHostState.showSnackbar("All messages cleared")
                                            }
                                            showClearDialog = false
                                        }
                                    ) {
                                        Text("Clear", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { showClearDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Clear Messages", fontWeight = FontWeight.Bold)
                                    Text("Delete all stored messages", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedEntry(
    visibleState: MutableTransitionState<Boolean>,
    delay: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(
            initialOffsetY = { 50 },
            animationSpec = tween(500, delayMillis = delay, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(500, delayMillis = delay)),
        content = { content() }
    )
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsSwitchCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // Interaction animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeautifulTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            containerColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
}

@Composable
fun GeminiKeyItem(index: Int, key: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "•••• " + key.takeLast(4),
            modifier = Modifier.weight(1f),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatisticCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
