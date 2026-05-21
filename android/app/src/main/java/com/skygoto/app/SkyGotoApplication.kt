package com.skygoto.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.skygoto.app.data.datasource.BluetoothConnectionManager
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 应用 Application 类
 *
 * 使用 @HiltAndroidApp 注解标记此类为 Hilt 依赖注入容器的入口点。
 * 当 Application 类被创建时，Hilt 会自动生成并管理整个应用的依赖图。
 * 所有使用 @AndroidEntryPoint、@HiltViewModel 等注解的组件都将自动获得依赖注入支持。
 */
@HiltAndroidApp
class SkyGotoApplication : Application() {

    @Inject
    lateinit var bluetoothManager: BluetoothConnectionManager

    @Inject
    lateinit var lifecycleObserver: AppLifecycleObserver

    override fun onCreate() {
        super.onCreate()

        // 初始化日志系统
        AppLogger.init(this)

        // 注册 App 生命周期观察器（用于清理蓝牙资源）
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // 初始化 Chaquopy Python 环境（必须！）
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
            AppLogger.i("SkyGotoApp", "Chaquopy Python 环境初始化完成")
        } else {
            AppLogger.i("SkyGotoApp", "Chaquopy Python 环境已就绪")
        }

        // 提取 de421.bsp 到内部存储（如果尚未提取）
        extractEphemerisFile()
    }

    /**
     * 从 assets 提取 de421.bsp 星历文件到内部存储
     * 这是必要的，因为 Chaquopy 不会自动提取 .bsp 等数据文件
     */
    private fun extractEphemerisFile() {
        val ephemerisFileName = "de421.bsp"
        val internalFile = File(filesDir, ephemerisFileName)

        // 如果文件已存在且大小正确（~17MB），跳过提取
        if (internalFile.exists() && internalFile.length() > 10_000_000) {
            AppLogger.i("SkyGotoApp", "de421.bsp 已存在于: ${internalFile.absolutePath}")
            return
        }

        AppLogger.i("SkyGotoApp", "正在提取 de421.bsp 到内部存储...")

        try {
            // 从 assets 打开输入流
            assets.open(ephemerisFileName).use { inputStream ->
                // 创建输出文件
                FileOutputStream(internalFile).use { outputStream ->
                    // 复制文件
                    inputStream.copyTo(outputStream)
                }
            }

            AppLogger.i("SkyGotoApp", "de421.bsp 提取完成: ${internalFile.absolutePath} (${internalFile.length()} bytes)")
        } catch (e: Exception) {
            AppLogger.e("SkyGotoApp", "提取 de421.bsp 失败: ${e.message}", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        AppLogger.close()
    }
}

/**
 * 应用生命周期观察器
 *
 * 负责在应用退出时清理全局资源：
 * - 关闭蓝牙扫描和连接（注销广播接收器、移除超时 Runnable）
 *
 * 使用 ProcessLifecycleOwner 监听，应用任意 Activity 退出都会触发 onStop，
 * 但这里只在意 ON_DESTROY（应用进程被杀死）时才真正清理资源。
 */
class AppLifecycleObserver @Inject constructor(
    private val bluetoothManager: BluetoothConnectionManager
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppLifecycleObserver"
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // 应用进程即将被杀死，清理蓝牙资源
        AppLogger.i(TAG, "应用退出，清理蓝牙资源...")
        bluetoothManager.close()
        AppLogger.i(TAG, "蓝牙资源已清理")
    }
}