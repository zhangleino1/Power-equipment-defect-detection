package com.example.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.File
import java.io.FileInputStream
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

enum class ModelArchitecture(
    val displayName: String,
    val sizeMb: String,
    val baseLatencyMs: Long,
    val assetFileName: String?,
    val availabilityLabel: String
) {
    MOBILENET_SSD_V1(
        displayName = "SSD MobileNet V1 · COCO 通用识别",
        sizeMb = "4.0 MB",
        baseLatencyMs = 30L,
        assetFileName = "mobilenet_ssd.tflite",
        availabilityLabel = "内置可用"
    ),
    CUSTOM_YOLO(
        displayName = "自训练 YOLO 电力缺陷模型",
        sizeMb = "本地文件",
        baseLatencyMs = 0L,
        assetFileName = null,
        availabilityLabel = "导入模型"
    );

    val isBundled: Boolean
        get() = assetFileName != null

    val isSelectable: Boolean
        get() = isBundled
}

enum class HardwareDelegate(val displayName: String, val speedMultiplier: Float) {
    CPU_4THREADS("CPU", 1.0f),
    GPU_DELEGATE("GPU 硬件加速 (NNAPI)", 0.45f),
    NPU_HEXAGON("NPU 专用算力芯片", 0.25f)
}

class DefectDetectorEngine(private val context: Context) {
    var selectedModel: ModelArchitecture = ModelArchitecture.MOBILENET_SSD_V1
        private set
    var selectedDelegate: HardwareDelegate = HardwareDelegate.CPU_4THREADS
    var confidenceThreshold: Float = 0.50f
    var iouThreshold: Float = 0.45f

    // Performance Metrics
    var lastInferenceTimeMs: Long = 0L
    var lastFps: Int = 0
    var totalProcessedFrames: Long = 0L

    private var objectDetector: ObjectDetector? = null
    private var customModelFile: File? = null

    var loadError: String? = null
        private set

    val isReady: Boolean
        get() = objectDetector != null && loadError == null

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        objectDetector?.close()
        objectDetector = null
        loadError = null

        try {
            val optionsBuilder = ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(confidenceThreshold)
            val options = optionsBuilder.build()

            objectDetector = when (selectedModel) {
                ModelArchitecture.MOBILENET_SSD_V1 -> {
                    ObjectDetector.createFromFileAndOptions(
                        context,
                        requireNotNull(selectedModel.assetFileName),
                        options
                    )
                }
                ModelArchitecture.CUSTOM_YOLO -> {
                    val modelFile = requireNotNull(customModelFile) {
                        "请先导入带 TFLite Metadata 的 YOLO 模型"
                    }
                    FileInputStream(modelFile).channel.use { channel ->
                        val modelBuffer = channel.map(
                            java.nio.channels.FileChannel.MapMode.READ_ONLY,
                            0,
                            channel.size()
                        )
                        ObjectDetector.createFromBufferAndOptions(modelBuffer, options)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadError = e.localizedMessage ?: "模型加载失败"
        }
    }

    fun selectBundledModel(model: ModelArchitecture): Boolean {
        if (!model.isSelectable) return false
        selectedModel = model
        customModelFile = null
        setupObjectDetector()
        return isReady
    }

    fun importCustomYolo(modelFile: File): Boolean {
        selectedModel = ModelArchitecture.CUSTOM_YOLO
        customModelFile = modelFile
        setupObjectDetector()
        return isReady
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
