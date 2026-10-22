package com.sfzsgybb.ai.ui.screens


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.hook.config.GlobalSwitch
import com.sfzsgybb.ai.hook.config.HookRule
import com.sfzsgybb.ai.hook.config.HookSwitch
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.components.MonoBlock
import com.sfzsgybb.ai.ui.components.SectionHeader
import com.sfzsgybb.ai.ui.components.StatCard
import com.sfzsgybb.ai.ui.components.StatusDot
import com.sfzsgybb.ai.ui.components.copyToClipboard
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import com.sfzsgybb.ai.ui.components.cardOutline
import androidx.compose.material3.Switch
import com.sfzsgybb.ai.ui.theme.Sem

/**
 * 项目开源地址。
 *
 * 设置页「其他」里的入口用这个跳转 —— 用户看得到源码，
 * 也方便他们自己提 issue 或看更新。
 */
private const val PROJECT_REPO_URL = "https://github.com/1455466930/AidePro"


// ---------------- 设置 ----------------

@Composable
fun SettingsScreen(
    onOpenMcp: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCache: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AideTopBar(title = "设置")

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { SectionHeader("日志与展示") }
            val displayItems = listOf(
                GlobalSwitch.BASE64, GlobalSwitch.HEX, GlobalSwitch.STACK,
                GlobalSwitch.DISCARD_SYS_STACK, GlobalSwitch.FOLD_DUPLICATE_LOGS,
                GlobalSwitch.LOG_DETAIL_VIEW, GlobalSwitch.TOAST,
            )
            items(displayItems, key = { it.key }) { sw ->
                SwitchRow(sw.title, sw.desc, state.global[sw.key] ?: sw.default) { vm.setGlobal(sw, it) }
            }

            item { SectionHeader("抓包") }
            val captureItems = listOf(GlobalSwitch.CAPTURE_FILTER_LOCAL, GlobalSwitch.CAPTURE_FLOATING_WINDOW)
            items(captureItems, key = { it.key }) { sw ->
                SwitchRow(sw.title, sw.desc, state.global[sw.key] ?: sw.default) { vm.setGlobal(sw, it) }
            }

            item { SectionHeader("MCP 服务") }
            item {
                SettingsNavRow(
                    title = "MCP 服务",
                    subtitle = if (state.mcpRunning) "运行中 · 127.0.0.1:${state.mcpPort}" else "未启动 · 端口 ${state.mcpPort}",
                    active = state.mcpRunning,
                    onClick = onOpenMcp,
                )
            }
            item {
                SwitchRow(
                    GlobalSwitch.MCP_HTTP_ENABLED.title,
                    GlobalSwitch.MCP_HTTP_ENABLED.desc,
                    state.global[GlobalSwitch.MCP_HTTP_ENABLED.key] ?: false,
                ) { on ->
                    vm.setGlobal(GlobalSwitch.MCP_HTTP_ENABLED, on)
                    vm.toggleMcp(on)
                }
            }

            item { SectionHeader("其他") }
            item {
                // 缓存管理：显示当前占用，点进去可选择性清理
                SettingsNavRow(
                    title = "缓存管理",
                    subtitle = if (state.cacheSize.isBlank()) "查看并清理临时文件"
                    else "可清理 ${state.cacheSize}",
                    active = false,
                    onClick = onOpenCache,
                )
            }
            item {
                // 开源地址：点一下用浏览器打开仓库
                val ctx = androidx.compose.ui.platform.LocalContext.current
                SettingsNavRow(
                    title = "开源地址",
                    subtitle = PROJECT_REPO_URL.removePrefix("https://"),
                    active = false,
                    onClick = { com.sfzsgybb.ai.ui.components.openUrl(ctx, PROJECT_REPO_URL) },
                )
            }
            item { SettingsNavRow("关于", "版本 ${state.versionName}", false, onOpenAbout) }
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) StatusDot(true)
    }
}

// ---------------- MCP 控制台 ----------------

