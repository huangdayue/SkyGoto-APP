# OnStepX 源码研究 - LX200 协议实现

## 📅 研究日期：2026-05-12

## 1. 源码结构

```
OnStepX/src/telescope/mount/
├── Mount.command.cpp    # 基础命令（位置查询、追踪设置）
├── goto/
│   └── Goto.command.cpp # GOTO 相关命令
├── guide/
│   └── Guide.command.cpp # 移动/导星命令
├── coordinates/        # 坐标转换
├── library/           # 天体库
└── ...
```

**关键发现：** LX200 命令分布在三个主要文件中，而不是单一的协议文件。

---

## 2. 命令分类总结

### 2.1 位置查询命令 (Mount.command.cpp)

| 命令 | 功能 | 响应格式 |
|------|------|---------|
| `:GR#` | 获取当前赤经 | `HH:MM.T#` 或 `HH:MM:SS#` |
| `:GD#` | 获取当前赤纬 | `sDD*MM#` 或 `sDD*MM:SS#` |
| `:GA#` | 获取当前高度 | `sDD*MM#` |
| `:GZ#` | 获取当前方位 | `DDD*MM#` |
| `:GT#` | 获取跟踪速率 | `n.n#` |
| `:Gr#` | 获取目标赤经 | 同上 |
| `:Gd#` | 获取目标赤纬 | 同上 |

**高精度模式：** 加上 `H` 后缀，如 `:GRH#` 返回 `HH:MM:SS.SSSS#`

### 2.2 移动命令 (Guide.command.cpp)

| 命令 | 功能 | 说明 |
|------|------|------|
| `:Me#` | 向东移动 | 按当前导星速率 |
| `:Mw#` | 向西移动 | 按当前导星速率 |
| `:Mn#` | 向北移动 | 按当前导星速率 |
| `:Ms#` | 向南移动 | 按当前导星速率 |
| `:Mgd[n]#` | 脉冲导星 | n=毫秒数 |

**停止命令：**
| 命令 | 功能 |
|------|------|
| `:Q#` | 停止所有运动，中止 GOTO |
| `:Qe#` | 停止东向运动 |
| `:Qw#` | 停止西向运动 |
| `:Qn#` | 停止北向运动 |
| `:Qs#` | 停止南向运动 |

### 2.3 导星速率命令

| 命令 | 速率名称 | 倍率 |
|------|---------|------|
| `:RG#` | Guiding | 1X |
| `:RC#` | Centering | 8X |
| `:RM#` | Find | 20X |
| `:RF#` | Fast | 48X |
| `:RS#` | Slew | 当前 GOTO 速率的一半 |
| `:Rn#` | n=0-9 | 直接设置速率级别 |

**速率级别映射：**
```
0 = 停止
1 = 最低
...
9 = 最高（用于 GOTO）
```

### 2.4 追踪命令 (Mount.command.cpp)

| 命令 | 功能 |
|------|------|
| `:Te#` | 开启追踪 |
| `:Td#` | 关闭追踪 |
| `:TS#` | 太阳追踪速率 (60Hz) |
| `:TK#` | 恒星时追踪速率 (60.136Hz) |
| `:TL#` | 月球追踪速率 (57.9Hz) |
| `:TQ#` | 标准恒星时 (60.164Hz) |

**OnStepX 扩展：**
| 命令 | 功能 |
|------|------|
| `:T+#` | 主恒星钟加快 0.02Hz |
| `:T-#` | 主恒星钟减慢 0.02Hz |
| `:TR#` | 重置恒星时 |

### 2.5 GOTO 命令 (Goto.command.cpp)

**目标设置：**
| 命令 | 功能 | 格式示例 |
|------|------|---------|
| `:Sr HH:MM:SS#` | 设置目标赤经 | `:Sr 05:34:32#` |
| `:Sd sDD*MM:SS#` | 设置目标赤纬 | `:Sd +45*12:34#` |
| `:Sa sDD*MM:SS#` | 设置目标高度 | |
| `:Sz DDD*MM:SS#` | 设置目标方位 | |

