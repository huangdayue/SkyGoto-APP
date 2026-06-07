# OnStepX-APP 终端页面设计方案

> 设计日期：2026-05-29  
> 状态：已确认，待实现

---

## 一、页面布局与导航位置

### 1.1 底部导航栏调整

在现有 **目录** 与 **设置** 之间插入 **终端** 页签：

```
[ 连接 ]  [ 控制 ]  [ 目录 ]  [ 终端 ]  [ 设置 ]
```

图标建议：`Icons.Default.Terminal`（Material Icons 内置）

### 1.2 终端页面整体布局

```
┌─────────────────────────────────────┐
│  ← 终端         🧹 📋  ● 已连接     │  ← 顶部状态栏（清屏/Hex/状态）
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────┐            │  ← 聊天区域
│  │ 赤道仪回复内容       │            │     (LazyColumn)
│  │ 左对齐、深灰气泡     │            │     左侧 = 赤道仪回复
│  │ 14:32:15           │            │     右侧 = 用户指令
│  └─────────────────────┘            │
│                                     │
│            ┌─────────────────────┐  │
│            │ 用户发送的指令       │  │
│            │ 右对齐、青色气泡     │  │
│            │ 14:32:18           │  │
│            └─────────────────────┘  │
│                                     │
│  ┌─────────────────────┐            │
│  │ 赤道仪回复内容       │            │
│  │ V4.2.3b            │            │
│  │ 14:32:20           │            │
│  └─────────────────────┘            │
│                                     │
├─────────────────────────────────────┤
│ ┌──────────────────┐ [发送][指令]   │  ← 底部输入栏
│ │ :GVP             │               │
│ └──────────────────┘               │
└─────────────────────────────────────┘
```

### 1.3 UI 配色方案

| 元素 | 颜色 | 来源 |
|------|------|------|
| 页面背景 | `#0A1628` (Primary) | 保持深色主题 |
| 赤道仪气泡背景 | `#1E2A3A` (Secondary) | 深灰蓝 |
| 用户气泡背景 | `#00D4FF` (Accent) | 青色 |
| 用户气泡文字 | `#0A1628` | 深底+浅泡=深文字 |
| 赤道仪气泡文字 | `#FFFFFF` (TextPrimary) | |
| 时间戳文字 | `#B0B8C4` (TextSecondary) | 次要色 |
| 输入框背景 | `#1E2A3A` (Secondary) | |
| 输入框文字 | `#FFFFFF` (TextPrimary) | |
| 发送按钮 | `#00FF88` (Accent2) | 绿色醒目 |
| 指令按钮 | `#00D4FF` (Accent) | 青色 |
| Hex 激活按钮 | `#FFB800` (Warning) | 橙黄色，开启后变亮 |

---

## 二、功能详细设计

### 2.1 发送命令流程

```
用户输入 :GVP
     │
     ├── 按下回车 / 点击"发送"
     │
     ├── UI 立即显示用户气泡（右侧、青色）
     │    内容：":GVP"
     │    时间戳：当前时间 HH:mm:ss
     │
     ├── 调用 mountRepository.sendRawCommand(":GVP")
     │    内部使用 sendCommandNoResponse()（不等待回复）
     │
     └── 清空输入框，返回
```

### 2.2 终端数据接收（快速轮询，等效中断）

由于 Android BluetoothSocket 不支持 `setSoTimeout()`，且 `withTimeoutOrNull` 无法打断 Java 阻塞 I/O，无法使用真正的阻塞 read + 中断唤醒。

采用 **100ms 快速轮询** 替代，效果接近中断：

```
终端页面可见时：
  每隔 100ms 执行一次：
    1. 调用 protocol.readAvailableBytes()
       - 读取 inputStream 中当前可用字节（不阻塞）
       - 有数据就全部读走并返回
       - 无数据立即返回空串
    2. ViewModel 层处理返回的原始字节：
       - 逐字符累积到 partialBuffer
       - 遇到 '#' → 立即显示为一条完整消息
       - 静默 500ms 无新数据 → 显示累积内容（单字符响应兜底）
    3. 继续下一次循环

终端页面不可见时（离开/切到后台）：
  停止轮询，释放协程
```

