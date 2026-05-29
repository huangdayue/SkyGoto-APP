# SkyGoto - Android 赤道仪控制 APP

基于 **OnStepX** 协议的天文赤道仪控制应用，支持蓝牙和 WiFi 连接，可控制赤道仪 GOTO、追踪、手动移动，以及浏览梅西耶/NGC/太阳系天体目录。

---

## 主要功能

| 功能 | 说明 |
|------|------|
| **蓝牙连接** | 扫描已配对蓝牙设备，通过 RFCOMM 串口连接赤道仪 |
| **WiFi 连接** | 通过 IP:端口（默认 `192.168.1.1:9999`）TCP 连接赤道仪 |
| **方向控制** | N/S/E/W 四方向手动移动，支持 5 档速率（导星 1x → GOTO 速率） |
| **恒星时追踪** | 一键开启/关闭 Sidereal 追踪，补偿地球自转 |
| **GOTO 执行** | 选择天体目标后自动计算坐标并执行 GOTO，实时显示到位状态 |
| **天体目录** | 内置梅西耶（110）、NGC、太阳系（太阳/行星/月亮）、恒星目录 |
| **太阳系高精度** | 基于 Skyfield + JPL DE421 星历表计算行星实时位置 |
| **LX200 终端** | 类微信聊天风格的 LX200 命令终端，100ms 轮询接收，指令参考，Hex dump，消息持久化 |
| **设置管理** | 经纬度配置、时区、追踪模式、设备信息、日志导出 |

---

## 技术栈

| 分类 | 技术 |
|------|------|
| **语言** | Kotlin 1.9+ |
| **最低 SDK** | Android API 26 (Android 8.0) |
| **目标 SDK** | Android API 34 (Android 14) |
| **UI 框架** | Jetpack Compose (Material3) |
| **架构** | MVVM + Clean Architecture |
| **依赖注入** | Hilt |
| **异步** | Kotlin Coroutines + Flow |
| **导航** | Jetpack Navigation Compose |
| **本地存储** | DataStore Preferences |
| **通信** | LX200 协议（蓝牙 RFCOMM / TCP Socket）|
| **天文计算** | Python + Chaquopy（Skyfield + JPL DE421）|

---

## 项目结构

```
OnStepX-APP/
├── android/                    # Android 应用主目录
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/skygoto/app/
│   │       │   ├── MainActivity.kt
│   │       │   ├── SkyGotoApplication.kt     # Hilt 入口
│   │       │   ├── di/AppModule.kt           # 依赖注入配置
│   │       │   │
│   │       │   ├── data/                     # 数据层
│   │       │   │   ├── protocol/
│   │       │   │   │   ├── LX200Protocol.kt    # LX200 协议实现
│   │       │   │   │   └── LX200Commands.kt    # 命令常量
│   │       │   │   ├── repository/
│   │       │   │   │   ├── MountRepositoryImpl.kt    # 赤道仪操作
│   │       │   │   │   └── CatalogRepositoryImpl.kt   # 天体目录
│   │       │   │   ├── datasource/
│   │       │   │   │   └── BluetoothConnectionManager.kt  # 蓝牙管理
│   │       │   │   └── preferences/
│   │       │   │       └── SettingsDataStore.kt  # 设置持久化
│   │       │   │
│   │       │   ├── domain/                   # 领域层
│   │       │   │   ├── model/
│   │       │   │   │   ├── MountStatus.kt     # 连接状态、追踪模式、速率
│   │       │   │   │   ├── CelestialObject.kt # 天体对象、类型枚举
│   │       │   │   │   ├── GotoResult.kt      # GOTO 结果与错误码
│   │       │   │   │   ├── EnhancedAstronomyCalculator.kt  # 太阳系计算
│   │       │   │   │   ├── SkyfieldCalculator.kt   # Skyfield 桥接
│   │       │   │   │   └── SolarPositionCalculator.kt
│   │       │   │   └── repository/
│   │       │   │       ├── MountRepository.kt      # 赤道仪操作接口
│   │       │   │       ├── CatalogRepository.kt    # 天体目录接口
│   │       │   │       └── ConnectionManager.kt   # 连接管理接口
│   │       │   │
│   │       │   └── ui/                         # UI 层
│   │       │       ├── theme/                  # Material3 暗色主题
│   │       │       ├── navigation/
│   │       │       │   └── Navigation.kt       # 底部导航图
│   │       │       └── screens/
│   │       │           ├── connect/            # 连接页面（蓝牙/WiFi Tab）
│   │       │           ├── control/            # 控制页面（方向/GOTO/追踪）
│   │       │           ├── catalog/            # 天体目录（梅西耶/NGC/太阳系）
│   │       │           ├── terminal/           # 终端页面（LX200 命令终端）
│   │       │           │   ├── TerminalScreen.kt
│   │       │           │   ├── TerminalViewModel.kt
│   │       │           │   ├── TerminalModels.kt
│   │       │           │   └── CommandReferenceDialog.kt
│   │       │           └── settings/           # 设置页面（含日志导出）
│   │       │
│   │       ├── assets/
│   │       │   ├── MessierCatalog.json         # 梅西耶目录 110 个
│   │       │   ├── NGCCatalog.json             # NGC 目录
│   │       │   ├── SolarSystemCatalog.json     # 太阳系天体
│   │       │   ├── StarCatalog.json            # 恒星目录
│   │       │   └── de421.bsp                    # JPL 星历文件（~17MB）
│   │       │
│   │       ├── python/
│   │       │   └── skyfield_position.py         # Skyfield 天文计算
│   │       │
│   │       └── kotlin/io/github/cosinekitty/    # astronomy.kt（纯 Kotlin 天文库）
│   │
│   └── build.gradle.kts        # Gradle 配置
│
├── docs/                       # 项目文档
│   ├── SPEC.md                 # 技术规格书
│   ├── LX200_PROTOCOL.md       # LX200 协议详解
│   ├── CONNECTION_IMPLEMENTATION.md  # 蓝牙/WiFi 连接实现
│   └── UI_DESIGN.md            # UI 设计文档
│
├── source/                      # OnStepX 固件源码（Arduino）
│   ├── OnStepX.ino
│   └── docs/
│       └── COMMAND_REFERENCE.md  # OnStepX 命令参考
│
└── data/                       # 原始天体数据（备份）
    ├── MessierCatalog.json
    └── NGCCatalog.json
```

