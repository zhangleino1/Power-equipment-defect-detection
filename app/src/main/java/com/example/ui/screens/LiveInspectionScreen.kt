package com.example.ui.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tflite.DetectedDefect
import com.example.ui.MainUiState
import com.example.ui.theme.AlertOrange
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800

@Composable
fun LiveInspectionScreen(
    uiState: MainUiState,
    onToggleDetecting: () -> Unit,
    onToggleCameraMode: () -> Unit,
    onToggleAlarmSound: () -> Unit,
    onFrameCaptured: (android.graphics.Bitmap) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Linkage Alarm Banner
        AnimatedVisibility(
            visible = uiState.isAlarmTriggered,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, EmergencyRed, RoundedCornerShape(12.dp)),
                color = EmergencyRed.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "告警",
                        tint = EmergencyRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.alarmMessage.isNotEmpty()) uiState.alarmMessage else "警告：无人机巡检识别到紧急配电缺陷！",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "联动声光告警与抓拍功能已自动激活，Web控制台同步提醒中",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Camera Feed / Simulated Drone Live Frame with Canvas Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
        ) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasCameraPermission = isGranted
            }

            androidx.compose.runtime.LaunchedEffect(uiState.isCameraMode) {
                if (uiState.isCameraMode && !hasCameraPermission) {
                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }

            if (uiState.isCameraMode && hasCameraPermission) {
                // Real Live Camera Feed via CameraX
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val bitmap = imageProxy.toBitmap()
                                    val rotatedBitmap = if (rotation != 0) {
                                        val matrix = android.graphics.Matrix()
                                        matrix.postRotate(rotation.toFloat())
                                        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    } else {
                                        bitmap
                                    }
                                    onFrameCaptured(rotatedBitmap)
                                    imageProxy.close()
                                }
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Simple placeholder background when camera is disabled
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("摄像头已关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val canvasW = maxWidth.value
                val canvasH = maxHeight.value

                // Real-Time TFLite Bounding Boxes Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    uiState.currentDetections.forEach { defect ->
                        val bbox = defect.boundingBox
                        val left = bbox.left * w
                        val top = bbox.top * h
                        val boxW = bbox.width() * w
                        val boxH = bbox.height() * h

                        val color = Color(defect.severityColorHex)

                        // Bounding rectangle
                        drawRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            style = Stroke(
                                width = 5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                            )
                        )

                        // Outer glowing target corners
                        drawRect(
                            color = color.copy(alpha = 0.3f),
                            topLeft = Offset(left - 4, top - 4),
                            size = Size(boxW + 8, boxH + 8),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Text Bounding Overlay (Compose Labels over coordinates)
                uiState.currentDetections.forEach { defect ->
                    val bbox = defect.boundingBox
                    val color = Color(defect.severityColorHex)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = (bbox.left * canvasW).dp,
                                top = ((bbox.top * canvasH) - 28).dp.coerceAtLeast(8.dp)
                            )
                    ) {
                        Surface(
                            color = color,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${defect.defectType} ${(defect.confidence * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Top HUD Telemetry Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Edge Metrics Pill
                Surface(
                    color = Slate800.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, "AI", tint = CautionYellow, modifier = Modifier.size(16.dp))
                        Text("FPS: ${uiState.fps}", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("|", color = Slate700)
                        Text("延迟: ${uiState.latencyMs}ms", color = SkyCyan, fontSize = 11.sp)
                    }
                }
            }

            // Bottom Frame Mode Indicator
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                color = Slate800.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isDetecting) EmeraldGreen else EmergencyRed)
                    )
                    Text(
                        text = if (uiState.isCameraMode) "源: 本地摄像头实时图传" else "源: 离线视频流",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Live Defect Counter Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val detectionCount = uiState.currentDetections.size

            DefectSummaryBadge(
                modifier = Modifier.weight(1f),
                title = "识别目标数",
                count = detectionCount,
                color = EmeraldGreen
            )
        }

        // Control Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Slate800,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleDetecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isDetecting) EmergencyRed else EmeraldGreen
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isDetecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.isDetecting) "暂停边缘推理" else "启动边缘推理")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onToggleCameraMode,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.isCameraMode) ElectricBlue else Slate700)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "切换源",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onToggleAlarmSound,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.isAlarmSoundEnabled) AlertOrange else Slate700)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "告警声音",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DefectSummaryBadge(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$count 处",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
