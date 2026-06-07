package com.skygoto.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skygoto.app.ui.theme.*
data class HelpSection(
    val title: String,
    val content: String,
    val code: String? = null
)

/**
 * 对齐页面帮助内容
 */
val ALIGN_HELP_SECTIONS = listOf(
    HelpSection(
        "什么是星点对齐",
        "星点对齐（Star Alignment）是通过观测天空中已知坐标的亮星，让赤道仪建立指向模型的过程。\n\n" +
        "未经对齐的赤道仪只能根据机械位置推算指向，误差可能达到数度。通过对齐，精度可提升到 1-3 角分。\n\n" +
        "OnStepX 支持 1~9 星几何对齐，星数越多模型越精确。"
    ),
    HelpSection(
        "对齐流程",
        "1. 在星图上选择一颗校准星（建议选天顶附近的亮星）\n" +
        "2. 点击「GOTO」，赤道仪自动指向该星\n" +
        "3. 使用方向键将星点精确居中日视场中心\n" +
        "4. 点击「同步」，告诉赤道仪当前位置对应校准星的坐标\n" +
        "5. 重复上述步骤完成剩余校准星\n\n" +
        "提示：\n" +
        "• 第一颗星建议选天顶附近，误差最小\n" +
        "• 校准星应在天空不同区域均匀分布\n" +
        "• 北极星附近区域不宜选星（模型奇点）"
    ),
    HelpSection(
        "极轴对中的影响",
        "对齐前如已精确对极轴，对齐后的 GOTO 精度会显著提高。\n\n" +
        "极轴误差的影响：\n" +
        "• 误差 < 0.5°：对齐后可获最优 GOTO 精度\n" +
        "• 误差 < 3°：对齐后仍可正常使用\n" +
        "• 误差 > 5°：模型负担大，不同天空区域精度不均\n\n" +
        "建议：先粗对极轴（可用极轴镜或 Polar Align 功能），再做星点对齐。"
    ),
    HelpSection(
        "在 Extended.config.h 中配置",
        "// 最大对齐星数（AUTO 使用板型默认值）\n" +
        "#define ALIGN_MAX_STARS        AUTO    // 可选: 1~9, 或 AUTO\n\n" +
        "配置说明：\n" +
        "• 3星对齐：基础精度，约 5-10 角分\n" +
        "• 6星对齐：推荐精度，约 1-3 角分\n" +
        "• 9星对齐：最高精度，约 0.5-1 角分",
        "C++"
    )
)

/**
 * PEC 页面帮助内容
 */
val PEC_HELP_SECTIONS = listOf(
    HelpSection(
        "什么是 PEC",
        "PEC（Periodic Error Compensation，周期性误差补偿）是改善赤道仪跟踪精度的重要功能。\n\n" +
        "谐波减速机或蜗轮蜗杆在旋转一周时，由于机械加工精度限制，会产生周期性的跟踪误差。\n" +
        "典型振幅 10-50 角秒，周期对应输入轴旋转一圈。\n\n" +
        "PEC 通过记录每个旋转位置的误差量，在跟踪时提前补偿，可消除 80-90% 的周期误差。"
    ),
    HelpSection(
        "PEC 的工作原理",
        "PEC 的核心是一张一维查找表（缓存），对应蜗杆旋转一周的每一秒：\n\n" +
        "录制过程：\n" +
        "1. 开启自动导星，锁定目标星\n" +
        "2. OnStepX 记录每个蜗杆位置的导星修正量\n" +
        "3. 持续一个完整蜗杆周期（约 14.4 分钟）\n" +
        "4. 多圈数据加权平均，滤除随机干扰\n\n" +
        "回放过程：\n" +
        "在每个蜗杆位置，OnStepX 自动叠加对应的修正量到跟踪速度。\n" +
        "修正提前一秒执行，补偿图像采集到指令生效的延迟。\n\n" +
        "硬件传感器（可选）：每圈触发一次索引信号，确保缓存位置与蜗杆实际位置同步。"
    ),
    HelpSection(
        "PEC 的效果",
        "以你的谐波减速机配置为例：\n\n" +
        "• 谐波减速机输入轴一圈：25,600 微步\n" +
        "• 一圈耗时：约 862 秒（14.4 分钟）\n" +
        "• PEC 缓存：862 秒（需设为 900）\n\n" +
        "PEC 前后对比：\n" +
        "• 开启前：~40 角秒峰峰值周期误差\n" +
        "• 开启后：~3-5 角秒残余误差\n\n" +
        "残余误差主要来自谐波的高阶非周期性分量，可被导星轻松覆盖。"
    ),
    HelpSection(
        "在 Extended.config.h 中配置",
        "// 启用 PEC：设蜗杆每转步数（0=禁用）\n" +
        "#define PEC_STEPS_PER_WORM_ROTATION   25600\n\n" +
        "// PEC 缓存上限（秒），必须大于一圈时间\n" +
        "#define PEC_BUFFER_SIZE_LIMIT         900\n\n" +
        "// 硬件索传感器（可选）\n" +
        "#define PEC_SENSE                     HIGH    // OFF 禁用, HIGH/LOW 数字, THLD()|HYST() 模拟\n" +
        "#define PEC_SENSE_INIT                INPUT_PULLUP\n\n" +
        "// 蜗杆每转步数 = 电机每转步数 × 微步数 × 减速比\n" +
        "// 你的配置: 200(全步) × 32(微步) × 4(皮带) = 25,600",
        "C++"
    ),
    HelpSection(
        "硬件传感器安装",
        "推荐使用 A3144 霍尔传感器 + 磁铁：\n\n" +
        "• 在谐波减速机输入轴（大皮带轮）上粘一颗钕磁铁\n" +
        "• A3144 霍尔固定在支架上，对准磁铁轨迹\n" +
        "• 传感器与磁铁的间隙 1-3mm\n" +
        "• 接线：VCC→3.3V, GND→GND, OUT→PEC_SENSE_PIN\n\n" +
        "注意：磁铁要装牢，支架不能用弹性材料（尼龙扎带不行），\n" +
        "最好用 L 型金属支架固定霍尔传感器。"
    )
)

/**
 * 帮助弹窗
 */
@Composable
fun HelpDialog(
    title: String,
    sections: List<HelpSection>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
        shape = RoundedCornerShape(16.dp),
        containerColor = Secondary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = Accent2,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                sections.forEachIndexed { index, section ->
                    HelpSectionCard(section, index)
                    if (index < sections.size - 1) {
                        Spacer(Modifier.height(12.dp))
                        Divider(color = TextSecondary.copy(alpha = 0.2f))
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Accent2)
            }
        }
    )
}

@Composable
private fun HelpSectionCard(section: HelpSection, index: Int) {
    Column {
        Text(
            "${index + 1}. ${section.title}",
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            section.content,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        if (section.code != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    section.code,
                    color = Accent2.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
