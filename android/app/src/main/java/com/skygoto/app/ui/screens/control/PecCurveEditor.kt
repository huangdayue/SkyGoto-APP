package com.skygoto.app.ui.screens.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skygoto.app.domain.model.PecState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PEC 修正曲线绘制组件
 *
 * 支持触摸选择、缩放和平移。
 */
@Composable
fun PecCurveEditor(
    curveData: List<Int>,
    pecState: PecState,
    selectedIndex: Int,
    bufferSize: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()

    if (curveData.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp)) {
            Text(
                text = if (pecState == PecState.UNKNOWN || bufferSize == 0)
                    "连接赤道仪后加载 PEC 数据"
                else
                    "暂无数据，请先录制",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val lineColor = when (pecState) {
        PecState.RECORDING, PecState.READY_RECORD -> Color(0xFF4FC3F7) // 蓝色-录制
        PecState.PLAYING, PecState.READY_PLAY -> Color(0xFF66BB6A)     // 绿色-回放
        else -> Color(0xFFB0BEC5)                                      // 灰色-空闲
    }
    val selectColor = Color(0xFFFF5252)
    val zeroLineColor = Color(0xFF455A64)

    // Auto-fit y axis
    val maxAbs = (curveData.maxOf { abs(it) }.coerceAtLeast(1) * 1.2f).coerceAtLeast(10f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val w = size.width.toFloat()
                    val viewRange = (curveData.size.toFloat() / scale).coerceAtLeast(1f)
                    val startIdx = ((offsetX / w) * curveData.size).coerceIn(0f, (curveData.size - viewRange))
                    val idx = (startIdx + (tapOffset.x / w) * viewRange).roundToInt()
                        .coerceIn(0, curveData.size - 1)
                    onSelectIndex(idx)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 10f)
                    offsetX = (offsetX + pan.x).coerceIn(
                        -(curveData.size.toFloat() / scale - 1f) * size.width.toFloat(),
                        0f
                    )
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val pad = 40f
        val chartW = w - pad * 2
        val chartH = h - pad * 2
        val midY = pad + chartH / 2f
        val viewRange = (curveData.size.toFloat() / scale).coerceAtLeast(1f)
        val startIdx = ((offsetX / w) * curveData.size).coerceIn(0f, (curveData.size - viewRange)).toInt()
        val endIdx = (startIdx + viewRange).toInt().coerceAtMost(curveData.size)

        // Zero line
        drawLine(
            color = zeroLineColor,
            start = Offset(pad, midY),
            end = Offset(w - pad, midY),
            strokeWidth = 1f
        )

        // Y axis labels
        val textStyle = TextStyle(color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        listOf(-maxAbs.roundToInt(), 0, maxAbs.roundToInt()).forEach { label ->
            val y = midY - (label / maxAbs) * (chartH / 2f)
            val text = if (label >= 0) "+${label}" else "$label"
            val result = textMeasurer.measure(text, textStyle)
            drawText(result, topLeft = Offset(2f, y - result.size.height / 2f))
        }

        if (startIdx >= endIdx) return@Canvas

        // PEC curve
        val path = Path()
        val stepX = chartW / (endIdx - startIdx - 1).coerceAtLeast(1)

        for (i in startIdx until endIdx) {
            val x = pad + (i - startIdx) * stepX
            val y = midY - (curveData[i].toFloat() / maxAbs) * (chartH / 2f)
            if (i == startIdx) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Selected point
        if (selectedIndex in startIdx until endIdx) {
            val sx = pad + (selectedIndex - startIdx) * stepX
            val sy = midY - (curveData[selectedIndex].toFloat() / maxAbs) * (chartH / 2f)
            drawCircle(color = selectColor, radius = 8f, center = Offset(sx, sy))
            drawCircle(color = Color.White, radius = 4f, center = Offset(sx, sy))
        }

        // X axis labels
        val xStepLabels = (endIdx - startIdx).let { range ->
            val step = when {
                range <= 30 -> 5
                range <= 100 -> 20
                range <= 300 -> 60
                else -> 120
            }
            step
        }
        for (i in startIdx until endIdx step xStepLabels) {
            val x = pad + (i - startIdx) * stepX
            val text = "${i}s"
            val result = textMeasurer.measure(text, textStyle)
            drawText(result, topLeft = Offset(x - result.size.width / 2f, h - 14f))
        }

        // RMS annotation
        if (curveData.isNotEmpty()) {
            val rms = kotlin.math.sqrt(curveData.map { it * it }.average())
            val rmsText = "RMS: ${"%.1f".format(rms)} 步"
            val rmsResult = textMeasurer.measure(rmsText, textStyle)
            drawText(rmsResult, topLeft = Offset(w - rmsResult.size.width - 4f, 4f))
        }
    }
}