**为什么 100ms 就够用：**
- 人眼视觉暂留 ~50ms，最快反应时间 ~150ms，100ms 延迟基本感知不到
- `inputStream.available()` 是微秒级系统调用，每秒 10 次开销极低
- 相比 300ms 轮询延迟降低 3 倍，兼顾响应速度和 CPU 开销

**关键设计：缓冲区内容读取（改进版）**

赤道仪的回复分两种情况：
- **带 `#` 终止符**（如 `:GVP#` → `OnStepX V4.2.3b#`）：以 `#` 标记响应结束
- **不带 `#` 终止符**（如 `:Te#` → `1`、`:Td#` → `0`、`:MS#` → `0`）：只有单个字符，直接返回

原来的设计假设所有响应都以 `#` 结尾，等待 1 秒超时来兜底 —— **这在终端场景下是不可接受的**，会导致界面响应迟钝。

**改进方案：两层设计**

第一层 — `readAvailableBytes()`（连接层，无等待）：
```kotlin
// ProtocolConnection 新增方法
suspend fun readAvailableBytes(): String

// 实现逻辑：
// 1. 检查 inputStream.available()
// 2. 如果有数据 → 一次性读取所有可用字节 → 立即返回原始字符串
// 3. 如果无数据 → 立即返回空字符串
// 核心原则：0 等待时间，不阻塞
```

第二层 — ViewModel 中累积 + 智能断句：
```kotlin
// TerminalViewModel 中维护：
private var partialBuffer = StringBuilder()  // 累积不完整的响应
private var lastByteTime = 0L                // 最后一次收到字节的时间

// 轮询循环中（每 100ms 执行）：
val data = readAvailableBytes()
if (data.isNotEmpty()) {
    lastByteTime = System.currentTimeMillis()
    for (ch in data) {
        if (ch == '#') {
            // # 出现 → 当前缓冲区内容作为一条完整消息显示
            addMountMessage(partialBuffer.toString())
            partialBuffer.clear()
        } else {
            partialBuffer.append(ch)
        }
    }
}

// 如果缓冲区有数据且已静默 500ms → 也作为完整消息显示
// 这处理了不带 # 的单字符响应（如 "1", "0"）
if (partialBuffer.isNotEmpty() && 
    System.currentTimeMillis() - lastByteTime > 500) {
    addMountMessage(partialBuffer.toString())
    partialBuffer.clear()
}
```

**四种响应场景对比：**

| 场景 | 赤道仪返回 | 处理方式 | 显示延迟 |
|------|-----------|---------|---------|
| `:GVP#` | `OnStepX V4.2.3b#` | 读到 `#` 后立即显示 | ~0ms |
| `:Te#` | `1`（无#） | 静默 500ms 后自动结束 | ~500ms |
| `:GR#`（慢速） | `05:` …延… `34:32#` | 多轮累积，`#` 时一次性显示 | ~0ms |
| `:Q#` | 无响应 | 不显示任何内容 | N/A |

**为什么不直接用 500ms 超时：**

500ms 的"静默超时"只用于**不带 `#` 的单字符响应兜底**。对于带 `#` 的响应（占多数），`#` 一到立即显示，0 延迟。对于无响应的命令，什么也不显示。这样的设计保证了界面流畅，不会出现卡顿。

### 2.3 指令参考界面

### 2.3 清屏功能

顶部状态栏右侧 **🧹 清屏图标**（`Icons.Default.DeleteSweep`）：
- 点击 → 弹出确认对话框："确定清空所有聊天记录？"
- 确认 → 清空 `messages` 列表 + `partialBuffer`
- 取消 → 不做任何操作
- 清空的是内存中的消息列表，持久化文件同步删除

