package com.sfzsgybb.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.sfzsgybb.ai.ui.theme.Sem

/**
 * 抓到的请求列表。
 *
 * 【两种模式共用】
 * mode = "hook" → 只看 Hook 抓的数据，停止按钮关 Hook 抓包
 * mode = "vpn"  → 只看代理抓的数据，停止按钮关 VPN
 *
 * 数据来源在入库时用 source 字段标好了，这里按来源过滤，
 * 不会出现「Hook 页混进代理数据」的情况。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureTrafficScreen(
    mode: String,
    onBack: () -> Unit,
    onOpenHttp: (Long) -> Unit,
    vm: CaptureViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    val isHook = mode == "hook"
    val running = if (isHook) state.running else state.vpnRunning

    // 进页面就切到对应来源
    LaunchedEffect(mode) {
        vm.setTrafficSource(mode)
        vm.startTrafficAutoRefresh()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.stopTrafficAutoRefresh() }
    }

    // ---------- 跟随尾部滚动 ----------
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom) { if (atBottom) following = true }

    val count = state.recentTraffic.size
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count, following) {
        if (following && count > lastCount && count > 0) {
            listState.animateScrollToItem(count - 1)
            }
        lastCount = count
    }

    Scaffold(
        topBar = {
            AideTopBar(
                title = if (isHook) "Hook 抓包" else "代理抓包",
                onBack = onBack,
                actions = {
                    if (running) {
                        // 暂停 / 恢复：链路照常跑，只是不再记录新数据
                        IconButton(onClick = { vm.togglePause() }) {
                            Icon(
                                if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (state.paused) "恢复记录" else "暂停记录",
                                tint = if (state.paused) Sem.success
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 真正停止：会关掉 VPN / 会话
                        IconButton(onClick = {
                            if (isHook) vm.stop() else vm.stopVpn()
                            onBack()
                        }) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "停止抓包",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ---------- 状态提示 ----------
                if (state.paused) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Sem.warningContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Sem.warning,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "已暂停记录，应用仍可正常联网。点顶栏 ▶ 继续。",
                                fontSize = 12.sp,
                                color = Sem.warning,
                            )
                        }
                    }
                } else if (!running) {
                    item {
                        Text(
                            "抓包已停止，以下是历史记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }

                // ---------- 搜索 + 一键删除 ----------
                if (state.recentTraffic.isNotEmpty() || state.trafficFilter.isNotBlank()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.trafficFilter,
                                onValueChange = vm::setTrafficFilter,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("搜索域名 / 路径", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (state.trafficFilter.isNotEmpty()) {
                                        IconButton(onClick = { vm.setTrafficFilter("") }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "清空搜索")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                            )
                            FilledTonalIconButton(
                                onClick = { confirmClear = true },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = "一键删除请求",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                // ---------- 列表 ----------
                if (state.recentTraffic.isEmpty()) {
                    item {
                        EmptyState(
                            if (running) "还没有抓到请求" else "没有记录",
                            when {
                                state.trafficFilter.isNotBlank() -> "换个关键词，或清空搜索框"
                                isHook -> "去操作目标应用，请求会实时出现在这里"
                                else -> "去操作手机上的应用，流量会实时出现在这里"
                            },
                        )
                    }
                } else {
                    items(state.recentTraffic, key = { it.id }) { item ->
                        TrafficRow(item = item, onClick = { onOpenHttp(item.id) })
                    }
                }
            }

            // 暂停跟随时显示「回到底部」
            if (!following && count > 0) {
                SmallFloatingActionButton(
                    onClick = {
                        following = true
                        scope.launch { listState.animateScrollToItem(count - 1) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("回到底部", fontSize = 12.sp)
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("删除已抓到的请求？") },
            text = { Text("将删除 ${state.recentTraffic.size} 条记录，抓包仍在继续。") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearTraffic() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}
