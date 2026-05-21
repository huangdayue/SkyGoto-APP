/*
 * 文件名：AppLogger.kt
 * 描述：应用日志工具 - 将日志同时写入文件和控制台
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 将日志写入手机存储文件（方便调试）
 * - 同时输出到 Logcat
 * - 支持日志级别过滤
 * - 自动日志轮转（单文件最大 1MB）
 *
 * 使用方法：
 *   AppLogger.d("TAG", "Debug message")
 *   AppLogger.i("TAG", "Info message")
 *   AppLogger.w("TAG", "Warning message")
 *   AppLogger.e("TAG", "Error message")
 */

package com.skygoto.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 应用日志工具
 *
 * 同时将日志写入文件和控制台，支持：
 * - 日志级别（DEBUG/INFO/WARN/ERROR）
 * - 时间戳
 * - 日志文件轮转
 * - 线程安全
 */
object AppLogger {
    
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "onstepx_app.log"
    private const val MAX_FILE_SIZE = 1024 * 1024  // 1MB
    private const val MAX_FILES = 3
    
    private var context: Context? = null
    private var logFile: File? = null
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var writerThread: Thread? = null
    private var isRunning = false
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 初始化日志系统
     * 应在 Application.onCreate() 中调用
     */
    fun init(ctx: Context) {
        context = ctx.applicationContext
        setupLogFile()
        startWriterThread()
        i("AppLogger", "=== 日志系统初始化 ===")
        i("AppLogger", "日志文件: ${logFile?.absolutePath}")
    }
    
    /**
     * 关闭日志系统
     * 应在应用退出时调用
     */
    fun close() {
        isRunning = false
        writerThread?.join(1000)
        i("AppLogger", "=== 日志系统已关闭 ===")
    }
    
    private fun setupLogFile() {
        val ctx = context ?: return
        val logDir = File(ctx.filesDir, LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        logFile = File(logDir, LOG_FILE)
        
        // 检查文件大小，必要时轮转
        if (logFile!!.exists() && logFile!!.length() > MAX_FILE_SIZE) {
            rotateLogs()
        }
    }
    
    private fun rotateLogs() {
        val logDir = logFile?.parentFile ?: return
        
        // 删除最旧的日志
        for (i in MAX_FILES downTo 1) {
            val oldFile = File(logDir, "$LOG_FILE.$i")
            if (oldFile.exists()) {
                if (i >= MAX_FILES) {
                    oldFile.delete()
                } else {
                    oldFile.renameTo(File(logDir, "$LOG_FILE.${i + 1}"))
                }
            }
        }
        
        // 当前日志变为 .1
        logFile?.renameTo(File(logDir, "$LOG_FILE.1"))
    }
    
    private fun startWriterThread() {
        isRunning = true
        writerThread = Thread {
            while (isRunning) {
                val msg = logQueue.poll()
                if (msg != null) {
                    writeToFile(msg)
                } else {
                    Thread.sleep(100)
                }
            }
            // 退出前清空队列
            while (logQueue.isNotEmpty()) {
                writeToFile(logQueue.poll()!!)
            }
        }.apply { start() }
    }
    
    private fun writeToFile(msg: String) {
        try {
            val f = logFile ?: return
            PrintWriter(FileWriter(f, true)).use { pw ->
                pw.println(msg)
            }
        } catch (e: Exception) {
            // 忽略写入错误
        }
    }
    
    private fun formatLog(level: String, tag: String, msg: String): String {
        val time = dateFormat.format(Date())
        val tid = Thread.currentThread().id.toString().padStart(5)
        return "$time [$tid] $level/$tag: $msg"
    }
    
    private fun log(level: String, tag: String, msg: String) {
        val formatted = formatLog(level, tag, msg)
        
        // 输出到 Logcat
        when (level) {
            "D" -> Log.d(tag, msg)
            "I" -> Log.i(tag, msg)
            "W" -> Log.w(tag, msg)
            "E" -> Log.e(tag, msg)
        }
        
        // 加入队列写入文件
        logQueue.add(formatted)
    }
    
    // ========== 公开 API ==========
    
    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String, ex: Throwable? = null) {
        log("E", tag, msg)
        ex?.let { Log.e(tag, it.message, it) }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    /**
     * 清空日志文件
     * 删除当前日志文件内容
     */
    fun clearLogs() {
        try {
            val f = logFile
            if (f != null && f.exists()) {
                // 清空文件内容而不是删除文件
                PrintWriter(FileWriter(f, false)).use { pw ->
                    pw.print("")
                }
                i("AppLogger", "日志文件已清空")
            }
            // 清空队列中待写入的消息
            logQueue.clear()
        } catch (e: Exception) {
            e("AppLogger", "清空日志失败: ${e.message}", e)
        }
    }

    /**
     * 读取日志文件内容（最后 N 行）
     */
    fun tailLogFile(lines: Int = 100): String {
        val f = logFile ?: return ""
        if (!f.exists()) return ""
        
        return try {
            f.readLines().takeLast(lines).joinToString("\n")
        } catch (e: Exception) {
            ""
        }
    }
}