### 2.4 Hex Dump 模式

顶部状态栏右侧 **📋 Hex 切换按钮**（`Icons.Default.Code`）：

某些 LX200 命令返回不可打印的控制字符（如 `:D#` 返回 `0x7f`），普通 ASCII 显示会出现乱码。开启 Hex 模式后：

```
普通模式（Hex OFF）：
  赤道仪气泡： OnStepX V4.2.3b

Hex 模式（Hex ON）：
  赤道仪气泡：
    4F 6E 53 74 65 70 58 20   OnStepX
    56 34 2E 32 2E 33 62      V4.2.3b
```

- 按钮打开 → 之后收到的赤道仪回复全部以 Hex + ASCII 双栏显示
- 按钮关闭 → 正常文本显示
- 用户已发送的命令不受影响（始终显示为文本）
- 状态由 `_hexModeEnabled: MutableStateFlow<Boolean>` 管理
- Hex 格式化函数：每 8 字节一组，右侧对应 ASCII 列（不可打印字符显示 `.`）

### 2.5 未连接时使用终端

终端页面不依赖连接状态：
- **任何时候都可以进入终端页面**，浏览历史消息和指令参考
- 输入框正常可用，输入命令不阻止
- 按下发送时：
  - 如果未连接 → 底部弹出 Snackbar："⚠ 请先连接赤道仪"，命令**不发送**，保留在输入框中
  - 如果已连接 → 正常发送
- 轮询仅在已连接 + 页面可见时才启动

### 2.6 历史消息持久化

聊天记录持久化到本地文件，切换页面或重启应用后恢复：

**存储方案：JSON 文件**
- 路径：`context.filesDir/terminal_history.json`
- 格式：每行一个 JSON 对象（JSONL），便于逐条追加和流式读取
- 最大保留条数：**500 条**（超过自动裁剪旧的 100 条）

```json
{"content":":GVP","isFromUser":true,"timestamp":1748487600000,"hexMode":false}
{"content":"OnStepX V4.2.3b","isFromUser":false,"timestamp":1748487600100,"hexMode":false}
```

**实现细节：**
- `TerminalViewModel.init` 时从文件加载历史消息
- 每次新增消息时追加写入文件（`File.appendText()`）
- 清屏时删除文件
- 条数超 500 条时：保留最近 400 条，重写文件
- 不持久化 `partialBuffer`（未完成的响应只存在于内存中）

**文件管理：**
- 文件命名加入 MAC 地址后缀区分不同赤道仪？→ **暂不区分**，所有连接的终端记录共用同一个文件
- 后续如果用户需要，可以在设置页面加"清除终端记录"选项

### 2.7 指令参考界面

点击 **"指令"** 按钮 → 全屏 Dialog 弹出：

```
┌─────────────────────────────────────┐
│  ← 返回        LX200 指令参考        │  ← 顶部
├─────────────────────────────────────┤
│                                     │
│  ┌ 位置查询 ──────────────────────┐ │
│  │ :GR    获取当前赤经 (RA)       │ │  ← 可点击条目
│  │ :GD    获取当前赤纬 (Dec)      │ │
│  │ :GA    获取当前高度 (Alt)      │ │
│  │ :GZ    获取当前方位角 (Az)     │ │
│  │ :GRH   获取赤经-高精度         │ │
│  │ :GDH   获取赤纬-高精度         │ │
│  │ ...                           │ │
│  └────────────────────────────────┘ │
│                                     │
│  ┌ 追踪控制 ──────────────────────┐ │
│  │ :Te    开启追踪                │ │
│  │ :Td    关闭追踪                │ │
│  │ :TQ    恒星时追踪              │ │
│  │ ...                           │ │
│  └────────────────────────────────┘ │
│                                     │
│  ┌ 移动控制 ──────────────────────┐ │
│  │ :Mn    向北移动                │ │
│  │ :Ms    向南移动                │ │
│  │ :Q#    停止所有运动            │ │
│  │ ...                           │ │
│  └────────────────────────────────┘ │
│                                     │
├─────────────────────────────────────┤
```

