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
    val hasParams: Boolean = false,
    val note: String = ""   // 额外提示 (返回值、格式等)
)

/**
 * 所有 LX200 命令的分类参考数据
 *
 * 基于 OnStepX 固件源码 (source/docs/COMMAND_REFERENCE.md) 整理。
 * 命令按子系统分类，涵盖 Mount/Focuser/Rotator/Aux 等。
 */
val commandReferenceData: List<CommandReference> = listOf(
    // ============================================================
    // 1. 位置查询 - Mount Position
    // ============================================================
    CommandReference(":GR", "获取当前赤经 (RA)", "位置查询", note = "返回 HH:MM:SS#"),
    CommandReference(":GD", "获取当前赤纬 (Dec)", "位置查询", note = "返回 sDD*MM:SS#"),
    CommandReference(":GA", "获取当前高度 (Altitude)", "位置查询", note = "返回 sDD*MM#"),
    CommandReference(":GZ", "获取当前方位角 (Azimuth)", "位置查询", note = "返回 DDD*MM#"),
    CommandReference(":GRH", "赤经 - 最高精度", "位置查询", note = "返回 HH:MM:SS.SSSS#"),
    CommandReference(":GDH", "赤纬 - 最高精度", "位置查询", note = "返回 sDD*MM:SS.SSS#"),
    CommandReference(":GAH", "高度 - 最高精度", "位置查询", note = "返回 sDD*MM'SS.SSS#"),
    CommandReference(":GZH", "方位角 - 最高精度", "位置查询", note = "返回 DDD*MM'SS.SSS#"),
    CommandReference(":Gr", "获取目标赤经", "位置查询"),
    CommandReference(":Gd", "获取目标赤纬", "位置查询"),
    CommandReference(":Gal", "获取目标高度", "位置查询"),
    CommandReference(":Gz", "获取目标方位角", "位置查询"),
    CommandReference(":GrH", "目标赤经 - 最高精度", "位置查询"),
    CommandReference(":GdH", "目标赤纬 - 最高精度", "位置查询"),
    CommandReference(":GaH", "目标高度 - 最高精度", "位置查询"),
    CommandReference(":GzH", "目标方位角 - 最高精度", "位置查询"),
    CommandReference(":GT", "获取跟踪速率 (Hz, 0=停止)", "位置查询", note = "返回 trackingRate × 60.164，参见 :GXF3 获取真实电机频率"),

    // ============================================================
    // 2. 时间和日期 - Time / Date / Site
    // ============================================================
    CommandReference(":Ga", "获取本地时间（12小时制）", "时间和日期", note = "HH:MM:SS#"),
    CommandReference(":GL", "获取本地时间（24小时制）", "时间和日期", note = "HH:MM:SS#"),
    CommandReference(":GLH", "本地时间 - 最高精度", "时间和日期", note = "HH:MM:SS.SSSS#"),
    CommandReference(":GS", "获取恒星时 (Sidereal Time)", "时间和日期", note = "HH:MM:SS#"),
    CommandReference(":GSH", "恒星时 - 最高精度", "时间和日期", note = "HH:MM:SS.SS#"),
    CommandReference(":GC", "获取本地日期", "时间和日期", note = "MM/DD/YY#"),
    CommandReference(":Gc", "获取时间格式指示器", "时间和日期", note = "24# = 24h制"),
    CommandReference(":GG", "获取 UTC 偏移", "时间和日期", note = "sHH:MM#"),
    CommandReference(":GX80", "获取 UT1 时间", "时间和日期", note = "HH:MM:SS.SS#"),
    CommandReference(":GX81", "获取 UT1 日期", "时间和日期", note = "MM/DD/YY#"),
    CommandReference(":GX89", "日期/时间就绪状态", "时间和日期", note = "0=就绪, 1=未就绪"),

    // ============================================================
    // 3. 追踪控制 - Tracking Control
    // ============================================================
    CommandReference(":Te", "开启追踪", "追踪控制", note = "返回 1/0"),
    CommandReference(":Td", "关闭追踪", "追踪控制", note = "返回 1/0"),
    CommandReference(":TQ", "恒星时追踪模式", "追踪控制"),
    CommandReference(":TS", "太阳追踪模式", "追踪控制"),
    CommandReference(":TL", "月球追踪模式", "追踪控制"),
    CommandReference(":TK", "King 追踪模式", "追踪控制"),
    CommandReference(":To", "启用全补偿模型", "追踪控制", note = "返回 1/0"),
    CommandReference(":Tr", "启用折射补偿", "追踪控制", note = "返回 1/0"),
    CommandReference(":Tn", "禁用补偿", "追踪控制", note = "返回 1/0"),
    CommandReference(":T1", "单轴追踪模式", "追踪控制", note = "返回 1/0"),
    CommandReference(":T2", "双轴追踪模式", "追踪控制", note = "返回 1/0"),
    CommandReference(":ST n.n", "[SET] 跟踪速率 (Hz, 0=停止)", "追踪控制", hasParams = true, note = "标准格式：:ST 29.704#"),
    CommandReference(":T+", "增加主恒星时钟 0.02Hz", "追踪控制"),
    CommandReference(":T-", "减少主恒星时钟 0.02Hz", "追踪控制"),
    CommandReference(":TR", "重置主恒星时钟", "追踪控制"),
    CommandReference(":GXTD", "获取 Dec 追踪速率偏移", "追踪控制", note = "arcsec/恒星秒"),
    CommandReference(":GXTR", "获取 RA 追踪速率偏移", "追踪控制", note = "arcsec/恒星秒"),
    CommandReference(":SXTD,n.n", "[SET] Dec 追踪速率偏移 (arcsec/恒星秒)", "追踪控制", hasParams = true),
    CommandReference(":SXTR,n.n", "[SET] RA 追踪速率偏移 (arcsec/恒星秒)", "追踪控制", hasParams = true),

    // ============================================================
    // 4. 目标设置 & GOTO
    // ============================================================
    CommandReference(":Sr HH:MM:SS", "设置目标赤经", "目标设置 & GOTO", hasParams = true),
    CommandReference(":Sd +DD*MM:SS", "设置目标赤纬", "目标设置 & GOTO", hasParams = true),
    CommandReference(":Sa +DD*MM", "设置目标高度", "目标设置 & GOTO", hasParams = true),
    CommandReference(":Sz DDD*MM", "设置目标方位角", "目标设置 & GOTO", hasParams = true),
    CommandReference(":MS", "执行 GOTO 到目标坐标", "目标设置 & GOTO", note = "返回 0-9, 0=成功"),
    CommandReference(":MA", "执行 Alt/Az GOTO", "目标设置 & GOTO", note = "返回 0-9, 0=成功"),
    CommandReference(":MN", "GOTO 到对面 pier side", "目标设置 & GOTO", note = "返回 0-9"),
    CommandReference(":MNe", "强制东侧 GOTO", "目标设置 & GOTO", note = "返回 0-9"),
    CommandReference(":MNw", "强制西侧 GOTO", "目标设置 & GOTO", note = "返回 0-9"),
    CommandReference(":MP", "极轴对齐 GOTO", "目标设置 & GOTO", note = "返回 0-9"),
    CommandReference(":MD", "获取目标 pier side", "目标设置 & GOTO", note = "0=东, 1=西, 2=错误"),
    CommandReference(":D", "获取运动状态", "目标设置 & GOTO", note = "0x7f=移动中, #=静止"),
    // GOTO 扩展设置
    CommandReference(":GX92", "获取当前 GOTO 周期 (us/step)", "目标设置 & GOTO"),
    CommandReference(":GX93", "获取默认 GOTO 周期", "目标设置 & GOTO"),
    CommandReference(":GX94", "获取当前 pier side", "目标设置 & GOTO", note = "0=无, 1=东, 2=西"),
    CommandReference(":GX95", "自动中天翻转是否启用", "目标设置 & GOTO", note = "0/1"),
    CommandReference(":GX96", "获取首选 pier side", "目标设置 & GOTO", note = "E/W/B/A"),
    CommandReference(":GX97", "获取当前步进速率 (deg/s)", "目标设置 & GOTO"),
    CommandReference(":GX99", "获取最快 GOTO 周期", "目标设置 & GOTO"),
    CommandReference(":SX92,n.nnn", "[SET] GOTO 周期 (us/step)", "目标设置 & GOTO", hasParams = true),
    CommandReference(":SX95,0/1", "[SET] 禁用/启用自动中天翻转", "目标设置 & GOTO", hasParams = true),
    CommandReference(":SX96,E/W/B/A", "[SET] 首选 pier side", "目标设置 & GOTO", hasParams = true),

    // ============================================================
    // 5. 移动控制 - Movement / Guide
    // ============================================================
    CommandReference(":Mn", "向北移动", "移动控制"),
    CommandReference(":Ms", "向南移动", "移动控制"),
    CommandReference(":Me", "向东移动", "移动控制"),
    CommandReference(":Mw", "向西移动", "移动控制"),
    CommandReference(":Mp", "螺旋搜索运动", "移动控制"),
    CommandReference(":Q", "停止所有运动 / 中止 GOTO", "移动控制"),
    CommandReference(":Qe", "停止 RA 轴 (东/西)", "移动控制"),
    CommandReference(":Qn", "停止 Dec 轴 (北/南)", "移动控制"),
    CommandReference(":RG", "导星速率 1x", "移动控制"),
    CommandReference(":RC", "居中速率 8x", "移动控制"),
    CommandReference(":RM", "寻找速率 20x", "移动控制"),
    CommandReference(":RF", "快速速率 48x", "移动控制"),
    CommandReference(":RS", "GOTO 速率（最大速度半速）", "移动控制"),
    CommandReference(":R+", "增加当前速率", "移动控制"),
    CommandReference(":R-", "减少当前速率", "移动控制"),
    CommandReference(":R0..R9", "数字速率预设 (0=最快, 9=最慢)", "移动控制"),
    CommandReference(":RA n.n", "设置 Axis1 自定义导星速率 (deg/s)", "移动控制", hasParams = true),
    CommandReference(":RE n.n", "设置 Axis2 自定义导星速率 (deg/s)", "移动控制", hasParams = true),
    // 脉冲导星
    CommandReference(":Mge nnn", "脉冲导星 - 向东 (ms)", "移动控制", hasParams = true),
    CommandReference(":Mgw nnn", "脉冲导星 - 向西 (ms)", "移动控制", hasParams = true),
    CommandReference(":Mgn nnn", "脉冲导星 - 向北 (ms)", "移动控制", hasParams = true),
    CommandReference(":Mgs nnn", "脉冲导星 - 向南 (ms)", "移动控制", hasParams = true),
    CommandReference(":GX90", "获取脉冲导星速率", "移动控制"),

    // ============================================================
    // 6. 同步与校准 - Sync / Alignment
    // ============================================================
    CommandReference(":CS", "同步到当前目标坐标", "同步与校准"),
    CommandReference(":CM", "同步到星表天体", "同步与校准", note = "返回 N/A# 或 En#"),
    CommandReference(":A?", "获取对齐状态", "同步与校准", note = "返回 max,cur,last#"),
    CommandReference(":A1..A9", "开始 n 星校准", "同步与校准", note = "1-9 星对齐"),
    CommandReference(":A+", "接受已居中的校准星", "同步与校准", note = "返回 1/0"),
    CommandReference(":AW", "写入对齐模型到 NV 存储", "同步与校准"),
    // 对齐模型参数 (仅 ALIGN_MAX_NUM_STARS > 1 时可用)
    CommandReference(":GX00", "获取 ax1Cor (arcsec)", "同步与校准"),
    CommandReference(":GX01", "获取 ax2Cor (arcsec)", "同步与校准"),
    CommandReference(":GX02", "获取 altCor (arcsec)", "同步与校准"),
    CommandReference(":GX03", "获取 azmCor (arcsec)", "同步与校准"),
    CommandReference(":GX04", "获取 doCor (arcsec)", "同步与校准"),
    CommandReference(":GX05", "获取 pdCor (arcsec)", "同步与校准"),
    CommandReference(":GX08", "获取 tfCor (arcsec)", "同步与校准"),
    CommandReference(":GX09", "上传星点数 / 重置索引", "同步与校准"),

    // ============================================================
    // 7. 系统信息 - System Info
    // ============================================================
    CommandReference(":GVN", "获取固件版本号", "系统信息", note = "M.mm...#"),
    CommandReference(":GVM", "获取固件名称+版本", "系统信息"),
    CommandReference(":GVP", "获取产品名称", "系统信息"),
    CommandReference(":GVD", "获取固件构建日期", "系统信息", note = "MTH DD YYYY#"),
    CommandReference(":GVT", "获取固件构建时间", "系统信息", note = "HH:MM:SS#"),
    CommandReference(":GVC", "获取固件配置/产品描述", "系统信息"),
    CommandReference(":GVH", "获取硬件 pinmap 信息", "系统信息"),
    CommandReference(":GE", "获取最后一个命令错误码", "系统信息", note = "返回 2 位数字码"),
    CommandReference(":GX9A", "获取环境温度 (C)", "系统信息"),
    CommandReference(":GX9B", "获取气压 (mbar)", "系统信息"),
    CommandReference(":GX9C", "获取相对湿度 (%)", "系统信息"),
    CommandReference(":GX9E", "获取露点温度 (C)", "系统信息"),
    CommandReference(":GX9F", "获取 MCU 温度 (C)", "系统信息"),
    CommandReference(":EC text", "[SET] 回显文本到调试输出", "系统信息", hasParams = true, note = "_=空格, &=换行"),
    CommandReference(":ERESET", "[SET] 重置 MCU", "系统信息"),
    CommandReference(":ENVRESET", "[SET] 标记 NV 存储下次启动时清除", "系统信息"),
    CommandReference(":ESPFLASH", "[SET] ESP 设备进入固件刷写模式", "系统信息"),
    // 环境传感器设置
    CommandReference(":SX9A,sn.n", "[SET] 环境温度 (C)", "系统信息", hasParams = true),
    CommandReference(":SX9B,n.n", "[SET] 气压 (mbar)", "系统信息", hasParams = true),
    CommandReference(":SX9C,n.n", "[SET] 湿度 (%)", "系统信息", hasParams = true),

    // ============================================================
    // 8. 背隙设置 - Backlash
    // ============================================================
    CommandReference(":%BD", "获取 Dec/Alt 背隙 (arcsec)", "背隙设置"),
    CommandReference(":%BR", "获取 RA/Azm 背隙 (arcsec)", "背隙设置"),
    CommandReference(":" + '$' + "BD n", "[SET] Dec/Alt 背隙 (arcsec)", "背隙设置", hasParams = true, note = "值直接接在 \$BD 后，如 :\$BD30#"),
    CommandReference(":" + '$' + "BR n", "[SET] RA/Azm 背隙 (arcsec)", "背隙设置", hasParams = true, note = "值直接接在 \$BR 后，如 :\$BR20#"),

    // ============================================================
    // 9. Home/零位控制
    // ============================================================
    CommandReference(":hC", "移动到 Home 位置", "Home/零位控制"),
    CommandReference(":hF", "在零位重置赤道仪（冷启动）", "Home/零位控制"),
    CommandReference(":h?", "获取 Home 状态", "Home/零位控制", note = "hasSense,axis1Off,axis2Off#"),
    CommandReference(":hA1", "启用启动时自动 Home", "Home/零位控制"),
    CommandReference(":hA0", "禁用启动时自动 Home", "Home/零位控制"),
    CommandReference(":hC1,R", "切换 Axis1 Home 感应反向", "Home/零位控制"),
    CommandReference(":hC1 n", "设置 Axis1 Home 偏移 (arcsec)", "Home/零位控制", hasParams = true),
    CommandReference(":hC2,R", "切换 Axis2 Home 感应反向", "Home/零位控制"),
    CommandReference(":hC2 n", "设置 Axis2 Home 偏移 (arcsec)", "Home/零位控制", hasParams = true),

    // ============================================================
    // 10. 停放控制 - Park
    // ============================================================
    CommandReference(":hP", "移动到停放位置 (Park)", "停放控制", note = "返回 1/0"),
    CommandReference(":hQ", "将当前位置设为停放位", "停放控制", note = "返回 1/0"),
    CommandReference(":hR", "解除停放", "停放控制", note = "返回 1/0"),

    // ============================================================
    // 11. 日期时间设置
    // ============================================================
    CommandReference(":SC MM/DD/YY", "设置本地日期", "日期时间设置", hasParams = true),
    CommandReference(":SL HH:MM:SS", "设置本地时间", "日期时间设置", hasParams = true),
    CommandReference(":SG sHH:MM", "设置 UTC 偏移", "日期时间设置", hasParams = true, note = "MM 应为 00/30/45"),
    CommandReference(":Sv meters", "设置海拔高度 (米)", "日期时间设置", hasParams = true),
    CommandReference(":SU s.s", "设置 DUT1 修正 (秒)", "日期时间设置", hasParams = true, note = "范围 -0.9 ~ +0.9"),

    // ============================================================
    // 12. 位置设置 - Site Position
    // ============================================================
    CommandReference(":St +DD*MM:SS", "设置纬度", "位置设置", hasParams = true),
    CommandReference(":Sg DDD*MM:SS", "设置经度", "位置设置", hasParams = true),
    CommandReference(":Gt", "获取当前纬度", "位置设置"),
    CommandReference(":Gg", "获取当前经度", "位置设置"),
    CommandReference(":GtH", "纬度 - 最高精度", "位置设置"),
    CommandReference(":GgH", "经度 - 最高精度", "位置设置"),
    CommandReference(":Gv", "获取海拔高度 (米)", "位置设置"),

    // ============================================================
    // 13. 站点管理 - Site Management
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
    CommandReference(":SM name", "[SET] 站点1名称 (最多15字符)", "站点管理", hasParams = true),
    CommandReference(":SN name", "[SET] 站点2名称", "站点管理", hasParams = true),
    CommandReference(":SO name", "[SET] 站点3名称", "站点管理", hasParams = true),
    CommandReference(":SP name", "[SET] 站点4名称", "站点管理", hasParams = true),

    // ============================================================
    // 14. 轴控制 - Axis Control
    // ============================================================
    CommandReference(":GX40", "Axis1 仪器角度 (DDD:MM:SS)", "轴控制"),
    CommandReference(":GX41", "Axis2 仪器角度 (DDD:MM:SS)", "轴控制"),
    CommandReference(":GX42", "Axis1 仪器角度（十进制度）", "轴控制"),
    CommandReference(":GX43", "Axis2 仪器角度（十进制度）", "轴控制"),
    CommandReference(":GX44", "Axis1 编码器计数", "轴控制"),
    CommandReference(":GX45", "Axis2 编码器计数", "轴控制"),
    CommandReference(":GXE4", "Axis1 每度步数", "轴控制"),
    CommandReference(":GXE5", "Axis2 每度步数", "轴控制"),
    CommandReference(":GXF3", "Axis1 实际步进频率 (步/秒)", "轴控制", note = "正=正转，负=反转"),
    CommandReference(":GXF4", "Axis2 实际步进频率 (步/秒)", "轴控制", note = "正=正转，负=反转"),
    CommandReference(":GXEE", "安装坐标模式", "轴控制", note = "0=equ, 1=hor (MOUNT_COORDS-1)"),
    CommandReference(":GXEF", "Axis2 切线臂标记", "轴控制", note = "0/1"),
    CommandReference(":GXEG", "Axis1 扇形齿轮标记", "轴控制", note = "0/1"),
    CommandReference(":GXEM", "获取当前安装类型", "轴控制", note = "1=GEM, 2=FORK, 3=ALTAZM..."),
    CommandReference(":GXFF", "Axis1 索引位置", "轴控制"),
    CommandReference(":GXFG", "Axis2 索引位置", "轴控制"),
    CommandReference(":SX40,deg", "[SET] Axis1 仪器角度", "轴控制", hasParams = true),
    CommandReference(":SX41,deg", "[SET] Axis2 仪器角度", "轴控制", hasParams = true),
    CommandReference(":SX42,1", "[SET] 从编码器同步安装角度", "轴控制"),
    CommandReference(":SX44,d1,d2[a]", "[SET] 编码器分阶段+同步", "轴控制", hasParams = true, note = "a=绝对可信"),
    CommandReference(":SXEM,n", "[SET] 设置安装类型 (下次重启生效)", "轴控制", hasParams = true),
    CommandReference(":SEO", "保存绝对编码器原点", "轴控制"),
    CommandReference(":GXA1-9,p", "获取轴参数 (轴号,参数索引)", "轴控制", hasParams = true),
    CommandReference(":SXAC,0/1", "[SET] 使用 NV/编译时轴设置", "轴控制", hasParams = true),
    CommandReference(":SXA1-9,R", "[SET] 恢复轴参数为默认值", "轴控制", hasParams = true, note = "下次启动生效"),

    // ============================================================
    // 15. 极限设置 - Limits
    // ============================================================
    CommandReference(":Gh", "获取地平限制 (最低仰角)", "极限设置", note = "sDD*#"),
    CommandReference(":Go", "获取天顶限制 (最高仰角)", "极限设置", note = "DD*#"),
    CommandReference(":GXE9", "获取东侧中天限制 (分钟)", "极限设置"),
    CommandReference(":GXEA", "获取西侧中天限制 (分钟)", "极限设置"),
    CommandReference(":GXEe", "Axis1 最小限制 (度)", "极限设置"),
    CommandReference(":GXEw", "Axis1 最大限制 (度)", "极限设置"),
    CommandReference(":GXEB", "Axis1 最大限制 (小时)", "极限设置"),
    CommandReference(":GXEC", "Axis2 最小限制 (度)", "极限设置"),
    CommandReference(":GXED", "Axis2 最大限制 (度)", "极限设置"),
    CommandReference(":Sh sDD", "[SET] 地平高度限制", "极限设置", hasParams = true, note = "值直接接在 Sh 后，如 :Sh-10# 范围 -30~+30°"),
    CommandReference(":So DD", "[SET] 天顶高度限制", "极限设置", hasParams = true, note = "值直接接在 So 后，如 :So80# 范围 60~90°"),
    CommandReference(":SXE9,n", "[SET] 东侧中天限制 (分钟)", "极限设置", hasParams = true),
    CommandReference(":SXEA,n", "[SET] 西侧中天限制 (分钟)", "极限设置", hasParams = true),

    // ============================================================
    // 16. 状态查询 - Mount Status
    // ============================================================
    CommandReference(":Gm", "获取中天侧 (Pier Side)", "状态查询", note = "E/W/N#"),
    CommandReference(":GU", "获取完整状态字符串", "状态查询", note = "多字符状态码"),
    CommandReference(":Gu", "获取位打包状态 (9字节)", "状态查询", note = "高级调试使用"),
    CommandReference(":GW", "获取 4 字符基本状态", "状态查询", note = "G/P/A + T/N + P/H + 1/0"),
    CommandReference(":GXFA", "获取工作负载 (占位)", "状态查询", note = "返回 50%#"),
    CommandReference(":SX97,0/1/2/3/4", "[SET] 蜂鸣器控制", "状态查询", hasParams = true, note = "0=关 1=开 2=哔 3=警报 4=咔"),

    // ============================================================
    // 17. 波特率设置 - Baud Rate
    // ============================================================
    CommandReference(":SB0", "[SET] 波特率 115200", "波特率设置"),
    CommandReference(":SB1", "[SET] 波特率 56700", "波特率设置"),
    CommandReference(":SB2", "[SET] 波特率 38400", "波特率设置"),
    CommandReference(":SB3", "[SET] 波特率 28800", "波特率设置"),
    CommandReference(":SB4", "[SET] 波特率 19200", "波特率设置"),
    CommandReference(":SB5", "[SET] 波特率 14400", "波特率设置"),
    CommandReference(":SB6", "[SET] 波特率 9600", "波特率设置"),
    CommandReference(":SB7", "[SET] 波特率 4800", "波特率设置"),
    CommandReference(":SB8", "[SET] 波特率 2400", "波特率设置"),
    CommandReference(":SB9", "[SET] 波特率 1200", "波特率设置"),
    CommandReference(":SBA", "[SET] 波特率 230400", "波特率设置"),
    CommandReference(":SBB", "[SET] 波特率 460800", "波特率设置"),

    // ============================================================
    // 18. 分划板亮度 - Reticle
    // ============================================================
    CommandReference(":B+", "[SET] 增加分划板亮度", "分划板亮度"),
    CommandReference(":B-", "[SET] 减少分划板亮度", "分划板亮度"),

    // ============================================================
    // 19. 调焦器 - Focuser
    // ============================================================
    CommandReference(":FA", "[GET] 当前调焦器编号", "调焦器"),
    CommandReference(":FA1..FA6", "[SET] 选择调焦器 (1-6)", "调焦器", hasParams = true),
    CommandReference(":F+", "[SET] 向内移动 (微米单位)", "调焦器"),
    CommandReference(":F-", "[SET] 向外移动 (微米单位)", "调焦器"),
    CommandReference(":FQ", "[SET] 停止调焦器", "调焦器"),
    CommandReference(":FG", "[GET] 当前位置 (微米)", "调焦器"),
    CommandReference(":FS n", "[SET] 绝对 GOTO 到位置", "调焦器", hasParams = true),
    CommandReference(":FR n", "[SET] 相对 GOTO n 步", "调焦器", hasParams = true),
    CommandReference(":FZ", "[SET] 归零当前位置", "调焦器"),
    CommandReference(":FH", "[SET] 设当前位置为 Home", "调焦器"),
    CommandReference(":Fh", "[SET] 移动到 Home 位置", "调焦器"),
    CommandReference(":FT", "[GET] 调焦器状态+速率", "调焦器"),
    CommandReference(":Fp", "[GET] 模式 (1=DC/伪绝对)", "调焦器"),
    CommandReference(":FI", "[GET] 最小位置 (微米)", "调焦器"),
    CommandReference(":FM", "[GET] 最大位置 (微米)", "调焦器"),
    CommandReference(":Fe", "[GET] TCF 温差", "调焦器"),
    CommandReference(":Ft", "[GET] 调焦器温度 (C)", "调焦器"),
    CommandReference(":Fu", "[GET] 微米/步比例", "调焦器"),
    CommandReference(":FB n", "设置背隙", "调焦器", hasParams = true),
    CommandReference(":FC sn.n", "设置 TCF 系数 (微米/C)", "调焦器", hasParams = true),
    CommandReference(":Fc 0/1", "禁用/启用 TCF", "调焦器", hasParams = true),
    CommandReference(":F1..F9", "设置移动/GOTO速率预设", "调焦器"),
    CommandReference(":FW", "获取工作 GOTO 速率 (um/s)", "调焦器"),
    CommandReference(":FP n", "设置 DC 马达功率 (%)", "调焦器", hasParams = true),
    CommandReference(":FD n", "设置 TCF 死区", "调焦器", hasParams = true),

    // ============================================================
    // 20. 旋转器 - Rotator
    // ============================================================
    CommandReference(":rA", "[GET] 旋转器是否激活", "旋转器"),
    CommandReference(":rT", "[GET] 旋转器状态+速率", "旋转器"),
    CommandReference(":r>", "[SET] 顺时针旋转", "旋转器"),
    CommandReference(":r<", "[SET] 逆时针旋转", "旋转器"),
    CommandReference(":rQ", "[SET] 停止旋转", "旋转器"),
    CommandReference(":rG", "[GET] 当前角度", "旋转器"),
    CommandReference(":rS +DD", "[SET] 绝对 GOTO 到角度", "旋转器", hasParams = true),
    CommandReference(":rr +DD", "[SET] 相对旋转", "旋转器", hasParams = true),
    CommandReference(":rZ", "[SET] 归零当前位置", "旋转器"),
    CommandReference(":rF", "[SET] 设为半行程位置", "旋转器"),
    CommandReference(":rC", "[SET] 移动到半行程/Home", "旋转器"),
    CommandReference(":rI", "[GET] 最小角度 (度)", "旋转器"),
    CommandReference(":rM", "[GET] 最大角度 (度)", "旋转器"),
    CommandReference(":rD", "[GET] 度/步比例", "旋转器"),
    CommandReference(":rb n", "设置背隙 (步)", "旋转器", hasParams = true),
    CommandReference(":r1..r9", "设置移动/GOTO速率预设", "旋转器"),
    CommandReference(":rW", "获取工作 GOTO 速率 (deg/s)", "旋转器"),
    CommandReference(":r+", "启用消旋 (derotation)", "旋转器"),
    CommandReference(":r-", "禁用消旋", "旋转器"),
    CommandReference(":rP", "移动到视场角 (parallactic)", "旋转器"),
    CommandReference(":rR", "切换消旋反向", "旋转器"),
    CommandReference(":GX98", "旋转器能力查询", "旋转器", note = "D=消旋, R=仅旋转, N=无"),

    // ============================================================
    // 21. PEC - 周期性误差补偿
    // ============================================================
    CommandReference(":" + '$' + "QZ?", "获取 PEC 状态", "PEC", note = "I=关 p=待播放 P=播放中 r=待录 R=录制中"),
    CommandReference(":" + '$' + "QZ+", "启用 PEC 回放", "PEC"),
    CommandReference(":" + '$' + "QZ-", "禁用 PEC", "PEC"),
    CommandReference(":" + '$' + "QZ/", "开始录制 PEC", "PEC"),
    CommandReference(":" + '$' + "QZZ", "清空 PEC 缓存", "PEC"),
    CommandReference(":" + '$' + "QZ!", "写入 PEC 数据到 NV", "PEC"),
    CommandReference(":GXE7", "获取蜗杆步数", "PEC"),
    CommandReference(":GXE8", "获取 PEC 缓存大小 (秒)", "PEC"),
    CommandReference(":SXE7,n", "[SET] 蜗杆步数", "PEC", hasParams = true),
    CommandReference(":VH", "PEC 索引位置 (恒星秒)", "PEC"),
    CommandReference(":VR", "读取当前 PEC 修正", "PEC"),
    CommandReference(":VS", "蜗杆速 (步/恒星秒)", "PEC"),
    CommandReference(":VW", "蜗杆旋转步数", "PEC"),
    CommandReference(":WR+", "PEC 表前移1秒", "PEC"),
    CommandReference(":WR-", "PEC 表后移1秒", "PEC"),
    CommandReference(":WR n,sn", "写入 PEC 修正值", "PEC", hasParams = true),

    // ============================================================
    // 22. 星表/目标库 - Object Library
    // ============================================================
    CommandReference(":LI", "获取当前天体名称+类型", "星表/目标库"),
    CommandReference(":LR", "获取当前天体信息并前进", "星表/目标库", note = "name,type,ra,dec#"),
    CommandReference(":LN", "下一个匹配天体", "星表/目标库"),
    CommandReference(":LB", "上一个匹配天体", "星表/目标库"),
    CommandReference(":LC n", "选择记录编号 n", "星表/目标库", hasParams = true),
    CommandReference(":LIG", "装载当前天体到 GOTO 目标", "星表/目标库"),
    CommandReference(":LW name,type", "写入当前目标到新记录", "星表/目标库", hasParams = true),
    CommandReference(":LD", "清除当前记录", "星表/目标库"),
    CommandReference(":LL", "清除当前星表", "星表/目标库"),
    CommandReference(":L!", "清除所有星表", "星表/目标库"),
    CommandReference(":L?", "获取空闲记录数", "星表/目标库"),
    CommandReference(":Lo n", "选择星表 (0-14)", "星表/目标库", hasParams = true),
    CommandReference(":L" + '$', "移动到星表名称记录", "星表/目标库"),

    // ============================================================
    // 23. 辅助功能 - Auxiliary Features
    // ============================================================
    CommandReference(":GXY0", "获取功能槽位位图", "辅助功能", note = "8字符位图, 1=存在"),
    CommandReference(":GXY n", "获取槽位名称+用途", "辅助功能", hasParams = true),
    CommandReference(":GXX n", "获取槽位状态", "辅助功能", hasParams = true, note = "取决于用途"),
    CommandReference(":SXX,n,Vv", "[SET] 辅助槽位值", "辅助功能", hasParams = true),
)

/** 按分类分组的命令参考 */
val commandReferenceByCategory: Map<String, List<CommandReference>> =
    commandReferenceData.groupBy { it.category }
