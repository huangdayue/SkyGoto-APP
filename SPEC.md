# OnStepX 赤道仪控制 APP - 技术规格书

> **文档版本：** 2.0（更新于 2026-05-21）  
> **项目：** SkyGoto Android APP

---

## 1. 项目概述

### 1.1 项目名称
**SkyGoto** - OnStepX 赤道仪控制应用

### 1.2 目标
开发一款 Android 应用程序，通过 LX200 协议控制 OnStepX 赤道仪，实现天文观测的便捷控制。

### 1.3 平台
| 项目 | 值 |
|------|---|
| 最低 Android 版本 | API 26（Android 8.0）|
| 目标 Android 版本 | API 34（Android 14）|
| 开发语言 | Kotlin 1.9+ |
| UI 框架 | Jetpack Compose + Material3 |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 异步 | Kotlin Coroutines + Flow |

---

## 2. 功能规格

### 2.1 连接管理

| 功能 | 描述 | 实现 |
|------|------|------|
| 蓝牙连接 | 扫描/连接已配对蓝牙设备 | `BluetoothConnectionManager` |
| WiFi 连接 | IP + 端口 TCP 连接 | `TcpConnection` |
| 连接状态 | 实时显示连接/断开/错误 | `ConnectionState` sealed class |
| 自动重连 | 连接断开后自动尝试重连 | `ConnectionManager.startAutoReconnect()` |

### 2.2 手动控制

| 功能 | 命令 | 说明 |
|------|------|------|
| 方向移动 | `:Mn#` `:Me#` `:Mw#` `:Ms#` | N/E/W/S 四方向 |
| 停止 | `:Q#` | 紧急停止所有运动 |
| 速率调节 | `:R<n>#`（0-9）| 5 档速率（导星→GOTO）|
| 追踪开关 | `:TC1#` / `:TC0#` | 恒星时追踪开/关 |
| 回零位 | `:hC#` | 归位动作 |
| 置零位 | `:FZ#` | 将当前位置设为零位 |
| 停车 | `:hP#` | 停车 |
| 解锁 | `:hR#` | 解除停车 |

### 2.3 天体数据库

| 分类 | 数量 | 数据源 |
|------|------|--------|
| 梅西耶天体 | 110 | `MessierCatalog.json` |
| NGC 天体 | ~200 | `NGCCatalog.json` |
| 太阳系天体 | 10+ | `SolarSystemCatalog.json` + Skyfield 实时计算 |
| 恒星 | 支持 | `StarCatalog.json` |

### 2.4 GOTO 功能

| 功能 | 实现 |
|------|------|
| 目标选择 | 从目录选择天体，或手动输入坐标 |
| 坐标设置 | `:SdRA#` + `:SdDec#` 设置目标 |
| 执行 GOTO | `:MA#` 启动 GOTO |
| 进度显示 | `GotoProgressDialog` 实时显示目标/当前坐标 |
| 到位判断 | 精确 ±60 角秒 + 兜底 RA ±4秒 / Dec ±10秒 |
| 取消 GOTO | `:Q#` 发送两次 |
| 错误处理 | `GotoResult` sealed class + `GotoErrorCodes` |

### 2.5 太阳系高精度计算

| 组件 | 说明 |
|------|------|
| Skyfield | Python 天文库，通过 Chaquopy 集成 |
| JPL DE421 | 星历文件，计算行星实时位置 |
| 计算链路 | Unix ms → JD → Skyfield → RA/Dec → 格式化 |
| 备用方案 | 静态目录坐标（解析失败时）|

### 2.6 参数配置

| 参数 | 存储位置 |
|------|----------|
| 经度/纬度 | `SettingsDataStore`（DataStore Preferences）|
| 时区 | `SettingsDataStore` |
| 追踪模式 | `SettingsDataStore` |
| 限位角度 | `SettingsDataStore` |
| GOTO 速率 | `SettingsDataStore` |

---

## 3. 技术架构

### 3.1 分层架构

```
┌──────────────────────────────────────┐
│         UI Layer (Compose)            │
│   Screens, ViewModels, UiState         │
├──────────────────────────────────────┤
│          Domain Layer                  │
│   Models, Repository Interfaces         │
├──────────────────────────────────────┤
│           Data Layer                   │
│  Repositories, Protocol, DataSources   │
├──────────────────────────────────────┤
│         Device Layer                   │
│    Bluetooth, WiFi, Python/Skyfield    │
└──────────────────────────────────────┘
```

### 3.2 核心模块