**分类组织：**

| 序号 | 分类 | 命令数 |
|------|------|--------|
| 1 | 位置查询 | 16 |
| 2 | 时间和日期 | 10 |
| 3 | 追踪控制 | 16 |
| 4 | 目标设置 & GOTO | 11 |
| 5 | 移动控制 | 14 |
| 6 | 同步操作 | 3 |
| 7 | 系统信息 | 11 |
| 8 | 背隙设置 | 4 |
| 9 | Home/零位控制 | 9 |
| 10 | 日期时间设置 | 4 |
| 11 | 位置设置 | 6 |
| 12 | 站点管理 | 10 |
| 13 | 轴控制 | 10 |
| 14 | 对齐模型 | 2 |
| 15 | 其他命令 | 6 |

**交互行为：**
- 点击命令条目 → 自动关闭指令界面 → 将该指令**字符串**填入底部输入框
- 例如：点击 `:GR` → 输入框显示 `:GR` → 用户可以修改或直接按回车发送
- 支持参数化命令：`:Sr HH:MM:SS` 格式的标注提示用户需要替换参数

---

## 三、技术实现方案

### 3.1 新增文件清单

```
android/app/src/main/java/com/skygoto/app/
├── ui/screens/terminal/
│   ├── TerminalScreen.kt          ← 主界面 (Composable)
│   ├── TerminalViewModel.kt       ← 业务逻辑
│   └── CommandReferenceDialog.kt   ← 指令参考全屏弹窗
```

### 3.2 修改文件清单

| 文件 | 改动内容 |
|------|----------|
| `data/protocol/LX200Protocol.kt` | `ProtocolConnection` 接口新增 `readAvailableBytes()` 方法 |
| `data/datasource/BluetoothConnectionManager.kt` | 实现 `readAvailableBytes()` |
| `data/protocol/LX200Protocol.kt` | `TcpConnection` 实现 `readAvailableBytes()` |
| `domain/repository/MountRepository.kt` | 新增 `sendRawCommand()` 和 `readAvailableBytes()` |
| `data/repository/MountRepositoryImpl.kt` | 实现上述两个方法 |
| `ui/navigation/Navigation.kt` | 新增 Terminal Screen 导航 |
| `data/protocol/LX200Commands.kt` | 新增命令分组和中文注解数据结构 |

### 3.3 数据模型

```kotlin
// 聊天消息数据类（支持持久化）
data class TerminalMessage(
    val content: String,           // 消息内容
    val isFromUser: Boolean,       // true=用户发送, false=赤道仪回复
    val timestamp: Long,           // System.currentTimeMillis()
    val hexMode: Boolean = false,  // 此消息是否以 Hex 模式显示
    val id: String = UUID.randomUUID().toString()
)

// 指令参考条目（带分类）
data class CommandReference(
    val command: String,           // 如 ":GR"
    val description: String,       // 如 "获取当前赤经 (RA)"
    val category: String,          // 如 "位置查询"
    val hasParams: Boolean = false // 是否需要参数（如 :Sr HH:MM:SS）
)
```

### 3.4 ProtocolConnection 接口扩展

```kotlin
interface ProtocolConnection {
    // ... 现有方法 ...
    
    /**
     * 非阻塞读取输入流中当前可用的字节
     * 
     * - 如果有可用数据且以 # 结束，返回 # 之前的内容
     * - 如果有可用数据但无 #，返回当前已读内容
     * - 如果无可用数据，返回空字符串
     * - 最大等待时间 300ms（轮询间隔）
     */
    suspend fun readAvailableBytes(): String
}
```

### 3.5 BluetoothConnection 实现 readAvailableBytes

