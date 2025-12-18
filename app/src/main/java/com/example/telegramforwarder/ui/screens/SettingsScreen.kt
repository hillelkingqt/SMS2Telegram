package com.example.telegramforwarder.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch

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

    var tokenInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var newGeminiKeyInput by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }

    // Initialize inputs with stored values
    LaunchedEffect(botToken, chatId) {
        if (botToken != null) tokenInput = botToken!!
        if (chatId != null) chatInput = chatId!!
    }

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
                ),
                modifier = Modifier.statusBarsPadding()
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
