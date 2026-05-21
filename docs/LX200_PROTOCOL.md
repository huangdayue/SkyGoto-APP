# LX200 协议详解

> **文档版本：** 2.0（更新于 2026-05-21）  
> **基于：** OnStepX COMMAND_REFERENCE.md

## 概述

LX200 是 Meade 公司为其天文望远镜开发的串行通信协议，OnStep/OnStepX 完全兼容此协议。命令以 `:` 开头，以 `#` 结尾，所有响应也以 `#` 结束。

**通信参数：**
- 波特率：9600 / 19200 / 38400 / 115200（OnStep 默认 9600）
- 数据位：8
- 停止位：1
- 无奇偶校验

---

## 1. 命令格式

### 通用格式
```
:Command# → 响应#
```

### 带参数命令
```
:CommandParam# → 响应#
```

### 坐标格式
- **RA（赤经）**：`<sign>HH:MM:SS`（如 `+12:34:56`）
- **Dec（赤纬）**：`<sign>DD*MM:SS`（如 `+45*12:34`）
- 目标设置时 Dec 用 `:` 替代 `*`（如 `+45:12:34`）

---

## 2. 位置查询命令

| 命令 | 描述 | 响应示例 |
|------|------|---------|
| `:GR#` | 获取当前赤经 | `+12:34:56#` |
| `:GD#` | 获取当前赤纬 | `+45:12:34#` |
| `:GA#` | 获取当前高度 | `+45:23#` |
| `:GZ#` | 获取当前方位角 | `123:45#` |
| `:GT#` | 获取追踪速率（Hz）| `60.0#` |
| `:GS#` | 获取恒星时 | `12:34:56#` |

### 高精度位置查询
| 命令 | 描述 |
|------|------|
| `:GRH#` | 获取当前赤经（最高精度）|
| `:GDH#` | 获取当前赤纬（最高精度）|

---

## 3. 目标设置命令

| 命令 | 描述 | 格式 |
|------|------|------|
| `:SrRA#` | 设置目标赤经 | `HH:MM:SS` |
| `:SdDec#` | 设置目标赤纬 | `sDD:MM:SS`（`*` 替换为 `:`）|

**示例：**
```
:Sr01:45:30# → 设置目标 RA = 01h 45m 30s
:Sd+45:12:34# → 设置目标 Dec = +45° 12' 34"
```

---

## 4. GOTO 执行命令

| 命令 | 描述 | 响应 |
|------|------|------|
| `:MS#` | 执行 GOTO 到当前目标 | `1#`（成功）或 `0#`（失败）或错误码 |
| `:MA#` | 执行 Alt/Az GOTO | 同上 |

**GOTO 流程：**
```
1. :Sr01:45:30# → 设置目标 RA
2. :Sd+45:12:34# → 设置目标 Dec
3. :MS# → 执行 GOTO
```

**错误响应（:MS#）：**
| 响应 | 含义 |
|------|------|
| `1#` | 成功，开始移动 |
| `0#` | 失败（详细原因需查询 `:GE#`）|
| `2#` | GOTO 已在进行中 |
| `3#` | 目标低于地平线 |
| `4#` | 目标高于天顶 |
| `5#` | 赤道仪已停车（Parked）|
| `6#` | 超出限位 |
| `7#` | 硬件错误 |

---

## 5. 追踪控制命令

| 命令 | 描述 | 响应 |
|------|------|------|
| `:Te#` | 开启追踪 | `1#` |
| `:Td#` | 关闭追踪 | `0#` |
| `:TC1#` | 开始追踪（OnStepX 扩展）| `1#` |
| `:TC0#` | 停止追踪（OnStepX 扩展）| `0#` |
| `:TQ#` | 恒星时追踪 | `1#` |
| `:TS#` | 太阳追踪 | `1#` |
| `:TL#` | 月球追踪 | `1#` |
| `:ST<Hz>#` | 设置自定义追踪速率 | `1#` |

**Sidereal 追踪速率：** 60.0 Hz（地球自转速率补偿）

---

## 6. 移动控制命令

### 方向移动
| 命令 | 描述 |
|------|------|
| `:Mn#` | 向北（Dec 增大）|
| `:Ms#` | 向南（Dec 减少）|
| `:Me#` | 向东（RA 增大）|
| `:Mw#` | 向西（RA 减少）|

### 停止命令
| 命令 | 描述 |
|------|------|
| `:Q#` | 停止所有运动（紧急停止）|
| `:Qe#` | 停止 RA 轴 |
| `:Qn#` | 停止 Dec 轴 |