```kotlin
/**
 * 非阻塞读取当前缓冲区所有可用字节
 * 0 等待时间 — 有数据就返回，没数据立即返回空串
 */
override suspend fun readAvailableBytes(): String = withContext(Dispatchers.IO) {
    if (!socket.isConnected) return@withContext ""
    
    try {
        val available = inputStream.available()
        if (available <= 0) return@withContext ""
        
        val buffer = ByteArray(available)
        val bytesRead = inputStream.read(buffer)
        if (bytesRead <= 0) return@withContext ""
        
        // 返回原始字节串（包含 #、换行等所有字符）
        // ViewModel 层负责解析 # 终止符和累积逻辑
        String(buffer, 0, bytesRead, Charsets.US_ASCII)
    } catch (e: IOException) {
        ""
    }
}
```

**设计要点：**
- 不做任何等待，`available()` 返回什么就读什么
- 不解析 `#`，把原始数据原样交给 ViewModel
- 延迟全部由 ViewModel 层的 300ms 轮询间隔兜底
- 这样对于单字符响应（如 `1`），一次 `available()` 就能完整读到

### 3.6 MountRepository 扩展

```kotlin
interface MountRepository {
    // ... 现有方法 ...
    
    /**
     * 发送原始 LX200 命令（不等待响应）
     * 用于终端模式的 fire-and-forget 发送
     */
    suspend fun sendRawCommand(command: String): Result<Unit>
    
    /**
     * 非阻塞读取赤道仪返回的字节
     * 用于终端轮询模式
     */
    suspend fun readAvailableBytes(): Result<String>
}
```

### 3.7 MountRepositoryImpl 实现

