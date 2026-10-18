package com.sfzsgybb.ai.capture

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 抓包数据存在性探针。
 *
 * 【解决什么问题】
 * MCP 工具要按「有没有抓到数据」决定是否暴露，但 availability()
 * 是同步调用（tools/list 里每个工具问一遍），不能查库 ——
 * 查库要挂起协程，会把整个工具列表生成卡死。
 *
 * 所以用一个内存标志：写入路径置位，清空路径清零。
 * 两个 Repository 共享同一个探针，避免各维护一套状态不同步。
 */
object CaptureDataProbe {

    /** 是否曾经抓到过数据（会话停了也保持 true，因为库里还有）。 */
    private val hasData = AtomicBoolean(false)

    /** 是否有活跃会话。 */
    @Volatile
    var hasActiveSession: Boolean = false

    fun hasAnyData(): Boolean = hasActiveSession || hasData.get()

    fun markData() {
        hasData.set(true)
    }

    fun markCleared() {
        hasData.set(false)
    }

    /** 从库加载时同步一次真实状态。 */
    fun sync(anyData: Boolean, active: Boolean) {
        hasData.set(anyData)
        hasActiveSession = active
    }
}