| 模块 | 文件 | 职责 |
|------|------|------|
| 连接管理 | `BluetoothConnectionManager.kt` | 蓝牙扫描、连接、RFCOMM 通信 |
| 协议 | `LX200Protocol.kt` | LX200 命令发送、响应解析 |
| 命令常量 | `LX200Commands.kt` | LX200 命令字符串常量 |
| 赤道仪操作 | `MountRepositoryImpl.kt` | GOTO、追踪、移动、状态查询 |
| 天体目录 | `CatalogRepositoryImpl.kt` | 梅西耶/NGC/太阳系/恒星数据加载 |
| 天文计算 | `EnhancedAstronomyCalculator.kt` | 太阳系高精度位置计算（Skyfield）|

### 3.3 依赖注入（AppModule）

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideBluetoothConnectionManager(...): BluetoothConnectionManager
    
    @Provides
    @Singleton
    fun provideLX200Protocol(connection: ProtocolConnection): LX200Protocol
    
    @Provides
    @Singleton
    fun provideMountRepository(...): MountRepository
    
    @Provides
    @Singleton
    fun provideCatalogRepository(...): CatalogRepository
}
```

---

## 4. UI 设计

### 4.1 页面结构

```
MainActivity
└── NavHost（底部导航）
    ├── ConnectScreen（连接页面）
    │   ├── BluetoothTab（已配对设备列表）
    │   └── WiFiTab（IP + 端口输入）
    ├── ControlScreen（控制页面）
    │   ├── StatusBar（RA/Dec/Alt/Az/追踪状态）
    │   ├── DirectionPad（N/S/E/W + STOP）
    │   ├── MoveRateSelector（5 档速率）
    │   ├── TrackingToggle（追踪开关）
    │   ├── GotoPanel（目标坐标 + 取消按钮）
    │   └── GotoProgressDialog（GOTO 进度弹窗）
    ├── CatalogScreen（目录页面）
    │   ├── CategoryTabs（全部/梅西耶/NGC/太阳系/恒星）
    │   ├── SearchBar（搜索框）
    │   ├── ObjectList（天体列表）
    │   └── ObjectDetailSheet（天体详情 + GOTO）
    └── SettingsScreen（设置页面）
        ├── LimitsSettings（限位角度）
        ├── TrackingSettings（追踪模式）
        ├── GotoSettings（GOTO 速率、容差）
        ├── LocationSettings（经纬度）
        └── DeviceInfo（固件版本、关于）
```

### 4.2 导航结构

底部导航 4 个 Tab：`连接` / `控制` / `目录` / `设置`

### 4.3 主题

- **暗色主题**：适合夜间天文观测
- **颜色方案**：深蓝背景（`#0A1628`）+ 青色强调（`#00D4FF`）+ 绿色成功（`#00FF88`）

---

## 5. 数据结构

### 5.1 ConnectionState（连接状态）

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val type: ConnectionType,  // BLUETOOTH / WIFI
        val deviceName: String,
        val deviceAddress: String,
        val extraInfo: String = ""
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

### 5.2 MountStatus（赤道仪状态）

```kotlin
data class MountStatus(
    val ra: String = "--:--:--",       // 当前赤经
    val dec: String = "--:--:--",       // 当前赤纬
    val alt: String = "--:--",          // 高度
    val az: String = "---:--",          // 方位角
    val tracking: Boolean = false,      // 是否追踪中
    val slewing: Boolean = false,       // 是否在移动中
    val targetRa: String? = null,       // GOTO 目标赤经
    val targetDec: String? = null      // GOTO 目标赤纬
)
```

### 5.3 GotoResult（GOTO 结果）

```kotlin
sealed class GotoResult {
    object Success : GotoResult()
    data class GotoError(val code: Int, val message: String) : GotoResult()
}

object GotoErrorCodes {
    const val BELOW_HORIZON = 1
    const val ABOVE_OVERHEAD = 2
    const val STANDBY = 3
    const val PARKED = 4
    const val GOTO_IN_PROGRESS = 5
    const val OUTSIDE_LIMITS = 6
    const val HARDWARE_ERROR = 7
    const val ALREADY_SLEWING = 8
}
```

### 5.4 CelestialObject（天体对象）

```kotlin
data class CelestialObject(
    val id: String,
    val name: String,
    val nameEn: String = "",
    val altNames: List<String> = emptyList(),
    val type: ObjectType,
    val ra: String,
    val dec: String,
    val raDeg: Double = 0.0,
    val decDeg: Double = 0.0,
    val constellation: String,
    val magnitude: Double,
    val description: String = ""
)

enum class ObjectType(val code: String, val cnName: String) {
    GALAXY("GAL", "星系"),
    NEBULA("DN", "星云"),
    PLANETARY_NEBULA("PN", "行星状星云"),
    OPEN_CLUSTER("OC", "疏散星团"),
    GLOBULAR_CLUSTER("GC", "球状星团"),
    PLANET("PLT", "行星"),
    MOON("MON", "月亮"),
    SUN("SUN", "太阳"),
    STAR("STAR", "恒星"),
    // ...
}
```