**GOTO 执行：**
| 命令 | 功能 |
|------|------|
| `:MS#` | GOTO 到目标（RA/Dec）|
| `:MA#` | GOTO 到目标（Alt/Az）|
| `:MN#` | GOTO 到对面 pier side |
| `:MNe#` | GOTO 到东边 |
| `:MNw#` | GOTO 到西边 |
| `:MP#` | GOTO 到当前位置（极轴校准用）|

### 2.6 同步命令

| 命令 | 功能 |
|------|------|
| `:CS#` | 同步到当前 RA/Dec |
| `:CM#` | 同步到目标天体 |

---

## 3. GOTO 状态响应

`:MS#` 和 `:MA#` 的响应：

| 响应 | 含义 |
|------|------|
| `0#` | GOTO 可执行 |
| `1#` | 低于地平线限位 |
| `2#` | 高于天顶限位 |
| `3#` | 控制器处于待机状态 |
| `4#` | 赤道仪已停车 |
| `5#` | GOTO 进行中 |
| `6#` | 超出限位 |
| `7#` | 硬件故障 |
| `8#` | 已经在移动中 |
| `9#` | 未指明错误 |

---

## 4. OnStepX 扩展命令 (:GX, :SX)

### 4.1 查询命令 (:GX)

| 命令 | 功能 | 响应 |
|------|------|------|
| `:GX90#` | 获取脉冲导星速率 | `n.nn#` |
| `:GX92#` | 获取轴1位置（ArcSec）| |
| `:GX93#` | 获取轴2位置（ArcSec）| |
| `:GX94#` | 获取轴1编码器计数 | |
| `:GX95#` | 获取轴2编码器计数 | |
| `:GX97#` | 获取当前步进速率（deg/s）| |

### 4.2 设置命令 (:SX)

| 命令 | 功能 |
|------|------|
| `:SX92=nnn#` | 设置轴1目标（ArcSec）|
| `:SX93=nnn#` | 设置轴2目标（ArcSec）|
| `:SXTD,n.n#` | 设置 Dec 跟踪速率偏移 |
| `:SXTR,n.n#` | 设置 RA 跟踪速率偏移 |
| `:SX92,n#` | 设置导星速率（n=0-9）|

---

## 5. 坐标格式详解

### 5.1 赤经 (RA) 格式

```
格式: HH:MM.T    (标准精度)
      HH:MM:SS   (高精度)
      HH:MM:SS.SSSS (最高精度, :GRH#)

示例: 05:34:32  -> 5h 34m 32s
      00:42:44  -> 0h 42m 44s
```

### 5.2 赤纬 (Dec) 格式

```
格式: sDD*MM     (标准精度)
      sDD*MM:SS  (高精度)
      sDD*MM:SS.SSS (最高精度, :GDH#)

示例: +45*12     -> +45° 12'
      -05*23:45  -> -05° 23' 45"
      +41*16:09  -> +41° 16' 09"
```

### 5.3 高度/方位格式

```
高度: sDD*MM 或 sDD*MM:SS
方位: DDD*MM 或 DDD*MM:SS (0-360°)
```

---

## 6. 通信协议细节

### 6.1 命令格式

```
所有命令以 : 开头，# 结尾
响应也以 # 结束
```

### 6.2 响应类型

| 类型 | 说明 | 示例 |
|------|------|------|
| 数字响应 | 直接数字 | `0#`, `12.34#` |
| 坐标响应 | 带符号数字 | `+05:34:32#` |
| 文本响应 | N/A 或错误码 | `N/A#`, `E1#` |
| 无响应 | 执行完成无返回 | |

### 6.3 特殊命令前缀

| 前缀 | 用途 |
|------|------|
| `:` | 标准 LX200 命令 |
| `;` | 带校验和的命令（OnStepX 扩展）|

**校验和格式：** `;CCCCCCS#` 其中 CCCC 是校验和

---

## 7. 关键代码片段

### 7.1 移动命令处理 (Guide.command.cpp)

```cpp
// :Me# - 向东移动
if (command[1] == 'e' && parameter[0] == 0) {
  *commandError = startAxis1(GA_REVERSE, settings.axis1RateSelect, GUIDE_TIME_LIMIT*1000);
}

// :Q# - 停止所有
if (command[1] == 0) {
  #if GOTO_FEATURE == ON
    goTo.abort();
  #endif
  stop();
}
```

