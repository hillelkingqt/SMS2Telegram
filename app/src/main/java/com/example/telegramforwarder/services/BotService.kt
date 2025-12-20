package com.example.telegramforwarder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.telegramforwarder.R
import com.example.telegramforwarder.data.local.AppDatabase
import com.example.telegramforwarder.data.local.ContactHelper
import com.example.telegramforwarder.data.local.UserPreferences
import com.example.telegramforwarder.data.remote.TelegramRepository
import com.example.telegramforwarder.data.remote.Update
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BotService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var userPreferences: UserPreferences
    private lateinit var contactHelper: ContactHelper

    // Database
    private lateinit var database: AppDatabase

    // State
    private var isBotPollingEnabled = false
    private var botToken: String? = null
    private var chatId: String? = null
    private var lastUpdateId: Long = 0

    // Battery State
    private var lastBatteryLevel = -1
    private var batteryLowThreshold = 20f
    private var batteryHighThreshold = 90f
    private var isBatteryNotifyEnabled = false
    private var isEnhancedBatteryAlertsEnabled = true
    private var hasNotifiedLow = false
    private var hasNotifiedHigh = false

    // System Event Flags
    private var isNotifyPowerConnected = false
    private var isNotifyPowerDisconnected = false
    private var isNotifyAirplaneModeOn = false
    private var isNotifyAirplaneModeOff = false
    private var isNotifyWifiConnected = false
    private var isNotifyWifiDisconnected = false
    private var isNotifyBluetoothConnected = false
    private var isNotifyBluetoothDisconnected = false

    // Enhanced Battery Logic State
    private var lastReportedLevel = -1
    private var lastReportTime = 0L
    private var isCharging = false

    // Missed Call State
    private var isMissedCallEnabled = false

    // Conversation State: Map<ChatId, State>
    private val conversationStates = mutableMapOf<Long, BotState>()

    // Notification
    private val NOTIFICATION_ID = 999
    private val CHANNEL_ID = "BotServiceChannel"

    // Receivers
    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val wasCharging = isCharging
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                    // Handle Power Connect/Disconnect
                    if (isCharging && !wasCharging) {
                        if (isNotifyPowerConnected) sendTelegramMessage("🔌 <b>Power Connected</b>")
                    } else if (!isCharging && wasCharging) {
                         if (isNotifyPowerDisconnected) sendTelegramMessage("🔌 <b>Power Disconnected</b>")
                    }

                    if (level != -1 && scale != -1) {
                        val pct = (level.toFloat() / scale.toFloat()) * 100f
                        handleBatteryLevel(pct)
                    }
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val isAirplaneModeOn = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
                    if (isAirplaneModeOn && isNotifyAirplaneModeOn) {
                         sendTelegramMessage("✈️ <b>Airplane Mode Enabled</b>")
                    } else if (!isAirplaneModeOn && isNotifyAirplaneModeOff) {
                         sendTelegramMessage("✈️ <b>Airplane Mode Disabled</b>")
                    }
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)
                    if (state == BluetoothAdapter.STATE_CONNECTED) {
                        if (isNotifyBluetoothConnected) sendTelegramMessage("bluetooth <b>Bluetooth Device Connected</b>")
                    } else if (state == BluetoothAdapter.STATE_DISCONNECTED) {
                        if (isNotifyBluetoothDisconnected) sendTelegramMessage("bluetooth <b>Bluetooth Device Disconnected</b>")
                    }
                }
            }
        }
    }

    // Network Callback
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
             val caps = connectivityManager.getNetworkCapabilities(network)
             if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                 if (isNotifyWifiConnected) sendTelegramMessage("📶 <b>WiFi Connected</b>")
             }
        }

        override fun onLost(network: Network) {
             // We can't easily distinguish if lost was wifi unless we track it.
             // But usually if we lose wifi we want to know.
             // To be precise we should track if we were on wifi.
             // Simplification: Just notify lost. Or check if there is another wifi? No.
             // Actually `onLost` is for the specific network.
             // If we want "WiFi Disconnected", we should track the active wifi network.

             // Simple approach: trigger "WiFi Disconnected"
             if (isNotifyWifiDisconnected) sendTelegramMessage("📶 <b>WiFi/Network Disconnected</b>")
        }
    }

    // Call Listener
    private lateinit var telephonyManager: TelephonyManager
    private val phoneStateListener = object : PhoneStateListener() {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var lastIncomingNumber: String? = null

        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            if (!isMissedCallEnabled) return

            // Log.d("BotService", "Call State: $state, Number: $phoneNumber")

            // Simple logic: RINGING -> IDLE = Missed Call (if not OFFHOOK in between)
            // But PhoneStateListener doesn't guarantee sequence perfectly for "Missed" vs "Rejected".
            // However, a simple approximation:
            // If we went RINGING -> IDLE, it's a missed or rejected call.
            // We'll treat it as missed/ended without answer.

            if (lastState == TelephonyManager.CALL_STATE_RINGING && state == TelephonyManager.CALL_STATE_IDLE) {
                 // Forward missed call
                 val number = if (!phoneNumber.isNullOrBlank()) phoneNumber else lastIncomingNumber
                 if (number != null) {
                     handleMissedCall(number)
                 }
            }

            if (state == TelephonyManager.CALL_STATE_RINGING) {
                lastIncomingNumber = phoneNumber
            }

            lastState = state
        }
    }

    override fun onCreate() {
        super.onCreate()
        userPreferences = UserPreferences(this)
        contactHelper = ContactHelper(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        startForeground(NOTIFICATION_ID, createNotification())

        // Register Receivers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(systemEventReceiver, filter)

        // Register Network Callback
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("BotService", "Error registering network callback: ${e.message}")
        }

        // Register Phone Listener
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)

        // Initialize Database
        database = AppDatabase.getDatabase(applicationContext)

        // Start Loops
        startConfigurationObserver()
        startPollingLoop()
        startCleanupLoop()
    }

    private fun startCleanupLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val cutoff = System.currentTimeMillis() - 30 * 60 * 1000 // 30 minutes ago
                    database.logDao().deleteOldLogs(cutoff)
                    database.messageDao().deleteOldMessages(cutoff)
                } catch (e: Exception) {
                    Log.e("BotService", "Error during cleanup: ${e.message}")
                }
                delay(15 * 60 * 1000) // Check every 15 minutes
            }
        }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Bot Service", NotificationManager.IMPORTANCE_MIN).apply {
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Forwarder Bot")
            .setContentText("Listening for commands and events...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun startConfigurationObserver() {
        serviceScope.launch {
            userPreferences.botToken.collect { token ->
                botToken = token
            }
        }
        serviceScope.launch {
            userPreferences.chatId.collect { id ->
                chatId = id
            }
        }
        serviceScope.launch {
            userPreferences.batteryLowThreshold.collect { batteryLowThreshold = it }
        }
        serviceScope.launch {
             userPreferences.batteryHighThreshold.collect { batteryHighThreshold = it }
        }
        serviceScope.launch {
            userPreferences.isBatteryNotifyEnabled.collect { isBatteryNotifyEnabled = it }
        }
        serviceScope.launch {
            userPreferences.isEnhancedBatteryAlertsEnabled.collect { isEnhancedBatteryAlertsEnabled = it }
        }
        serviceScope.launch {
            userPreferences.isMissedCallEnabled.collect { isMissedCallEnabled = it }
        }
        serviceScope.launch {
            userPreferences.isBotPollingEnabled.collect { isBotPollingEnabled = it }
        }

        // New Observers
        serviceScope.launch { userPreferences.isNotifyPowerConnected.collect { isNotifyPowerConnected = it } }
        serviceScope.launch { userPreferences.isNotifyPowerDisconnected.collect { isNotifyPowerDisconnected = it } }
        serviceScope.launch { userPreferences.isNotifyAirplaneModeOn.collect { isNotifyAirplaneModeOn = it } }
        serviceScope.launch { userPreferences.isNotifyAirplaneModeOff.collect { isNotifyAirplaneModeOff = it } }
        serviceScope.launch { userPreferences.isNotifyWifiConnected.collect { isNotifyWifiConnected = it } }
        serviceScope.launch { userPreferences.isNotifyWifiDisconnected.collect { isNotifyWifiDisconnected = it } }
        serviceScope.launch { userPreferences.isNotifyBluetoothConnected.collect { isNotifyBluetoothConnected = it } }
        serviceScope.launch { userPreferences.isNotifyBluetoothDisconnected.collect { isNotifyBluetoothDisconnected = it } }
    }

    private fun handleBatteryLevel(pct: Float) {
        if (!isBatteryNotifyEnabled || botToken == null || chatId == null) return

        val currentLevel = pct.toInt()

        if (isEnhancedBatteryAlertsEnabled) {
            val now = System.currentTimeMillis()

            if (isCharging) {
                 // Charging logic
                 val thresholds = listOf(90, 95, 100)

                 // If we hit a threshold we haven't reported recently (or higher than last reported)

                 if (currentLevel in thresholds && currentLevel > lastReportedLevel) {
                     val text = if (currentLevel >= 95) {
                         "🔋 <b>Battery Charged:</b> $currentLevel%\nIt is recommended to unplug."
                     } else {
                         "🔋 <b>Battery Charged:</b> $currentLevel%"
                     }
                     sendTelegramMessage(text)
                     lastReportedLevel = currentLevel
                     lastReportTime = now
                 } else if (currentLevel == 100) {
                     // Check for repetition
                     if (now - lastReportTime >= 10 * 60 * 1000) { // 10 minutes
                         sendTelegramMessage("🔋 <b>Battery Fully Charged:</b> 100%\nIt is recommended to unplug.")
                         lastReportTime = now
                     }
                 }

                 // If charging, we reset the discharge alert flags if necessary,
                 // but here we just rely on lastReportedLevel.
                 // If we unplug, isCharging becomes false.

            } else {
                // Discharging logic
                // Notify at 20, 15, 10, 5

                // If we are just starting to discharge from 100, lastReportedLevel is 100.

                if (currentLevel != lastReportedLevel) {
                     if (currentLevel == 20 || currentLevel == 15 || currentLevel == 10 || currentLevel == 5) {
                         sendTelegramMessage("⚠️ <b>Battery Low:</b> $currentLevel%")
                         lastReportedLevel = currentLevel
                     }
                }
            }

        } else {
            // Standard Logic

            // Check Low
            if (pct <= batteryLowThreshold) {
                if (!hasNotifiedLow) {
                    sendTelegramMessage("⚠️ <b>Battery Low:</b> ${pct.toInt()}%")
                    hasNotifiedLow = true
                }
            } else {
                hasNotifiedLow = false
            }

            // Check High
            if (pct >= batteryHighThreshold) {
                if (!hasNotifiedHigh) {
                    sendTelegramMessage("🔋 <b>Battery Charged:</b> ${pct.toInt()}%")
                    hasNotifiedHigh = true
                }
            } else {
                hasNotifiedHigh = false
            }
        }

        lastBatteryLevel = currentLevel
    }

    private fun handleMissedCall(number: String) {
        if (botToken == null || chatId == null) return

        serviceScope.launch {
            val name = contactHelper.getContactNameByNumber(number)
            val display = if (name != null) "$name ($number)" else number
            sendTelegramMessage("📞 <b>Missed Call</b> from: $display")
        }
    }

    private fun sendTelegramMessage(text: String) {
        val token = botToken ?: return
        val chat = chatId ?: return
        serviceScope.launch {
            TelegramRepository.sendMessage(token, chat, text)
        }
    }

    // --- Polling Logic ---

    private fun startPollingLoop() {
        serviceScope.launch {
            var errorBackoff = 2000L
            while (isActive) {
                if (botToken != null && isBotPollingEnabled) {
                    try {
                        // Use a short timeout if we are just checking, but here we want long polling.
                        // If we are disabled, we shouldn't be here? No, the loop runs always but checks flag.
                        val response = TelegramRepository.getUpdates(botToken!!, lastUpdateId + 1)
                        if (response != null && response.ok) {
                            errorBackoff = 2000L // Reset backoff on success
                            for (update in response.result) {
                                lastUpdateId = update.updateId
                                processUpdate(update)
                            }
                            delay(500) // Short delay if success/timeout returned normally
                        } else {
                            // If response is null or not ok (network error?), backoff
                            delay(errorBackoff)
                            errorBackoff = (errorBackoff * 2).coerceAtMost(60000L) // Exponential backoff max 1 min
                        }
                    } catch (e: Exception) {
                        Log.e("BotService", "Polling error: ${e.message}")
                        delay(errorBackoff)
                        errorBackoff = (errorBackoff * 2).coerceAtMost(60000L)
                    }
                } else {
                    // If disabled or no token, wait longer to save battery
                    delay(10000)
                }
            }
        }
    }

    private fun processUpdate(update: Update) {
        // Only process updates from the configured chat ID (security)
        // But the user might be setting it up, so maybe allow any if not set?
        // No, strict mode: only respond if it matches configured chatId or if configured chatId is missing (maybe?)
        // The user requirement says "after initial check of VERIFY... starts polling".

        val incomingChatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id
        if (incomingChatId.toString() != chatId) {
            // Ignore messages from other chats
            return
        }

        if (update.message != null) {
            handleMessage(update.message)
        } else if (update.callbackQuery != null) {
            handleCallback(update.callbackQuery)
        }
    }

    private fun handleMessage(message: com.example.telegramforwarder.data.remote.Message) {
        val text = message.text ?: return
        val userId = message.chat.id
        val state = conversationStates[userId] ?: BotState.IDLE

        // Reset command
        if (text.startsWith("/start") || text.equals("help", ignoreCase = true) || text.equals("menu", ignoreCase = true)) {
            conversationStates[userId] = BotState.IDLE
            sendHelpMenu(userId)
            return
        }

        when (state) {
            BotState.IDLE -> {
                sendHelpMenu(userId)
            }
            BotState.WAITING_FOR_NUMBER -> {
                // Validate Number
                conversationStates[userId] = BotState.WAITING_FOR_SMS_CONTENT_NUMBER
                conversationStatesKeys[userId] = text // store number
                sendMessage(userId, "Enter the message to send to $text:")
            }
            BotState.WAITING_FOR_SMS_CONTENT_NUMBER -> {
                val number = conversationStatesKeys[userId]
                if (number != null) {
                    sendSms(number, text)
                    sendMessage(userId, "✅ SMS sent to $number")
                }
                conversationStates[userId] = BotState.IDLE
            }
            BotState.WAITING_FOR_SMS_CONTENT_CONTACT -> {
                 val number = conversationStatesKeys[userId]
                 if (number != null) {
                    sendSms(number, text)
                    val name = conversationStatesNames[userId] ?: number
                    sendMessage(userId, "✅ SMS sent to $name")
                 }
                 conversationStates[userId] = BotState.IDLE
            }
            BotState.SEARCHING_CONTACT -> {
                // User typed query
                handleContactSearch(userId, text)
            }
            else -> {
                sendHelpMenu(userId)
            }
        }
    }

    private fun handleCallback(callback: com.example.telegramforwarder.data.remote.CallbackQuery) {
        val userId = callback.message?.chat?.id ?: return
        val data = callback.data ?: return

        // Answer callback to stop spinner
        serviceScope.launch {
            botToken?.let { TelegramRepository.answerCallbackQuery(it, callback.id) }
        }

        if (data == "cmd_sms_number") {
            conversationStates[userId] = BotState.WAITING_FOR_NUMBER
            sendMessage(userId, "Please enter the phone number (e.g., +972...):")
        } else if (data == "cmd_sms_contact") {
            conversationStates[userId] = BotState.BROWSING_CONTACTS
            sendContactList(userId, 0)
        } else if (data.startsWith("page_")) {
            val page = data.removePrefix("page_").toIntOrNull() ?: 0
            sendContactList(userId, page)
        } else if (data == "cmd_search_contact") {
            conversationStates[userId] = BotState.SEARCHING_CONTACT
            sendMessage(userId, "Enter name to search:")
        } else if (data.startsWith("c:")) {
            // Selected a contact
            // Format: contact_ID|Name|Number (Need to be careful about length, maybe just store ID map?)
            // Telegram callback data limit is 64 bytes. This is tight.
            // Let's use a temporary cache or just try to fit it.
            // Or just ID? But we need number.
            // Let's assume we can't fit everything.
            // Better strategy: The callback data is "sel_contact_<unique_id>" and we keep a transient map?
            // Or just put the number in if it fits? "c:<number>"

            val number = data.removePrefix("c:")
            // We might want to look up name again or pass it.
            // For now, let's just store number.
            conversationStatesKeys[userId] = number
            conversationStates[userId] = BotState.WAITING_FOR_SMS_CONTENT_CONTACT

            // Try to resolve name for better UX
            val name = contactHelper.getContactNameByNumber(number)
            conversationStatesNames[userId] = name

            sendMessage(userId, "Enter message for ${name ?: number}:")
        }
    }

    private fun sendHelpMenu(chatId: Long) {
        val text = """
            <b>🤖 Bot Remote Control</b>

            Select an action:
        """.trimIndent()

        val keyboard = """
            {"inline_keyboard": [
                [{"text": "📨 Send SMS to Number", "callback_data": "cmd_sms_number"}],
                [{"text": "👤 Send SMS to Contact", "callback_data": "cmd_sms_contact"}]
            ]}
        """.trimIndent()

        sendMessage(chatId, text, keyboard)
    }

    private fun sendContactList(chatId: Long, page: Int) {
        val limit = 20
        val offset = page * limit
        val contacts = contactHelper.getAllContacts(offset, limit + 1) // Fetch one more to check if next exists

        val hasNext = contacts.size > limit
        val displayContacts = contacts.take(limit)

        var keyboardRows = displayContacts.map { contact ->
            // Truncate name if too long to save bytes
            val safeNum = contact.phoneNumber.replace(" ", "")
            // Callback data limit 64 bytes
            // "c:+1234567890" is safe.
            """[{"text": "${contact.name}", "callback_data": "c:$safeNum"}]"""
        }.toMutableList()

        // Navigation buttons
        val navButtons = mutableListOf<String>()
        if (page > 0) {
            navButtons.add("""{"text": "⬅️ Prev", "callback_data": "page_${page - 1}"}""")
        }
        navButtons.add("""{"text": "🔍 Search", "callback_data": "cmd_search_contact"}""")
        if (hasNext) {
            navButtons.add("""{"text": "Next ➡️", "callback_data": "page_${page + 1}"}""")
        }

        if (navButtons.isNotEmpty()) {
            keyboardRows.add(navButtons.joinToString(",", prefix = "[", postfix = "]"))
        }

        val keyboard = """{"inline_keyboard": [${keyboardRows.joinToString(",")}]}"""

        sendMessage(chatId, "<b>Select a Contact (Page ${page + 1}):</b>", keyboard)
    }

    private fun handleContactSearch(chatId: Long, query: String) {
        val contacts = contactHelper.searchContacts(query).take(20) // Limit results

        if (contacts.isEmpty()) {
            sendMessage(chatId, "No contacts found for '$query'.")
            sendHelpMenu(chatId) // Reset or go back?
            return
        }

        var keyboardRows = contacts.map { contact ->
            val safeNum = contact.phoneNumber.replace(" ", "")
            """[{"text": "${contact.name}", "callback_data": "c:$safeNum"}]"""
        }.toMutableList()

        // Back button
        keyboardRows.add("""[{"text": "🔙 Back to Menu", "callback_data": "cmd_sms_contact"}]""")

        val keyboard = """{"inline_keyboard": [${keyboardRows.joinToString(",")}]}"""
        sendMessage(chatId, "<b>Search Results for '$query':</b>", keyboard)
    }

    private fun sendMessage(chatId: Long, text: String, markup: String? = null) {
        val token = botToken ?: return
        serviceScope.launch {
            TelegramRepository.sendMessage(token, chatId.toString(), text, markup)
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            sendMessage(chatId?.toLong() ?: return, "❌ SMS permission not granted. Please open the app and allow SMS permissions.")
            return
        }
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e("BotService", "Error sending SMS: ${e.message}")
            // Notify user of failure
            sendMessage(chatId!!.toLong(), "❌ Failed to send SMS: ${e.message}")
        }
    }

    // Simple state storage
    enum class BotState {
        IDLE,
        WAITING_FOR_NUMBER,
        WAITING_FOR_SMS_CONTENT_NUMBER,
        BROWSING_CONTACTS,
        SEARCHING_CONTACT,
        WAITING_FOR_SMS_CONTENT_CONTACT
    }

    // Auxiliary storage for context (e.g. the number selected)
    private val conversationStatesKeys = mutableMapOf<Long, String>()
    private val conversationStatesNames = mutableMapOf<Long, String?>()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(systemEventReceiver)
        } catch (e: Exception) {
            Log.e("BotService", "Error unregistering receiver: ${e.message}")
        }
        try {
             connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
             Log.e("BotService", "Error unregistering network callback: ${e.message}")
        }
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
            Log.e("BotService", "Error stopping phone state listener: ${e.message}")
        }
        serviceScope.cancel()
    }
}
