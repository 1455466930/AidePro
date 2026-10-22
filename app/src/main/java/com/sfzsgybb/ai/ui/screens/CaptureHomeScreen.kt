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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Pause

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

    // 从抓包列表页返回时刷新状态。
    //
    // 之前没有任何刷新逻辑：在列表页暂停 / 停止后返回首页，
    // 卡片还显示着进去之前的状态，用户以为操作没生效。
    // 用 LifecycleResumeEffect 在每次页面重新可见时拉一次最新状态。
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh()
        vm.refreshVpn()
        vm.refreshMitm()
        onPauseOrDispose { }
    }

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
                    // 【暂停状态要显示出来】
                    // 暂停只是「不再记录」，链路本身还活着（running 仍为 true）。
                    // 之前这里只看 running，用户暂停后返回首页仍看到「运行中」，
                    // 会以为暂停没生效。
                    runningText = when {
                        !state.running -> ""
                        state.paused -> "已暂停 · ${state.recordCount} 条"
                        else -> "运行中 · ${state.recordCount} 条"
                    },
                    paused = state.running && state.paused,
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
                    runningText = when {
                        !state.vpnRunning -> ""
                        state.paused -> "已暂停"
                        else -> "运行中"
                    },
                    paused = state.vpnRunning && state.paused,
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
                    // 证书操作放这里而不是藏进帮助对话框：
                    // 它是代理抓包的前置条件，没装就只能看到域名看不到内容
                    extra = if (!state.vpnRunning) {
                        {
                            CaCertSection(
                                caState = state.caState,
                                onExport = { vm.exportCertificate() },
                                onInstall = { vm.installCertificateToSystem() },
                            )
                        }
                    } else null,
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
 * CA 证书区（代理抓包卡片内）。
 *
 * 【为什么放这里而不是设置页】
 * 证书是代理抓包的**前置条件**：没装的话 HTTPS 只能看到域名和流量大小，
 * 看不到内容。用户点「启动抓包」之前就该知道这件事，而不是抓完发现全是密文。
 *
 * 【安装成功时不显示】只在那两种「有问题」的状态下出现，
 * 避免卡片长期挂着一堆用不到的东西。
 */
@Composable
private fun CaCertSection(
    caState: com.sfzsgybb.ai.mitm.CaStatusChecker.CaState,
    onExport: () -> Unit,
    onInstall: () -> Unit,
) {
    // 系统信任 = 正常，什么都不显示
    if (caState == com.sfzsgybb.ai.mitm.CaStatusChecker.CaState.SYSTEM) return

    val isMissing = caState == com.sfzsgybb.ai.mitm.CaStatusChecker.CaState.NONE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Sem.warningContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (isMissing) "未检测到 CA 证书" else "证书仅在用户级",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Sem.warning,
        )
        Text(
            if (isMissing) "HTTPS 内容会是密文，只能看到域名和大小"
            else "Android 7+ 应用默认不信任用户证书，建议装到系统",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                Text("导出证书", fontSize = 12.sp)
            }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                Text("装到系统", fontSize = 12.sp)
            }
        }
    }
}

/**
 * 抓包入口卡片。
 *
 * 未运行时按钮是「启动」，运行中时整张卡片可点（进入列表），按钮变「查看」。
 *
 * @param extra 卡片内、启动按钮上方的附加区域（代理抓包用来放证书操作）
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
    /** 是否处于暂停记录状态（链路在跑但不入库） */
    paused: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().cardOutline(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(
            // 暂停时用中性底色，不要和「正常运行」混在一起
            containerColor = when {
                paused -> MaterialTheme.colorScheme.surfaceVariant
                running -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
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
                    if (paused) Icons.Filled.Pause else icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = when {
                        paused -> Sem.warning
                        running -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                            // 暂停用警示色，正常运行用成功色，一眼能分清
                            color = if (paused) Sem.warning else Sem.success,
                        )
                    }
                }
            }

            Text(
                desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 附加区（证书操作）
            extra?.invoke()

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
