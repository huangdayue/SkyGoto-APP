package com.skygoto.app.data.protocol

/**
 * LX200 协议命令常量
 * 
 * 基于 OnStepX COMMAND_REFERENCE.md 完整命令集
 * 所有命令以 ":" 开头，以 "#" 结尾（发送时自动添加）
 * 
 * @see <a href="https://onstepx.com">OnStepX 官方文档</a>
 */
object LX200Commands {
    
    // ============================================================
    // 位置查询 - Mount Position / Tracking / Rates
    // ============================================================
    
    // 当前位置查询
    const val GET_RA = ":GR"             // 获取当前赤经 (Right Ascension)
    const val GET_DEC = ":GD"            // 获取当前赤纬 (Declination)
    const val GET_ALT = ":GA"            // 获取当前高度 (Altitude)
    const val GET_AZ = ":GZ"            // 获取当前方位角 (Azimuth)
    const val GET_TRACKING = ":GT"       // 获取跟踪速率 (Hz, 0=停止追踪)
    
    // 高精度位置查询
    const val GET_RA_HIGH = ":GRH"       // 获取当前赤经 - 最高精度
    const val GET_DEC_HIGH = ":GDH"      // 获取当前赤纬 - 最高精度
    const val GET_ALT_HIGH = ":GAH"      // 获取当前高度 - 最高精度
    const val GET_AZ_HIGH = ":GZH"       // 获取当前方位 - 最高精度
    
    // 目标位置查询
    const val GET_TARGET_RA = ":Gr"      // 获取目标赤经
    const val GET_TARGET_DEC = ":Gd"    // 获取目标赤纬
    const val GET_TARGET_ALT = ":Gal"    // 获取目标高度
    const val GET_TARGET_AZ = ":Gz"      // 获取目标方位角
    
    // 高精度目标位置查询
    const val GET_TARGET_RA_HIGH = ":GrH"    // 获取目标赤经 - 最高精度
    const val GET_TARGET_DEC_HIGH = ":GdH"   // 获取目标赤纬 - 最高精度
    const val GET_TARGET_ALT_HIGH = ":GaH"   // 获取目标高度 - 最高精度
    const val GET_TARGET_AZ_HIGH = ":GzH"    // 获取目标方位 - 最高精度
    
    // ============================================================
    // 时间和日期 - Time / Date / Site
    // ============================================================
    
    // 时间查询
    const val GET_LOCAL_TIME_12H = ":Ga"    // 获取本地时间（12小时制）
    const val GET_LOCAL_TIME_24H = ":GL"    // 获取本地时间（24小时制）
    const val GET_LOCAL_TIME_HIGH = ":GLH"  // 获取本地时间 - 最高精度
    const val GET_SIDEREAL_TIME = ":GS"     // 获取恒星时 (Sidereal Time)
    const val GET_SIDEREAL_TIME_HIGH = ":GSH" // 获取恒星时 - 最高精度
    
    // 日期查询
    const val GET_DATE = ":GC"            // 获取本地日期 (MM/DD/YY)
    const val GET_TIME_FORMAT = ":Gc"     // 获取时间格式指示器 (24=24小时制)
    
    // UTC偏移查询
    const val GET_UTC_OFFSET = ":GG"      // 获取UTC偏移 (sHH:MM)
    const val GET_TIMEZONE_OFFSET = ":GG"  // 获取时区偏移 (别名)
    
    // 站点位置查询
    const val GET_LONGITUDE = ":Gg"       // 获取当前经度
    const val GET_LATITUDE = ":Gt"        // 获取当前纬度
    const val GET_LONGITUDE_HIGH = ":GgH" // 获取经度 - 最高精度
    const val GET_LATITUDE_HIGH = ":GtH"  // 获取纬度 - 最高精度
    const val GET_ELEVATION = ":Gv"       // 获取海拔高度 (米)
    
    // 站点名称查询
    const val GET_SITE_NAME_1 = ":GM"     // 获取站点1名称
    const val GET_SITE_NAME_2 = ":GN"     // 获取站点2名称
    const val GET_SITE_NAME_3 = ":GO"     // 获取站点3名称
    const val GET_SITE_NAME_4 = ":GP"     // 获取站点4名称
    
