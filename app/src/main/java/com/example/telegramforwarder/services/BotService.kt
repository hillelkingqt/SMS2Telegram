package com.example.telegramforwarder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.telegramforwarder.R
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

    // State
    private var isPolling = false
    private var botToken: String? = null
    private var chatId: String? = null
    private var lastUpdateId: Long = 0

    // Battery State
    private var lastBatteryLevel = -1
    private var batteryLowThreshold = 20f
    private var batteryHighThreshold = 90f
    private var isBatteryNotifyEnabled = false
    private var hasNotifiedLow = false
    private var hasNotifiedHigh = false

    // Missed Call State
    private var isMissedCallEnabled = false

    // Conversation State: Map<ChatId, State>
    private val conversationStates = mutableMapOf<Long, BotState>()

    // Notification
    private val NOTIFICATION_ID = 999
    private val CHANNEL_ID = "BotServiceChannel"

    // Receivers
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    val pct = (level.toFloat() / scale.toFloat()) * 100f
                    handleBatteryLevel(pct)
                }
            }
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

        // Register Battery Receiver
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Register Phone Listener
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)

        // Start Loops
        startConfigurationObserver()
        startPollingLoop()
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Bot Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Forwarder Bot")
            .setContentText("Listening for commands and events...")
            .setSmallIcon(R.mipmap.ic_launcher)
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
            userPreferences.isMissedCallEnabled.collect { isMissedCallEnabled = it }
        }
    }

    private fun handleBatteryLevel(pct: Float) {
        if (!isBatteryNotifyEnabled || botToken == null || chatId == null) return

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
            while (isActive) {
                if (botToken != null) {
                    try {
                        val response = TelegramRepository.getUpdates(botToken!!, lastUpdateId + 1)
                        if (response != null && response.ok) {
                            for (update in response.result) {
                                lastUpdateId = update.updateId
                                processUpdate(update)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BotService", "Polling error: ${e.message}")
                    }
                }
                delay(2000) // Poll every 2 seconds
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
        } else if (data.startsWith("contact_")) {
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
        unregisterReceiver(batteryReceiver)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        serviceScope.cancel()
    }
}
