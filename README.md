# SkyGoto - Android 赤道仪控制APP

基于 **OnStepX** 协议的天文赤道仪控制应用，支持蓝牙和 WiFi 连接。

## 项目结构

```
OnStepX-APP/
├── android/                 # Android 应用主目录
│   ├── app/                 # APP 模块
│   │   └── src/main/
│   │       ├── java/com/skygoto/app/
│   │       │   ├── MainActivity.kt           # 应用入口
│   │       │   ├── SkyGotoApplication.kt     # Application 类（Hilt 初始化）
│   │       │   ├── di/                      # 依赖注入
│   │       │   │   └── AppModule.kt          # Hilt 模块配置
│   │       │   │
│   │       │   ├── data/                    # 数据层
│   │       │   │   ├── protocol/            # 通信协议
│   │       │   │   │   ├── LX200Protocol.kt  # LX200 协议实现
│   │       │   │   │   └── LX200Commands.kt # LX200 命令常量（基于 OnStepX COMMAND_REFERENCE.md）
│   │       │   │   ├── repository/          # 数据仓库实现
│   │       │   │   │   ├── MountRepositoryImpl.kt    # 赤道仪仓库实现
│   │       │   │   │   └── CatalogRepositoryImpl.kt  # 天体目录仓库实现
│   │       │   │   ├── datasource/          # 数据源
│   │       │   │   │   └── BluetoothConnectionManager.kt  # 蓝牙连接管理
│   │       │   │   └── preferences/
│   │       │   │       └── SettingsDataStore.kt  # 设置持久化（DataStore）
│   │       │   │
│   │       │   ├── domain/                   # 领域层
│   │       │   │   ├── model/               # 领域模型
│   │       │   │   │   ├── MountStatus.kt   # 赤道仪状态（RA/Dec/Alt/Az/追踪状态）
│   │       │   │   │   ├── CelestialObject.kt  # 天体对象（名称/RA/Dec/分类）
│   │       │   │   │   └── GotoResult.kt    # GOTO 执行结果
│   │       │   │   └── repository/          # 仓库接口
│   │       │   │       ├── MountRepository.kt     # 赤道仪操作接口
│   │       │   │       ├── CatalogRepository.kt   # 天体目录接口
│   │       │   │       └── ConnectionManager.kt   # 连接管理接口
│   │       │   │
│   │       │   └── ui/                      # UI 层
│   │       │       ├── theme/                # 主题配置
│   │       │       │   ├── Color.kt         # 颜色定义
│   │       │       │   └── Theme.kt         # Material3 主题
│   │       │       ├── navigation/
│   │       │       │   └── Navigation.kt     # 导航图（底部导航）
│   │       │       └── screens/             # 页面
│   │       │           ├── connect/          # 连接页面
│   │       │           │   ├── ConnectScreen.kt    # 连接界面（WiFi/蓝牙 Tab）
│   │       │           │   ├── ConnectViewModel.kt # 连接逻辑
│   │       │           │   └── BluetoothPairingScreen.kt  # 蓝牙配对界面
│   │       │           ├── control/          # 控制页面
│   │       │           │   ├── ControlScreen.kt    # 控制界面（N/S/E/W方向键/速率/GOTO）
│   │       │           │   └── ControlViewModel.kt # 控制逻辑
│   │       │           ├── catalog/         # 天体目录页面
│   │       │           │   ├── CatalogScreen.kt    # 目录浏览/GOTO
│   │       │           │   └── CatalogViewModel.kt # 目录逻辑
│   │       │           └── settings/         # 设置页面
│   │       │               ├── SettingsScreen.kt   # 设置界面
│   │       │               └── SettingsViewModel.kt # 设置逻辑
│   │       │
│   │       └── res/                 # Android 资源
│   │           ├── values/           # 字符串/颜色等资源
│   │           └── ...
│   │
│   ├── gradle/               # Gradle 包装器
│   ├── build.gradle.kts      # 根构建配置
│   ├── settings.gradle.kts   # 项目设置
│   ├── gradle.properties     # Gradle 属性（JVM 参数等）
│   └── build.sh             # 构建脚本
│
├── source/                  # OnStepX 固件源码（Arduino）
│   ├── OnStepX.ino          # 主程序入口
│   ├── Config.h             # 硬件配置
│   ├── Extended.config.h   # 扩展配置
│   ├── src/                # 源码目录
│   │   ├── HAL/           # 硬件抽象层
│   │   ├── lib/           # 库（通用）
│   │   ├── libApp/       # 应用库
│   │   ├── pinmaps/       # 引脚映射
│   │   ├── plugins/       # 插件
│   │   └── telescope/     # 望远镜控制核心
│   └── docs/              # 固件文档
│       ├── COMMAND_REFERENCE.md  # OnStepX 命令参考
│       └── HOMING_NOTES.md      # 回零位说明
│
├── docs/                   # 项目文档
│   ├── SPEC.md             # 项目规格说明书
│   ├── LX200_PROTOCOL.md   # LX200 协议分析
│   ├── ONSTEPX_SOURCE_RESEARCH.md   # OnStepX 源码研究
│   ├── ONSTEPX_LIBRARY_RESEARCH.md  # OnStepX 库研究
│   ├── CONNECTION_IMPLEMENTATION.md # 连接实现文档
│   └── UI_DESIGN.md        # UI 设计文档
│
└── data/                   # 数据文件（天体目录等）
    └── (待扩展)
```

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