    // 站点选择
    const val SELECT_SITE_0 = ":W0"       // 选择站点槽位0
    const val SELECT_SITE_1 = ":W1"       // 选择站点槽位1
    const val SELECT_SITE_2 = ":W2"       // 选择站点槽位2
    const val SELECT_SITE_3 = ":W3"       // 选择站点槽位3
    const val GET_ACTIVE_SITE = ":W?"     // 获取当前活动的站点槽位
    
    // ============================================================
    // 追踪控制 - Tracking Control
    // ============================================================
    
    const val SET_TRACKING_RATE = ":ST"   // 设置跟踪速率 (Hz, 0=停止追踪)
    const val TRACK_SIDEREAL = ":TQ"      // 恒星时追踪 (Sidereal)
    const val TRACK_SOLAR = ":TS"        // 太阳追踪 (Solar)
    const val TRACK_LUNAR = ":TL"        // 月球追踪 (Lunar)
    const val TRACK_KING = ":TK"         // King追踪
    
    const val TRACKING_ON = ":Te"         // 开启追踪
    const val TRACKING_OFF = ":Td"        // 关闭追踪
    
    // 高级追踪控制
    const val COMPENSATION_ON = ":To"     // 启用全补偿模型
    const val COMPENSATION_REFRACTION = ":Tr"  // 启用折射补偿
    const val COMPENSATION_OFF = ":Tn"    // 禁用补偿
    const val TRACK_SINGLE_AXIS = ":T1"   // 单轴追踪模式
    const val TRACK_DUAL_AXIS = ":T2"     // 双轴追踪模式
    
    // 恒星时钟调整
    const val SIDEREAL_CLOCK_INC = ":T+"  // 增加主恒星时钟 0.02Hz
    const val SIDEREAL_CLOCK_DEC = ":T-"  // 减少主恒星时钟 0.02Hz
    const val SIDEREAL_CLOCK_RESET = ":TR" // 重置主恒星时钟
    
    // 跟踪速率偏移
    const val GET_TRACKING_OFFSET_DEC = ":GXTD"  // 获取 Dec 追踪速率偏移 (arcsec/sidereal sec)
    const val GET_TRACKING_OFFSET_RA = ":GXTR"  // 获取 RA 追踪速率偏移 (arcsec/sidereal sec)
    const val SET_TRACKING_OFFSET_DEC = ":SXTD"  // 设置 Dec 追踪速率偏移
    const val SET_TRACKING_OFFSET_RA = ":SXTR"  // 设置 RA 追踪速率偏移
    
    // ============================================================
    // 目标设置 - Target Setting
    // ============================================================
    
    const val SET_TARGET_RA = ":Sr"       // 设置目标赤经
    const val SET_TARGET_DEC = ":Sd"      // 设置目标赤纬
    const val SET_TARGET_ALT = ":Sa"      // 设置目标高度
    const val SET_TARGET_AZ = ":Sz"       // 设置目标方位角
    
    // ============================================================
    // GOTO 执行
    // ============================================================
    
    const val GOTO_TARGET = ":MS"         // 执行GOTO到当前目标
    const val GOTO_ALT_AZ = ":MA"         // 执行Alt/Az GOTO
    const val GOTO_OPPOSITE_SIDE = ":MN"  // GOTO到对面 pier side
    const val GOTO_EAST_SIDE = ":MNe"     // 强制东侧GOTO
    const val GOTO_WEST_SIDE = ":MNw"     // 强制西侧GOTO
    const val GOTO_POLAR_ALIGN = ":MP"    // 极轴对齐GOTO
    
    // GOTO状态查询
    const val GET_PIER_SIDE = ":MD"       // 获取目标 pier side
    const val GET_MOTION_STATUS = ":D"    // 获取运动状态 (0x7f# 移动中, # 静止)
    
    // ============================================================
    // 同步 - Sync
    // ============================================================
    
    const val SYNC_TO_TARGET = ":CS"      // 同步到当前目标坐标
    const val SYNC_TO_CATALOG = ":CM"     // 同步到当前目录/数据库天体
    
    // ============================================================
    // 移动控制 - Movement Control
    // ============================================================
    
    const val MOVE_NORTH = ":Mn"          // 向北移动
    const val MOVE_SOUTH = ":Ms"          // 向南移动
    const val MOVE_EAST = ":Me"           // 向东移动
    const val MOVE_WEST = ":Mw"           // 向西移动
    
