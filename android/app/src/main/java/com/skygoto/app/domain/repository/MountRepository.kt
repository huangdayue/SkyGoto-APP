/*
 * 文件名：MountRepository.kt
 * 描述：赤道仪仓储接口 - 定义赤道仪控制的所有操作方法
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 定义赤道仪的数据查询和操作接口
 * - 支持赤道仪连接/断开
 * - 支持追踪控制（开始/停止）
 * - 支持手动移动（四个方向，不同速率）
 * - 支持 GOTO 目标定位
 * - 支持回零位和设零位操作
 *
 * 设计理念：
 * - Repository 模式隔离赤道仪通信细节
 * - 调用者只需知道接口定义，无需关心 LX200 协议实现
 * - 使用 StateFlow 发布状态，便于 UI 层观察
 *
 * 使用方式：
 * - 实现此接口创建具体的赤道仪控制实现
 * - 通过 mountStatus StateFlow 观察赤道仪状态
 * - 调用 startTracking()/stopTracking() 控制追踪
 * - 调用 move()/stopMove() 进行手动微调
 * - 调用 setTargetAndGoto() 执行goto
 */

package com.skygoto.app.domain.repository

import com.skygoto.app.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 赤道仪仓储接口
 *
 * 定义赤道仪控制的所有操作方法。
 * 实现类负责与赤道仪通信（通过 LX200 协议），
 * 并通过 StateFlow 发布实时状态。
 *
 * 主要功能分类：
 * 1. 连接管理 - 连接、断开、获取状态
 * 2. 追踪控制 - 开始/停止恒星时追踪
 * 3. 移动控制 - 手动微调（四个方向，多档速率）
 * 4. GOTO 控制 - 目标定位和取消
 * 5. 同步操作 - 当前位置同步、回零位、设零位
 * 6. 位置设置 - 设置观察地点的经纬度
 */
interface MountRepository {

    /**
     * 赤道仪状态流
     *
     * 包含赤道仪的实时位置、追踪状态、移动状态等。
     * 应用应定期观察此流以更新 UI 显示。
     */
    val mountStatus: StateFlow<MountStatus>

    /**
     * 连接状态流
     *
     * 指示赤道仪是否已连接。
     * 结合 connectionState 可以获取更详细的连接信息。
     */
    val isConnected: StateFlow<Boolean>

    // ========== 连接管理 ==========

    /**
     * 连接到赤道仪
     *
     * @param host 赤道仪的 IP 地址（WiFi 连接时）
     * @param port 赤道仪的端口号（WiFi 连接时）
     * @return Result<Unit> 连接成功返回成功结果
     */
    suspend fun connect(host: String, port: Int): Result<Unit>

    /**
     * 断开与赤道仪的连接
     */
    suspend fun disconnect()

    /**
     * 获取赤道仪当前状态
     *
     * 发送命令查询赤道仪的：
     * - 当前 RA/Dec 坐标
     * - 当前 Alt/Az 坐标
     * - 追踪状态
     * - 移动状态
     * - 目标坐标（如果正在 GOTO）
     *
     * @return Result<MountStatus> 查询结果
     */
    suspend fun getStatus(): Result<MountStatus>

    // ========== 追踪控制 ==========

    /**
     * 开始追踪
     *
     * 启动恒星时追踪，使赤道仪跟随地球自转旋转，
     * 从而抵消地球自转造成的目标天体视运动。
     * 这是天文观测的标准追踪模式。
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun startTracking(): Result<Unit>

    /**
     * 停止追踪
     *
     * 停止赤道仪跟踪，电机停止转动。
     * 适用于需要手动移动赤道仪或结束观测时。
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun stopTracking(): Result<Unit>

    // ========== 移动控制 ==========

    /**
     * 开始移动
     *
     * 向指定方向移动赤道仪。
     * 速率需要提前通过 setMoveRate() 设置。
     *
     * @param direction 移动方向（NORTH/SOUTH/EAST/WEST）
     * @return Result<Unit> 操作结果
     */
    suspend fun move(direction: Direction): Result<Unit>

    /**
     * 设置移动速率
     *
     * 立即发送到赤道仪，无需等待响应。
     *
     * @param rate 移动速率等级
     * @return Result<Unit> 操作结果
     */
    suspend fun setMoveRate(rate: MoveRate): Result<Unit>