## LX200 命令参考

本 APP 使用 OnStepX 固件的 LX200 协议进行通信，命令定义见 `LX200Commands.kt`：

| 功能 | 命令 | 说明 |
|------|------|------|
| 追踪开启 | `:TQ#` | Sidereal 恒星时追踪 |
| 追踪关闭 | `:TL#` | 停止追踪 |
| 回零位 | `:hC#` | 移动到物理零位 |
| 置为零位 | `:FZ#` | 将当前位置设为零位 |
| 同步 | `:CS#` | 同步到目标坐标 |
| GOTO | `:SdRA#` + `:SdDec#` + `:MA#` | 设置目标并执行 GOTO |
| 获取位置 | `:GR#` (RA) / `:GD#` (Dec) | 获取当前坐标 |
| 移动 | `:Mn#` / `:Me#` / `:Mw#` / `:Ms#` | N/E/W/S 方向移动 |
| 停止移动 | `:Q#` | 停止所有移动 |
| 速率设置 | `:Rm#` | 设置移动速率 |

详细命令请参考 `source/docs/COMMAND_REFERENCE.md`。

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

## 页面功能

### 1. 连接页面 (ConnectScreen)
- **WiFi 连接**：IP 地址 + 端口连接
- **蓝牙连接**：扫描/配对蓝牙设备
- 显示已配对设备列表
- 支持权限请求（BLUETOOTH_CONNECT / BLUETOOTH_SCAN / ACCESS_FINE_LOCATION）

### 2. 控制页面 (ControlScreen)
- **方向控制**：N/S/E/W 方向键 + STOP 紧急停止
- **移动速率**：导星 1x / 居中 8x / 寻找 20x / 快速 48x
- **追踪开关**：Sidereal 恒星时追踪
- **零位按钮**：回零位 / 置为零位
- **GOTO**：设置目标坐标并执行 goto
- 所有按钮带操作结果提示（成功/失败）

### 3. 目录页面 (CatalogScreen)
- 内置天体目录（梅西耶目录等）
- 按分类筛选（星系/星云/星团/行星）
- 搜索天体
- 点击 GOTO 跳转

### 4. 设置页面 (SettingsScreen)
- 经度/纬度设置
- 时区设置
- 蓝牙设备管理
- 关于信息

## 权限

| 权限 | 用途 |
|------|------|
| `BLUETOOTH_CONNECT` | 连接蓝牙设备 |
| `BLUETOOTH_SCAN` | 扫描蓝牙设备 |
| `ACCESS_FINE_LOCATION` | Android 12+ 蓝牙扫描必需 |
| `INTERNET` | WiFi 连接 |
| `ACCESS_NETWORK_STATE` | 检测网络状态 |

## 开发笔记

- APP 使用 MVVM 架构，ViewModel 持有 StateFlow 管理 UI 状态
- 蓝牙和 WiFi 通信统一通过 `LX200Protocol` 抽象
- `MountRepository` 是赤道仪操作的统一入口
- 蓝牙扫描使用原生 `BroadcastReceiver` + `startDiscovery()`
- UI 采用暗色主题，适合夜间天文观测

## License

OnStepX 固件部分采用 GPL v3 协议。Android APP 部分待定。
