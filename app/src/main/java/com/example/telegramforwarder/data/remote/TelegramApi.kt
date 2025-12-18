package com.example.telegramforwarder.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

// Data classes for getUpdates response
data class UpdateResponse(
    val ok: Boolean,
    val result: List<Update>
)

data class Update(
    @SerializedName("update_id") val updateId: Long,
    @SerializedName("message") val message: Message? = null,
    @SerializedName("callback_query") val callbackQuery: CallbackQuery? = null
)

data class Message(
    @SerializedName("message_id") val messageId: Long,
    val from: User? = null,
    val chat: Chat,
    val text: String? = null
)

data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String? = null
)

data class User(
    val id: Long,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("username") val username: String? = null
)

data class Chat(
    val id: Long,
    val type: String
)

interface TelegramApi {
    @GET("sendMessage")
    suspend fun sendMessage(
        @Query("chat_id") chatId: String,
        @Query("text") text: String,
        @Query("parse_mode") parseMode: String = "HTML",
        @Query("reply_markup") replyMarkup: String? = null
    ): Any

    @GET("getUpdates")
    suspend fun getUpdates(
        @Query("offset") offset: Long? = null,
        @Query("timeout") timeout: Int = 30, // Long polling
        @Query("allowed_updates") allowedUpdates: String = "[\"message\", \"callback_query\"]"
    ): UpdateResponse

    @GET("answerCallbackQuery")
    suspend fun answerCallbackQuery(
        @Query("callback_query_id") callbackQueryId: String,
        @Query("text") text: String? = null
    ): Any
}

data class TelegramResponse(
    val success: Boolean,
    val message: String? = null
)

object TelegramRepository {

    private const val BASE_URL = "https://api.telegram.org/"

    // Single OkHttpClient instance
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.NONE) // Don't log full body to avoid spam

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(40, TimeUnit.SECONDS) // Slightly more than long poll timeout
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    // Cache for TelegramApi instances
    private val apiCache = ConcurrentHashMap<String, TelegramApi>()

    private fun getApi(botToken: String): TelegramApi {
        return apiCache.getOrPut(botToken) {
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("${BASE_URL}bot$botToken/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
            retrofit.create(TelegramApi::class.java)
        }
    }

    suspend fun sendMessage(botToken: String, chatId: String, message: String, replyMarkup: String? = null): TelegramResponse {
        val api = getApi(botToken)
        return try {
            api.sendMessage(chatId, message, replyMarkup = replyMarkup)
            TelegramResponse(true, "Message sent successfully")
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            TelegramResponse(false, errorMsg)
        }
    }

    suspend fun getUpdates(botToken: String, offset: Long?): UpdateResponse? {
        val api = getApi(botToken)
        return try {
            api.getUpdates(offset = offset)
        } catch (e: Exception) {
            // Log.e("TelegramRepository", "Error getting updates: ${e.message}")
            null
        }
    }

    suspend fun answerCallbackQuery(botToken: String, callbackQueryId: String, text: String? = null): TelegramResponse {
        val api = getApi(botToken)
        return try {
            api.answerCallbackQuery(callbackQueryId, text)
            TelegramResponse(true, "Answered")
        } catch (e: Exception) {
            TelegramResponse(false, e.message)
        }
    }
}