    /**
     * 停止移动
     *
     * 立即停止当前的手动移动操作。
     * 不会影响追踪状态（如果正在追踪则继续追踪）。
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun stopMove(): Result<Unit>

    // ========== GOTO 控制 ==========

    /**
     * 设置目标并执行 GOTO
     *
     * 将赤道仪指向指定的赤经/赤纬坐标。
     * 赤道仪会计算最短路径并驱动马达到达目标位置。
     *
     * GOTO 流程：
     * 1. 验证目标坐标（是否在地平线上、是否超限）
     * 2. 如果有效，赤道仪开始移动到目标
     * 3. 如果无效，返回错误信息
     * 4. 到达目标后自动进入追踪模式
     *
     * @param ra 目标赤经（格式："HH:MM:SS"）
     * @param dec 目标赤纬（格式："+DD*MM:SS" 或 "-DD*MM:SS"）
     * @param latitude 观测者纬度（格式：DMS，用于同步到 OnStepX，可选）
     * @param longitude 观测者经度（格式：DMS，用于同步到 OnStepX，可选）
     * @return Result<GotoResult> GotoResult.Success 或 GotoResult.Error
     */
    suspend fun setTargetAndGoto(ra: String, dec: String): Result<GotoResult>

    /**
     * 取消当前 GOTO 操作
     *
     * 如果正在执行 GOTO，取消并停止移动。
     * 不会改变追踪状态。
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun cancelGoto(): Result<Unit>

    // ========== 同步操作 ==========

    /**
     * 同步到当前位置
     *
     * 告诉赤道仪当前指向的位置就是指定的坐标。
     * 用于校准赤道仪的指向精度。
     *
     * 使用场景：
     * - 完成星点校准后
     * - 发现指向偏差时校准
     * - 重新初始化位置后
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun syncToCurrentPosition(): Result<Unit>

    /**
     * 回零位（Home）
     *
     * 将赤道仪移动到零位位置（机械原点）。
     * 通常是 RA 和 Dec 都处于 0 的位置。
     * 用于：
     * - 结束观测后归位
     * - 重新初始化位置参考
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun home(): Result<Unit>

    /**
     * 回零并等待完成
     *
     * 1. 发送 :hC# 启动回零
     * 2. 轮询 :D# 直到赤道仪停止运动（超时 60 秒）
     *
     * @return Result<Unit>
     */
    suspend fun homeAndWait(): Result<Unit>

    /**
     * 设零位（Set Zero Position）
     *
     * 将当前位置设置为零位参考点。
     * 与 home() 的区别：
     * - home(): 移动到已知的零位位置
     * - setZeroPosition(): 将当前位置定义为新的零位
     *
     * 使用场景：
     * - 完成校准后定义新的参考点
     * - 手动初始化位置后保存
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun setZeroPosition(): Result<Unit>

    // ========== 对齐模式 ==========
    
    /**
     * 启动对齐模式
     *
     * 发送 :A[n]# 进入 OnStepX 的对齐模式，n 为对齐星数。
     * 对齐模式下 polling 将暂停，由对齐流程自行管理状态。
     *
     * @param starCount 对齐星数（1~9）
     * @return Result<Unit> 操作结果
     */
    suspend fun startAlign(starCount: Int = 6): Result<Unit>
    
    /**
     * 结束对齐模式并保存模型
     *
     * 1. 发送 :A-# 退出对齐模式
     * 2. 发送 :AW# 将对齐模型写入 NV 存储
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun finishAlign(): Result<Unit>
    
    /**
     * 接受当前居中的校准星
     *
     * 发送 :A+# 将当前居中的星加入对齐模型。
     * 在用户手动居中目标星后调用。
     *
     * @return Result<Unit>
     */
    suspend fun acceptAlignStar(): Result<Unit>
    
    /**
     * 取消对齐模式
     *
     * 发送 :A-# 退出对齐模式，不保存模型。
     * 恢复 polling。
     *
     * @return Result<Unit> 操作结果
     */
    suspend fun cancelAlign(): Result<Unit>
    
    /**
     * 清除对齐模型
     *
     * 发送 :A[n]#（清空 RAM）后 :AW#（写入 NV 覆盖旧数据）。
     *
     * @param starCount 星数
     * @return Result<Unit>
     */
    suspend fun clearAlignModel(starCount: Int = 6): Result<Unit>
    
    /**
     * 获取对齐状态
     *
     * 发送 :A?# 查询当前对齐进度。
     * 返回三元组 (maxStars, currentStar, lastStar)
     *
     * @return Result<Triple<Int,Int,Int>> (计划星数, 当前第几星, 已录星数)
     */
    suspend fun getAlignStatus(): Result<Triple<Int, Int, Int>>
    
