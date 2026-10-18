package com.sfzsgybb.ai.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sfzsgybb.ai.BuildConfig
import com.sfzsgybb.ai.hook.config.AppConfig
import com.sfzsgybb.ai.hook.config.ConfigRepository
import com.sfzsgybb.ai.hook.config.GlobalSwitch
import com.sfzsgybb.ai.hook.config.HookRule
import com.sfzsgybb.ai.mcp.McpConstants
import com.sfzsgybb.ai.mcp.McpHttpServer
import com.sfzsgybb.ai.mcp.McpNetwork
import com.sfzsgybb.ai.mcp.McpToolMeta
import com.sfzsgybb.ai.mcp.tools.FridaLogEntry
import com.sfzsgybb.ai.mcp.tools.FridaLogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val global: Map<String, Boolean> = emptyMap(),
    val mcpRunning: Boolean = false,
    val mcpPort: Int = McpConstants.DEFAULT_PORT,
    val versionName: String = BuildConfig.VERSION_NAME,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val mcpServer: McpHttpServer,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.observeAllGlobal().collect { map ->
                _state.value = _state.value.copy(global = map, mcpRunning = mcpServer.isRunning)
            }
        }
    }

    fun setGlobal(sw: GlobalSwitch, v: Boolean) {
        viewModelScope.launch { configRepository.setGlobal(sw, v) }
    }

    fun toggleMcp(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                // 同步绑定策略，避免单例里的旧 config 绑错地址
                val lan = configRepository.getGlobal(GlobalSwitch.MCP_LAN_ENABLED)
                mcpServer.config = mcpServer.config.copy(bindLocalhostOnly = !lan)
                runCatching { mcpServer.start() }
            } else {
                runCatching { mcpServer.stop() }
            }
            _state.value = _state.value.copy(mcpRunning = mcpServer.isRunning)
        }
    }
}

data class McpConsoleUiState(
    val running: Boolean = false,
    val port: Int = McpConstants.DEFAULT_PORT,
    val tools: List<McpToolMeta> = emptyList(),
    val lanEnabled: Boolean = false,
    val keepAlive: Boolean = true,
    val lanUrl: String? = null,
    val localUrl: String = "",
    val connections: Int = 0,
    val requests: Long = 0,
    /** 启停进行中：按钮要禁用并转圈，否则连点会反复 stop/start */
    val busy: Boolean = false,
    /** 上一次启停失败的原始原因 */
    val error: String? = null,
) {
    /** 当前实际要用的连接地址（局域网优先）。 */
    val activeUrl: String get() = if (lanEnabled && !lanUrl.isNullOrBlank()) lanUrl else localUrl
}

