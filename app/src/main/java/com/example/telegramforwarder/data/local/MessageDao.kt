package com.example.telegramforwarder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages")
    fun getMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE type = :type")
    fun getMessageCountByType(type: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE sender LIKE :query OR content LIKE :query ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE timestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Insert
    suspend fun insertMessage(message: MessageEntity)
}
