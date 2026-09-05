package com.example.data

import kotlinx.coroutines.flow.Flow

class VoiceMessageRepository(private val dao: VoiceMessageDao) {

    val allMessages: Flow<List<VoiceMessageEntity>> = dao.getAllMessages()
    val alertCount: Flow<Int> = dao.getAlertCount()

    suspend fun logMessage(message: VoiceMessageEntity): Long {
        return dao.insert(message)
    }

    suspend fun markAsPlayed(id: Long) {
        dao.markAsPlayed(id)
    }

    suspend fun deleteMessage(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearMissionLogs() {
        dao.clearAll()
    }
}