@HiltViewModel
class McpConsoleViewModel @Inject constructor(
    private val mcpServer: McpHttpServer,
    private val dispatcher: com.sfzsgybb.ai.mcp.McpDispatcher,
    private val configRepository: ConfigRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(McpConsoleUiState(tools = dispatcher.toolMetaList()))
    val state: StateFlow<McpConsoleUiState> = _state.asStateFlow()

    init {
        syncState()
    }

    fun syncState() {
        val lan = configRepository.getGlobal(GlobalSwitch.MCP_LAN_ENABLED)
        val keep = configRepository.getGlobal(GlobalSwitch.MCP_KEEP_ALIVE)
        val port = mcpServer.actualPort.takeIf { it > 0 } ?: McpConstants.DEFAULT_PORT
        _state.value = _state.value.copy(
            running = mcpServer.isRunning,
            port = port,
            lanEnabled = lan,
            keepAlive = keep,
            localUrl = "http://127.0.0.1:$port/mcp",
            lanUrl = if (lan) McpNetwork.lanUrl(port) else null,
            connections = mcpServer.activeConnections.get(),
            requests = mcpServer.totalRequests.get(),
        )
    }

    fun startServer() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            var failure: String? = null
            runCatching { configRepository.setGlobal(GlobalSwitch.MCP_HTTP_ENABLED, true) }
            // 同步绑定策略：配置里的局域网开关可能与 server 单例里的旧 config 不一致
            val lan = configRepository.getGlobal(GlobalSwitch.MCP_LAN_ENABLED)
            mcpServer.config = mcpServer.config.copy(bindLocalhostOnly = !lan)
            runCatching { mcpServer.stop() }
            runCatching { mcpServer.start() }.onFailure {
                failure = "启动失败：${it.message ?: it.javaClass.simpleName}"
            }
            if (configRepository.getGlobal(GlobalSwitch.MCP_KEEP_ALIVE)) {
                runCatching { com.sfzsgybb.ai.mcp.McpKeepAliveService.start(appContext) }
            }
            // start() 内部吞异常时 isRunning 仍是 false，这里再兜一次
            if (failure == null && !mcpServer.isRunning) {
                failure = "启动失败：端口 ${mcpServer.actualPort.takeIf { it > 0 } ?: McpConstants.DEFAULT_PORT} 可能已被占用"
            }
            _state.value = _state.value.copy(busy = false, error = failure)
            syncState()
        }
    }

    fun stopServer() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { mcpServer.stop() }
            // 【保活独立】关 MCP 不等于关保活。
            // 之前这里无条件 stopService，用户只是想暂停 MCP，
            // 结果进程也失去保护，下次 AI 连不上。
            // 只有保活开关本身是关闭状态才停。
            if (!configRepository.getGlobal(GlobalSwitch.MCP_KEEP_ALIVE)) {
                runCatching { com.sfzsgybb.ai.mcp.McpKeepAliveService.stop(appContext) }
            }
            runCatching { configRepository.setGlobal(GlobalSwitch.MCP_HTTP_ENABLED, false) }
            _state.value = _state.value.copy(busy = false)
            syncState()
        }
    }

    /** 切换局域网模式。切换后需要重启服务生效。 */
    fun setLanEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.setGlobal(GlobalSwitch.MCP_LAN_ENABLED, enabled)
            // 【Bug 修复】McpHttpServer 是单例，构造时只读一次配置。
            // 必须先更新它的 config 再重启，否则重启后仍绑 127.0.0.1，
            // 局域网永远连不上。
            mcpServer.config = mcpServer.config.copy(bindLocalhostOnly = !enabled)
            runCatching { mcpServer.stop() }
            runCatching { mcpServer.start() }
            syncState()
        }
    }

    fun setKeepAlive(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.setGlobal(GlobalSwitch.MCP_KEEP_ALIVE, enabled)
            // 保活不依赖 MCP 是否在跑：开着就该保持进程存活，
            // 这样 AI 随时能连上，不用先把 APP 唤回前台。
            if (enabled) {
                runCatching { com.sfzsgybb.ai.mcp.McpKeepAliveService.start(appContext) }
            } else {
                runCatching { com.sfzsgybb.ai.mcp.McpKeepAliveService.stop(appContext) }
            }
            syncState()
        }
    }

    /** 定时刷新统计。 */
    fun refreshStats() {
        _state.value = _state.value.copy(
            running = mcpServer.isRunning,
            connections = mcpServer.activeConnections.get(),
            requests = mcpServer.totalRequests.get(),
        )
    }
}
data class HookListUiState(
    val packageName: String = "",
    val rules: List<HookRule> = emptyList(),
)

@HiltViewModel
class HookListViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HookListUiState())
    val state: StateFlow<HookListUiState> = _state.asStateFlow()

    fun load(pkg: String) {
        viewModelScope.launch {
            val cfg = configRepository.getAppConfig(pkg)
            _state.value = HookListUiState(pkg, cfg.hooks)
        }
    }

    fun add(rule: HookRule) {
        viewModelScope.launch {
            configRepository.addHookRule(_state.value.packageName, rule)
            load(_state.value.packageName)
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            configRepository.removeHookRule(_state.value.packageName, id)
            load(_state.value.packageName)
        }
    }
}

data class FridaUiState(
    val packageName: String = "",
    val script: String = FridaViewModel.DEFAULT_SCRIPT,
    val savedMessage: String? = null,
    /** 脚本已从磁盘读出来（用于区分「还没加载完」和「用户清空了」） */
    val loaded: Boolean = false,
    /** 磁盘上当前的版本，用来判断是否有未保存改动 */
    val savedScript: String = FridaViewModel.DEFAULT_SCRIPT,
) {
    /** 有未保存改动。 */
    val dirty: Boolean get() = loaded && script != savedScript
}

