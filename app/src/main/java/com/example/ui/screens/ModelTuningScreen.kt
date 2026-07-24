package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tflite.HardwareDelegate
import com.example.tflite.ModelArchitecture
import com.example.ui.MainUiState
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTuningScreen(
    uiState: MainUiState,
    onModelSelected: (ModelArchitecture) -> Unit,
    onImportCustomYolo: (android.net.Uri) -> Unit,
    onDelegateSelected: (HardwareDelegate) -> Unit,
    onConfidenceChanged: (Float) -> Unit
) {
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportCustomYolo)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Edge Telemetry Benchmark Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = SkyCyan)
                        Text(
                            text = "终端轻量化推理性能监控",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BenchmarkPill(
                            modifier = Modifier.weight(1f),
                            label = "推理时延 (ms)",
                            value = "${uiState.latencyMs} ms",
                            color = EmeraldGreen
                        )
                        BenchmarkPill(
                            modifier = Modifier.weight(1f),
                            label = "帧率 (FPS)",
                            value = "${uiState.fps} FPS",
                            color = SkyCyan
                        )
                        BenchmarkPill(
                            modifier = Modifier.weight(1f),
                            label = "模型占用内存",
                            value = uiState.selectedModel.sizeMb,
                            color = CautionYellow
                        )
                    }
                }
            }
        }

        // Model Architecture Selection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = ElectricBlue)
                        Column {
                            Text(
                            text = "端侧目标检测模型",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "内置通用模型可运行；自定义模型必须兼容 Task Vision Metadata",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (uiState.modelReady) {
                            EmeraldGreen.copy(alpha = 0.12f)
                        } else {
                            EmergencyRed.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.modelReady) {
                                EmeraldGreen.copy(alpha = 0.45f)
                            } else {
                                EmergencyRed.copy(alpha = 0.45f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.modelReady) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Build
                                },
                                contentDescription = null,
                                tint = if (uiState.modelReady) EmeraldGreen else EmergencyRed
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.modelReady) "模型已就绪" else "模型不可用",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = uiState.modelLoadError
                                        ?: "${uiState.selectedModel.displayName} · 可开始实时检测",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    ModelArchitecture.entries.forEach { model ->
                        val isSelected = uiState.selectedModel == model
                        val isImportEntry = model == ModelArchitecture.CUSTOM_YOLO
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else Slate800),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ElectricBlue else Slate700
                            ),
                            onClick = {
                                if (isImportEntry) {
                                    modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
                                } else {
                                    onModelSelected(model)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isImportEntry && uiState.customModelName != null) {
                                            uiState.customModelName
                                        } else {
                                            model.displayName
                                        },
                                        color = if (isSelected) SkyCyan else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (isImportEntry) {
                                            "仅兼容 Task Vision 检测模型；原始 Ultralytics LiteRT 需专用解析器"
                                        } else {
                                            "COCO 通用目标识别 · 模型大小 ${model.sizeMb} · 约 ${model.baseLatencyMs}ms"
                                        },
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isImportEntry && !isSelected) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = SkyCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "导入",
                                            color = SkyCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (isSelected) {
                                    Surface(
                                        color = ElectricBlue,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "当前使用",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hardware Acceleration Delegate Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = EmeraldGreen)
                        Text(
                            text = "边缘硬件运行器配置",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = "当前版本只接入 CPU 4 线程。GPU/NPU 入口用于课堂讲解，" +
                            "在实现并验证真实 Delegate 前保持禁用。",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HardwareDelegate.entries.forEach { delegate ->
                            val isSelected = uiState.selectedDelegate == delegate
                            FilterChip(
                                selected = isSelected,
                                enabled = delegate.isImplemented,
                                onClick = { onDelegateSelected(delegate) },
                                label = { Text(delegate.displayName) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldGreen,
                                    containerColor = Slate800,
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }

        // Confidence & NMS Threshold Slider Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CautionYellow)
                        Text(
                            text = "检测后处理阈值调优 (Confidence Threshold)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("置信度阈值过滤: ", color = TextMuted, fontSize = 12.sp)
                        Text(
                            text = "${(uiState.confThreshold * 100).toInt()}%",
                            color = CautionYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Slider(
                        value = uiState.confThreshold,
                        onValueChange = onConfidenceChanged,
                        valueRange = 0.10f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = CautionYellow,
                            activeTrackColor = CautionYellow
                        )
                    )

                    Text(
                        text = "提高置信度阈值可减少误报率；降低阈值可提高小目标与微小缺陷（如销钉脱落）的召回率。",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BenchmarkPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = TextMuted, fontSize = 10.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
