package com.sfzsgybb.ai.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 导航目的地。
 *
 * 对照原版 19 个 Activity，这里收敛为「底部 5 个主区 + 栈内二级页」：
 * 原版把 Hook 配置、抓包、日志、设置平铺成 19 个 Activity，
 * 层级靠 parentActivityName 维护，既难跳转也难回退。
 */
sealed class Dest(val route: String, val title: String) {

    // 主区（底部导航）
    data object Home : Dest("home", "首页")
    data object Apps : Dest("apps", "应用")
    data object Logs : Dest("logs", "日志") {
        /**
         * 带应用过滤的日志页路由。
         *
         * 用**可选查询参数**而不是新增路由：Logs 是底部导航主区，
         * 改 route 会让底栏高亮匹配失效。`logs?pkg=xxx` 解析时
         * route 仍算 `logs`，底栏照常亮。
         *
         * 从抓包页的「查看日志」图标跳转用这个。
         */
        fun withPackage(pkg: String?): String =
            if (pkg.isNullOrBlank()) route else "$route?pkg=${android.net.Uri.encode(pkg)}"

        const val PKG_QUERY = "pkg"
    }
    data object Capture : Dest("capture", "抓包")
    data object Settings : Dest("settings", "设置")

    // 二级页
    data object AppDetail : Dest("app/{packageName}", "应用详情") {
        fun of(pkg: String) = "app/$pkg"
    }
    data object LogDetail : Dest("log/{id}", "日志详情") {
        fun of(id: Long) = "log/$id"
    }
    data object CaptureHistory : Dest("capture/history", "抓包历史")

    /** 缓存管理页（查看占用、选择清理）。 */
    data object CacheSettings : Dest("settings/cache", "缓存管理")

    /**
     * 抓到的请求列表（钻进某一种抓包后）。
     *
     * mode = hook / vpn，决定看哪类数据、顶部停止按钮关哪个。
     */
    data object CaptureTraffic : Dest("capture/traffic/{mode}", "抓到的请求") {
        const val MODE_ARG = "mode"
        fun of(mode: String) = "capture/traffic/$mode"
    }
    data object SessionDetail : Dest("capture/session/{sessionId}", "会话详情") {
        fun of(id: String) = "capture/session/$id"
    }
    data object HttpDetail : Dest("capture/http/{id}", "请求详情") {
        fun of(id: Long) = "capture/http/$id"
    }
    data object TcpDetail : Dest("capture/tcp/{id}", "报文详情") {
        fun of(id: Long) = "capture/tcp/$id"
    }
    data object HookList : Dest("apps/{packageName}/hooks", "Hook 规则") {
        fun of(pkg: String) = "apps/$pkg/hooks"
    }
    /** Frida 脚本列表（一个应用可以有多个脚本）。 */
    data object FridaEditor : Dest("frida/{packageName}", "Frida 脚本") {
        fun of(pkg: String) = "frida/$pkg"
    }

    /** 编辑某个具体脚本。 */
    data object FridaScriptEdit : Dest("frida/{packageName}/{scriptId}", "编辑脚本") {
        const val SCRIPT_ARG = "scriptId"
        fun of(pkg: String, scriptId: String) = "frida/$pkg/$scriptId"
    }
    data object FridaLog : Dest("frida/{packageName}/log", "Frida 日志") {
        fun of(pkg: String) = "frida/$pkg/log"
    }
    data object McpConsole : Dest("settings/mcp", "MCP 服务")
    data object About : Dest("settings/about", "关于")
    data object Feedback : Dest("settings/feedback", "反馈")

    companion object {
        const val PACKAGE_ARG = "packageName"
        const val ID_ARG = "id"
        const val SESSION_ARG = "sessionId"
    }
}

/** 底部导航项。 */
enum class BottomTab(val dest: Dest, val icon: ImageVector) {
    HOME(Dest.Home, Icons.Filled.Home),
    APPS(Dest.Apps, Icons.Filled.BugReport),
    LOGS(Dest.Logs, Icons.Filled.Terminal),
    CAPTURE(Dest.Capture, Icons.Filled.NetworkCheck),
    SETTINGS(Dest.Settings, Icons.Filled.Settings),
}
