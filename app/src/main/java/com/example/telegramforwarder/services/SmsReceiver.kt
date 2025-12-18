package com.example.telegramforwarder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.telegramforwarder.data.LogRepository
import com.example.telegramforwarder.data.local.AppDatabase
import com.example.telegramforwarder.data.local.ContactHelper
import com.example.telegramforwarder.data.local.MessageEntity
import com.example.telegramforwarder.data.local.UserPreferences
import com.example.telegramforwarder.data.remote.GeminiRepository
import com.example.telegramforwarder.data.remote.TelegramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val logger = LogRepository(context)

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()

            scope.launch {
                try {
                    val preferences = UserPreferences(context)
                    val isEnabled = preferences.isSmsEnabled.first()

                    if (!isEnabled) {
                        logger.logInfo("SmsReceiver", "SMS received but forwarding is disabled in settings.")
                        return@launch
                    }

                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    logger.logInfo("SmsReceiver", "Received ${messages?.size ?: 0} SMS parts.")

                    messages?.forEach { sms ->
                        val sender = sms.originatingAddress ?: "Unknown"
                        val messageBody = sms.messageBody ?: ""
                        logger.logInfo("SmsReceiver", "Processing SMS from $sender")

                        processIncomingMessage(context, sender, messageBody, "SMS", logger)
                    }
                } catch (e: Exception) {
                    logger.logError("SmsReceiver", "Error receiving SMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private suspend fun processIncomingMessage(
        context: Context,
        sender: String,
        content: String,
        type: String,
        logger: LogRepository
    ) {
        val database = AppDatabase.getDatabase(context)
        val preferences = UserPreferences(context)

        // Save to local DB
        try {
            database.messageDao().insertMessage(
                MessageEntity(
                    sender = sender,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    type = type
                )
            )
            logger.logDebug("SmsReceiver", "Message saved to local database")
        } catch (e: Exception) {
            logger.logError("SmsReceiver", "Failed to save message to local DB", e)
        }

        // Send to Telegram
        val botToken = preferences.botToken.first()
        val chatId = preferences.chatId.first()

        if (!botToken.isNullOrEmpty() && !chatId.isNullOrEmpty()) {
            val sb = StringBuilder()

            // Check for verification code using Gemini if it's an SMS
            if (type.equals("SMS", ignoreCase = true)) {
                val geminiKeys = preferences.geminiApiKeys.first()
                if (geminiKeys.isNotEmpty()) {
                    val geminiRepo = GeminiRepository(logger)
                    val code = geminiRepo.checkMessageForCode(content, geminiKeys)

                    if (code != null) {
                        logger.logInfo("SmsReceiver", "Gemini found code: $code")
                        sb.append("<b>Verification code by AI:</b>\n")
                        sb.append("<code>$code</code>\n\n")
                    } else {
                        logger.logInfo("SmsReceiver", "Gemini did not find a code.")
                    }
                } else {
                    logger.logInfo("SmsReceiver", "No Gemini keys found, skipping AI check.")
                }
            }

            // Construct HTML message
            val contactHelper = ContactHelper(context)
            val contactName = contactHelper.getContactNameByNumber(sender)
            val displaySender = if (contactName != null) "$contactName ($sender)" else sender

            val safeSender = escapeHtml(displaySender)
            val safeContent = escapeHtml(content)

            sb.append("📩 <b>New $type</b>\n\n")
            sb.append("<b>From:</b> $safeSender\n\n")
            sb.append(safeContent)

            val formattedMessage = sb.toString()

            logger.logInfo("SmsReceiver", "Attempting to send to Telegram (ChatID: $chatId)")

            val result = TelegramRepository.sendMessage(botToken, chatId, formattedMessage)
            if (result.success) {
                logger.logInfo("SmsReceiver", "Successfully sent to Telegram")
            } else {
                logger.logError("SmsReceiver", "Failed to send to Telegram: ${result.message}")
            }
        } else {
            logger.logError("SmsReceiver", "Telegram credentials not set (Bot Token or Chat ID missing).")
        }
    }
}