    /**
     * 设置对齐目标星
     *
     * 发送 :Sr + :Sd 设置目标星的 RA/Dec。
     *
     * @param ra 赤经 HH:MM:SS
     * @param dec 赤纬 sDD*MM:SS
     * @return Result<Unit>
     */
    suspend fun setAlignTarget(ra: String, dec: String): Result<Unit>
    
    // ========== 位置设置 ==========

    /**
     * 设置观察位置
     *
     * 将当前位置（经纬度）发送给赤道仪，
     * 用于精确计算地平坐标（Alt/Az）。
     *
     * @param longitude 经度（格式："DDD:MM:SS"）
     * @param latitude 纬度（格式："DD:MM:SS"）
     * @return Result<Unit> 操作结果
     */
    suspend fun setLocation(longitude: String, latitude: String): Result<Unit>
    
    /**
     * 从赤道仪获取当前位置（经纬度）
     * 
     * @return Result<Pair<Longitude, Latitude>> 成功返回经纬度对
     */
    suspend fun getLocation(): Result<Pair<String, String>>
    
    // ========== 时间设置 ==========
    
    /**
     * 从赤道仪获取本地时间
     * 
     * 发送 :GL# 命令获取赤道仪的本地时间（HH:MM:SS）
     * 
     * @return Result<String> 赤道仪本地时间字符串
     */
    suspend fun getLocalTime(): Result<String>
    
    /**
     * 获取赤道仪本地日期
     *
     * 发送 :GC# 命令获取赤道仪的本地日期
     *
     * @return Result<String> 赤道仪本地日期字符串，格式 MM/DD/YY
     */
    suspend fun getDate(): Result<String>
    
    /**
     * 获取赤道仪时区偏移
     *
     * 发送 :GG# 命令获取赤道仪的 UTC 时区偏移
     *
     * @return Result<String> 时区偏移字符串，格式 sHH:MM
     */
    suspend fun getTimezone(): Result<String>
    
    /**
     * 设置赤道仪本地时间
     * 
     * 发送 :SLHH:MM:SS# 命令设置赤道仪的本地时间
     * 注意：日期需要单独使用 setDate() 设置
     * 
     * @param time 时间字符串，格式 "HH:MM:SS"
     * @return Result<Unit> 操作结果
     */
    suspend fun setTime(time: String): Result<Unit>
    
    /**
     * 设置赤道仪本地日期
     *
     * 发送 :SCMM/DD/YY# 命令设置赤道仪的本地日期
     *
     * @param date 日期字符串，格式 "MM/DD/YY"
     * @return Result<Unit> 操作结果
     */
    suspend fun setDate(date: String): Result<Unit>
    
    /**
     * 设置赤道仪时区偏移
     *
     * 发送 :SGsHH:MM# 命令设置赤道仪的时区偏移
     * 注意：东半球时区发送负值（如北京发送 "-08:00"）
     *
     * @param timezone 时区偏移字符串，格式 "sHH:MM" 如 "-08:00" 或 "+05:00"
     * @return Result<Unit> 操作结果
     */
    suspend fun setTimezone(timezone: String): Result<Unit>
    
    // ========== PEC - 周期性误差补偿 ==========
    
    /**
     * 获取 PEC 状态
     *
     * 发送 :\$QZ?# 查询当前 PEC 状态。
     * 返回状态字符：I=空闲, p=待回放, P=回放中, r=待录制, R=录制中
     * 可选择附带小数点后缀表示已检测到索信号
     *
     * @return Result<PecInfo> PEC 完整状态信息
     */
    suspend fun getPecState(): Result<PecInfo>
    
    /**
     * 启用 PEC 回放
     *
     * 发送 :\$QZ+# 启动 PEC 补偿。
     * 仅在已录制有效 PEC 数据时生效。
     *
     * @return Result<Unit>
     */
    suspend fun pecPlay(): Result<Unit>
    
    /**
     * 禁用 PEC
     *
     * 发送 :\$QZ-# 停止 PEC 回放或录制。
     *
     * @return Result<Unit>
     */
    suspend fun pecStop(): Result<Unit>
    
    /**
     * 开始录制 PEC 数据
     *
     * 发送 :\$QZ/# 进入待录制状态，
     * 蜗杆到达整秒位置后自动开始录制。
     * 录制持续一个完整的蜗杆旋转周期。
     *
     * @return Result<Unit>
     */
    suspend fun pecRecord(): Result<Unit>
    
