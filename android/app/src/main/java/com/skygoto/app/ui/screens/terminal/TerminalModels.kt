package com.skygoto.app.ui.screens.terminal

import java.util.UUID

/**
 * 终端聊天消息数据类
 */
data class TerminalMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val hexMode: Boolean = false,
    val id: String = UUID.randomUUID().toString()
)

/**
 * LX200 指令参考条目
 */
data class CommandReference(
    val command: String,
    val description: String,
    val category: String,
    val hasParams: Boolean = false
)

/**
 * 所有 LX200 命令的分类参考数据
 */
val commandReferenceData: List<CommandReference> = listOf(
    // ============================================================
    // 1. 位置查询
    // ============================================================
    CommandReference(":GR", "获取当前赤经 (RA)", "位置查询"),
    CommandReference(":GD", "获取当前赤纬 (Dec)", "位置查询"),
    CommandReference(":GA", "获取当前高度 (Altitude)", "位置查询"),
    CommandReference(":GZ", "获取当前方位角 (Azimuth)", "位置查询"),
    CommandReference(":GRH", "获取赤经 - 最高精度", "位置查询"),
    CommandReference(":GDH", "获取赤纬 - 最高精度", "位置查询"),
    CommandReference(":GAH", "获取高度 - 最高精度", "位置查询"),
    CommandReference(":GZH", "获取方位角 - 最高精度", "位置查询"),
    CommandReference(":Gr", "获取目标赤经", "位置查询"),
    CommandReference(":Gd", "获取目标赤纬", "位置查询"),
    CommandReference(":Gal", "获取目标高度", "位置查询"),
    CommandReference(":Gz", "获取目标方位角", "位置查询"),
    CommandReference(":GrH", "获取目标赤经 - 最高精度", "位置查询"),
    CommandReference(":GdH", "获取目标赤纬 - 最高精度", "位置查询"),
    CommandReference(":GaH", "获取目标高度 - 最高精度", "位置查询"),
    CommandReference(":GzH", "获取目标方位 - 最高精度", "位置查询"),

    // ============================================================
    // 2. 时间和日期
    // ============================================================
    CommandReference(":Ga", "获取本地时间（12小时制）", "时间和日期"),
    CommandReference(":GL", "获取本地时间（24小时制）", "时间和日期"),
    CommandReference(":GLH", "获取本地时间 - 最高精度", "时间和日期"),
    CommandReference(":GS", "获取恒星时 (Sidereal Time)", "时间和日期"),
    CommandReference(":GSH", "获取恒星时 - 最高精度", "时间和日期"),
    CommandReference(":GC", "获取本地日期 (MM/DD/YY)", "时间和日期"),
    CommandReference(":Gc", "获取时间格式指示器 (24=24h)", "时间和日期"),
    CommandReference(":GG", "获取 UTC 偏移 (sHH:MM)", "时间和日期"),

    // ============================================================
    // 3. 追踪控制
    // ============================================================
    CommandReference(":Te", "开启追踪 (返回1=成功/0=失败)", "追踪控制"),
    CommandReference(":Td", "关闭追踪 (返回1=成功/0=失败)", "追踪控制"),
    CommandReference(":TQ", "恒星时追踪模式", "追踪控制"),
    CommandReference(":TS", "太阳追踪模式", "追踪控制"),
    CommandReference(":TL", "月球追踪模式", "追踪控制"),
    CommandReference(":TK", "King 追踪模式", "追踪控制"),
    CommandReference(":To", "启用全补偿模型", "追踪控制"),
    CommandReference(":Tr", "启用折射补偿", "追踪控制"),
    CommandReference(":Tn", "禁用补偿", "追踪控制"),
    CommandReference(":T1", "单轴追踪模式", "追踪控制"),
    CommandReference(":T2", "双轴追踪模式", "追踪控制"),
    CommandReference(":T+", "增加主恒星时钟 0.02Hz", "追踪控制"),
    CommandReference(":T-", "减少主恒星时钟 0.02Hz", "追踪控制"),
    CommandReference(":TR", "重置主恒星时钟", "追踪控制"),
    CommandReference(":GT", "获取当前跟踪速率 (Hz)", "追踪控制"),

    // ============================================================
    // 4. 目标设置 & GOTO
    // ============================================================
    CommandReference(":Sr HH:MM:SS", "设置目标赤经", "目标设置 & GOTO", hasParams = true),
    CommandReference(":Sd +DD*MM:SS", "设置目标赤纬", "目标设置 & GOTO", hasParams = true),
    CommandReference(":Sa +DD*MM", "设置目标高度", "目标设置 & GOTO", hasParams = true),
    CommandReference(":Sz DDD*MM", "设置目标方位角", "目标设置 & GOTO", hasParams = true),
    CommandReference(":MS", "执行 GOTO 到目标坐标", "目标设置 & GOTO"),
    CommandReference(":MA", "执行 Alt/Az GOTO", "目标设置 & GOTO"),
    CommandReference(":MN", "GOTO 到对面 pier side", "目标设置 & GOTO"),
    CommandReference(":MNe", "强制东侧 GOTO", "目标设置 & GOTO"),
    CommandReference(":MNw", "强制西侧 GOTO", "目标设置 & GOTO"),
    CommandReference(":MP", "极轴对齐 GOTO", "目标设置 & GOTO"),
    CommandReference(":D", "获取运动状态 (0x7f=移动中)", "目标设置 & GOTO"),

    // ============================================================
    // 5. 移动控制
    // ============================================================
    CommandReference(":Mn", "向北移动", "移动控制"),
    CommandReference(":Ms", "向南移动", "移动控制"),
    CommandReference(":Me", "向东移动", "移动控制"),
    CommandReference(":Mw", "向西移动", "移动控制"),
    CommandReference(":Q", "停止所有运动", "移动控制"),
    CommandReference(":Qe", "停止 RA 轴", "移动控制"),
    CommandReference(":Qn", "停止 Dec 轴", "移动控制"),
    CommandReference(":RG", "导星速率 1x", "移动控制"),
    CommandReference(":RC", "居中速率 8x", "移动控制"),
    CommandReference(":RM", "寻找速率 20x", "移动控制"),
    CommandReference(":RF", "快速速率 48x", "移动控制"),
    CommandReference(":RS", "GOTO 速率（最大速度）", "移动控制"),
    CommandReference(":R+", "增加当前速率", "移动控制"),
    CommandReference(":R-", "减少当前速率", "移动控制"),

    // ============================================================
    // 6. 同步操作
    // ============================================================
    CommandReference(":CS", "同步到当前目标坐标", "同步操作"),
    CommandReference(":CM", "同步到星表天体", "同步操作"),
    CommandReference(":A?", "获取对齐状态 (max,cur,last)", "同步操作"),

    // ============================================================
    // 7. 系统信息
    // ============================================================
    CommandReference(":GVP", "获取产品名称", "系统信息"),
    CommandReference(":GVN", "获取固件版本号", "系统信息"),
    CommandReference(":GVM", "获取固件名称+版本", "系统信息"),
    CommandReference(":GVD", "获取固件构建日期", "系统信息"),
    CommandReference(":GVT", "获取固件构建时间", "系统信息"),
    CommandReference(":GVC", "获取固件配置/产品描述", "系统信息"),
    CommandReference(":GVH", "获取硬件 pinmap 信息", "系统信息"),
    CommandReference(":GX9A", "获取环境温度 (C)", "系统信息"),
    CommandReference(":GX9B", "获取气压 (mbar)", "系统信息"),
    CommandReference(":GX9C", "获取相对湿度 (%)", "系统信息"),
    CommandReference(":GX9E", "获取露点温度 (C)", "系统信息"),
    CommandReference(":GX9F", "获取 MCU 温度 (C)", "系统信息"),
    CommandReference(":GE", "获取最后一个命令错误码", "系统信息"),

    // ============================================================
    // 8. 背隙设置
    // ============================================================
    CommandReference(":%BD", "获取 Dec/Alt 背隙 (arcsec)", "背隙设置"),
    CommandReference(":%BR", "获取 RA/Azm 背隙 (arcsec)", "背隙设置"),
    CommandReference(":${'$'}BD n", "设置 Dec/Alt 背隙", "背隙设置", hasParams = true),
    CommandReference(":${'$'}BR n", "设置 RA/Azm 背隙", "背隙设置", hasParams = true),

    // ============================================================
    // 9. Home/零位控制
    // ============================================================
    CommandReference(":hC", "移动到 Home 位置", "Home/零位控制"),
    CommandReference(":hF", "在零位重置赤道仪", "Home/零位控制"),
    CommandReference(":h?", "获取 Home 状态", "Home/零位控制"),
    CommandReference(":hA1", "启用启动时自动 Home", "Home/零位控制"),
    CommandReference(":hA0", "禁用启动时自动 Home", "Home/零位控制"),

    // ============================================================
    // 10. 日期时间设置
    // ============================================================
    CommandReference(":SC MM/DD/YY", "设置本地日期", "日期时间设置", hasParams = true),
    CommandReference(":SL HH:MM:SS", "设置本地时间", "日期时间设置", hasParams = true),
    CommandReference(":SG sHH:MM", "设置 UTC 偏移", "日期时间设置", hasParams = true),
    CommandReference(":Sv meters", "设置海拔高度 (米)", "日期时间设置", hasParams = true),

    // ============================================================
    // 11. 位置设置
    // ============================================================
    CommandReference(":St +DD*MM:SS", "设置纬度", "位置设置", hasParams = true),
    CommandReference(":Sg DDD*MM:SS", "设置经度", "位置设置", hasParams = true),
    CommandReference(":Gt", "获取当前纬度", "位置设置"),
    CommandReference(":Gg", "获取当前经度", "位置设置"),
    CommandReference(":GtH", "获取纬度 - 最高精度", "位置设置"),
    CommandReference(":GgH", "获取经度 - 最高精度", "位置设置"),

    // ============================================================
    // 12. 站点管理
    // ============================================================
    CommandReference(":W0", "选择站点槽位 0", "站点管理"),
    CommandReference(":W1", "选择站点槽位 1", "站点管理"),
    CommandReference(":W2", "选择站点槽位 2", "站点管理"),
    CommandReference(":W3", "选择站点槽位 3", "站点管理"),
    CommandReference(":W?", "获取当前活动站点槽位", "站点管理"),
    CommandReference(":GM", "获取站点1名称", "站点管理"),
    CommandReference(":GN", "获取站点2名称", "站点管理"),
    CommandReference(":GO", "获取站点3名称", "站点管理"),
    CommandReference(":GP", "获取站点4名称", "站点管理"),

    // ============================================================
    // 13. 轴控制
    // ============================================================
    CommandReference(":GX40", "获取 Axis1 仪器角度 (DDD*MM:SS)", "轴控制"),
    CommandReference(":GX41", "获取 Axis2 仪器角度 (DDD*MM:SS)", "轴控制"),
    CommandReference(":GX42", "获取 Axis1 仪器角度（十进制度）", "轴控制"),
    CommandReference(":GX43", "获取 Axis2 仪器角度（十进制度）", "轴控制"),
    CommandReference(":GX44", "获取 Axis1 编码器计数", "轴控制"),
    CommandReference(":GX45", "获取 Axis2 编码器计数", "轴控制"),
    CommandReference(":GXE4", "获取 Axis1 每度步数", "轴控制"),
    CommandReference(":GXE5", "获取 Axis2 每度步数", "轴控制"),
    CommandReference(":SX40 deg", "设置 Axis1 仪器角度", "轴控制", hasParams = true),
    CommandReference(":SX41 deg", "设置 Axis2 仪器角度", "轴控制", hasParams = true),

    // ============================================================
    // 14. 对齐模型
    // ============================================================
    CommandReference(":AW", "写入对齐模型到 NV 存储", "对齐模型"),
    CommandReference(":A?", "获取对齐状态", "对齐模型"),

    // ============================================================
    // 15. 其他命令
    // ============================================================
    CommandReference(":EC text", "回显文本到调试输出", "其他命令", hasParams = true),
    CommandReference(":B+", "增加分划板亮度", "其他命令"),
    CommandReference(":B-", "减少分划板亮度", "其他命令"),
    CommandReference(":ERESET", "重置 MCU", "其他命令"),
    CommandReference(":ENVRESET", "标记 NV 存储下次启动时清除", "其他命令"),
    CommandReference(":SEO", "保存绝对编码器原点", "其他命令"),
    CommandReference(":GXEM", "获取当前安装类型", "其他命令"),
)

/** 按分类分组的命令参考 */
val commandReferenceByCategory: Map<String, List<CommandReference>> =
    commandReferenceData.groupBy { it.category }
