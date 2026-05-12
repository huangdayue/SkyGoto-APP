# OnStepX 赤道仪控制 APP - 技术规格书

## 📅 创建日期：2026-05-12

## 1. 项目概述

### 1.1 项目名称
**SkyGoto** - OnStepX 赤道仪控制应用

### 1.2 目标
开发一款安卓应用程序，通过 LX200 协议控制 OnStepX 赤道仪，实现天文观测的便捷控制。

### 1.3 平台
- **最低 Android 版本**：Android 7.0 (API 24)
- **目标 Android 版本**：Android 14 (API 34)
- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构**：MVVM + Clean Architecture

---

## 2. 功能规格

### 2.1 连接管理

| 功能 | 描述 |
|------|------|
| 蓝牙连接 | 扫描、配对、连接蓝牙串口设备 |
| WiFi 连接 | 通过 IP:端口连接 TCP 串口 |
| 连接状态 | 实时显示连接状态和信号质量 |
| 自动重连 | 断开后自动尝试重连 |

### 2.2 手动控制

| 功能 | 描述 |
|------|------|
| 方向控制 | 东/西/南/北四方向运动 |
| 速率调节 | 1x, 2x, 4x, 8x, 16x, 64x 倍率 |
| 追踪控制 | 启动/停止恒星时追踪 |
| 停止 | 立即停止所有运动 |

### 2.3 天体数据库

| 分类 | 示例 |
|------|------|
| **梅西耶天体** | M1-M110 全部 110 个 |
| **NGC 天体** | 常见 NGC 对象（约 200 个）|
| **星云** | 猎户座星云、蟹状星云等 |
| **星系** | 仙女座星系、涡旋星系等 |
| **星团** | 昴宿星团、礁湖星团等 |

### 2.4 GOTO 功能

| 功能 | 描述 |
|------|------|
| 快速选择 | 下拉菜单选择目标 |
| 一键 GOTO | 选中目标后自动 goto |
| 状态显示 | 显示 goto 进度和预计时间 |
| 中止 goto | 取消正在进行的 goto |

### 2.5 参数配置

| 参数 | 描述 |
|------|------|
| 限位设置 | 设置东西方向限位角度 |
| 跟踪速率 | 恒星时/太阳/月球跟踪 |
| GOTO 速度 | 默认 goto 速率 |
| 导星设置 | PEC/导星参数 |

### 2.6 状态显示

- 当前赤道坐标 (RA/Dec)
- 当前地平坐标 (Alt/Az)
- 追踪状态
- 连接状态
- 当前目标

---

## 3. 技术架构

### 3.1 分层架构

```
┌─────────────────────────────────┐
│         UI Layer (Compose)       │
│    Screens, ViewModels, States   │
├─────────────────────────────────┤
│       Domain Layer                │
│  UseCases, Repository Interfaces │
├─────────────────────────────────┤
│        Data Layer                 │
│  Repositories, DataSources, API   │
├─────────────────────────────────┤
│      Device Layer                 │
│   Bluetooth, WiFi, Serial I/O    │
└─────────────────────────────────┘
```

### 3.2 核心模块

| 模块 | 职责 |
|------|------|
| `connection` | 蓝牙/WiFi 连接管理 |
| `protocol` | LX200 协议编解码 |
| `mount` | 赤道仪状态和控制 |
| `catalog` | 天体数据库 |
| `settings` | 参数配置存储 |

### 3.3 LX200 协议命令（部分）

| 命令 | 功能 | 示例 |
|------|------|------|
| `:Goto#` | GOTO 到坐标 | `:GR010.354#:GD+45.123#:Goto#` |
| `:MA#` | 移动到已知天体 | `:MA M31#` |
| `:MR#` | 向东移动 | `:MR 10#` (10x速率) |
| `:MS#` | 停止移动 | `:MS#` |
| `:Q#` | 立即停止 | `:Q#` |
| `:Te#` | 获取赤经 | `:Te#` 返回 `+12:34:56` |
| `:Td#` | 获取赤纬 | `:Td#` 返回 `-05:23:45` |
| `:PO#` | 停止追踪 | `:PO#` |
| `:ON#` | 开始追踪 | `:ON#` |
| `:GS#` | 获取跟踪状态 | `:GS#` |