    // 停止命令
    const val STOP_ALL = ":Q"             // 停止所有运动
    const val STOP_RA = ":Qe"             // 停止RA轴
    const val STOP_DEC = ":Qn"            // 停止Dec轴
    
    // 速率设置
    const val RATE_GUIDE = ":RG"          // 导星速率 1x
    const val RATE_CENTER = ":RC"         // 居中速率 8x
    const val RATE_FIND = ":RM"           // 寻找速率 20x
    const val RATE_FAST = ":RF"           // 快速 48x
    const val RATE_SLEW = ":RS"           // GOTO速率
    
    // ============================================================
    // 背隙设置 - Backlash Settings
    // ============================================================
    
    const val SET_DEC_BACKLASH = ":\$BD"   // 设置 Dec/Alt 背隙 (arcsec)
    const val SET_RA_BACKLASH = ":\$BR"    // 设置 RA/Azm 背隙 (arcsec)
    const val GET_DEC_BACKLASH = ":%BD"   // 获取 Dec/Alt 背隙 (arcsec)
    const val GET_RA_BACKLASH = ":%BR"    // 获取 RA/Azm 背隙 (arcsec)
    
    // ============================================================
    // Home/零位控制 - Home Control
    // ============================================================
    
    const val HOME_GOTO = ":hC"           // 移动到 home 位置
    const val HOME_RESET = ":hF"          // 在零位重置赤道仪
    
    // Home 高级命令
    const val HOME_GET_STATUS = ":h?"     // 获取 Home 状态
    const val HOME_AUTO_ENABLE = ":hA1"   // 启用启动时自动 home
    const val HOME_AUTO_DISABLE = ":hA0" // 禁用启动时自动 home
    const val HOME_AXIS1_REVERSE = ":hC1,R"  // 切换 axis1 home 感应反向
    const val HOME_AXIS1_OFFSET = ":hC1"     // 设置 axis1 home 偏移 (arcsec)
    const val HOME_AXIS2_REVERSE = ":hC2,R"  // 切换 axis2 home 感应反向
    const val HOME_AXIS2_OFFSET = ":hC2"     // 设置 axis2 home 偏移 (arcsec)
    
    // ============================================================
    // 系统信息 - System Info
    // ============================================================
    
    const val VERSION_NUM = ":GVN"        // 获取固件版本号 (M.mm...)
    const val VERSION_FULL = ":GVM"       // 获取固件名称+版本
    const val PRODUCT_NAME = ":GVM"       // 获取产品名称
    const val VERSION_DATE = ":GVD"       // 获取固件构建日期 (MTH DD YYYY)
    const val VERSION_TIME = ":GVT"       // 获取固件构建时间 (HH:MM:SS)
    const val VERSION_CONFIG = ":GVC"     // 获取固件配置/产品描述
    const val VERSION_HARDWARE = ":GVH"   // 获取硬件/pinmap字符串
    
    // 环境传感器
    const val GET_AMBIENT_TEMP = ":GX9A"  // 获取环境温度 (C)
    const val GET_PRESSURE = ":GX9B"      // 获取气压 (mbar)
    const val GET_HUMIDITY = ":GX9C"      // 获取相对湿度 (%)
    const val GET_DEW_POINT = ":GX9E"     // 获取露点温度 (C)
    const val GET_MCU_TEMP = ":GX9F"      // 获取MCU温度 (C)
    
    // 错误查询
    const val GET_LAST_ERROR = ":GE"      // 获取上一个命令错误码
    
    // ============================================================
    // 日期时间设置 - Date/Time Setting
    // ============================================================
    
    const val SET_DATE = ":SC"            // 设置本地日期 (MM/DD/YY 或 MM/DD/YYYY)
    const val SET_TIME = ":SL"            // 设置本地时间 (HH:MM:SS)
    const val SET_UTC_OFFSET = ":SG"      // 设置UTC偏移 (sHH 或 sHH:MM)
    const val SET_ELEVATION = ":Sv"       // 设置海拔高度 (米)
    
    // 站点名称设置
    const val SET_SITE_NAME_1 = ":SM"     // 设置站点1名称 (最多15字符)
    const val SET_SITE_NAME_2 = ":SN"     // 设置站点2名称
    const val SET_SITE_NAME_3 = ":SO"     // 设置站点3名称
    const val SET_SITE_NAME_4 = ":SP"     // 设置站点4名称
    
