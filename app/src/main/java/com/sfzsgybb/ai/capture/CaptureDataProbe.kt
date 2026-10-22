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

    /**
     * 是否处于「暂停记录」状态。
     *
     * 【和「停止抓包」的区别】
     *  - 停止：真的关掉 VPN / 关掉会话，新流量完全不经过我们
     *  - 暂停：抓包链路照常运行（应用不断网），只是**不往库里写**
     *
     * 用户常见需求是「先停一下，把已经抓到的看完」——
     * 中途想恢复时点一下就行，不用重新授权 VPN、重新等握手。
     */
    @Volatile
    var paused: Boolean = false

    fun isRecording(): Boolean = !paused

    fun hasAnyData(): Boolean = hasActiveSession || hasData.get()

    fun markData() {
        hasData.set(true)
    }

    fun markCleared() {
        hasData.set(false)
    }

    /**
     * 按数据库真实情况重新计算。
     *
     * 【为什么需要这个】
     * 之前在每处删除路径手写 markCleared()，结果漏了
     * deleteSessions / delete 两条路径 —— 删完数据后工具列表
     * 仍然全量暴露。现在删除操作统一调这个方法按实际条数校准，
     * 不再依赖「记得在每处补一行」。
     *
     * @param remaining 库里剩余记录数
     */
    fun refreshFromCount(remaining: Long) {
        hasData.set(remaining > 0)
    }
}