### 速率设置
| 命令 | 速率 | 说明 |
|------|------|------|
| `:RG#` | 1x | 导星速率 |
| `:RC#` | 8x | 居中速率 |
| `:RM#` | 20x | 寻找速率 |
| `:RF#` | 48x | 快速速率 |
| `:RS#` | 最大 | GOTO 速率 |
| `:R0#` | - | 停止 |

---

## 7. 同步命令

| 命令 | 描述 | 响应 |
|------|------|------|
| `:CS#` | 同步到当前目标坐标 | `1#` |
| `:CM#` | 同步到目录天体 | `1#` |

---

## 8. Home / 零位命令

| 命令 | 描述 | 响应 |
|------|------|------|
| `:hC#` | 移动到 Home 位置 | `1#` |
| `:hF#` | 重置到零位 | `1#` |
| `:hP#` | 停车（Park）| `1#` |
| `:hR#` | 解除停车（Unpark）| `1#` |

---

## 9. 日期/时间设置

| 命令 | 描述 | 格式 | 示例 |
|------|------|------|------|
| `:SCMM/DD/YY#` | 设置日期 | `MM/DD/YY` | `:SC05/21/26#` |
| `:SLHH:MM:SS#` | 设置时间 | 24小时制 | `:SL23:45:00#` |
| `:SG<sHH>#` | 设置 UTC 偏移 | 符号+小时 | `:SG+08#` |
| `:St<sDD:MM>#` | 设置纬度 | 度:分 | `:St+40:00#` |
| `:Sg<DDD:MM>#` | 设置经度 | 度:分 | `:Sg075:00#` |

---

## 10. 系统信息查询

| 命令 | 描述 | 响应示例 |
|------|------|---------|
| `:GVN#` | 获取版本号 | `1.23#` |
| `:GVM#` | 获取完整版本 | `OnStepX v1.23#` |
| `:GE#` | 获取上一个错误码 | `0#` |

---

## 11. OnStepX 扩展命令（:GX）

| 命令 | 描述 | 响应示例 |
|------|------|---------|
| `:GX9A#` | 环境温度（℃）| `25.5#` |
| `:GX9B#` | 气压（mbar）| `1013.25#` |
| `:GX9C#` | 相对湿度（%）| `60#` |
| `:GXTD#` | Dec 追踪偏移 | `0.0#` |
| `:GXTR#` | RA 追踪偏移 | `0.0#` |

---

## 12. 坐标解析说明

### 响应格式
- **RA**：`<sign>HH:MM:SS#`（如 `+12:34:56#`）
- **Dec**：`<sign>DD*MM:SS#`（如 `+45*12:34#`）

### 解析步骤（CatalogViewModel.kt）
```kotlin
// 1. clean() - 清理不可见字符
private fun String.clean(): String {
    return this.replace("\r", "").replace("\n", "").replace(" ", "")
        .filter { it.isDigit() || it == ':' || it == '*' || it == '+' || it == '-' }
}

// 2. 统一分隔符（:GR# 返回用 * 分隔秒，目录用 : 分隔）
val targetDecNorm = targetDec.replace("*", ":")  // "*" → ":"

// 3. parseRA / parseDec - 解析为 Double（度）
private fun parseRA(ra: String): Double? {
    val clean = ra.clean()
    val match = Regex("""([+-]?\d+):(\d+):(\d+)""").find(clean) ?: return null
    // 返回小时数 × 15 = 度
    return (match.groupValues[1].toDouble() + ...) * 15
}

// 4. isCoordinateReached - 到位判断
// 精确：|raDiff| ≤ 60 角秒 && |decDiff| ≤ 60 角秒
// 兜底：RA ±4 秒，Dec ±10 秒
```

---

## 13. GOTO 进度轮询

```kotlin
// 每秒轮询一次
private suspend fun startGotoProgressPolling(currentRa: String, currentDec: String) {
    while (isActive) {
        val now = mountRepository.getStatus().getOrNull()
        if (now != null && isCoordinateReached(
                currentRa, currentDec,
                now.targetRa ?: "--:--:--", now.targetDec ?: "--:--:--"
            )) {
            // 到位 → 显示成功 Banner
        }
        delay(1000)
    }
}
```

---

## 14. 错误码（用于 GotoError）

```kotlin
object GotoErrorCodes {
    const val BELOW_HORIZON = 1     // 低于地平线
    const val ABOVE_OVERHEAD = 2     // 高于天顶
    const val STANDBY = 3            // 赤道仪待机中
    const val PARKED = 4             // 已停车
    const val GOTO_IN_PROGRESS = 5  // GOTO 进行中
    const val OUTSIDE_LIMITS = 6     // 超出限位
    const val HARDWARE_ERROR = 7     // 硬件故障
    const val ALREADY_SLEWING = 8    // 已经在移动
}
```