---

## 核心数据模型

### MountStatus（赤道仪状态）

```kotlin
data class MountStatus(
    val ra: String = "--:--:--",      // 当前赤经 "HH:MM:SS"
    val dec: String = "--:--:--",       // 当前赤纬 "+DD*MM:SS"
    val alt: String = "--:--",          // 高度
    val az: String = "---:--",          // 方位角
    val tracking: Boolean = false,     // 是否追踪中
    val slewing: Boolean = false,      // 是否在移动中
    val targetRa: String? = null,      // GOTO 目标赤经
    val targetDec: String? = null      // GOTO 目标赤纬
)
```

### CelestialObject（天体对象）

```kotlin
data class CelestialObject(
    val id: String,                    // "M31", "NGC7000", "SOL"
    val name: String,                  // "仙女座星系"
    val type: ObjectType,              // GALAXY, NEBULA, PLANET, MOON, ...
    val ra: String,                    // "00:42:44"
    val dec: String,                   // "+41*16:09"
    val magnitude: Double,            // 视星等
    val constellation: String          // 所在星座
)

enum class ObjectType {
    GALAXY, NEBULA, PLANETARY_NEBULA, OPEN_CLUSTER, GLOBULAR_CLUSTER,
    SUPERNOVA_REMNANT, PLANET, MOON, SUN, STAR, ...
}
```

### GotoResult（GOTO 结果）

```kotlin
sealed class GotoResult {
    object Success : GotoResult()
    data class GotoError(val code: Int, val message: String) : GotoResult()
}

object GotoErrorCodes {
    const val BELOW_HORIZON = 1       // 低于地平线
    const val ABOVE_OVERHEAD = 2       // 高于天顶
    const val STANDBY = 3             // 赤道仪待机中
    const val PARKED = 4              // 已停车
    const val GOTO_IN_PROGRESS = 5    // GOTO 进行中
    const val OUTSIDE_LIMITS = 6      // 超出限位
    const val HARDWARE_ERROR = 7       // 硬件故障
    const val ALREADY_SLEWING = 8     // 已经在移动
}
```

---

## LX200 命令参考

