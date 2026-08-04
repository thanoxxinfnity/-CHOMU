package com.chomu.aiagent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatEntity(
    @PrimaryKey val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false,
    val automationLog: String? = null,
    val sessionId: String = "default"
)
