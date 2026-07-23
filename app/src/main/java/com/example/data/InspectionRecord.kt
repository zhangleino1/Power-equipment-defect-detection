package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspection_records")
data class InspectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val defectType: String,       // 绝缘子破损, 杆塔鸟巢, 防振锤移位, 闪络烧伤痕迹, 销钉脱落, 树障侵限, 导线断股
    val confidence: Float,        // 0.88f
    val bboxX: Float,             // 0.2f
    val bboxY: Float,             // 0.3f
    val bboxW: Float,             // 0.25f
    val bboxH: Float,             // 0.2f
    val location: String,         // "220kV 华东线 #042杆塔"
    val reviewStatus: String = "待复核", // 待复核, 已确认, 误报
    val reviewNote: String = ""
)

@Entity(tableName = "inspection_sessions")
data class InspectionSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val defectCount: Int = 0,
    val status: String = "进行中"
)