| 功能 | 命令 | 说明 |
|------|------|------|
| 追踪开启 | `:TC1#` | 开始恒星时追踪 |
| 追踪关闭 | `:TC0#` | 停止追踪 |
| 获取 RA | `:GR#` | 返回 `+12:34:56#` |
| 获取 Dec | `:GD#` | 返回 `+45:12:34#` |
| 获取追踪状态 | `:GS#` | 返回 `Tracking#` / `Idle#` |
| 设置目标并 GOTO | `:SdRA#` → `:SdDec#` → `:MA#` | 设定目标坐标并执行 |
| 停止移动 | `:Q#` | 紧急停止 |
| 移动方向 | `:Mn#` / `:Me#` / `:Mw#` / `:Ms#` | N/E/W/S 方向 |
| 速率设置 | `:R<n>#` | 设置移动速率（0-9） |
| 同步 | `:CS#` | 同步到目标坐标 |
| 回零位 | `:hC#` | 归位动作 |
| 置零位 | `:FZ#` | 将当前位置设为零位 |
| 公园 | `:hP#` | 停车 |
| 解锁 | `:hR#` | 解除停车 |

---

## 构建

### 环境要求
- JDK 17+
- Android SDK 34
- Gradle 8.x

### 构建 Debug APK

```bash
cd android
./gradlew assembleDebug
```

APK 输出位置：`android/app/build/outputs/apk/debug/app-debug.apk`

### 构建 Release APK

```bash
./gradlew assembleRelease
```

---

## 页面说明

### 连接页面（ConnectScreen）
- **蓝牙 Tab**：显示已配对设备列表，点击连接
- **WiFi Tab**：输入 IP 和端口，点击连接
- 需要权限：`BLUETOOTH_CONNECT`、`BLUETOOTH_SCAN`、`ACCESS_FINE_LOCATION`、`INTERNET`

### 控制页面（ControlScreen）
- **状态栏**：显示当前 RA/Dec、高度/方位、追踪状态
- **方向控制**：N/S/E/W 按钮 + STOP 紧急停止
- **速率选择**：导星 1x / 居中 8x / 寻找 20x / 快速 48x
- **追踪开关**：一键开启/关闭恒星时追踪
- **GOTO 面板**：显示当前目标坐标，支持取消 GOTO
- **GOTO 进度弹窗**：GOTO 执行时实时显示目标/当前坐标

### 目录页面（CatalogScreen）
- **分类 Tab**：全部 / 梅西耶 / NGC / 太阳系 / 恒星
- **搜索栏**：按名称/编号搜索
- **天体列表**：显示编号、名称、类型、坐标
- **底部详情弹窗**：点击天体弹出底部浮窗，显示详细信息 + GoTo 按钮
- **太阳系特殊处理**：通过 Skyfield 实时计算行星位置
- **地平线检查**：目标低于地平线时弹出确认对话框

### 终端页面（TerminalScreen）
- **聊天气泡 UI**：用户命令（右侧青色）和赤道仪回复（左侧深灰）分开显示
- **LX200 命令发送**：输入框按回车发送，fire-and-forget 不等待回复
- **100ms 轮询接收**：自动读取赤道仪缓冲区，`#` 终止符即时断句
- **指令参考**：15 类 132 条 LX200 命令，点击自动填入输入框
- **Hex dump 模式**：二进制数据以十六进制双栏显示
- **历史消息持久化**：JSONL 文件存储，最多 500 条自动裁剪
- **未连接保护**：未连接时 Snackbar 提示，命令不发送

### 设置页面（SettingsScreen）
- **赤道仪位置**：显示经纬度，支持同步手机 GPS
- **赤道仪时间**：显示/同步日期时间、时区
- **界面设置**：深色主题、语言
- **日志管理**：导出日志（分享）、清空日志
- **关于**：版本号、协议兼容信息

---

## GOTO 到位判断

| 级别 | 精度 | 说明 |
|------|------|------|
| 精确比较 | ±60 角秒 | RA 和 Dec 都在 60 角秒内则视为到位 |
| 兜底比较 | RA ±4 秒 / Dec ±10 秒 | 解析失败时使用宽松比较 |

坐标格式说明：
- 目录坐标（目标）：`+41*16:09`（`*` 分隔秒）
- 赤道仪返回：`:GR#` → `+12:34:56`（`:` 分隔秒）
- 解析前统一 `clean()` 清理（移除 `\r`、`\n`、空格）

---

## 权限

| 权限 | 用途 |
|------|------|
| `BLUETOOTH_CONNECT` | Android 12+ 连接蓝牙设备 |
| `BLUETOOTH_SCAN` | Android 12+ 扫描蓝牙设备 |
| `ACCESS_FINE_LOCATION` | Android 12+ 蓝牙扫描需要定位权限 |
| `INTERNET` | WiFi 连接 |
| `ACCESS_NETWORK_STATE` | 检测网络状态 |

---

## License

OnStepX 固件源码（Arduino）采用 GPL v3 协议。Android APP 部分待定。