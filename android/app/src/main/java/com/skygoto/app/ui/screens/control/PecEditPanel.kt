package com.skygoto.app.ui.screens.control

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skygoto.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skygoto.app.ui.theme.*

/**
 * PEC 编辑面板组件
 *
 * 选中曲线某点后展开，显示该点信息并提供滑动条和快捷按钮。
 */
@Composable
fun PecEditPanel(
    selectedIndex: Int,
    totalSize: Int,
    editValue: Int,
    onValueChange: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Secondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Position info
            Text(
                text = "第 $selectedIndex 秒 / 共 $totalSize 秒",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "修正值: ${if (editValue >= 0) "+" else ""}$editValue 步",
                style = MaterialTheme.typography.titleSmall,
                color = Accent2,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            // Slider
            Slider(
                value = editValue.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = -127f..127f,
                steps = 253,
                colors = SliderDefaults.colors(
                    thumbColor = Accent2,
                    activeTrackColor = Accent2,
                    inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                )
            )

            // Quick buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(-10, -5, -1, 1, 5, 10).forEach { step ->
                    Button(
                        onClick = { onValueChange((editValue + step).coerceIn(-127, 127)) },
                        modifier = Modifier.height(36.dp).widthIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (step < 0) Error.copy(alpha = 0.6f) else Accent2.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (step < 0) "-${abs(step)}" else "+${step}",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onApply,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent2)
                ) {
                    Text("应用", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning)
                ) {
                    Text("归零")
                }
            }
        }
    }
}