```kotlin
override suspend fun sendRawCommand(command: String): Result<Unit> {
    val p = protocol ?: return Result.failure(Exception("Not connected"))
    return try {
        p.sendCommandNoResponse(command)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

override suspend fun readAvailableBytes(): Result<String> {
    val p = protocol ?: return Result.failure(Exception("Not connected"))
    return try {
        val data = p.readAvailableBytes()
        Result.success(data)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 3.8 TerminalViewModel 核心逻辑

```kotlin
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val mountRepository: MountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    companion object {
        private const val POLL_INTERVAL_MS = 100L      // 轮询间隔
        private const val SILENCE_TIMEOUT_MS = 500L    // 静默超时（无#响应兜底）
        private const val MAX_HISTORY_SIZE = 500       // 最大历史消息数
        private const val TRIM_SIZE = 400              // 裁剪后保留数
        private const val HISTORY_FILE = "terminal_history.json"
    }
    
    private val historyFile by lazy { File(context.filesDir, HISTORY_FILE) }
    
    private val _messages = MutableStateFlow<List<TerminalMessage>>(emptyList())
    val messages: StateFlow<List<TerminalMessage>> = _messages.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _hexModeEnabled = MutableStateFlow(false)
    val hexModeEnabled: StateFlow<Boolean> = _hexModeEnabled.asStateFlow()
    
    private var pollingJob: Job? = null
    private var isPageVisible = false
    
    // 累积不完整的响应（跨轮询周期）
    private val partialBuffer = StringBuilder()
    private var lastByteTime = 0L
    
    init {
        // 从文件加载历史消息
        loadHistory()
        
        viewModelScope.launch {
            mountRepository.isConnected.collect { connected ->
                _isConnected.value = connected
                if (!connected) {
                    stopPolling()
                    flushPartialBuffer()
                }
            }
        }
    }
    
    fun updateInputText(text: String) {
        _inputText.value = text
    }
    
    fun sendCommand() {
        val command = _inputText.value.trim()
        if (command.isEmpty()) return
        
        // 未连接时提示，不发送
        if (!_isConnected.value) {
            // 由 UI 层监听 showNotConnectedHint 显示 Snackbar
            return
        }
        
        flushPartialBuffer()
        
        val userMsg = TerminalMessage(
            content = command,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        addMessage(userMsg)
        
        viewModelScope.launch {
            mountRepository.sendRawCommand(command)
        }
        
        _inputText.value = ""
    }
    
    fun toggleHexMode() {
        _hexModeEnabled.update { !it }
    }
    
    fun startPolling() {
        isPageVisible = true
        if (!_isConnected.value) return
        
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && isPageVisible) {
                val result = mountRepository.readAvailableBytes()
                result.onSuccess { data ->
                    if (data.isNotEmpty()) {
                        lastByteTime = System.currentTimeMillis()
                        for (ch in data) {
                            if (ch == '#') {
                                flushPartialBuffer()
                            } else {
                                partialBuffer.append(ch)
                            }
                        }
                    }
                }
                
                if (partialBuffer.isNotEmpty() &&
                    System.currentTimeMillis() - lastByteTime > SILENCE_TIMEOUT_MS) {
                    flushPartialBuffer()
                }
                
                delay(POLL_INTERVAL_MS)
            }
        }
    }
    
    fun stopPolling() {
        isPageVisible = false
        pollingJob?.cancel()
        pollingJob = null
    }
    
    private fun flushPartialBuffer() {
        if (partialBuffer.isEmpty()) return
        val mountMsg = TerminalMessage(
            content = partialBuffer.toString(),
            isFromUser = false,
            hexMode = _hexModeEnabled.value,
            timestamp = System.currentTimeMillis()
        )
        addMessage(mountMsg)
        partialBuffer.clear()
    }
    
    private fun addMessage(msg: TerminalMessage) {
        _messages.update { current ->
            val newList = current + msg
            // 超出上限时裁剪
            if (newList.size > MAX_HISTORY_SIZE) {
                newList.drop(newList.size - TRIM_SIZE)
            } else {
                newList
            }
        }
        // 异步持久化
        viewModelScope.launch(Dispatchers.IO) {
            appendToFile(msg)
        }
    }
    
    private fun appendToFile(msg: TerminalMessage) {
        try {
            val json = JSONObject().apply {
                put("content", msg.content)
                put("isFromUser", msg.isFromUser)
                put("timestamp", msg.timestamp)
                put("hexMode", msg.hexMode)
            }
            historyFile.appendText(json.toString() + "\n")
        } catch (e: Exception) {
            AppLogger.e("TerminalVM", "Failed to persist message", e)
        }
    }
    
    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!historyFile.exists()) return@launch
                val messages = historyFile.readLines()
                    .mapNotNull { line ->
                        try {
                            val obj = JSONObject(line)
                            TerminalMessage(
                                content = obj.getString("content"),
                                isFromUser = obj.getBoolean("isFromUser"),
                                timestamp = obj.getLong("timestamp"),
                                hexMode = obj.optBoolean("hexMode", false)
                            )
                        } catch (e: Exception) { null }
                    }
                    .takeLast(MAX_HISTORY_SIZE)
                _messages.value = messages
            } catch (e: Exception) {
                AppLogger.e("TerminalVM", "Failed to load history", e)
            }
        }
    }
    
    private fun deleteHistoryFile() {
        try { historyFile.delete() } catch (_: Exception) {}
    }
    
    fun setCommandFromReference(cmd: String) {
        _inputText.value = cmd
    }
    
    fun clearMessages() {
        partialBuffer.clear()
        _messages.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { deleteHistoryFile() }
    }
}
```

### 3.9 导航修改

```kotlin
// Navigation.kt 中新增
object Terminal : Screen("terminal", "终端", Icons.Default.Terminal)

private val bottomNavItems = listOf(
    Screen.Connect,
    Screen.Control,
    Screen.Catalog,
    Screen.Terminal,    // ← 新增
    Screen.Settings
)

