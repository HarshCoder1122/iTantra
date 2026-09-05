package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_transcripts")
data class VoiceMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageUid: String,
    val text: String,
    val senderCallsign: String,
    val isLocal: Boolean,
    val languageCode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAlert: Boolean = false,
    val alertPriority: String = "ROUTINE",
    val audioDurationSec: Float = 2.5f,
    val hasPlayed: Boolean = false
)
