package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.LiveInspectionScreen
import com.example.ui.screens.ModelTuningScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.WebServerScreen
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.Slate800
import com.example.ui.theme.TextMuted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyRecords by viewModel.historyRecords.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        NavItem("实时巡检", Icons.Default.Videocam),
        NavItem("局域网 Web", Icons.Default.Language),
        NavItem("模型调优", Icons.Default.Tune),
        NavItem("巡检日志", Icons.Default.History)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = Slate800,
                tonalElevation = 8.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SkyCyan,
                            selectedTextColor = SkyCyan,
                            indicatorColor = ElectricBlue.copy(alpha = 0.25f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> LiveInspectionScreen(
                    uiState = uiState,
                    onToggleDetecting = { viewModel.toggleDetecting() },
                    onToggleCameraMode = { viewModel.toggleCameraMode() },
                    onToggleAlarmSound = { viewModel.toggleAlarmSound() },
                    onFrameCaptured = { viewModel.updateCameraFrame(it) }
                )
                1 -> WebServerScreen(
                    uiState = uiState,
                    historyRecords = historyRecords
                )
                2 -> ModelTuningScreen(
                    uiState = uiState,
                    onModelSelected = { viewModel.updateModel(it) },
                    onImportCustomYolo = { viewModel.importCustomYolo(it) },
                    onDelegateSelected = { viewModel.updateDelegate(it) },
                    onConfidenceChanged = { viewModel.updateConfidenceThreshold(it) }
                )
                3 -> RecordsScreen(
                    records = historyRecords,
                    onUpdateReview = { id, status, note -> viewModel.updateRecordReview(id, status, note) },
                    onDeleteRecord = { id -> viewModel.deleteRecord(id) },
                    onClearAll = { viewModel.clearAllRecords() }
                )
            }
        }
    }
}

data class NavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
