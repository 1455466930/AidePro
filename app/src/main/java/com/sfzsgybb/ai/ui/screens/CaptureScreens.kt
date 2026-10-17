package com.sfzsgybb.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.capture.CaptureRepository
import com.sfzsgybb.ai.data.model.CaptureSession
import com.sfzsgybb.ai.data.model.ValueMode
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.Chip
import com.sfzsgybb.ai.ui.components.CopyableMonoBlock
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.components.KeyValueRow
import com.sfzsgybb.ai.ui.components.MonoBlock
import com.sfzsgybb.ai.ui.components.SectionHeader
import com.sfzsgybb.ai.ui.components.StatCard
import com.sfzsgybb.ai.ui.components.StatusDot
import com.sfzsgybb.ai.ui.components.copyToClipboard
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import com.sfzsgybb.ai.ui.theme.statusColor
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.sfzsgybb.ai.ui.theme.Sem
import com.sfzsgybb.ai.ui.components.cardOutline
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch

// ==================== 抓包主页 ====================

@Composable
fun CaptureScreen(
    onOpenHistory: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenHttp: (Long) -> Unit = {},
    vm: CaptureViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    // 多选模式下，返回键先退出多选，而不是直接离开抓包页。
    // 之前只能靠右上角菜单里的「多选删除」再点一次切回来，
    // 误进多选后用户的第一反应就是按返回。
    androidx.activity.compose.BackHandler(enabled = state.selectionMode) {
        vm.toggleSelectionMode()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AideTopBar(
                title = "抓包",
                actions = {
                    if (state.selectionMode) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Filled.Check, contentDescription = "全选")
                        }
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除所选")
                        }
                    } else {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("抓包历史") },
                                    onClick = { menuOpen = false; onOpenHistory() },
                                )
                                DropdownMenuItem(
                                    text = { Text("多选删除") },
                                    onClick = { menuOpen = false; vm.toggleSelectionMode() },
                                )
                                DropdownMenuItem(
                                    text = { Text("清理已结束会话") },
                                    onClick = { menuOpen = false; vm.clearStopped() },
                                )
                                DropdownMenuItem(
                                    text = { Text("清空全部") },
                                    onClick = { menuOpen = false; confirmClearAll = true },
                                )
                            }
                        }
                    }
                },
            )

            // ---------- 跟随尾部滚动 ----------
            // 抓包时新数据不断进来。用户停在底部时自动滚到最新一条；
            // 一旦手动上滑就暂停跟随，并显示「有新 N 条 ↓」气泡，点击跳回底部。
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            // 用户是否停在底部（留 2 行容差，避免像素级抖动）
            val atBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
                }
            }
            var following by remember { mutableStateOf(true) }
            LaunchedEffect(atBottom) {
                // 手动滚回底部就恢复跟随
                if (atBottom) following = true
            }

            val count = state.recentTraffic.size
            var lastCount by remember { mutableIntStateOf(count) }
            LaunchedEffect(count, following) {
                if (following && count > lastCount && count > 0) {
                    listState.animateScrollToItem((count - 1).coerceAtLeast(0))
                }
                lastCount = count
            }

            Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 12.dp),
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
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusDot(state.running)
                                Text(
                                    if (state.running) "Hook 抓包中" else "Hook 抓包未启动",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (state.running) {
                                    Chip("累计 ${state.recordCount} 条", MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.weight(1f))
                                // 说明书
                                HelpButton(HOOK_CAPTURE_HELP_TITLE, HOOK_CAPTURE_HELP)
                            }

                            state.packageName?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    "目标：$it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (state.running) {
                                Button(onClick = { vm.stop() }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  停止抓包")
                                }
                            } else {
                                Button(onClick = { vm.start() }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  开始抓包")
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("过滤本机回环请求", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "忽略 127.0.0.1 / localhost，减少噪音",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                                Switch(checked = state.filterLocal, onCheckedChange = vm::setFilterLocal)
                            }
                        }
                    }
                }

                // ---------- VPN 抓包（路由层接管，覆盖不读系统代理的应用） ----------
                item {
                    val ctx = LocalContext.current
                    Card(
                        Modifier.fillMaxWidth().cardOutline(),
                        shape = RoundedCornerShape(CardCorner),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.vpnRunning)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusDot(state.vpnRunning)
                                Text(
                                    if (state.vpnRunning) "代理抓包运行中" else "代理抓包未启动",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.weight(1f))
                                // 说明书（含证书导出/安装）
                                HelpButton(
                                    title = VPN_CAPTURE_HELP_TITLE,
                                    content = VPN_CAPTURE_HELP,
                                    actions = listOf(
                                        "导出证书（无需 root）" to { vm.exportCertificate() },
                                        "一键装到系统（需 root）" to { vm.installCertificateToSystem() },
                                    ),
                                )
                            }

                            Text(
                                "在内核路由层接管全部应用流量，可抓到 Hook 覆盖不到的应用" +
                                    "（Cronet、Flutter、自带网络栈）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                            )

                            // CA 证书状态：HTTPS 解密的前提
                            // 系统级 CA → 直接能解密
                            // 仅用户级/未安装 → Android 7+ 应用不信任，需开 Hook 证书信任
                            when (state.caState) {
                                com.sfzsgybb.ai.mitm.CaStatusChecker.CaState.SYSTEM -> Unit // 正常，不提示
                                com.sfzsgybb.ai.mitm.CaStatusChecker.CaState.USER_ONLY -> {
                                    Text(
                                        "⚠ 只装了用户级 CA。Android 7+ 的应用默认不信任用户 CA，" +
                                            "HTTPS 内容抓不到。\n" +
                                            "→ 到「应用」页给目标应用开启「SSL 证书信任」开关；" +
                                            "用 okhttp 的应用也可直接用 Hook 抓包（无需证书）。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                                com.sfzsgybb.ai.mitm.CaStatusChecker.CaState.NONE -> {
                                    Text(
                                        "⚠ 未检测到 CA 证书，只能看到域名和流量大小，看不到 HTTPS 内容。\n" +
                                            "→ 有 root：把 CA 装到系统信任区；" +
                                            "没 root：到「应用」页开启目标应用的「SSL 证书信任」开关。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            }

                            // 运行时的「已收包 / 接管流量目标」不再展示：
                            // 这些是开发者诊断信息（裸 IP 用户看不懂），
                            // 而且抓到的数据在下方请求列表里已经能看到，重复占地方。

                            state.vpnError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }

                            // 抓取范围：默认只抓已勾选 Hook 的应用，可选全局
                            if (!state.vpnRunning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("全局抓包", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "关闭时只抓「应用」页里已勾选的应用；" +
                                                "开启则接管全部应用（隐私风险、也更耗电）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                        )
                                    }
                                    Switch(
                                        checked = state.vpnGlobal,
                                        onCheckedChange = { vm.setVpnGlobal(it) },
                                    )
                                }
                            }

                            if (state.vpnRunning && state.vpnTargets.isNotEmpty()) {
                                Text(
                                    "抓取范围：${state.vpnTargets.size} 个应用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }

                            // 用 ActivityResultLauncher 处理 VPN 授权：
                            // 用户点「确定」后**自动启动**，不需要再点一次按钮。
                            val vpnLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartActivityForResult(),
                            ) { result ->
                                if (result.resultCode == android.app.Activity.RESULT_OK) {
                                    vm.startVpn(state.vpnGlobal)
                                } else {
                                    vm.onVpnPermissionDenied()
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when {
                                    // 过渡态：服务正在启停，按钮禁用并显示进度
                                    state.vpnBusy -> {
                                        Button(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(if (state.vpnRunning) "  停止中…" else "  启动中…")
                                        }
                                    }
                                    state.vpnRunning -> {
                                        Button(onClick = { vm.stopVpn() }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Filled.Stop, contentDescription = null,
                                                modifier = Modifier.size(18.dp))
                                            Text("  停止代理抓包")
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                val intent = vm.vpnPermissionIntent()
                                                if (intent != null) {
                                                    // 未授权：弹系统框，授权后由 launcher 回调启动
                                                    vpnLauncher.launch(intent)
                                                } else {
                                                    // 已授权：直接启动
                                                    vm.startVpn(state.vpnGlobal)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null,
                                                modifier = Modifier.size(18.dp))
                                            Text("  启动代理抓包")
                                        }
                                    }
                                }
                            }

                            if (!state.vpnAuthorized) {
                                Text(
                                    "首次使用会弹出系统授权框，点「确定」即可（只需一次）。" +
                                        "授权后会自动启动，不用再点一次。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }

                // ---------- 来源筛选 ----------
                // Hook 抓包（Java 层明文）和代理抓包（路由层+MITM 解密）是两条链路，
                // 数据混在一起时分不清。这里让用户明确选择要看哪一种。
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SectionHeader("数据来源")
                        Spacer(Modifier.weight(1f))
                        // 提示当前有哪些抓包在运行
                        if (state.vpnRunning) {
                            Chip("代理运行中", MaterialTheme.colorScheme.primary)
                        }
                        if (state.running) {
                            Chip("Hook 运行中", MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val sources = listOf(
                            "" to "全部",
                            "hook" to "Hook 抓包",
                            "vpn" to "代理抓包",
                        )
                        sources.forEach { (value, label) ->
                            FilterChip(
                                selected = state.trafficSource == value,
                                onClick = { vm.setTrafficSource(value) },
                                label = { Text(label, fontSize = 12.sp) },
                            )
                        }
                    }
                }

                // ---------- 目标应用选择 ----------
                // 已启用的应用全塞一行横向滚动里会滑不完，这里改成可换行的流式布局；
                // 选中的应用挪到最前面（见 CaptureViewModel 的排序），
                // 用户切完来源一眼就能看到自己选的是谁。
                if (state.enabledApps.isNotEmpty()) {
                    item { SectionHeader("抓取目标应用（${state.enabledApps.size}）") }

                    item {
                        var expanded by remember { mutableStateOf(false) }
                        val limit = 6
                        val ordered = state.enabledApps.sortedByDescending {
                            it.first == state.selectedPackage
                        }
                        val showAll = expanded || ordered.size <= limit
                        val visible = if (showAll) ordered else ordered.take(limit)

                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = state.selectedPackage == null,
                                onClick = { vm.selectPackage(null) },
                                label = { Text("全部应用", fontSize = 12.sp) },
                            )
                            visible.forEach { (pkg, label) ->
                                FilterChip(
                                    selected = state.selectedPackage == pkg,
                                    onClick = { vm.selectPackage(pkg) },
                                    label = {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            if (!showAll) {
                                AssistChip(
                                    onClick = { expanded = true },
                                    label = { Text("还有 ${ordered.size - limit} 个", fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                }

                // ---------- 抓到的数据列表 ----------
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionHeader("抓到的请求")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${state.recentTraffic.size} 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                            IconButton(onClick = { vm.loadTraffic() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // 搜索框：一次抓到 300 条，靠肉眼翻找是不可用的。
                // CaptureViewModel.setTrafficFilter 早就有，之前一直没接到界面上。
                if (state.recentTraffic.isNotEmpty() || state.trafficFilter.isNotBlank()) {
                    item {
                        OutlinedTextField(
                            value = state.trafficFilter,
                            onValueChange = vm::setTrafficFilter,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索域名 / 路径", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.trafficFilter.isNotEmpty()) {
                                    IconButton(onClick = { vm.setTrafficFilter("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "清空")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }

                if (state.recentTraffic.isEmpty()) {
                    item {
                        EmptyState(
                            if (state.trafficFilter.isNotBlank()) "没有匹配的请求" else "还没有抓到数据",
                            when {
                                state.trafficFilter.isNotBlank() -> "换个关键词，或清空搜索框"
                                state.vpnRunning -> "代理已启动，去操作目标应用即可看到请求"
                                state.running -> "Hook 已就绪，重启目标应用后操作几下"
                                else -> "先启动上面的抓包，再去操作目标应用"
                            },
                        )
                    }
                } else {
                    items(state.recentTraffic, key = { it.id }) { item ->
                        // animateItem：新请求从尾部进来时平滑插入，
                        // 不加的话每 2 秒自动刷新整屏闪跳一次
                        Column(Modifier.animateItem()) {
                            TrafficRow(item = item, onClick = { onOpenHttp(item.id) })
                        }
                    }
                }
            }

            // 暂停跟随时显示「有新数据 ↓」气泡：点一下跳回最新
            if (!following && count > 0) {
                SmallFloatingActionButton(
                    onClick = {
                        following = true
                        scope.launch { listState.animateScrollToItem(count - 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("回到底部", fontSize = 12.sp)
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部会话？") },
            text = { Text("将删除所有会话及其流量记录，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmClearAll = false; vm.clearAll() }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } },
        )
    }
}

/** 会话卡片，带删除按钮与多选状态。 */
@Composable
private fun SessionCard(
    session: CaptureSession,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectionMode) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                StatusDot(session.state.name == "RUNNING")
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    session.appLabel.ifBlank { session.packageName.ifBlank { "未指定目标" } },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        formatTimeShort(session.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Text(
                        "${session.recordCount} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Text(
                        if (session.state.name == "RUNNING") "运行中" else "已结束",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (session.state.name == "RUNNING") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }

            if (!selectionMode) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除会话",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除该会话？") },
            text = { Text("将同时删除该会话下的 ${session.recordCount} 条流量记录。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

// ==================== 会话详情 ====================

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenHttp: (Long) -> Unit,
    onOpenTcp: (Long) -> Unit,
    vm: SessionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    // 同上：多选态下返回键先退多选
    androidx.activity.compose.BackHandler(enabled = state.selectionMode) {
        vm.toggleSelectionMode()
    }

    LaunchedEffect(sessionId) { vm.load(sessionId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AideTopBar(
                title = "会话详情",
                onBack = onBack,
                actions = {
                    if (state.selectionMode) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Filled.Check, contentDescription = "全选")
                        }
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除所选")
                        }
                    } else {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { vm.toggleSelectionMode() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "管理")
                        }
                    }
                },
            )

            // 统计条
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("记录", state.items.size.toString(), Modifier.weight(1f))
                StatCard(
                    "HTTP", state.items.count { it.kind == "http" }.toString(), Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                )
                StatCard(
                    "TCP/WS", state.items.count { it.kind != "http" }.toString(), Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
                placeholder = { Text("搜索 URL / 地址 / 载荷内容") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val filters = listOf("全部" to null, "HTTP" to "HTTP", "WebSocket" to "WebSocket", "TCP" to "TCP")
                filters.forEach { (label, proto) ->
                    FilterChip(
                        selected = state.protocolFilter == proto,
                        onClick = { vm.setProtocolFilter(proto) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    if (state.loading) "加载中…" else "暂无流量",
                    "目标应用的网络请求会实时出现在这里",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { "${it.kind}-${it.id}" }) { item ->
                        NetworkRow(
                            item = item,
                            selectionMode = state.selectionMode,
                            selected = "${item.kind}-${item.id}" in state.selected,
                            onClick = {
                                if (state.selectionMode) vm.toggleSelect("${item.kind}-${item.id}")
                                else if (item.kind == "http") onOpenHttp(item.id) else onOpenTcp(item.id)
                            },
                            onDelete = { vm.deleteRecord(item) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(
    item: CaptureRepository.NetworkItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = ScreenPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectionMode) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                }
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.kind == "http") {
                    Chip(item.method ?: "GET", MaterialTheme.colorScheme.primary)
                    item.statusCode?.takeIf { it > 0 }?.let { Chip(it.toString(), statusColor(it)) }
                } else {
                    Chip(item.protocol, MaterialTheme.colorScheme.secondary)
                }
                Text(
                    text = item.url ?: item.remoteAddress ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${item.size}B",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Text(
                formatTimeShort(item.time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }

        if (!selectionMode) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ==================== HTTP 详情 ====================

@Composable
fun HttpDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    vm: HttpDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(recordId) { vm.load(recordId) }

    val r = state.record
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        AideTopBar(
            title = "请求详情",
            onBack = onBack,
            actions = {
                // 整条请求一键复制：抓包分析时最常用的动作
                if (r != null) {
                    IconButton(onClick = {
                        copyToClipboard(
                            ctx,
                            buildString {
                                append("${r.method} ${r.url} HTTP/${r.httpVersion}\n")
                                r.requestHeaders.forEach { append("${it.name}: ${it.value}\n") }
                                append("\n--- 响应 ${r.statusCode} ${r.statusText} ---\n")
                                r.responseHeaders.forEach { append("${it.name}: ${it.value}\n") }
                                append("\n").append(vm.renderBodyText())
                            },
                        )
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制整条请求")
                    }
                }
            },
        )

        if (r == null) {
            EmptyState(if (state.loading) "加载中…" else "记录不存在")
            return
        }

        // 两行筛选：上行 请求/响应，下行 渲染模式。
        // 合成一行在窄屏上会挤到换行错位。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.part == "request",
                onClick = { vm.setPart("request") },
                label = { Text("请求", fontSize = 12.sp) },
            )
            FilterChip(
                selected = state.part == "response",
                onClick = { vm.setPart("response") },
                label = { Text("响应", fontSize = 12.sp) },
            )
            Spacer(Modifier.width(6.dp))
            ValueMode.entries.forEach { m ->
                FilterChip(
                    selected = state.mode == m,
                    onClick = { vm.setMode(m) },
                    label = { Text(m.name.lowercase(), fontSize = 12.sp) },
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(r.method, MaterialTheme.colorScheme.primary)
                            Chip("${r.statusCode} ${r.statusText}", statusColor(r.statusCode))
                            Chip("HTTP/${r.httpVersion}", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            r.url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "耗时 ${r.durationMs} ms · 请求 ${r.requestSize}B · 响应 ${r.responseSize}B",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val headers = if (state.part == "request") r.requestHeaders else r.responseHeaders
            if (headers.isNotEmpty()) {
                item { SectionHeader("${if (state.part == "request") "请求头" else "响应头"}（${headers.size}）") }
                items(headers.size, key = { i -> "hdr-$i-${headers[i].name}" }) { i ->
                        KeyValueRow(headers[i].name, headers[i].value)
                    }
                // 头部整体复制：一键贴给他人或搜关键词，不用一行行划
                item {
                    Text(
                        "复制全部头部",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                copyToClipboard(
                                    ctx,
                                    headers.joinToString("\n") { "${it.name}: ${it.value}" },
                                )
                            }
                            .padding(vertical = 6.dp),
                    )
                }
            }

            item {
                CopyableMonoBlock(
                    text = vm.renderBody().ifBlank { "(空)" },
                    label = "正文",
                )
            }
        }
    }
}

// ==================== TCP / WebSocket 详情 ====================

@Composable
fun TcpDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    vm: TcpDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(recordId) { vm.load(recordId) }

    val p = state.packet
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        AideTopBar(title = "报文详情", onBack = onBack)

        if (p == null) {
            EmptyState(if (state.loading) "加载中…" else "报文不存在")
            return
        }

        Row(
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ValueMode.entries.forEach { m ->
                FilterChip(
                    selected = state.mode == m,
                    onClick = { vm.setMode(m) },
                    label = { Text(m.name.lowercase(), fontSize = 12.sp) },
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().cardOutline(),
                    shape = RoundedCornerShape(CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(p.protocol.name, MaterialTheme.colorScheme.secondary)
                            Chip(
                                if (p.outbound) "发送" else "接收",
                                if (p.outbound) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                            )
                            if (p.wsFrameType.isNotBlank()) {
                                Chip(p.wsFrameType, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // 远端地址常用来搜「连到哪个服务器」，必须能复制
                        Text(
                            "${p.remoteAddress}:${p.remotePort}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                copyToClipboard(ctx, "${p.remoteAddress}:${p.remotePort}")
                            },
                        )
                        Text(
                            "${p.size} B · ${formatTime(p.time)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (p.stackTrace.isNotBlank()) {
                item {
                    CopyableMonoBlock(
                        text = p.stackTrace,
                        label = "调用栈",
                        collapsedHeight = 200.dp,
                    )
                }
            }

            item {
                CopyableMonoBlock(
                    text = vm.render().ifBlank { "(空)" },
                    label = "载荷",
                )
            }
        }
    }
}

// ==================== 抓包历史 ====================

@Composable
fun CaptureHistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    vm: CaptureViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmClearAll by remember { mutableStateOf(false) }

    // 同上：多选态下返回键先退多选
    androidx.activity.compose.BackHandler(enabled = state.selectionMode) {
        vm.toggleSelectionMode()
    }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AideTopBar(
                title = "抓包历史",
                onBack = onBack,
                actions = {
                    if (state.selectionMode) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Filled.Check, contentDescription = "全选")
                        }
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除所选")
                        }
                    } else {
                        IconButton(onClick = { vm.toggleSelectionMode() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "管理")
                        }
                    }
                },
            )

            if (state.sessions.isEmpty()) {
                EmptyState("暂无会话记录")
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { vm.clearStopped() }) { Text("清理已结束") }
                    TextButton(onClick = { confirmClearAll = true }) {
                        Text("清空全部", color = MaterialTheme.colorScheme.error)
                    }
                }
                LazyColumn(contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp)) {
                    items(state.sessions, key = { it.sessionId }) { s ->
                        SessionCard(
                            session = s,
                            selectionMode = state.selectionMode,
                            selected = s.sessionId in state.selected,
                            onClick = {
                                if (state.selectionMode) vm.toggleSelect(s.sessionId)
                                else onOpenSession(s.sessionId)
                            },
                            onDelete = { vm.deleteSession(s.sessionId) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部会话？") },
            text = { Text("将删除所有会话及其流量记录，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmClearAll = false; vm.clearAll() }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } },
        )
    }
}


/**
 * 流量列表的一行：方法 · 软件名 · 域名+路径 · 状态码 · 耗时 · 大小。
 */
@Composable
fun TrafficRow(item: TrafficItem, onClick: () -> Unit = {}) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 方法标签
                Text(
                    item.method,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = methodColor(item.method),
                    modifier = Modifier.width(46.dp),
                )
                // 来源标记：H=Hook 抓包 / P=代理抓包
                // 列表里两种来源混排时，一眼区分数据从哪条链路来
                Text(
                    if (item.source == "vpn") "P" else "H",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.source == "vpn")
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.width(12.dp),
                )
                // 软件名（包名转显示名由调用方处理，这里显示包名简称）
                if (item.packageName.isNotBlank()) {
                    Text(
                        item.packageName.substringAfterLast('.').take(12),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (item.statusCode > 0) {
                    Text(
                        item.statusCode.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor(item.statusCode),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.host,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (item.durationMs > 0) {
                    Text(
                        "${item.durationMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.responseSize > 0) {
                    Text(
                        fmtSize(item.responseSize),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                item.url.substringAfter(item.host, "").ifBlank { "/" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

/**
 * 按 HTTP 方法着色。
 *
 * 从语义令牌取色：GET 成功绿、POST 信息蓝、PUT/PATCH 警告橙、DELETE 危险红。
 * 之前这里硬编码了一套十六进制色，和 DetailScreen 的 statusColor 互相矛盾
 * （比如 5xx 一个说红一个说紫），现在统一口径。
 */
@Composable
private fun methodColor(m: String): Color = when (m.uppercase()) {
    "GET" -> Sem.success
    "POST" -> Sem.info
    "PUT", "PATCH" -> Sem.warning
    "DELETE" -> Sem.danger
    else -> Sem.neutral
}


/** 人类可读的大小。 */
private fun fmtSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}K"
    else -> String.format("%.1fM", bytes / 1024.0 / 1024.0)
}


// ==================== 说明书 ====================

private const val HOOK_CAPTURE_HELP_TITLE = "Hook 抓包说明"
private const val HOOK_CAPTURE_HELP = """在目标应用进程内拦截 OkHttp / HttpURLConnection / WebView 的请求与响应。

【优点】
· 拿到的是明文，不需要装证书
· 加密参数（sign、token）也能看到原始值
· 不影响应用正常使用

【怎么用】
1. 到「应用」页打开目标应用的开关
2. 打开该应用的「HTTP 抓包」开关
3. 强行停止目标应用，再重新打开
4. 页面上的请求就会出现在下方列表

【限制】
· 只能抓走 Java 网络栈的应用
· 用 Cronet / Flutter / 自研网络栈的应用抓不到（改用代理抓包）"""

private const val VPN_CAPTURE_HELP_TITLE = "代理抓包说明"
private const val VPN_CAPTURE_HELP = """在系统路由层接管应用流量，可抓到 Hook 覆盖不到的应用。

【适用】
Cronet、Flutter、原生代码等不走 Java 网络栈的应用。

【怎么用】
1. 首次启动会弹出系统授权框，点「确定」（只需一次）
2. 默认只抓「应用」页里已启用的应用；也可打开「全局抓包」
3. 到目标应用里操作，请求会出现在下方列表

【HTTPS 解密的前提】
必须让目标应用信任我们签发的证书。三种方式：

① 一键装到系统（推荐，需 root）
   点下方按钮，证书写入系统信任区，所有应用立即信任。

② 导出证书手动安装（无需 root）
   导出后用系统设置安装：设置 → 安全 → 加密与凭据 →
   安装证书 → CA 证书。注意：Android 7+ 应用默认不信任用户证书，
   这种方式只对少数应用有效。

③ 用 Hook 的「SSL 证书信任」开关
   到「应用」页打开目标应用的该开关，让它无条件信任任何证书。
   不需要装证书，但要求该应用在 LSPosed 作用域内。

【注意】
· 开启期间网速会略慢（流量要经过用户态转发）
· 有证书固定的应用（如谷歌服务）可能仍抓不到内容"""


/**
 * 说明按钮：点击弹出说明书对话框。
 *
 * @param actions 额外操作按钮（如导出证书、装到系统）
 */
@Composable
fun HelpButton(
    title: String,
    content: String,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }

    TextButton(onClick = { open = true }) {
        Text("说明", fontSize = 12.sp)
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        content,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 19.sp,
                    )
                    actions.forEach { (label, act) ->
                        OutlinedButton(
                            onClick = { open = false; act() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("知道了") }
            },
        )
    }
}
