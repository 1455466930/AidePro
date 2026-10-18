package com.sfzsgybb.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.SectionHeader
import com.sfzsgybb.ai.ui.components.cardOutline
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import com.sfzsgybb.ai.ui.theme.Sem

/**
 * 抓包首页。
 *
 * 【为什么重做】
 * 之前的抓包页把「状态卡片 + 数据来源筛选 + 目标应用选择 + 搜索 + 列表」
 * 全塞在一屏里，信息密度过高。实际使用只有两条路径：
 *   要 Hook 抓包 → 开 → 看列表
 *   要代理抓包 → 开 → 看列表
 *
 * 所以这里只留两个入口按钮。点上任何一个 → 进入对应的请求列表页，
 * 配置细节（选应用、局域网、证书）都收进各自页面里。
 *
 * 【互斥】两种抓包不能同时开，开一个自动关另一个（在 ViewModel 里实现）。
 */
@Composable
fun CaptureHomeScreen(
    onOpenHook: () -> Unit,
    onOpenVpn: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: CaptureViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // 启动代理抓包需要 VPN 授权：先申请，拿到后自动继续
    var pendingVpn by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.startVpn()
            // 启动成功后自动进入列表页
            onOpenVpn()
        } else {
            vm.onVpnPermissionDenied()
        }
        pendingVpn = false
    }

    Column(Modifier.fillMaxSize()) {
        AideTopBar(title = "抓包")

        LazyColumn(
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionHeader("选择抓包方式")
            }

            // ---------- Hook 抓包 ----------
            item {
                CaptureEntryCard(
                    icon = Icons.Filled.Terminal,
                    title = "Hook 抓包",
                    desc = "在 Java 层拦截，拿请求与响应明文，无需证书",
                    running = state.running,
                    runningText = if (state.running) "运行中 · ${state.recordCount} 条" else "",
                    busy = false,
                    onStart = {
                        vm.start()
                        onOpenHook()
                    },
                    onOpen = onOpenHook,
                )
            }

            // ---------- 代理抓包 ----------
            item {
                CaptureEntryCard(
                    icon = Icons.Filled.NetworkCheck,
                    title = "代理抓包",
                    desc = "路由层接管，覆盖 Flutter / Cronet 等不走 Java 的网络栈",
                    running = state.vpnRunning,
                    runningText = if (state.vpnRunning) "运行中" else "",
                    busy = state.vpnBusy,
                    onStart = {
                        val intent = vm.vpnPermissionIntent()
                        if (intent != null) {
                            pendingVpn = true
                            permissionLauncher.launch(intent)
                        } else {
                            vm.startVpn()
                            onOpenVpn()
                        }
                    },
                    onOpen = onOpenVpn,
                )
            }

            // ---------- 说明 ----------
            item {
                Spacer(Modifier.size(4.dp))
                Text(
                    "两种抓包互斥：开启其中一个会自动关闭另一个，避免同一请求被重复记录。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            // ---------- 抓包历史 ----------
            item {
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  抓包历史")
                }
            }
        }
    }
}

/**
 * 抓包入口卡片。
 *
 * 未运行时按钮是「启动」，运行中时整张卡片可点（进入列表），按钮变「查看」。
 */
@Composable
private fun CaptureEntryCard(
    icon: ImageVector,
    title: String,
    desc: String,
    running: Boolean,
    runningText: String,
    busy: Boolean,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().cardOutline(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        onClick = { if (running) onOpen() },
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (running && runningText.isNotBlank()) {
                        Text(
                            runningText,
                            fontSize = 11.sp,
                            color = Sem.success,
                        )
                    }
                }
            }

            Text(
                desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (running) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("查看抓到的请求")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("  启动中…")
                    } else {
                        Text("启动抓包")
                    }
                }
            }
        }
    }
}