    // 位置设置
    const val SET_LATITUDE = ":St"        // 设置纬度
    const val SET_LONGITUDE = ":Sg"       // 设置经度
    
    // ============================================================
    // 轴控制 - Axis Control
    // ============================================================
    
    const val GET_AXIS1_ANGLE = ":GX40"  // 获取 Axis1 仪器角度 (DDD*MM:SS)
    const val GET_AXIS2_ANGLE = ":GX41"  // 获取 Axis2 仪器角度 (DDD*MM:SS)
    const val GET_AXIS1_ANGLE_DEC = ":GX42" // 获取 Axis1 仪器角度 (十进制度)
    const val GET_AXIS2_ANGLE_DEC = ":GX43" // 获取 Axis2 仪器角度 (十进制度)
    const val GET_AXIS1_ENCODER = ":GX44" // 获取 Axis1 编码器计数
    const val GET_AXIS2_ENCODER = ":GX45" // 获取 Axis2 编码器计数
    const val GET_AXIS1_STEPS_PER_DEG = ":GXE4" // 获取 Axis1 每度步数
    const val GET_AXIS2_STEPS_PER_DEG = ":GXE5" // 获取 Axis2 每度步数
    const val GET_AXIS1_FREQ = ":GXF3"   // 获取 Axis1 步进频率
    const val GET_AXIS2_FREQ = ":GXF4"   // 获取 Axis2 步进频率
    
    // 轴角度设置
    const val SET_AXIS1_ANGLE = ":SX40"   // 设置 Axis1 仪器角度 (度)
    const val SET_AXIS2_ANGLE = ":SX41"   // 设置 Axis2 仪器角度 (度)
    
    // ============================================================
    // 对齐模型 - Alignment Model
    // ============================================================
    
    const val ALIGN_START = ":A"           // 开始对齐模式（后接星数，如 :A6#）
    const val ALIGN_ACCEPT = ":A+"        // 接受已居中的校准星
    const val ALIGN_END = ":A-"            // 结束对齐模式
    const val ALIGN_WRITE = ":AW"          // 写入对齐模型到 NV 存储
    const val ALIGN_GET_STATUS = ":A?"     // 获取对齐状态 (max,current,last stars)
    
    // ============================================================
    // 其他命令
    // ============================================================
    
    const val ECHO_TEXT = ":EC"           // 回显文本到调试输出
    const val RETICLE_BRIGHT_INC = ":B+"  // 增加分划板亮度
    const val RETICLE_BRIGHT_DEC = ":B-"  // 减少分划板亮度
    const val RESET_MCU = ":ERESET"       // 重置MCU
    const val RESET_NV_STORAGE = ":ENVRESET" // 标记NV存储在下次启动时清除
    
    // 扩展命令前缀
    const val EXTENDED = ":GX"           // OnStepX 扩展命令前缀
    
    // 编码器相关
    const val SAVE_ABSOLUTE_ENCODER = ":SEO" // 保存绝对编码器原点
    const val SET_MOUNT_TYPE = ":SXEM"   // 设置安装类型（需重启）
    const val GET_MOUNT_TYPE = ":GXEM"   // 获取当前安装类型
    
    // ============================================================
    // PEC - 周期性误差补偿
    // ============================================================
    
    const val PEC_STATUS = ":\$QZ?"      // 获取 PEC 状态 (I/p/P/r/R)
    const val PEC_PLAY = ":\$QZ+"        // 启用 PEC 回放
    const val PEC_STOP = ":\$QZ-"        // 禁用 PEC
    const val PEC_RECORD = ":\$QZ/"      // 开始录制
    const val PEC_CLEAR = ":\$QZZ"       // 清空缓存
    const val PEC_SAVE = ":\$QZ!"        // 写入 NV
    const val PEC_READ_ENTRY = ":VR"      // 读修正值
    const val PEC_WRITE_ENTRY = ":WR"     // 写修正值
    const val PEC_GET_WORM_STEPS = ":GXE7"  // 蜗杆每转步数
    const val PEC_SET_WORM_STEPS = ":SXE7," // 设置蜗杆每转步数
    const val PEC_GET_BUFFER_SIZE = ":GXE8" // 缓存大小（秒）
    const val PEC_GET_INDEX_POS = ":VH"     // 索位置
}