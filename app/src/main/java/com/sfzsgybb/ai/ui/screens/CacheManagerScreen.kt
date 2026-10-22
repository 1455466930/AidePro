package com.sfzsgybb.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.components.SectionHeader
import com.sfzsgybb.ai.ui.components.cardOutline
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import com.sfzsgybb.ai.ui.theme.Sem
import com.sfzsgybb.ai.util.CacheEntry
import com.sfzsgybb.ai.util.AppCache

/**
 * 缓存管理。
 *
 * 【为什么单独做一页而不是设置里一个按钮直接清】
 * 直接清的话用户不知道删了什么、释放了多少，也没法只删其中一类。
 * 分项展示 + 勾选，用户能看到「Frida 日志占了 8 MB」这种具体信息，
 * 再决定要不要保留。
 */
@Composable
fun CacheManagerScreen(
    onBack: () -> Unit,
    vm: CacheViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AideTopBar(title = "缓存管理", onBack = onBack)

            if (state.loading) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ---------- 总览 ----------
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().cardOutline(),
                        shape = RoundedCornerShape(CardCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("可清理缓存", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                com.sfzsgybb.ai.util.AppCache.formatSize(state.cacheBytes),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "应用数据总计 ${com.sfzsgybb.ai.util.AppCache.formatSize(state.totalDataBytes)}（含日志、脚本、配置）",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // ---------- 全选 ----------
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("缓存明细")
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val allSelected = state.entries.isNotEmpty() &&
                                state.selected.size == state.entries.size
                            Text("全选", fontSize = 12.sp)
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { vm.selectAll(it) },
                            )
                        }
                    }
                }

                // ---------- 明细 ----------
                if (state.entries.isEmpty()) {
                    item {
                        EmptyState("没有缓存", "当前没有可清理的临时文件")
                    }
                } else {
                    items(items = state.entries, key = { e: CacheEntry -> e.name }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggle(e.name, e.name !in state.selected) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = e.name in state.selected,
                                onCheckedChange = { vm.toggle(e.name, it) },
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(e.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    e.desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            Text(
                                com.sfzsgybb.ai.util.AppCache.formatSize(e.bytes),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }

                // ---------- 说明 ----------
                item {
                    Text(
                        "只清理可重新生成的临时文件。日志记录、抓包数据、Frida 脚本、各项配置都不受影响。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                // ---------- 清理按钮 ----------
                item {
                    Button(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = state.selected.isNotEmpty(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("  清理选中缓存")
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清理缓存") },
            text = {
                Text(
                    "将释放 ${com.sfzsgybb.ai.util.AppCache.formatSize(state.cacheBytes)}。\n\n" +
                        "日志、抓包记录、脚本和配置不会被删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clear()
                }) { Text("清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}
