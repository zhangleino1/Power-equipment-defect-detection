package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspection_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<InspectionRecord>>

    @Query("SELECT * FROM inspection_records WHERE reviewStatus = :status ORDER BY timestamp DESC")
    fun getRecordsByStatus(status: String): Flow<List<InspectionRecord>>

    @Query("SELECT * FROM inspection_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): InspectionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: InspectionRecord): Long

    @Update
    suspend fun updateRecord(record: InspectionRecord)

    @Query("UPDATE inspection_records SET reviewStatus = :status, reviewNote = :note WHERE id = :id")
    suspend fun updateReviewStatus(id: Long, status: String, note: String)

    @Query("DELETE FROM inspection_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM inspection_records")
    suspend fun clearAllRecords()

    @Query("SELECT * FROM inspection_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<InspectionSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InspectionSession): Long
}
