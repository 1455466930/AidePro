package com.sfzsgybb.ai.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.data.model.LogGroup
import com.sfzsgybb.ai.data.model.LogRecord
import com.sfzsgybb.ai.data.model.ValueMode
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.Chip
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.components.MonoBlock
import com.sfzsgybb.ai.ui.components.copyToClipboard
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.GroupColors
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableIntStateOf
import com.sfzsgybb.ai.ui.theme.Sem
import com.sfzsgybb.ai.ui.components.cardOutline
import com.sfzsgybb.ai.ui.components.AppIconByPackage
import androidx.compose.material.icons.filled.DeleteSweep

/**
 * 日志页。
 *
 * 【两态设计】
 *  - packageFilter == null → 显示「应用卡片列表」
 *    每个卡片：图标 + 软件名 + 包名 + 最近时间 + 数据量
 *    点进去看该应用的日志
 *  - packageFilter != null → 显示该应用的日志列表
 *    顶部有返回按钮 + 分组筛选
 *
 * 这样打开日志页第一眼就是「哪些应用产生了日志」，
 * 不用在一大堆混在一起的日志里翻。
 */
@Composable
fun LogListScreen(
    onOpenLog: (Long) -> Unit,
    /**
     * 初始要展示的应用包名。
     *
     * 从抓包页的「查看日志」图标跳过来时带上，直接进该应用的日志列表，
     * 不用再在应用卡片里找一遍。传 null 表示显示应用卡片列表。
     */
    initialPackage: String? = null,
    vm: LogListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // 带着包名进来时，直接切到该应用的日志列表（只做一次，避免覆盖用户手动返回）
    LaunchedEffect(initialPackage) {
        if (!initialPackage.isNullOrBlank() && state.packageFilter != initialPackage) {
            vm.setPackageFilter(initialPackage)
        }
    }

    // 进入页面时开启自动刷新，离开时停止，避免后台空转
    DisposableEffect(Unit) {
        vm.startAutoRefresh()
        onDispose { vm.stopAutoRefresh() }
    }

    // 进入某个应用的日志后（内部二级态，导航栈并不知道），
    // 返回键应该退回应用列表，而不是直接退出日志页跳回首页。
    androidx.activity.compose.BackHandler(enabled = state.packageFilter != null) {
        vm.setPackage(null)
    }

    // 操作结果提示（清空日志等）
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.packageFilter == null) {
            AppLogList(
                state = state,
                onOpenApp = { vm.setPackage(it) },
                onRefresh = { vm.loadAppOptions(); vm.refresh() },
                onSearch = { vm.setAppQuery(it) },
                onDeleteApp = { vm.deleteAppLogs(it) },
                onClearAll = { vm.clearAllLogs() },
            )
        } else {
            AppLogDetail(
                state = state,
                onBack = { vm.setPackage(null) },
                onOpenLog = onOpenLog,
                onGroup = { vm.setGroup(it) },
                onRefresh = { vm.refresh() },
                onSearch = { vm.search(it) },
                onLoadMore = { vm.loadMore() },
                onClearCurrent = { vm.clearCurrentLogs() },
                vm = vm,
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 第一态：应用卡片列表。
 *
 * 顶部是搜索框（可按软件名或包名过滤），
 * 长按卡片弹出「是否删除该应用日志」的确认框。
 */
@Composable
private fun AppLogList(
    state: LogListUiState,
    onOpenApp: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onDeleteApp: (String) -> Unit,
    /** 清空全部应用的日志 */
    onClearAll: () -> Unit,
) {
    // 待删除的应用（长按后设置，用户确认才真删）
    var pendingDelete by remember { mutableStateOf<LogAppOption?>(null) }
    val apps = state.filteredApps

    Column(Modifier.fillMaxSize()) {
        AideTopBar(
            title = "日志",
            actions = {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "刷新") }
                // 清空全部日志：放在刷新右边，和抓包页的操作位置保持一致
                IconButton(onClick = onClearAll) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "清空全部日志",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )

        // ---------- 搜索框（替代原来的删除按钮）----------
        OutlinedTextField(
            value = state.appQuery,
            onValueChange = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 6.dp),
            placeholder = { Text("搜索应用名称或包名") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.appQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Filled.Clear, "清空")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )

        if (apps.isEmpty()) {
            EmptyState(
                title = when {
                    state.loading -> "加载中…"
                    state.appQuery.isNotBlank() -> "没有匹配的应用"
                    else -> "暂无日志"
                },
                hint = if (state.appQuery.isNotBlank()) "换个关键词试试"
                else "在应用页给目标应用打开开关，再重启该应用即可捕获",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 长按删除必须先说一声。
            // 之前删除只能长按卡片触发，界面上零提示 ——
            // 用户既找不到删除入口，又可能误长按弹出个吓人的框。
            item(key = "hint_long_press") {
                Text(
                    "点卡片查看日志；长按或点右侧垃圾桶可删除该应用的日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            items(apps, key = { it.packageName }) { opt ->
                AppLogCard(
                    opt = opt,
                    onClick = { onOpenApp(opt.packageName) },
                    // 长按 → 弹确认框（不用顶部按钮，避免误删）
                    onLongClick = { pendingDelete = opt },
                    onDelete = { pendingDelete = opt },
                )
            }
        }
    }

    // ---------- 删除确认对话框 ----------
    pendingDelete?.let { opt ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除该应用的日志？") },
            text = {
                Text(
                    "将删除「${opt.label}」的 ${opt.count} 条日志，此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteApp(opt.packageName)
                    pendingDelete = null
                }) {
                    Text("确定删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 应用日志卡片：图标 + 软件名 + 包名 + 时间 + 数据量。
 *
 * 点击进入该应用的日志列表；长按**或点右侧垃圾桶**弹出删除确认框。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppLogCard(
    opt: LogAppOption,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 应用图标
            AppIcon(opt.icon)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    opt.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    opt.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    formatTimeShort(opt.lastTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Text(
                    formatBytes(opt.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                )
            }

            // 可见的删除入口。真正删除仍然要过确认框，
            // 这里只是让「能删」这件事被发现，而不是只靠长按这种隐藏手势。
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除「${opt.label}」的日志",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 应用图标：取不到时用占位方块。 */
@Composable
private fun AppIcon(icon: android.graphics.drawable.Drawable?) {
    val size = 42.dp
    if (icon == null) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val bmp = remember(icon) { icon.toBitmap().asImageBitmap() }
    androidx.compose.foundation.Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = Modifier.size(size).clip(RoundedCornerShape(9.dp)),
    )
}

/** Drawable → Bitmap。 */
private fun android.graphics.drawable.Drawable.toBitmap(): android.graphics.Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable && bitmap != null) return bitmap
    val w = if (intrinsicWidth > 0) intrinsicWidth else 96
    val h = if (intrinsicHeight > 0) intrinsicHeight else 96
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

/**
 * 第二态：某个应用的日志列表。
 */
@Composable
private fun AppLogDetail(
    state: LogListUiState,
    onBack: () -> Unit,
    onOpenLog: (Long) -> Unit,
    onGroup: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    /** 清空当前应用的日志 */
    onClearCurrent: () -> Unit,
    vm: LogListViewModel,
) {
    val listState = rememberLazyListState()
    val appLabel = state.appOptions.firstOrNull {
        it.packageName == state.packageFilter
    }?.label ?: state.packageFilter.orEmpty()

    // 触底自动加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.logs.isNotEmpty() && last >= state.logs.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, state.query) {
        if (shouldLoadMore && state.query.isBlank()) onLoadMore()
    }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(
            title = appLabel,
            onBack = onBack,
            actions = {
                Text(
                    state.total.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "刷新") }
                // 清空该应用的日志
                IconButton(onClick = onClearCurrent) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "清空该应用日志",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 6.dp),
            placeholder = { Text("搜索日志名 / 类名 / 内容") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Clear, "清空") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.groupFilter < 0,
                onClick = { onGroup(LogGroup.ANY) },
                label = { Text("全部", fontSize = 12.sp) },
            )
            // 只列出实际有日志的分组（带条数）。
            // 顺序按 LogGroup.ALL 的固定顺序，不随数量跳动，用户找位置靠位置感。
            LogGroup.ALL.forEach { g ->
                val count = state.groupCounts[g] ?: 0
                if (count <= 0) return@forEach
                FilterChip(
                    selected = state.groupFilter == g,
                    onClick = { onGroup(g) },
                    label = { Text("${LogGroup.label(g)} $count", fontSize = 12.sp) },
                )
            }
        }

        if (state.logs.isEmpty()) {
            EmptyState(
                title = when {
                    state.loading -> "加载中…"
                    // 分组筛空了要给「怎么退回去」的出口，不能只说没日志
                    state.groupFilter >= 0 ->
                        "「${LogGroup.label(state.groupFilter)}」分组暂无日志"
                    state.query.isNotBlank() -> "没有匹配的日志"
                    else -> "该应用暂无日志"
                },
                hint = when {
                    state.groupFilter >= 0 -> "点上方「全部」查看其他分组"
                    state.query.isNotBlank() -> "换个关键词，或清空搜索框"
                    else -> "重启该应用后操作几下，日志会自动上报"
                },
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.logs, key = { it.id }) { log ->
                    // animateItem：新日志从顶部插入时整列平滑下推，
                    // 不加的话自动刷新每 2 秒整屏内容闪跳一次
                    Column(Modifier.animateItem()) {
                        LogRow(log = log, onClick = { onOpenLog(log.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
                if (state.hasMore) {
                    item {
                        Text(
                            "加载更多…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 人类可读的字节数。 */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0B"
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> String.format("%.2fMB", bytes / 1024.0 / 1024.0)
}

@Composable
private fun LogRow(
    log: LogRecord,
    onClick: () -> Unit,
    /** 已按应用筛选时为 true，此时不再重复显示应用名 */
    showApp: Boolean = false,
    /** 应用显示名（未知时为 null） */
    appLabel: String? = null,
) {
    val groupColor = GroupColors.of(log.group)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 应用图标：一眼看出这条日志来自哪个应用。
        // 按应用筛选时也显示 —— 统一的行布局比「有时有有时没有」更好扫视。
        if (!log.packageName.isNullOrBlank()) {
            AppIconByPackage(
                packageName = log.packageName,
                size = 24.dp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(LogGroup.label(log.group), groupColor)
                // 文件读写方向标签最多占一个，避免一行里塞太多 chip
                fileOpTag(log.logName)?.let { (tag, color) ->
                    Chip(tag, color)
                }
                // 【标题层】14sp / SemiBold / onSurface —— 未读时再加粗一级，
                // 不只用颜色小圆点区分（色弱和深色模式下圆点几乎看不见）
                Text(
                    text = log.logName.ifBlank { log.className.substringAfterLast('.') },
                    fontSize = 14.sp,
                    fontWeight = if (log.isRead) FontWeight.Medium else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (!log.isRead) {
                    // 未读标记：图标 + 圆点双保险
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(groupColor),
                    )
                }
                // 【元信息层】11sp + 降到 60% 不透明度
                Text(
                    formatTimeShort(log.time),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            // 应用名：看「全部应用」时能一眼分辨日志来源（元信息层）
            if (!showApp && !log.packageName.isNullOrBlank()) {
                Text(
                    text = appLabel ?: log.packageName.orEmpty(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
            // 【正文层】12sp mono
            if (log.payloadBrief.isNotBlank()) {
                Text(
                    text = log.payloadBrief,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                )
            }
            // 操作细节：比如「⚠ 覆盖写入（会清空原内容）」—— 默认折一行
            if (log.paramDesc.isNotBlank()) {
                Text(
                    text = log.paramDesc,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = if (log.paramDesc.startsWith("⚠")) Sem.warning
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (log.resultBrief.isNotBlank()) {
                Text(
                    text = "→ " + log.resultBrief,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * 把「文件读取 / 文件写入 / 文件删除」映射成带颜色的短标签。
 *
 * 列表里一堆文件操作长得都一样，光看路径分不出方向，
 * 加个彩色标签比读文字快得多。
 *
 * 颜色走语义令牌（不再硬编码十六进制），深色浅色都自适应。
 *
 * @return (标签文本, 颜色)；不是文件操作返回 null
 */
@Composable
private fun fileOpTag(logName: String): Pair<String, Color>? {
    if (!logName.startsWith("文件")) return null
    return when {
        "读取" in logName -> "读" to Sem.success
        "写入" in logName -> "写" to Sem.warning
        "删除" in logName -> "删" to Sem.danger
        else -> null
    }
}

/**
 * 日志详情页。
 *
 * 【字段排列对齐原版】
 * 原版把加密调用按类别拆开显示，而不是一堆裸字节：
 *
 *   时间 / 日志名称 / 日志分组 / 应用
 *   算法名称            ← algoName
 *   参数 / 参数类型      ← paramDesc
 *   加密密钥 / 密钥类型   ← keyData / keyType
 *   加密内容            ← payload
 *   结果（加密结果等）    ← results
 *   调用堆栈            ← callStack
 *
 * 每块内容都可长按复制。
 */
/**
 * 日志详情页。
 *
 * 【字段排列对齐原版】
 *   时间 / 日志名称 / 日志分组 / 应用包名 / 类名 / 对象
 *   算法名称            ← algoName
 *   参数                ← paramDesc
 *   加密密钥 + 类型标签  ← keyData / keyType
 *   加密内容            ← payload
 *   结果（摘要/加密/解密/MAC）← results
 *   调用堆栈            ← callStack
 *
 * 【每块内容独立控制】
 * 内容区有独立的渲染模式（text / hex / base64）与折叠开关 ——
 * 密钥看 hex 方便，正文看 text 方便，不用整页统一切换。
 */
@Composable
fun LogDetailScreen(
    logId: Long,
    onBack: () -> Unit,
    vm: LogDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(logId) { vm.load(logId) }

    // 统一的复制 + 提示
    fun doCopy(text: String, what: String) {
        copyToClipboard(ctx, text)
        scope.launch { snackbar.showSnackbar("已复制$what") }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val log = state.log
            AideTopBar(
                title = log?.logName?.takeIf { it.isNotBlank() } ?: "日志详情",
                onBack = onBack,
                actions = {
                    if (log != null) {
                        IconButton(onClick = { doCopy(vm.buildFullText(), "整条日志") }) {
                            Icon(Icons.Filled.ContentCopy, "复制整条日志")
                        }
                    }
                },
            )

            if (log == null) {
                EmptyState(if (state.loading) "加载中…" else "记录不存在")
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---------- 基本信息 ----------
                item {
                    DetailCard {
                        MetaLine("时间", formatTime(log.time))
                        MetaLine("日志名称", log.logName)
                        MetaLine("日志分组", LogGroup.label(log.group))
                        if (!log.packageName.isNullOrBlank()) MetaLine("应用包名", log.packageName)
                        MetaLine("类名", log.className)
                        MetaLine("对象", log.objName)
                    }
                }

                // ---------- 算法名称 ----------
                if (log.algoName.isNotBlank()) {
                    item { TextField("算法名称", log.algoName) { doCopy(log.algoName, "算法名称") } }
                }

                // ---------- 参数 ----------
                if (log.paramDesc.isNotBlank()) {
                    item { TextField("参数", log.paramDesc) { doCopy(log.paramDesc, "参数") } }
                }

                // ---------- 加密密钥 ----------
                if (log.keyData.isNotEmpty()) {
                    item {
                        BlobField(
                            title = "加密密钥",
                            badge = log.keyType.ifBlank { null },
                            bytes = log.keyData,
                            onCopy = { doCopy(it, "密钥") },
                        )
                    }
                }

                // ---------- 加密内容 ----------
                item {
                    BlobField(
                        title = if (log.keyData.isNotEmpty()) "加密内容" else "输入 / 载荷",
                        bytes = log.payload,
                        onCopy = { doCopy(it, "内容") },
                    )
                }

                // ---------- 结果 ----------
                items(log.results.size, key = { idx -> "res-${log.id}-$idx" }) { idx ->
                    val kind = log.resultKind.ifBlank {
                        if (log.results.size == 1) "结果" else "结果 #${idx + 1}"
                    }
                    BlobField(
                        title = kind,
                        bytes = log.results[idx],
                        onCopy = { doCopy(it, kind) },
                    )
                }

                // ---------- 调用堆栈 ----------
                if (log.callStack.isNotBlank()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FieldHeader("调用堆栈") { doCopy(log.callStack, "调用堆栈") }
                            MonoBlock(log.callStack, maxLines = 80)
                        }
                    }
                }
            }
        }
    }
}

/** 字段头：标题 + 右侧复制按钮。 */
@Composable
private fun FieldHeader(title: String, badge: String? = null, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DetailLabel(title)
        if (badge != null) {
            Spacer(Modifier.size(6.dp))
            Chip(badge, MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "复制",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onCopy),
        )
    }
}

/** 纯文本字段。 */
@Composable
private fun TextField(label: String, value: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        FieldHeader(label, onCopy = onCopy)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

/**
 * 二进制内容字段：自带渲染模式切换与分段展开。
 *
 * 【为什么不能一次性渲染整串】
 * 之前的写法是 `rendered.lineSequence().count()` 遍历全文算行数，
 * 展开时又把整串（可能几 MB）塞进一个 Text 节点 ——
 * 大响应体会直接掉帧甚至 ANR。
 *
 * 现在：
 *  - 行数按「字节数 ÷ 每行字节」估算，不遍历
 *  - 每次只解码/渲染前 [PAGE] 行，点「再显示」按页追加
 *  - 单个 Text 节点控制在几十 KB 内
 *  - 内容包在 SelectionContainer 里，支持长按选中复制片段
 */
@Composable
private fun BlobField(
    title: String,
    bytes: ByteArray,
    badge: String? = null,
    onCopy: (String) -> Unit,
) {
    var mode by remember { mutableStateOf(ValueMode.TEXT) }
    // 当前显示多少行；0 = 折叠（只显示预览）
    var visibleLines by remember(bytes, mode) { mutableIntStateOf(0) }

    val previewLines = 8

    // 估算总行数，不遍历全文
    val totalLines = remember(bytes, mode) { estimateLines(bytes, mode) }

    // 只渲染需要的行 —— 永远不会把整串塞进 Text
    val shown = remember(bytes, mode, visibleLines) {
        val want = if (visibleLines == 0) previewLines else visibleLines
        renderBytesHead(bytes, mode, want)
    }
    val shownLineCount = remember(shown) { shown.count { it == '\n' } + 1 }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 头：标题 + 大小 + 复制（复制始终拷完整内容）
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailLabel(title)
            if (badge != null) {
                Spacer(Modifier.size(6.dp))
                Chip(badge, MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${bytes.size} B",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "复制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onCopy(renderBytesFull(bytes, mode)) },
            )
        }

        // 渲染模式切换（每块独立）
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m; visibleLines = 0 },
                    label = { Text(m.name.lowercase(), fontSize = 11.sp) },
                )
            }
        }

        // 内容区：可选中的等宽文本
        SelectionContainer {
            MonoBlock(shown.ifBlank { "(空)" })
        }

        // 分段加载：只在内容确实超过预览行数时出现
        if (totalLines > previewLines) {
            val expandedAll = visibleLines >= totalLines
            Text(
                when {
                    expandedAll -> "收起"
                    visibleLines == 0 -> "展开（约 $totalLines 行）"
                    else -> "再显示 $PAGE_LINES 行（已显示 $shownLineCount / 约 $totalLines）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                modifier = Modifier.clickable {
                    visibleLines = when {
                        expandedAll -> 0
                        visibleLines == 0 -> PAGE_LINES
                        else -> visibleLines + PAGE_LINES
                    }
                },
            )
        }
    }
}

/** 每次点「再显示」追加的行数。 */
private const val PAGE_LINES = 200

/**
 * 估算渲染后的行数（不遍历全文）。
 *
 * - TEXT：按换行符 + 平均行宽估算
 * - HEX/BASE64：按每行字节数换算
 */
private fun estimateLines(bytes: ByteArray, mode: ValueMode): Int {
    if (bytes.isEmpty()) return 0
    return when (mode) {
        // 文本：平均每行 60 字节，最少 1 行
        ValueMode.TEXT -> {
            val sample = bytes.copyOfRange(0, minOf(bytes.size, 64 * 1024))
            val newlines = sample.count { it == '\n'.code.toByte() }
            if (newlines > 0) {
                // 用样本里的换行密度推算全文
                (newlines.toLong() * bytes.size / sample.size).toInt().coerceAtLeast(1)
            } else {
                (bytes.size / 60).coerceAtLeast(1)
            }
        }
        // HEX：每行约 32 字节 = 64 字符
        ValueMode.HEX -> (bytes.size / 32).coerceAtLeast(1)
        // BASE64：每行约 48 字节
        ValueMode.BASE64 -> (bytes.size / 48).coerceAtLeast(1)
    }
}

/**
 * 只渲染前 [lines] 行（对应的字节切片），不碰整串。
 *
 * 为了不因为截断把多字节字符劈开，TEXT 模式取到字节边界后再解码。
 */
private fun renderBytesHead(bytes: ByteArray, mode: ValueMode, lines: Int): String {
    if (bytes.isEmpty()) return ""

    // 按「每行多少字节」算出需要多少字节
    val bytesPerLine = when (mode) {
        ValueMode.TEXT -> 60
        ValueMode.HEX -> 32
        ValueMode.BASE64 -> 48
    }
    val need = (lines.toLong() * bytesPerLine).coerceAtMost(bytes.size.toLong()).toInt()
    if (need >= bytes.size) return renderBytesFull(bytes, mode)

    val slice = bytes.copyOfRange(0, need)
    val body = when (mode) {
        ValueMode.TEXT -> runCatching { String(slice, Charsets.UTF_8) }.getOrDefault("")
        ValueMode.HEX -> toHexStr(slice)
        ValueMode.BASE64 -> android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)
    }
    return body + "\n…"
}

/** 完整渲染（仅「复制」和内容确实很小时才走这里）。 */
private fun renderBytesFull(bytes: ByteArray, mode: ValueMode): String = when (mode) {
    ValueMode.TEXT -> runCatching { String(bytes, Charsets.UTF_8) }
        .getOrDefault("").ifBlank { toHexStr(bytes) }
    ValueMode.HEX -> toHexStr(bytes)
    ValueMode.BASE64 -> android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
}

private fun toHexStr(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }

/** 详情卡片的统一容器。 */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().cardOutline(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

// 复制实现统一走 ui.components.copyToClipboard，这里不再保留一份副本 ——
// 之前日志详情页自己定义了一份，别的页面又各写各的，改一处漏一处。

@Composable
private fun MetaLine(k: String, v: String) {
    if (v.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            k,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.28f),
        )
        Text(v, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.72f))
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * 列表用的短时间：今天只显示「时:分:秒」，非今天补「月-日」。
 *
 * 【为什么不再带毫秒】
 * 原来固定 `MM-dd HH:mm:ss.SSS`，18 个字符，在日志行的右端挤得第 1 列
 * 文字只剩几个像素。毫秒信息对列表浏览没用，只在详情里才需要。
 *
 * 跨天加日期是必须的 —— 只显示「时:分:秒」的话，
 * 三天前 01:00 和今天 01:00 长得一模一样，日志按时间排却看不出隔了几天。
 */
internal fun formatTimeShort(t: Long): String {
    if (t <= 0L) return ""
    val cal = java.util.Calendar.getInstance()
    val now = cal.clone() as java.util.Calendar
    cal.timeInMillis = t
    val sameDay = now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm:ss" else "MM-dd HH:mm"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(t))
}

/** 详情用的完整时间：精确到毫秒。 */
internal fun formatTime(t: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(t))
}
