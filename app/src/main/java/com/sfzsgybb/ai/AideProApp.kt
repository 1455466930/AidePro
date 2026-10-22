package com.sfzsgybb.ai

import android.app.Application
import android.util.Log
import com.sfzsgybb.ai.hook.config.ConfigRepository
import com.sfzsgybb.ai.hook.config.GlobalSwitch
import com.sfzsgybb.ai.ipc.AlgorithmServiceHost
import com.sfzsgybb.ai.mcp.McpHttpServer
import com.sfzsgybb.ai.util.PipeConsumer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AideProApp : Application() {

    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var mcpServer: McpHttpServer
    @Inject lateinit var serviceHost: AlgorithmServiceHost
    @Inject lateinit var captureRepository: com.sfzsgybb.ai.capture.CaptureRepository

    /** 全局协程作用域。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        runCatching { configRepository.ensureDefaults() }
            .onFailure { Log.w(TAG, "初始化默认配置失败", it) }

        // 刷新 Binder 服务的 uid 白名单：只有被启用了 Hook 的应用才允许连接
        runCatching { serviceHost.refreshAllowedUids() }
            .onFailure { Log.w(TAG, "刷新 uid 白名单失败", it) }

        // 管道消费者（供大数据量传输用）
        PipeConsumer.impl = object : PipeConsumer {
            override fun onLine(packageName: String, channel: String, line: String) {
                Log.d(TAG, "pipe[$channel] $packageName: ${line.take(200)}")
            }

            override fun exportContent(query: String): String {
                return runCatching { configRepository.mirrorPreview() }.getOrDefault("")
            }
        }

        appScope.launch {
            // 【数据修复】早期版本把 Hook / 代理的数据混进了同一个会话，
            // 导致「抓包历史条数」和「按来源过滤条数」对不上。
            // 这里把记录迁回各自来源的会话，并重算每个会话的记录数。
            runCatching {
                val moved = captureRepository.repairSessionSources()
                if (moved > 0) Log.i(TAG, "已修复 $moved 条归属错误的抓包记录")
            }.onFailure { Log.w(TAG, "修复会话归属失败", it) }

            // 【探针校准】冷启动时内存标志是空的，但库里可能还有历史抓包数据。
            // 不同步的话 MCP 会误判成「没有数据」，把查询类工具全藏起来，
            // 用户明明有历史记录却看不到相关工具。
            runCatching {
                val has = captureRepository.hasAnyStoredRecord()
                com.sfzsgybb.ai.capture.CaptureDataProbe.refreshFromCount(if (has) 1L else 0L)
                Log.i(TAG, "抓包探针已校准: hasData=$has")
            }.onFailure { Log.w(TAG, "校准抓包探针失败", it) }

            val enabled = runCatching {
                configRepository.getGlobal(GlobalSwitch.MCP_HTTP_ENABLED)
            }.getOrDefault(false)
            val keepAlive = runCatching {
                configRepository.getGlobal(GlobalSwitch.MCP_KEEP_ALIVE)
            }.getOrDefault(true)

            if (enabled && !mcpServer.isRunning) {
                runCatching { mcpServer.start() }
                    .onFailure { Log.w(TAG, "MCP 服务启动失败", it) }
            }

            // 【保活独立于 MCP 开关】
            // 之前是 `enabled && keepAlive` —— MCP 没开时保活不启动，
            // 用户开了保活却没效果。现在只要保活开关是 true 就启动：
            //  - 保持进程存活，随时能让 AI 连上（不用等 APP 回到前台）
            //  - MCP 中途被打开时，服务已在跑，不必再抢救一次
            if (keepAlive) {
                runCatching { com.sfzsgybb.ai.mcp.McpKeepAliveService.start(this@AideProApp) }
                    .onFailure { Log.w(TAG, "MCP 保活服务启动失败", it) }
            }
        }
    }

    /** 供 Provider 获取 Binder 服务实现。 */
    fun serviceHostOrNull(): AlgorithmServiceHost = serviceHost

    companion object {
        private const val TAG = "AideProApp"

        @Volatile
        private var instance: AideProApp? = null

        fun get(): AideProApp = instance ?: throw IllegalStateException("Application 尚未初始化")
        fun peek(): AideProApp? = instance
    }
}

/** 供 Provider 以非 Hilt 方式访问（Provider 早于 Hilt 注入时机）。 */
val AideProApp.serviceHost: AlgorithmServiceHost
    get() = serviceHostOrNull()