### 7.2 GOTO 命令处理 (Goto.command.cpp)

```cpp
// :MS# - GOTO 到目标
if (command[1] == 'S' && parameter[0] == 0) {
  CommandError e = request(gotoTarget, settings.preferredPierSide);
  strcpy(reply,"0");
  if (e >= CE_SLEW_ERR_BELOW_HORIZON && e <= CE_SLEW_ERR_UNSPECIFIED) 
    reply[0] = (char)(e - CE_SLEW_ERR_BELOW_HORIZON) + '1';
  if (e == CE_NONE) reply[0] = '0';
}
```

### 7.3 追踪命令处理 (Mount.command.cpp)

```cpp
// :Te# - 开启追踪
if (command[1] == 'e') {
  #if GOTO_FEATURE == ON
    if (park.state != PS_PARKED) {
      tracking(true);
    } else *commandError = CE_PARKED;
  #endif
}

// :Td# - 关闭追踪
if (command[1] == 'd') {
  tracking(false);
}
```

---

## 8. 安卓端实现要点

### 8.1 命令发送流程

```kotlin
suspend fun sendCommand(command: String): String {
    val fullCommand = "$command#"
    outputStream.write(fullCommand.toByteArray(StandardCharsets.UTF_8))
    return readResponse() // 读取直到 #
}
```

### 8.2 坐标解析

```kotlin
// 解析 RA: "05:34:32" -> Double (小时)
fun parseRA(str: String): Double {
    val parts = str.split(":")
    return parts[0].toDouble() + parts[1].toDouble()/60 + parts[2].toDouble()/3600
}

// 解析 Dec: "+45*12:34" -> Double (度)
fun parseDec(str: String): Double {
    val sign = if (str[0] == '-') -1 else 1
    val parts = str.drop(1).split("*", ":")
    return sign * (parts[0].toDouble() + parts[1].toDouble()/60 + parts[2].toDouble()/3600)
}
```

### 8.3 状态轮询

```kotlin
// 每秒查询状态保持连接
suspend fun pollStatus() {
    while (isConnected) {
        val trackingState = sendCommand("GT")  // 获取追踪状态
        val ra = sendCommand("GR")             // 获取 RA
        val dec = sendCommand("GD")           // 获取 Dec
        delay(1000)
    }
}
```

---

## 9. 实际通信示例

### 连接后查询状态
```
→ :V#              ← OnStep 4.24#
→ :GS#             ← (无响应表示未追踪) 或 1 (追踪中)
→ :GR#             ← +05:34:32#
→ :GD#             ← +45:12:34#
```

### 执行 GOTO M31
```
→ :Sr 00:42:44#    ← 1 (成功)
→ :Sd +41*16:09#   ← 1 (成功)
→ :MS#             ← 0 (开始GOTO) 或 1-9 (错误)
(查询进度)
→ :GS#             ← (查看是否到达)
→ :GR#             ← +00:42:45# (接近目标)
```

### 手动控制
```
→ :RG#             ← (设置 1X 导星速率)
→ :Me#             ← (开始向东移动)
→ :Q#              ← (停止)
```

### 开启/关闭追踪
```
→ :Te#             ← (开启追踪)
→ :Td#             ← (关闭追踪)
```

---

## 10. 注意事项

1. **波特率：** OnStepX 默认 9600, 支持 115200
2. **GOTO 中发送 :Q# 会中止**
3. **parked 状态下不能追踪或 GOTO**
4. **:MS# 返回后 GOTO 已在后台执行**
5. **坐标格式要注意正负号和度数符号**

---

## 附录：源码文件路径

| 功能 | 文件 |
|------|------|
| 基础命令 | `src/telescope/mount/Mount.command.cpp` |
| GOTO 命令 | `src/telescope/mount/goto/Goto.command.cpp` |
| 移动命令 | `src/telescope/mount/guide/Guide.command.cpp` |
| 命令解析 | `src/lib/commands/BufferCmds.cpp` |
| 配置 | `Config.h`, `src/Config.defaults.h` |

---

**文档版本：** 2.0  
**更新日期：** 2026-05-12  
**参考来源：** OnStepX 源码分析