// NavHost 中新增
composable(Screen.Terminal.route) { TerminalScreen() }
```

---

## 四、消息气泡 UI 组件设计

```
用户消息（右侧、青色气泡）：
┌────────────────────┐
│ :GVP          ─────┤  ← 气泡背景 #00D4FF
│              14:32 │  ← 文字 #0A1628
└────────────────────┘

赤道仪消息-普通模式（左侧、深灰气泡）：
┌────────────────────┐
│ OnStepX V4.2.3b    │  ← 气泡背景 #1E2A3A
│              14:32 │  ← 文字 #FFFFFF
└────────────────────┘

赤道仪消息-Hex模式（左侧、深灰气泡 + 代码样式）：
┌──────────────────────────────┐
│ 4F 6E 53 74 65 70 58 20  OnS│  ← 气泡背景 #1E2A3A
│ 56 34 2E 32 2E 33 62     V4.│  ← 文字等宽 #B0B8C4
│                       14:32  │
└──────────────────────────────┘
```

气泡圆角：12dp，消息最大宽度：屏幕宽度的 75%
Hex 模式字体：`FontFamily.Monospace`，字号 12sp

---

## 五、边界条件处理

| 场景 | 处理方式 |
|------|----------|
| 未连接时发送命令 | UI 底部弹出 Snackbar "⚠ 请先连接赤道仪"，命令保留在输入框中不发送 |
| 未连接时打开指令参考 | 正常显示指令列表（离线也可浏览），点击指令正常填入输入框 |
| 未连接时使用终端 | 任意时候可进入终端，浏览历史消息和指令；发送和轮询仅连接后可用 |
| 连接断开时 | 自动停止轮询，在聊天区显示系统消息 "⚠ 连接已断开，14:32:00" |
| 输入空命令 | 忽略，不发送 |
| 发送超长命令 | 气泡内容可换行，最大高度 5 行，超出可滚动 |
| 消息过多 | 内存中保留最近 500 条（LazyColumn + StateFlow 限制），超出自动裁剪 |
| 轮询时收到乱码/二进制 | 普通模式过滤不可打印字符；Hex 模式全量显示 |
| 页面切后台/切换标签 | 通过 Lifecycle Observer 停止轮询 |
| 页面回到前台 | 恢复轮询（如果已连接） |
| 应用重启 | 从 terminal_history.json 恢复历史消息 |
| Hex 模式切换 | 不影响已有消息显示，只影响后续收到的赤道仪回复 |
| 清屏 | 确认对话框后清空内存 + 删除持久化文件 |

---

## 六、工作量估算

| 任务 | 预估时间 |
|------|----------|
| 接口扩展 (readAvailableBytes) | 15 min |
| BluetoothConnection & TcpConnection 实现 | 20 min |
| MountRepository 扩展 | 10 min |
| TerminalMessage 数据模型 & LX200 命令参考数据 | 25 min |
| TerminalViewModel 实现（含持久化、Hex、清屏） | 35 min |
| TerminalScreen UI（聊天气泡 + Hex 渲染 + 清屏确认） | 50 min |
| CommandReferenceDialog（指令参考全屏弹窗） | 30 min |
| Navigation 导航修改 | 10 min |
| 边界条件处理 & 调试 | 35 min |
| **总计** | **约 3.5 小时** |

---

## 七、最终确认（2026-05-29）

| # | 问题 | 决策 |
|---|------|------|
| 1 | 轮询间隔 | **100ms** — 兼顾响应速度和 CPU 开销 |
| 2 | 清屏按钮 | **顶部状态栏右侧** — 清除图标 + 确认对话框 |
| 3 | Hex 模式 | **需要** — 顶部切换按钮，Monospace 字体双栏显示 |
| 4 | 未连接时使用终端 | **允许浏览** — 可打开终端页面、看历史、查指令；发送时 Snackbar 提示 |
| 5 | 历史消息持久化 | **需要** — JSONL 文件存储，最多 500 条，自动裁剪 |

---

请审核以上方案，有任何修改意见告诉我！ 📡
