package com.example.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.File
import java.io.FileInputStream
import kotlin.random.Random

/**
 * 统一的检测结果。
 *
 * 无论底层以后接 SSD 还是 YOLO，UI、数据库和网页端都只依赖这个数据类。
 * boundingBox 使用 0～1 的归一化坐标，绘制到不同尺寸画面时不需要保存原图像素。
 */
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

/**
 * 课程演示中的模型入口。
 *
 * 当前真正内置的是带 TensorFlow Lite Task Metadata 的 SSD MobileNet。
 * CUSTOM_YOLO 只是导入入口；Ultralytics 直接导出的 LiteRT 文件通常需要
 * YOLO 专用输出解析器，不能因为扩展名同为 .tflite 就认为一定兼容。
 */
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

/**
 * 硬件运行器的教学状态。
 *
 * 本工程目前只实际接入 CPU 4 线程。GPU 和 NPU 保留为课堂扩展入口，但在
 * 没有创建真实 Delegate 前必须禁用，不能用虚构的倍率显示成已加速。
 */
enum class HardwareDelegate(
    val displayName: String,
    val isImplemented: Boolean
) {
    CPU_4THREADS("CPU（4线程，已接入）", true),
    GPU_DELEGATE("GPU Delegate（待接入）", false),
    NPU_HEXAGON("NPU / NNAPI（待接入）", false)
}

/** 置信度等于阈值时也保留，便于 UI、测试和推理代码共用同一边界规则。 */
internal fun passesConfidenceThreshold(score: Float, threshold: Float): Boolean {
    return score >= threshold
}

class DefectDetectorEngine(private val context: Context) {
    var selectedModel: ModelArchitecture = ModelArchitecture.MOBILENET_SSD_V1
        private set
    var selectedDelegate: HardwareDelegate = HardwareDelegate.CPU_4THREADS
        private set
    var confidenceThreshold: Float = 0.50f

    // 这里只统计 detect() 调用时间，不包含相机取帧、绘制和网页编码。
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
            // Task Vision 的 ObjectDetector 已经包含它支持模型的解码和 NMS。
            // 内部阈值设低，再用当前 UI 阈值二次筛选，滑块降低阈值时才不会
            // 因为构造时已经丢掉候选框而“看起来没有生效”。
            val baseOptions = BaseOptions.builder()
                .setNumThreads(4)
                .build()
            val optionsBuilder = ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(20)
                .setScoreThreshold(0.01f)
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
                        "请先导入兼容 Task Vision Metadata 的检测模型"
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

    /**
     * 选择已实现的硬件运行器。
     *
     * 返回 false 表示该入口只是教学占位，调用方应保持当前 CPU 配置。
     */
    fun selectDelegate(delegate: HardwareDelegate): Boolean {
        if (!delegate.isImplemented) return false
        selectedDelegate = delegate
        setupObjectDetector()
        return isReady
    }

    fun processFrame(bitmap: Bitmap?, frameIndex: Long): List<DetectedDefect> {
        totalProcessedFrames++

        if (bitmap == null || objectDetector == null) {
            return emptyList()
        }

        // TensorImage 会把 Android Bitmap 交给 Task Vision 预处理管线。
        // 自定义 Ultralytics YOLO 解析器不能直接复用此假设，必须按模型张量契约实现。
        val tensorImage = TensorImage.fromBitmap(bitmap)

        val startTime = SystemClock.uptimeMillis()
        val results = try {
            objectDetector?.detect(tensorImage) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        lastInferenceTimeMs = SystemClock.uptimeMillis() - startTime
        
        lastFps = if (lastInferenceTimeMs > 0) (1000 / lastInferenceTimeMs).toInt() else 0

        val detections = mutableListOf<DetectedDefect>()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        for (result in results) {
            val boundingBox = result.boundingBox
            val category = result.categories.firstOrNull() ?: continue
            if (!passesConfidenceThreshold(category.score, confidenceThreshold)) continue

            // Task Vision 返回原图像素坐标，这里统一转换为 0～1 供 Compose 和网页绘制。
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