### 5.5 TrackingMode（追踪模式）

```kotlin
enum class TrackingMode(val displayName: String, val rateHz: Double) {
    SIDEREAL("恒星时", 60.0),
    SOLAR("太阳", 60.0),
    LUNAR("月球", 57.9),
    STOPPED("停止", 0.0)
}
```

### 5.6 MoveRate（移动速率）

```kotlin
enum class MoveRate(val displayName: String, val level: Int) {
    GUIDE("导星 1x", 2),
    CENTERING("居中 8x", 5),
    FIND("寻找 20x", 6),
    FAST("快速 48x", 7),
    SLEW("GOTO速率", 8)
}
```

---

## 6. LX200 协议实现

### 6.1 命令格式

```
:Command# → 响应#
:CommandParam# → 响应#
```

### 6.2 坐标格式

- **RA**：`<sign>HH:MM:SS`（如 `+12:34:56`）
- **Dec**：`<sign>DD*MM:SS`（如 `+45*12:34`）
- 响应中 `#` 表示结束符

### 6.3 关键命令

| 命令 | 功能 | 响应 |
|------|------|------|
| `:GR#` | 获取赤经 | `+HH:MM:SS#` |
| `:GD#` | 获取赤纬 | `+DD*MM:SS#` |
| `:GS#` | 获取追踪状态 | `Tracking#` / `Idle#` |
| `:GA#` | 获取高度 | `+DD*MM#` |
| `:GZ#` | 获取方位 | `DDD*MM#` |
| `:TC1#` | 开始追踪 | `1#` |
| `:TC0#` | 停止追踪 | `0#` |
| `:SdRA#` | 设置目标 RA | `1#` |
| `:SdDec#` | 设置目标 Dec | `1#` |
| `:MA#` | 执行 GOTO | `1#` 或错误码 |
| `:Q#` | 停止移动 | `1#` |

---

## 7. GOTO 到位判断算法

```kotlin
fun isCoordinateReached(currentRa: String, currentDec: String,
                         targetRa: String, targetDec: String): Boolean {
    // 1. 清理字符串（移除 \r \n 空格）
    // 2. 统一分隔符（目标用 :，赤道仪返回可能用 *）
    // 3. 精确比较：|raDiff| ≤ 60角秒 && |decDiff| ≤ 60角秒
    // 4. 兜底比较：RA ±4秒，Dec ±10秒
}
```

---

## 8. 验收标准

### 8.1 连接
- [x] 能发现并连接蓝牙设备
- [x] 能通过 IP:端口连接 WiFi
- [x] 断开后显示错误状态
- [x] 连接状态显示正确

### 8.2 控制
- [x] N/S/E/W 方向移动
- [x] 速率调节（5 档）
- [x] 追踪开关正常工作
- [x] 停止命令响应及时

### 8.3 天体目录
- [x] 梅西耶 110 个天体
- [x] NGC 目录
- [x] 太阳系天体（实时计算位置）
- [x] 按分类筛选
- [x] 搜索功能

### 8.4 GOTO
- [x] 选择目标后显示坐标
- [x] GOTO 执行并显示进度弹窗
- [x] 到位后显示成功 Banner（3 秒自动消失）
- [x] GOTO 过程中能取消
- [x] 错误码正确解析并显示

### 8.5 设置
- [x] 经纬度保存
- [x] 时区配置
- [x] 追踪模式切换
- [x] 设置持久化

---

## 9. 开发计划

| 阶段 | 状态 | 说明 |
|------|------|------|
| 1. 项目搭建 | ✅ | MVVM + Hilt + Compose |
| 2. 连接模块 | ✅ | 蓝牙 + WiFi 双连接 |
| 3. LX200 协议 | ✅ | 命令发送/响应解析 |
| 4. 手动控制 | ✅ | 方向/速率/追踪/停止 |
| 5. 天体目录 | ✅ | 梅西耶 + NGC + 太阳系 |
| 6. GOTO 功能 | ✅ | 进度弹窗 + 到位检测 |
| 7. 设置页面 | ✅ | 经纬度/追踪/限位 |
| 8. 太阳系计算 | ✅ | Skyfield + DE421 |
| 9. 代码审查 | ✅ | 多轮修复 |