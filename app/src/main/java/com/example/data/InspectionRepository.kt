package com.example.data

import kotlinx.coroutines.flow.Flow

class InspectionRepository(private val dao: InspectionDao) {
    val allRecords: Flow<List<InspectionRecord>> = dao.getAllRecords()
    val allSessions: Flow<List<InspectionSession>> = dao.getAllSessions()

    suspend fun insertRecord(record: InspectionRecord): Long = dao.insertRecord(record)

    suspend fun updateRecord(record: InspectionRecord) = dao.updateRecord(record)

    suspend fun updateReview(id: Long, status: String, note: String) = dao.updateReviewStatus(id, status, note)

    suspend fun deleteRecord(id: Long) = dao.deleteRecordById(id)

    suspend fun clearAll() = dao.clearAllRecords()

    suspend fun createSession(title: String): Long {
        return dao.insertSession(InspectionSession(title = title))
    }
}