    /**
     * 清空 PEC 数据缓存
     *
     * 发送 :\$QZZ# 清除内存中的 PEC 修正数据。
     *
     * @return Result<Unit>
     */
    suspend fun pecClear(): Result<Unit>
    
    /**
     * 保存 PEC 数据到 NV
     *
     * 发送 :\$QZ!# 将当前内存中的 PEC 数据写入非易失存储，
     * 断电不丢失。
     *
     * @return Result<Unit>
     */
    suspend fun pecSave(): Result<Unit>
    
    /**
     * 获取 PEC 配置参数
     *
     * 发送 :GXE7# 获取蜗杆每转步数
     * 发送 :GXE8# 获取缓存大小（秒数）
     *
     * @return Result<Pair<Long, Int>> (wormRotationSteps, bufferSizeSeconds)
     */
    suspend fun getPecConfig(): Result<Pair<Long, Int>>
    
    /**
     * 设置蜗杆每转步数
     *
     * 发送 :SXE7,[n]# 设置蜗杆旋转一周的微步数。
     * 此参数需与硬件实际配置一致。
     *
     * @param steps 蜗杆每转微步数（0 到 129600000）
     * @return Result<Unit>
     */
    suspend fun setPecWormSteps(steps: Long): Result<Unit>
    
    /**
     * 读取某秒的修正值
     *
     * 发送 :VR[n]# 读取 PEC 表中第 n 秒的修正值。
     *
     * @param index 索引（秒），0 到 bufferSize-1
     * @return Result<Int> 修正值（步），范围 -127 到 +127
     */
    suspend fun readPecEntry(index: Int): Result<Int>
    
    /**
     * 写入某秒的修正值
     *
     * 发送 :WR[n,sn]# 设置 PEC 表中第 n 秒的修正值。
     *
     * @param index 索引（秒），0 到 bufferSize-1
     * @param value 修正值（步），范围 -127 到 +127
     * @return Result<Unit>
     */
    suspend fun writePecEntry(index: Int, value: Int): Result<Unit>
    
    /**
     * 批量读取全部 PEC 修正值
     *
     * 遍历发送 :VR[n]# 读取整条 PEC 曲线。
     * 此操作会暂停状态轮询，完成后恢复。
     *
     * @param bufferSize 缓存大小（秒数）
     * @return Result<List<Int>> 修正值列表，索引对应秒
     */
    suspend fun loadPecCurve(bufferSize: Int): Result<List<Int>>
    
    /**
     * 批量写入全部 PEC 修正值
     *
     * 遍历发送 :WR[n,sn]# 写入整条 PEC 曲线。
     * 此操作会暂停状态轮询，完成后恢复。
     *
     * @param values 修正值列表
     * @return Result<Unit>
     */
    suspend fun savePecCurve(values: List<Int>): Result<Unit>
    
    // ========== 状态轮询控制 ==========
    
    /**
     * 启动状态轮询
     * 
     * 开始定期从赤道仪获取 RA/Dec/Alt/Az/追踪状态等5个参数。
     * 调用此方法后，mountStatus StateFlow 会定期更新。
     * 
     * 通常在用户切换到控制页面时调用。
     */
    fun startPolling()
    
    /**
     * 停止状态轮询
     * 
     * 停止定期获取赤道仪状态。
     * 调用此方法后，mountStatus 停止更新。
     * 
     * 通常在用户离开控制页面时调用。
     */
    fun stopPolling()
    
    /**
     * 暂停状态轮询（页面不可见时）
     * 
     * 暂停轮询但保留状态，用于用户切换到其他页面时。
     * 下次调用 resumePolling() 时可以恢复。
     */
    fun pausePolling()
    
    /**
     * 恢复状态轮询
     * 
     * 恢复被 pausePolling() 暂停的轮询。
     * 只有在 pausePolling() 之后调用才有效。
     */
    fun resumePolling()
    
    // ========== 终端命令支持 ==========
    
    /**
     * 发送原始 LX200 命令（不等待响应）
     * 用于终端模式的 fire-and-forget 发送
     */
    suspend fun sendRawCommand(command: String): Result<Unit>
    
    /**
     * 非阻塞读取赤道仪返回的字节
     * 用于终端轮询模式，有数据就返回，无数据立即返回空
     */
    suspend fun readAvailableBytes(): Result<String>
}