package com.skygoto.app.ui.screens.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skygoto.app.domain.model.PecState
import com.skygoto.app.ui.components.HelpDialog
import com.skygoto.app.ui.components.PEC_HELP_SECTIONS
import com.skygoto.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PecScreen(
    viewModel: PecViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.startPolling() }
    DisposableEffect(Unit) { onDispose { viewModel.stopPolling() } }

    if (showHelp) {
        HelpDialog(
            title = "PEC - 帮助",
            sections = PEC_HELP_SECTIONS,
            onDismiss = { showHelp = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 状态卡 + 帮助按钮
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PecStatusCard(pecInfo = uiState.pecInfo, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showHelp = true }) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "帮助", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            PecCurveEditor(
                curveData = uiState.curveData,
                pecState = uiState.pecInfo.state,
                selectedIndex = uiState.selectedIndex,
                bufferSize = uiState.pecInfo.bufferSizeSeconds,
                onSelectIndex = viewModel::selectIndex
            )

            Spacer(Modifier.height(8.dp))

            if (uiState.selectedIndex >= 0 && uiState.curveData.isNotEmpty()) {
                PecEditPanel(
                    selectedIndex = uiState.selectedIndex,
                    totalSize = uiState.curveData.size,
                    editValue = uiState.editValue,
                    onValueChange = viewModel::updateEditValue,
                    onApply = viewModel::applyEdit,
                    onReset = viewModel::resetAtSelected
                )
                Spacer(Modifier.height(12.dp))
            }

            PecControlButtons(
                pecState = uiState.pecInfo.state,
                isConnected = uiState.isConnected,
                isSaving = uiState.isSaving,
                hasData = uiState.curveData.isNotEmpty(),
                isRecorded = uiState.pecInfo.isRecorded,
                onPlay = viewModel::pecPlay,
                onStop = viewModel::pecStop,
                onRecord = viewModel::pecRecord,
                onClear = viewModel::pecClear,
                onSave = viewModel::pecSave,
                onSaveCurve = viewModel::saveCurveToMount
            )

            Spacer(Modifier.height(16.dp))

            PecInstructions()

            Spacer(Modifier.height(48.dp))
        }

        // 消息提示
        if (uiState.message.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .fillMaxWidth(),
                color = if (uiState.messageIsError) Error else Accent2,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(uiState.message, color = TextPrimary, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PecStatusCard(pecInfo: com.skygoto.app.domain.model.PecInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Secondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator dot
                val dotColor = when (pecInfo.state) {
                    PecState.IDLE -> Color.Gray
                    PecState.READY_PLAY -> Accent2
                    PecState.PLAYING -> Accent2.copy(alpha = 0.8f)
                    PecState.READY_RECORD -> Warning
                    PecState.RECORDING -> Error
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pecInfo.state.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (pecInfo.hasIndexDetect) {
                    Spacer(Modifier.width(6.dp))
                    Text("索", fontSize = 10.sp, color = Accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pecInfo.bufferSizeSeconds}s",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(8.dp))
            // Detail row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "缓存 ${pecInfo.bufferSizeSeconds}秒",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "${pecInfo.wormRotationSteps} 步/圈",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            // Recording progress
            if (pecInfo.isRecording && pecInfo.progressPercent > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = pecInfo.progressPercent / 100f,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Accent2,
                    trackColor = TextSecondary.copy(alpha = 0.2f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "录制中: ${pecInfo.currentIndexSecond}/${pecInfo.totalSeconds}秒 (${pecInfo.progressPercent}%)",
                    fontSize = 11.sp,
                    color = Warning
                )
            }
            // Active correction
            if (pecInfo.isActive) {
                Spacer(Modifier.height(4.dp))
                val sign = if (pecInfo.currentCorrection >= 0) "+" else ""
                Text(
                    text = "当前修正: $sign${pecInfo.currentCorrection} 步/秒",
                    fontSize = 12.sp,
                    color = Accent2,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PecControlButtons(
    pecState: PecState,
    isConnected: Boolean,
    isSaving: Boolean,
    hasData: Boolean,
    isRecorded: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onSaveCurve: () -> Unit
) {
    val disabled = !isConnected || isSaving

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Record button
            Button(
                onClick = onRecord,
                enabled = !disabled && pecState == PecState.IDLE,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Warning,
                    contentColor = Primary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("开始录制", fontWeight = FontWeight.Bold)
            }
            // Play button
            Button(
                onClick = onPlay,
                enabled = !disabled && (pecState == PecState.IDLE || pecState == PecState.UNKNOWN) && isRecorded,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent2,
                    contentColor = Primary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("启用回放", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Secondary buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStop,
                enabled = !disabled && (pecState == PecState.PLAYING || pecState == PecState.RECORDING),
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("禁用", color = TextPrimary)
            }
            OutlinedButton(
                onClick = onClear,
                enabled = !disabled && hasData,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("清空")
            }
            OutlinedButton(
                onClick = onSaveCurve,
                enabled = !disabled && hasData && !isSaving,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存NV")
                }
            }
        }
    }
}

@Composable
private fun PecInstructions() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Secondary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("使用说明", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val steps = listOf(
                "1. 锁好目标星，开启自动导星",
                "2. 点「开始录制」运行一个完整周期",
                "3. 录制完成自动切换为回放模式",
                "4. 点击曲线上某点可编辑修正值",
                "5. 编辑后点「保存NV」持久化"
            )
            steps.forEach { step ->
                Text("• $step", fontSize = 11.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