@HiltViewModel
class FridaViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FridaUiState())
    val state: StateFlow<FridaUiState> = _state.asStateFlow()

    fun load(pkg: String) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                val f = File(scriptDir(pkg), "main.js")
                if (f.exists()) runCatching { f.readText() }.getOrDefault(DEFAULT_SCRIPT)
                else DEFAULT_SCRIPT
            }
            _state.value = FridaUiState(
                packageName = pkg,
                script = content,
                savedScript = content,
                loaded = true,
            )
        }
    }

    fun setScript(s: String) {
        _state.value = _state.value.copy(script = s)
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val dir = scriptDir(s.packageName)
                if (!dir.exists()) dir.mkdirs()
                File(dir, "main.js").writeText(s.script)
                true
            }.getOrDefault(false)
            _state.value = _state.value.copy(
                savedMessage = if (ok) "已保存" else "保存失败：写不进应用私有目录",
                // 存成功后把基准更新掉，标题的「未保存」才会消失
                savedScript = if (ok) s.script else _state.value.savedScript,
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(savedMessage = null)
    }

    private fun scriptDir(pkg: String): File =
        File(com.sfzsgybb.ai.AideProApp.get().filesDir, "frida/$pkg")

    companion object {
        /**
         * 新脚本的初始模板。
         *
         * 【注意缩进】
         * trimIndent() 只去掉所有行共同的最少缩进。
         * 之前整个模板写在 trimIndent 块里但内容本身从第 0 列开始，
         * 结果首行缩进 0、其余行缩进 16，粘出来是一段畸形脚本。
         * 现在统一从第 0 列写，靠 trimIndent 只负责去掉整体缩进。
         */
        val DEFAULT_SCRIPT = """
Java.perform(function () {
    // 在这里编写 Frida 脚本，保存后由目标应用进程执行
    //
    // 示例：打印所有 AES 加解密结果
    // var Cipher = Java.use('javax.crypto.Cipher');
    // Cipher.doFinal.overload('[B').implementation = function (input) {
    //     var out = this.doFinal(input);
    //     console.log('Cipher.doFinal ->', bytesToHex(out));
    //     return out;
    // };
    //
    // 常用速查：
    //   Java.available                    是否可 hook Java 层
    //   Java.use('完整类名')               获取类包装
    //   X.overload('签名').implementation   替换方法实现
    //   Java.perform(函数)                 在目标类加载后执行
});
""".trimIndent().trimEnd('\n') + "\n"
    }
}

data class FridaLogUiState(
    val packageName: String = "",
    val logs: List<FridaLogEntry> = emptyList(),
)

@HiltViewModel
class FridaLogViewModel @Inject constructor(
    private val provider: FridaLogProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(FridaLogUiState())
    val state: StateFlow<FridaLogUiState> = _state.asStateFlow()

    fun load(pkg: String) {
        viewModelScope.launch {
            val logs = provider.get(pkg, null, 500)
            _state.value = FridaLogUiState(pkg, logs)
        }
    }

    fun clear() {
        viewModelScope.launch {
            provider.clear(_state.value.packageName)
            load(_state.value.packageName)
        }
    }
}

data class AboutUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    /** 模块检测结果（复用首页那套三级检测） */
    val module: com.sfzsgybb.ai.util.ModuleStatusChecker.Status? = null,
    val rooted: Boolean = false,
    val scopeHint: String = "未配置",
    val toolCount: Int = 0,
) {
    /** 是否已激活（兼容旧 UI 字段）。 */
    val xposedActive: Boolean get() = module?.active == true

    /** 框架名。 */
    val frameworkName: String get() = module?.framework ?: "未检测到"
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val dispatcher: com.sfzsgybb.ai.mcp.McpDispatcher,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AboutUiState(toolCount = dispatcher.toolNames().size))
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // 【重要】复用 ModuleStatusChecker，别再自己写 Class.forName。
            // Xposed API 是 compileOnly，APK 里没有这个类，
            // 在宿主进程里永远加载不到 —— 那是个必然失败的检测。
            val module = withContext(Dispatchers.IO) {
                runCatching { com.sfzsgybb.ai.util.ModuleStatusChecker.check(appContext) }.getOrNull()
            }
            _state.value = _state.value.copy(
                module = module,
                rooted = module?.rooted ?: false,
            )
        }
    }
}
