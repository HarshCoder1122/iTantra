package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceMessageDao {

    @Query("SELECT * FROM voice_transcripts ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<VoiceMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: VoiceMessageEntity): Long

    @Query("UPDATE voice_transcripts SET hasPlayed = 1 WHERE id = :id")
    suspend fun markAsPlayed(id: Long)

    @Query("DELETE FROM voice_transcripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM voice_transcripts")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM voice_transcripts WHERE isAlert = 1")
    fun getAlertCount(): Flow<Int>
}