---

## 4. UI 设计

### 4.1 页面结构

```
MainActivity
├── ConnectScreen (连接页面)
│   ├── BluetoothScanner
│   └── WiFiConnector
├── ControlScreen (主控页面)
│   ├── StatusBar (状态栏)
│   ├── DirectionPad (方向控制)
│   ├── SpeedSelector (速率选择)
│   └── TrackingToggle (追踪开关)
├── CatalogScreen (天体目录)
│   ├── CategoryTabs (梅西耶/NGC/星云/星系)
│   ├── ObjectList (对象列表)
│   └── ObjectDetail (详情)
├── GotoScreen (GOTO 页面)
│   ├── TargetSelector (目标选择)
│   ├── CoordinateDisplay (坐标显示)
│   └── GotoControls (执行控制)
└── SettingsScreen (设置页面)
    ├── LimitsSettings (限位设置)
    ├── TrackingSettings (跟踪设置)
    └── DeviceInfo (设备信息)
```

### 4.2 导航结构

- **底部导航**：控制 / 目录 / 设置
- **连接页面**：首次进入或断开连接时显示
- **弹窗**：GOTO 确认、参数设置

---

## 5. 数据结构

### 5.1 天体对象

```kotlin
data class CelestialObject(
    val id: String,           // "M31", "NGC224"
    val name: String,          // "仙女座星系"
    val type: ObjectType,      // GALAXY, NEBULA, CLUSTER
    val ra: String,            // "00h 42m 44s"
    val dec: String,           // "+41° 16' 09\""
    val magnitude: Double,     // 3.4
    val constellation: String, // "Andromeda"
    val description: String    // 简短描述
)
```

### 5.2 连接状态

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val type: ConnectionType, val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

---

## 6. 验收标准

### 6.1 连接
- [ ] 能发现并连接蓝牙设备
- [ ] 能通过 IP:端口连接 WiFi
- [ ] 断开后能自动重连
- [ ] 连接状态显示正确

### 6.2 控制
- [ ] 能控制东西南北四方向运动
- [ ] 速率调节生效
- [ ] 追踪开关正常工作
- [ ] 停止命令响应及时

### 6.3 天体数据库
- [ ] 梅西耶 110 个天体完整
- [ ] 支持按分类筛选
- [ ] 搜索功能正常

### 6.4 GOTO
- [ ] 选择目标后显示坐标
- [ ] 一键 goto 执行成功
- [ ] goto 过程中能取消
- [ ] 到位后显示完成状态

### 6.5 设置
- [ ] 限位设置保存成功
- [ ] 跟踪速率切换生效
- [ ] 设置持久化保存

---

## 7. 风险和备选方案

| 风险 | 应对 |
|------|------|
| LX200 协议兼容性 | 参考 OnStepX 源码和现有 APP |
| 蓝牙连接不稳定 | 实现心跳机制和自动重连 |
| 设备固件差异 | 支持多种固件版本检测 |
| 低延迟要求 | 使用 Kotlin 协程和 Flow |

---

## 8. 开发计划

| 阶段 | 任务 | 预计工时 |
|------|------|---------|
| 1 | 项目搭建 + 依赖配置 | 1 天 |
| 2 | 连接模块（蓝牙/WiFi）| 2 天 |
| 3 | LX200 协议实现 | 2 天 |
| 4 | 手动控制界面 | 1 天 |
| 5 | 天体数据库 | 1 天 |
| 6 | GOTO 功能 | 2 天 |
| 7 | 设置页面 | 1 天 |
| 8 | 测试和优化 | 2 天 |
| **合计** | | **12 天** |