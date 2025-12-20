package com.example.telegramforwarder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.telegramforwarder.data.local.UserPreferences
import com.example.telegramforwarder.data.remote.TelegramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Start the service
            val serviceIntent = Intent(context, BotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Check if we should notify about Boot/Update
            val scope = CoroutineScope(Dispatchers.IO)
            val prefs = UserPreferences(context)

            scope.launch {
                val botToken = prefs.botToken.first()
                val chatId = prefs.chatId.first()

                if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) return@launch

                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    val isBootNotify = prefs.isNotifyBootCompleted.first()
                    if (isBootNotify) {
                        TelegramRepository.sendMessage(botToken, chatId, "🚀 <b>System Boot Completed</b>\nTelegram Forwarder is active.")
                    }
                } else if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                     val isUpdateNotify = prefs.isNotifyAppUpdated.first()
                     if (isUpdateNotify) {
                         TelegramRepository.sendMessage(botToken, chatId, "✨ <b>App Updated</b>\nTelegram Forwarder was updated.")
                     }
                }
            }
        }
    }
}
