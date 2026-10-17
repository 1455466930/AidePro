package com.sfzsgybb.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.data.model.LogGroup
import com.sfzsgybb.ai.hook.config.HookSwitch
import com.sfzsgybb.ai.mcp.tools.AppEntry
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.Chip
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.components.StatCard
import com.sfzsgybb.ai.ui.components.StatusDot
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import com.sfzsgybb.ai.ui.theme.Sem
import com.sfzsgybb.ai.ui.components.cardOutline
import kotlinx.coroutines.launch

/** 分组标题映射，避免中文散落在多处。顺序即界面展示顺序。 */
private val GROUP_TITLES = linkedMapOf(
    LogGroup.CRYPTO to "加密算法",
    LogGroup.NETWORK to "网络与证书",
    LogGroup.STORAGE to "存储访问",
    LogGroup.DATABASE to "数据库",
    LogGroup.PREFS to "SharedPreferences",
    LogGroup.UI to "界面交互",
    LogGroup.SYSTEM to "系统与环境",
)

@Composable
fun AppListScreen(
    onOpenApp: (String) -> Unit,
    vm: AppListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AideTopBar(
            title = "应用",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("已安装", state.totalCount.toString(), Modifier.weight(1f))
            StatCard(
                "已启用", state.enabledCount.toString(), Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 10.dp),
            placeholder = { Text("搜索应用名或包名") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清空")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )

        // 已启用的应用排在最前面（见 DeviceAppListProvider 的排序），
        // 这里只做个说明，不再提供「仅显示已启用」开关 —— 那个开关没什么用，
        // 而且开了之后反而找不到想新启用的应用。
        Row(
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "共 ${state.totalCount} 个应用，已启用 ${state.enabledCount} 个（已启用的排在前面）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }

        if (state.apps.isEmpty() && !state.loading) {
            EmptyState(
                if (state.query.isNotBlank()) "没有匹配的应用" else "没有已安装的应用",
                if (state.query.isNotBlank()) "换个关键词，或只输包名的一段" else "先刷新试试",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.apps, key = { it.packageName }) { app ->
                    Column(Modifier.animateItem()) {
                        AppRow(
                            app = app,
                            onClick = { onOpenApp(app.packageName) },
                            onToggle = { on -> vm.toggleEnabled(app.packageName, on) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }
}

/**
 * 应用图标。先显示首字母占位，图标异步加载完再换成真图标。
 *
 * 【为什么不能直接在数据里带图标】
 * listApps() 一次返回上千个 AppEntry，每个都 getApplicationIcon 要几百毫秒，
 * 列表要等全套图标取完才出来。这里让占位先上屏，图标按行懒加载。
 */
@Composable
private fun AppIconBox(app: AppEntry, size: Dp = 42.dp) {
    val ctx = LocalContext.current
    var bmp by remember(app.packageName) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(app.packageName) {
        bmp = runCatching {
            val d = ctx.packageManager.getApplicationIcon(app.packageName)
            if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                d.bitmap
            } else {
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 96
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 96
                val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(b)
                d.setBounds(0, 0, c.width, c.height)
                d.draw(c)
                b
            }.asImageBitmap()
        }.getOrNull()
    }

    val img = bmp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(bitmap = img, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Text(
                text = app.label.take(1).ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIconBox(app)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isSystem) Chip("系统", MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.hookEnabled) Chip("已启用", Sem.success)
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // 开关本身就是状态，不再额外挂一个 StatusDot ——
        // 之前绿点 + 开关并排，同一个信息画了两遍，还夹在中间显得乱。
        Switch(checked = app.hookEnabled, onCheckedChange = onToggle)
    }
}

@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onOpenHooks: () -> Unit,
    onOpenFrida: () -> Unit,
    onOpenFridaLog: () -> Unit,
    vm: AppDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(packageName) { vm.load(packageName) }

    // 操作结果提示（启动失败 / 无 root 等）
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 标题用应用名，而不是包名最后一段。
            // com.foo.bar 取出来只显示 "bar"，同一个前缀下的应用全长得一样。
            AideTopBar(
                title = state.appLabel.ifBlank { packageName.substringAfterLast('.') },
                onBack = onBack,
            )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("启用 Hook", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "关闭后该应用不加载任何模块",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.enabled, onCheckedChange = vm::setAppEnabled)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppActionCard("自定义规则", "${state.hooks} 条", Modifier.weight(1f), onOpenHooks)
                    AppActionCard("Frida 脚本", "编辑脚本", Modifier.weight(1f), onOpenFrida)
                    AppActionCard("Frida 日志", "查看输出", Modifier.weight(1f), onOpenFridaLog)
                }
            }

            // 启动 / 重启：配置改动后必须让目标应用重新走一遍 Application.onCreate，
            // Hook 才会重新读取配置并安装模块。
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusDot(state.targetRunning)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (state.targetRunning) "${state.appLabel} 正在运行" else "${state.appLabel} 未运行",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "改动开关后需重启目标应用才会生效",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.refreshRunning() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新状态")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.restartApp() },
                                modifier = Modifier.weight(1f),
                                // busy 期间两个按钮都禁用：
                                // 整个流程是 stop → 等 800ms → launch，
                                // 用户在这近一秒里连点会叠出两套操作
                                enabled = state.enabled && !state.busy,
                            ) {
                                if (state.busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(if (state.busy) "  处理中…" else "  强停并重启")
                            }
                            OutlinedButton(
                                onClick = { vm.launchApp() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  启动应用")
                            }
                        }

                        if (!state.hasRoot) {
                            Text(
                                "设备无 root，「强停并重启」会跳转到系统设置页手动停止",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = { vm.openSystemSettings() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("打开系统应用设置")
                        }
                    }
                }
            }

            // ---------- 分组快速跳转 ----------
            // 38 个开关排成一列，滚到「数据库查询」要划六七屏。
            // 顶部给一排锚点，点哪组就跳到哪组（scrollToItem 到该组标题的 index）。
            item(key = "group_index") {
                HookGroupIndex(
                    onJump = { idx -> scope.launch { listState.animateScrollToItem(idx) } },
                )
            }

            // 分组标题的 LazyColumn item 索引：用来实现跳转。
            // 这里边构建边记录，不需要提前算，因为 build 顺序就是渲染顺序。
            var itemCursor = 0
            GROUP_TITLES.forEach { (group, title) ->
                val switches = HookSwitch.byGroup(group)
                if (switches.isEmpty()) return@forEach

                val headerIndex = itemCursor
                itemCursor += 1 + switches.size

                item(key = "header_$group") {
                    HookGroupHeader(
                        title = title,
                        count = switches.size,
                        allOn = switches.all { state.switches[it.key] == true },
                        canEdit = state.enabled,
                        onSetAll = { on -> vm.setGroup(group, on) },
                    )
                }

                items(switches, key = { it.key }) { sw ->
                    SwitchRow(
                        title = sw.title,
                        desc = if (sw.risky) sw.desc + "（高风险）" else sw.desc,
                        checked = state.switches[sw.key] ?: false,
                        enabled = state.enabled,
                        onToggle = { vm.setSwitch(sw, it) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun AppActionCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick).cardOutline(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    desc: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

/**
 * 分组锚点栏：点一组直接滚到那一组。
 *
 * 【为什么要它】
 * 38 个 Hook 开关连成一列，找「数据库查询」要划六七屏；
 * 用户根本不知道下面还有这么多组。一排锚点等于目录。
 */
@Composable
private fun HookGroupIndex(onJump: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GROUP_TITLES.forEach { (group, title) ->
            AssistChip(
                onClick = { onJump(group) },
                label = { Text(title, fontSize = 12.sp) },
            )
        }
    }
}

/**
 * 分组标题行 + 「全部开启 / 全部关闭」。
 *
 * 【为什么全开要二次确认】
 * 一键打开存储组的 13 个 Hook，等于给目标应用挂了 13 个反射 Hook，
 * 会让应用明显变慢，有的应用直接崩。误触一次代价太大。
 * 全关是安全的（最多是抓不到数据），不确认。
 */
@Composable
private fun HookGroupHeader(
    title: String,
    count: Int,
    allOn: Boolean,
    canEdit: Boolean,
    onSetAll: (Boolean) -> Unit,
) {
    var confirmAllOn by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$count 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!allOn) {
                TextButton(
                    onClick = { confirmAllOn = true },
                    enabled = canEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text("全开", fontSize = 12.sp)
                }
            }
            TextButton(
                onClick = { onSetAll(false) },
                enabled = canEdit && !allOn,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    "全关",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmAllOn) {
        AlertDialog(
            onDismissRequest = { confirmAllOn = false },
            title = { Text("打开「$title」全部 $count 项？") },
            text = {
                Text(
                    "会给目标应用挂上 $count 个 Hook，可能让应用明显变慢，个别应用会崩溃。\n" +
                        "建议先只打开需要的那几项，需要时再逐项加。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmAllOn = false; onSetAll(true) }) { Text("全部打开") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAllOn = false }) { Text("取消") }
            },
        )
    }
}