@Composable
fun McpConsoleScreen(
    onBack: () -> Unit,
    vm: McpConsoleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // 定时刷新连接数/请求数
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            vm.refreshStats()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(title = "MCP 服务", onBack = onBack)

        // 自定义连接地址（用户手动改过的）。
        // null 表示用自动算出来的地址；提到屏幕层级，
        // 因为「客户端配置」那段 JSON 也要用同一个地址。
        var customUrl by remember { mutableStateOf<String?>(null) }
        val shownUrl = customUrl ?: state.activeUrl

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------- 状态卡片 ----------
            item {
                Card(
                    Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusDot(state.running)
                            Text(
                                if (state.running) "运行中 · 端口 ${state.port}" else "已停止",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        if (state.running) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("连接", state.connections.toString(), Modifier.weight(1f))
                                StatCard("请求", state.requests.toString(), Modifier.weight(1f),
                                    accent = MaterialTheme.colorScheme.secondary)
                            }
                        }

                        // 启停按钮互斥：运行中时「启动」必须禁用，
                        // 否则用户点一下会先 stop 再 start，端口闪断一次，
                        // 已经连上的 AI 客户端会掉线。
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.startServer() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.running && !state.busy,
                            ) {
                                if (state.busy && !state.running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Text("  启动中…")
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Text("  启动")
                                }
                            }
                            OutlinedButton(
                                onClick = { vm.stopServer() },
                                modifier = Modifier.weight(1f),
                                enabled = state.running && !state.busy,
                            ) {
                                Text(if (state.busy && state.running) "停止中…" else "停止")
                            }
                        }

                        // 启动失败要明确说出来。
                        // 之前 runCatching 把异常吞了，用户点「启动」没反应，
                        // 也不知道是端口被占、还是没权限。
                        state.error?.let { err ->
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // ---------- 连接地址 ----------
            // 地址可编辑（有些人用固定的局域网 IP、或有端口映射），
            // 编辑后点「复制」就能直接贴到 AI 客户端里。
            item { SectionHeader("连接地址") }

            item {
                var editing by remember { mutableStateOf(false) }
                // draft 只在用户明确点「编辑」时初始化一次，
                // 不能在 remember 的 key 里绑 state.activeUrl ——
                // 那样后台一旦刷新状态，用户正在输入的内容就被冲掉。
                var draft by remember { mutableStateOf("") }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (editing) draft else shownUrl,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = !editing,
                        singleLine = true,
                        label = { Text("MCP 地址") },
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (editing) {
                                    // 保存：写进 customUrl，这才是真正生效的那一步
                                    customUrl = draft.trim().ifBlank { null }
                                    editing = false
                                } else {
                                    draft = shownUrl
                                    editing = true
                                }
                            }) {
                                Icon(
                                    if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                                    contentDescription = if (editing) "完成" else "编辑",
                                )
                            }
                        },
                    )

                    if (customUrl != null) {
                        Text(
                            "已使用自定义地址；局域网开关变化不会再覆盖它",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(shownUrl))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Text("  复制地址")
                        }
                        if (editing) {
                            OutlinedButton(
                                onClick = { editing = false },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("取消")
                            }
                        } else if (customUrl != null) {
                            OutlinedButton(
                                onClick = { customUrl = null },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("恢复自动")
                            }
                        }
                    }
                }
            }

            // ---------- 局域网 ----------
            item { SectionHeader("局域网访问") }
            item {
                SwitchRow(
                    title = "允许局域网访问",
                    desc = "开启后同一 WiFi 下的电脑可直接连接，无需 adb forward。" +
                        "绑定 0.0.0.0，不校验令牌，开箱即用。",
                    checked = state.lanEnabled,
                    onToggle = vm::setLanEnabled,
                )
            }
            if (state.lanEnabled && state.lanUrl.isNullOrBlank()) {
                item {
                    Text(
                        "未获取到局域网 IP，请确认已连接 WiFi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                    )
                }
            }

            // ---------- 保活 ----------
            item { SectionHeader("稳定性") }
            item {
                SwitchRow(
                    title = "常驻保活",
                    desc = "前台服务保持 MCP 在线，避免 AI 客户端长时间空闲后断连。" +
                        "会显示一条低优先级通知。",
                    checked = state.keepAlive,
                    onToggle = vm::setKeepAlive,
                )
            }

            // ---------- 客户端配置 ----------
            item { SectionHeader("客户端配置") }
            item {
                val cfg = buildString {
                    append("{\n")
                    append("  \"mcpServers\": {\n")
                    append("    \"algorithm-aide-pro\": {\n")
                    append("      \"url\": \"").append(shownUrl).append("\"\n")
                    append("    }\n")
                    append("  }\n")
                    append("}")
                }
                CopyableBlock(text = cfg, onCopy = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(cfg))
                })
            }
            if (!state.lanEnabled) {
                item {
                    Text(
                        "本机地址需要先执行：adb forward tcp:${state.port} tcp:${state.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- 工具清单 ----------
            item { SectionHeader("可用工具（${state.tools.size}）") }
            items(state.tools, key = { it.name }) { t ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        t.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        t.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

/** 可点击复制的等宽文本块。 */
@Composable
private fun CopyableBlock(text: String, onCopy: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onCopy)) {
        MonoBlock(text)
    }
}

// ---------------- Hook 规则 ----------------

@Composable
fun HookListScreen(
    packageName: String,
    onBack: () -> Unit,
    vm: HookListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    LaunchedEffect(packageName) { vm.load(packageName) }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(
            title = "Hook 规则",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加")
                }
            },
        )

        if (state.rules.isEmpty()) {
            EmptyState("还没有自定义规则", "点右上角加号添加类名与方法名")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp)) {
                items(state.rules, key = { it.id }) { rule ->
                    RuleRow(rule, onDelete = { vm.remove(rule.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }

    if (showDialog) {
        AddRuleDialog(
            onDismiss = { showDialog = false },
            onConfirm = { rule ->
                vm.add(rule)
                showDialog = false
            },
        )
    }
}

@Composable
private fun RuleRow(rule: HookRule, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                rule.className,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                buildString {
                    append(rule.methodName)
                    if (rule.parameterSign.isNotBlank()) append(rule.parameterSign)
                    if (rule.isConstructor) append("  [构造]")
                    if (rule.intercept) append("  [拦截]")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onConfirm: (HookRule) -> Unit) {
    var className by remember { mutableStateOf("") }
    var methodName by remember { mutableStateOf("") }
    var paramSign by remember { mutableStateOf("") }
    var intercept by remember { mutableStateOf(false) }
    var replaceValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Hook 规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = className, onValueChange = { className = it },
                    label = { Text("类名") }, placeholder = { Text("com.example.Foo") }, singleLine = true,
                )
                OutlinedTextField(
                    value = methodName, onValueChange = { methodName = it },
                    label = { Text("方法名") }, placeholder = { Text("encrypt") }, singleLine = true,
                )
                OutlinedTextField(
                    value = paramSign, onValueChange = { paramSign = it },
                    label = { Text("参数签名（可空）") },
                    placeholder = { Text("Ljava.lang.String;I") }, singleLine = true,
                )
                SwitchRow("拦截并替换返回值", "", intercept) { intercept = it }
                if (intercept) {
                    OutlinedTextField(
                        value = replaceValue, onValueChange = { replaceValue = it },
                        label = { Text("替换值") }, placeholder = { Text("true 或 {\"ok\":1}") }, singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = className.isNotBlank() && methodName.isNotBlank(),
                onClick = {
                    onConfirm(
                        HookRule(
                            className = className.trim(),
                            methodName = methodName.trim(),
                            parameterSign = paramSign.trim(),
                            isConstructor = methodName.trim() == "<init>",
                            intercept = intercept,
                            replaceResult = replaceValue.trim(),
                        )
                    )
                },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------------- Frida ----------------

@Composable
fun FridaEditorScreen(
    packageName: String,
    scriptId: String? = null,
    onBack: () -> Unit,
    vm: FridaViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    // 有 scriptId 就打开指定脚本，否则打开第一个（首次进入时的兜底）
    LaunchedEffect(packageName, scriptId) {
        if (scriptId != null && scriptId.isNotBlank()) {
            vm.openScript(packageName, scriptId)
        } else {
            vm.load(packageName)
        }
    }

    // 保存结果反馈：之前 savedMessage 设了却没人显示，
    // 用户点「保存」后完全不知道到底存没存上。
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // 有未保存改动时提示，并且返回要确认，避免手滑丢掉脚本
    val dirty = state.dirty
    var confirmLeave by remember { mutableStateOf(false) }

    // 【关键】系统返回键也要拦。
    // 之前只在左上角箭头上判断 dirty，用户按返回键 / 手势返回时页面直接 pop，
    // 脚本一字不留。只有 dirty 时才注册，正常返回不受影响。
    androidx.activity.compose.BackHandler(enabled = dirty) { confirmLeave = true }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AideTopBar(
                title = if (dirty) "Frida 脚本 · 未保存" else "Frida 脚本",
                // 有改动时先拦一下，给「保存并退出 / 放弃」的选择
                onBack = { if (dirty) confirmLeave = true else onBack() },
                actions = {
                    IconButton(
                        onClick = {
                            copyToClipboard(ctx, state.script)
                        },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制脚本")
                    }
                    TextButton(
                        onClick = { vm.save() },
                        enabled = dirty,
                    ) { Text("保存") }
                },
            )
            // 脚本名 + 启用开关
            //
            // 名称可改（列表页靠它区分）；启用状态决定注入时是否执行。
            // 之前只有一个固定 main.js，既不能命名也没法单独关闭。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    modifier = Modifier.weight(1f),
                    label = { Text("脚本名称", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = state.enabled, onCheckedChange = vm::setEnabled)
                    Text(
                        if (state.enabled) "启用" else "停用",
                        fontSize = 10.sp,
                        color = if (state.enabled) Sem.success
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            OutlinedTextField(
                value = state.script,
                onValueChange = vm::setScript,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                // 12sp 太挤，脚本要能看清缩进；行高给足
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                placeholder = { Text("Java.perform(function () {\n    // 在这里编写脚本\n});") },
            )
        }
    }

    // 未保存时的离页确认
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("脚本还没保存") },
            text = { Text("直接返回会丢掉这次的修改。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.save()
                    onBack()
                }) { Text("保存并返回") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onBack()
                }) { Text("放弃修改") }
            },
        )
    }
}

@Composable
fun FridaLogScreen(
    packageName: String,
    onBack: () -> Unit,
    vm: FridaLogViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(packageName) { vm.load(packageName) }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(
            title = "Frida 日志",
            onBack = onBack,
            actions = {
                IconButton(onClick = { vm.clear() }) { Icon(Icons.Filled.Delete, contentDescription = "清空") }
            },
        )
        if (state.logs.isEmpty()) {
            EmptyState("暂无脚本日志", "脚本执行后输出会出现在这里")
        } else {
            LazyColumn(contentPadding = PaddingValues(ScreenPadding)) {
                items(state.logs, key = { "${it.time}-${it.scriptName}-${it.message.hashCode()}" }) { l ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            "${formatTimeShort(l.time)}  ${l.level}  ${l.scriptName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                        Text(l.message, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- 关于 ----------------

/**
 * 关于页。
 *
 * 【反馈模式】
 * 之前 feedbackOnly=true 只是换个标题，正文和关于页一字不差 ——
 * 用户点「反馈」看到的还是版本号，等于没有反馈入口。
 * 现在反馈模式改为：说明怎么反馈 + 一键复制诊断信息（系统版本、框架状态、
 * 作用域、开关数量），这样提问题时不用自己一项项抄。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    feedbackOnly: Boolean = false,
    vm: AboutViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    if (feedbackOnly) {
        FeedbackContent(state = state, clipboard = clipboard, onBack = onBack)
        return
    }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(title = "关于", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("AlgorithmAidePro", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "版本 ${state.versionName} (${state.versionCode})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Xposed 模块 / 抓包工具 / 算法日志分析，内建 MCP 服务供 AI 调用。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item { SectionHeader("运行环境") }
            item {
                // 直接展示 ModuleStatusChecker 的结论，而不是二元的「已激活/未检测到」：
                // 「无法确认」和「未激活」是两回事，混在一起会误导用户。
                val m = state.module
                InfoRow(
                    "Xposed 框架",
                    when {
                        m == null -> "检测中…"
                        m.active -> "${m.framework ?: "Xposed"} · 已启用"
                        m.state == com.sfzsgybb.ai.util.ModuleStatusChecker.State.NO_FRAMEWORK ->
                            "未检测到"
                        m.state == com.sfzsgybb.ai.util.ModuleStatusChecker.State.UNKNOWN ->
                            "${m.framework ?: "Xposed"} · 无法确认"
                        else -> "${m.framework ?: "Xposed"} · 未启用"
                    },
                )
            }
            item { InfoRow("Root", if (state.rooted) "已获取" else "未获取") }
            // 作用域：勾了哪些应用（这是最容易漏的一步）
            state.module?.let { m ->
                if (m.scopes.isNotEmpty()) {
                    item { InfoRow("Hook 作用域", "${m.scopes.size} 个应用") }
                    item { ScopeList(m.scopes) }
                }
            }

            item { SectionHeader("能力统计") }
            item { InfoRow("Hook 开关", "${HookSwitch.count} 项") }
            item { InfoRow("全局选项", "${GlobalSwitch.entries.size} 项") }
            item { InfoRow("MCP 工具", "${state.toolCount} 个") }

            item {
                Text(
                    "本工具用于自有设备上的应用调试与协议分析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/**
 * Hook 作用域包名列表。
 *
 * 默认只显示前 8 个，其余折叠 —— 十个以上的包名一列铺开能占满整屏，
 * 把下面的「能力统计」全挤到看不见的地方。
 */
@Composable
private fun ScopeList(scopes: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val limit = 8
    val showAll = expanded || scopes.size <= limit
    val visible = if (showAll) scopes else scopes.take(limit)

    Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        visible.forEach { pkg ->
            Text(
                pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (!showAll) {
            Text(
                "还有 ${scopes.size - limit} 个，展开",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = true }.padding(vertical = 6.dp),
            )
        } else if (scopes.size > limit) {
            Text(
                "收起",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = false }.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            v,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 反馈页。
 *
 * 【为什么不再是「关于页换个标题」】
 * 用户点「反馈」是想提问题，不是想看版本号。
 * 这里给的是：怎么反馈、以及一键复制诊断信息 —— 
 * 提问题时粘贴一段就够了，不用自己一项项报环境。
 */
@Composable
private fun FeedbackContent(
    state: AboutUiState,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onBack: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    val diag = buildString {
        appendLine("【诊断信息】")
        appendLine("版本：${state.versionName}(${state.versionCode})")
        appendLine("系统：Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("机型：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Root：${if (state.rooted) "已获取" else "未获取"}")
        val m = state.module
        appendLine(
            "框架：" + when {
                m == null -> "检测中"
                m.active -> "${m.framework ?: "Xposed"} · 已启用"
                else -> "${m.framework ?: "Xposed"} · ${m.state}"
            },
        )
        appendLine("作用域：${m?.scopes?.size ?: 0} 个应用")
        appendLine("MCP 工具：${state.toolCount} 个")
    }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(title = "反馈", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("怎么反馈") }
            item {
                Card(
                    Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "提问题时请附上下面这段诊断信息，方便定位问题。",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp,
                        )
                        Text(
                            "1. 点「复制诊断信息」\n" +
                                "2. 到社区帖子里粘贴\n" +
                                "3. 补充：目标应用名、你点了哪些开关、现象是什么",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            item { SectionHeader("诊断信息") }
            item {
                MonoBlock(diag)
            }
            item {
                Button(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(diag))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(if (copied) "  已复制" else "  复制诊断信息")
                }
            }
        }
    }
}

