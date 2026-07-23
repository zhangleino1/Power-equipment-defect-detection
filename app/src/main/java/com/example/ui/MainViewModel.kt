package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.InspectionRecord
import com.example.data.InspectionRepository
import com.example.tflite.DefectDetectorEngine
import com.example.tflite.DetectedDefect
import com.example.tflite.HardwareDelegate
import com.example.tflite.ModelArchitecture
import com.example.web.InspectionWebServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class MainUiState(
    val isDetecting: Boolean = true,
    val isCameraMode: Boolean = true, // true = CameraX Live
    val isAlarmSoundEnabled: Boolean = true,
    val isAlarmTriggered: Boolean = false,
    val alarmMessage: String = "",
    val webServerRunning: Boolean = false,
    val webServerUrl: String = "http://127.0.0.1:8080",
    val selectedModel: ModelArchitecture = ModelArchitecture.YOLO_V8N_FP16,
    val selectedDelegate: HardwareDelegate = HardwareDelegate.GPU_DELEGATE,
    val confThreshold: Float = 0.50f,
    val fps: Int = 58,
    val latencyMs: Long = 12L,
    val currentDetections: List<DetectedDefect> = emptyList(),
    val droneSignal: String = "强 (-62dBm)",
    val lastFrameBytes: ByteArray? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: InspectionRepository
    val historyRecords: StateFlow<List<InspectionRecord>>

    val detectorEngine = DefectDetectorEngine(application)
    private var webServer: InspectionWebServer? = null
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var frameCounter = 0L

    init {
        val dao = AppDatabase.getDatabase(application).inspectionDao()
        repository = InspectionRepository(dao)
        historyRecords = repository.allRecords.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        // Initialize Web Server
        startWebServer()

        // Start Real-time Edge Detection Loop
        startDetectionLoop()
    }

    private fun startWebServer() {
        val ip = InspectionWebServer.getLocalIpAddress(getApplication())
        val url = "http://$ip:8080"

        try {
            webServer = InspectionWebServer(
                port = 8080,
                onReviewCallback = { id, status, note ->
                    updateRecordReview(id, status, note)
                },
                onCommandCallback = { command, value ->
                    handleRemoteCommand(command, value)
                },
                getLatestStateProvider = {
                    val state = _uiState.value
                    InspectionWebServer.WebServerState(
                        isDetecting = state.isDetecting,
                        fps = state.fps,
                        latencyMs = state.latencyMs,
                        modelName = state.selectedModel.displayName,
                        delegateName = state.selectedDelegate.displayName,
                        confThreshold = state.confThreshold,
                        currentDefects = state.currentDetections.map { d ->
                            InspectionRecord(
                                defectType = d.defectType,
                                confidence = d.confidence,
                                bboxX = d.boundingBox.left,
                                bboxY = d.boundingBox.top,
                                bboxW = d.boundingBox.width(),
                                bboxH = d.boundingBox.height(),
                                location = d.locationName
                            )
                        },
                        historyRecords = historyRecords.value,
                        currentFrameBytes = state.lastFrameBytes,
                        droneSignal = state.droneSignal
                    )
                }
            )
            webServer?.start()
            _uiState.value = _uiState.value.copy(
                webServerRunning = true,
                webServerUrl = url
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                webServerRunning = false,
                webServerUrl = "启动失败: ${e.localizedMessage}"
            )
        }
    }

    private fun startDetectionLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                if (_uiState.value.isDetecting) {
                    frameCounter++
                    
                    val detections = detectorEngine.processFrame(currentCameraBitmap, frameCounter)

                    // Generate rendered snapshot bytes for web stream preview
                    val renderedFrame = generateFrameBitmap(detections)

                    if (detections.isNotEmpty() && _uiState.value.isAlarmSoundEnabled) {
                        triggerAlarmFeedback("提示：识别到目标！")
                    }

                    // Auto persist new defects to Room DB periodically
                    if (frameCounter % 30 == 0L && detections.isNotEmpty()) {
                        detections.forEach { d ->
                            repository.insertRecord(
                                InspectionRecord(
                                    defectType = d.defectType,
                                    confidence = d.confidence,
                                    bboxX = d.boundingBox.left,
                                    bboxY = d.boundingBox.top,
                                    bboxW = d.boundingBox.width(),
                                    bboxH = d.boundingBox.height(),
                                    location = d.locationName
                                )
                            )
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        currentDetections = detections,
                        fps = detectorEngine.lastFps,
                        latencyMs = detectorEngine.lastInferenceTimeMs,
                        lastFrameBytes = renderedFrame
                    )
                }
                delay(120) // ~8-10 FPS UI updates
            }
        }
    }

    private var currentCameraBitmap: Bitmap? = null

    fun updateCameraFrame(bitmap: Bitmap) {
        currentCameraBitmap = bitmap
    }

    private fun generateFrameBitmap(detections: List<DetectedDefect>): ByteArray {
        val baseBitmap = currentCameraBitmap?.let { bmp ->
            Bitmap.createScaledBitmap(bmp, 640, (640.0 / bmp.width * bmp.height).toInt(), true)
        } ?: Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.parseColor("#F1F5F9"))
        }

        val canvas = Canvas(baseBitmap)
        val w = baseBitmap.width.toFloat()
        val h = baseBitmap.height.toFloat()

        // Draw detections
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isAntiAlias = true
        }

        detections.forEach { d ->
            boxPaint.color = d.severityColorHex.toInt()
            val left = d.boundingBox.left * w
            val top = d.boundingBox.top * h
            val right = d.boundingBox.right * w
            val bottom = d.boundingBox.bottom * h

            canvas.drawRect(left, top, right, bottom, boxPaint)
            
            val textBgPaint = Paint().apply {
                color = d.severityColorHex.toInt()
                style = Paint.Style.FILL
            }
            val text = "${d.defectType} (${(d.confidence * 100).toInt()}%)"
            val textWidth = textPaint.measureText(text)
            val textTop = (top - 30f).coerceAtLeast(0f)
            canvas.drawRect(left, textTop, left + textWidth + 10f, textTop + 30f, textBgPaint)
            canvas.drawText(text, left + 5f, textTop + 24f, textPaint)
        }

        val baos = ByteArrayOutputStream()
        baseBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        return baos.toByteArray()
    }

    fun toggleDetecting() {
        _uiState.value = _uiState.value.copy(isDetecting = !_uiState.value.isDetecting)
    }

    fun toggleCameraMode() {
        _uiState.value = _uiState.value.copy(isCameraMode = !_uiState.value.isCameraMode)
    }

    fun toggleAlarmSound() {
        _uiState.value = _uiState.value.copy(isAlarmSoundEnabled = !_uiState.value.isAlarmSoundEnabled)
    }

    fun updateModel(model: ModelArchitecture) {
        detectorEngine.selectedModel = model
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun updateDelegate(delegate: HardwareDelegate) {
        detectorEngine.selectedDelegate = delegate
        _uiState.value = _uiState.value.copy(selectedDelegate = delegate)
    }

    fun updateConfidenceThreshold(conf: Float) {
        detectorEngine.confidenceThreshold = conf
        _uiState.value = _uiState.value.copy(confThreshold = conf)
    }

    fun updateRecordReview(id: Long, status: String, note: String) {
        viewModelScope.launch {
            repository.updateReview(id, status, note)
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    private fun handleRemoteCommand(command: String, value: String) {
        when (command) {
            "trigger_alarm" -> triggerAlarmFeedback("收到局域网电脑端指令：远程触发声光告警！")
            "set_threshold" -> {
                val valFloat = value.toFloatOrNull() ?: 0.5f
                updateConfidenceThreshold(valFloat)
            }
        }
    }

    private fun triggerAlarmFeedback(msg: String) {
        _uiState.value = _uiState.value.copy(
            isAlarmTriggered = true,
            alarmMessage = msg
        )

        viewModelScope.launch {
            delay(2500)
            _uiState.value = _uiState.value.copy(isAlarmTriggered = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        webServer?.stop()
    }
}
