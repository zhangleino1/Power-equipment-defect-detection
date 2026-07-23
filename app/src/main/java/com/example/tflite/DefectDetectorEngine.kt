package com.example.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import kotlin.random.Random

data class DetectedDefect(
    val id: String = System.currentTimeMillis().toString() + "_" + Random.nextInt(100, 999),
    val defectType: String,
    val confidence: Float,
    val boundingBox: RectF, // Normalized 0f..1f (left, top, right, bottom)
    val timestamp: Long = System.currentTimeMillis(),
    val locationName: String = "现场巡检点"
) {
    val severityColorHex: Long = 0xFF10B981 // Green color for general detection
}

enum class ModelArchitecture(val displayName: String, val sizeMb: String, val baseLatencyMs: Long) {
    YOLO_V8N_FP16("通用目标检测模型 (MobileNet SSD)", "4.2 MB", 30L),
    YOLO_V8S_INT8("电力专用模型 (待训练)", "-- MB", 0L),
    MOBILENET_V3_SSD("自定义模型 (待导入)", "-- MB", 0L)
}

enum class HardwareDelegate(val displayName: String, val speedMultiplier: Float) {
    CPU_4THREADS("CPU", 1.0f),
    GPU_DELEGATE("GPU 硬件加速 (NNAPI)", 0.45f),
    NPU_HEXAGON("NPU 专用算力芯片", 0.25f)
}

class DefectDetectorEngine(private val context: Context) {
    var selectedModel: ModelArchitecture = ModelArchitecture.YOLO_V8N_FP16
    var selectedDelegate: HardwareDelegate = HardwareDelegate.CPU_4THREADS
    var confidenceThreshold: Float = 0.50f
    var iouThreshold: Float = 0.45f

    // Performance Metrics
    var lastInferenceTimeMs: Long = 0L
    var lastFps: Int = 0
    var totalProcessedFrames: Long = 0L

    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        try {
            val optionsBuilder = ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(confidenceThreshold)
            val options = optionsBuilder.build()
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "mobilenet_ssd.tflite",
                options
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun processFrame(bitmap: Bitmap?, frameIndex: Long): List<DetectedDefect> {
        totalProcessedFrames++
        
        if (bitmap == null || objectDetector == null) {
            return emptyList()
        }

        val tensorImage = TensorImage.fromBitmap(bitmap)
        
        val startTime = SystemClock.uptimeMillis()
        val results = try {
            objectDetector?.detect(tensorImage) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        lastInferenceTimeMs = SystemClock.uptimeMillis() - startTime
        
        if (lastInferenceTimeMs > 0) {
            lastFps = (1000 / lastInferenceTimeMs).toInt()
        }

        val detections = mutableListOf<DetectedDefect>()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        for (result in results) {
            val boundingBox = result.boundingBox
            val category = result.categories.firstOrNull() ?: continue
            
            // Normalize bounding box
            val normalizedBox = RectF(
                (boundingBox.left / width).coerceIn(0f, 1f),
                (boundingBox.top / height).coerceIn(0f, 1f),
                (boundingBox.right / width).coerceIn(0f, 1f),
                (boundingBox.bottom / height).coerceIn(0f, 1f)
            )

            detections.add(
                DetectedDefect(
                    defectType = category.label, // General label
                    confidence = category.score,
                    boundingBox = normalizedBox,
                    locationName = "目标节点"
                )
            )
        }
        
        return detections
    }
}
