package com.sfzsgybb.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sfzsgybb.ai.frida.FridaScriptStore
import com.sfzsgybb.ai.ui.components.AideTopBar
import com.sfzsgybb.ai.ui.components.EmptyState
import com.sfzsgybb.ai.ui.components.SectionHeader
import com.sfzsgybb.ai.ui.components.cardOutline
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.ScreenPadding
import com.sfzsgybb.ai.ui.theme.Sem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Frida 脚本列表。
 *
 * 【为什么要有这一层】
 * 之前点「Frida 脚本」直接进编辑器，一个应用只有一份脚本。
 * 实际使用中一个应用常需要多份：一个 hook 加密、一个打印堆栈、
 * 一个绕过检测 —— 混在一个文件里既难维护也难开关。
 *
 * 现在一个应用可以有任意多个脚本，各自独立启停。
 */
@Composable
fun FridaScriptListScreen(
    packageName: String,
    onBack: () -> Unit,
    onOpenScript: (String) -> Unit,
    vm: FridaScriptListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var pendingDelete by remember { mutableStateOf<FridaScriptStore.Script?>(null) }
    // 导入时先问用户要不要立即启用
    var pendingImport by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(packageName) { vm.load(packageName) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // 系统文件选择器：不限扩展名，.js 之外也可能是 .txt
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        val name = runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i) else null
                } else null
            }
        }.getOrNull() ?: "导入的脚本.js"

        if (content.isNullOrBlank()) {
            vm.notify("读取文件失败或文件为空")
        } else {
            pendingImport = content to name
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.create(packageName, onOpenScript) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建脚本") },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AideTopBar(
                title = "Frida 脚本",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = "导入脚本")
                    }
                },
            )

            Text(
                packageName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )

            LazyColumn(
                contentPadding = PaddingValues(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionHeader("脚本（${state.scripts.size}）")
                }

                if (state.scripts.isEmpty()) {
                    item {
                        EmptyState(
                            "还没有脚本",
                            "点右下角「新建脚本」，或右上角导入本地 .js 文件",
                        )
                    }
                } else {
                    items(state.scripts, key = { it.id }) { s ->
                        ScriptRow(
                            script = s,
                            onOpen = { onOpenScript(s.id) },
                            onToggle = { vm.toggle(packageName, s.id, it) },
                            onDelete = { pendingDelete = s },
                        )
                    }
                }

                item {
                    Text(
                        "只有「已启用」的脚本会在注入时执行。导入的脚本默认不启用，" +
                            "确认内容无误后再打开开关。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    // ---------- 导入确认：让用户决定是否启用 ----------
    pendingImport?.let { (content, fileName) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入脚本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fileName, fontWeight = FontWeight.Medium)
                    Text(
                        "导入后是否立即启用？未启用的脚本会保存下来，但不会被注入执行。",
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.import(packageName, content, fileName, enabled = true, onOpenScript)
                    pendingImport = null
                }) { Text("导入并启用") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.import(packageName, content, fileName, enabled = false, onOpenScript)
                        pendingImport = null
                    }) { Text("仅导入") }
                }
            },
        )
    }

    // ---------- 删除确认 ----------
    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除脚本？") },
            text = { Text("「${s.name}」及其内容会被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(packageName, s.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ScriptRow(
    script: FridaScriptStore.Script,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().cardOutline(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (script.enabled) MaterialTheme.colorScheme.primary else Sem.neutral,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    script.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (script.enabled) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    if (script.enabled) "已启用" else "未启用",
                    fontSize = 11.sp,
                    color = if (script.enabled) Sem.success
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            // 启用开关：由用户决定哪个脚本生效
            Switch(checked = script.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ==================== ViewModel ====================

@HiltViewModel
class FridaScriptListViewModel @Inject constructor() : ViewModel() {

    private val store: FridaScriptStore by lazy {
        FridaScriptStore(com.sfzsgybb.ai.AideProApp.get())
    }

    private val _state = MutableStateFlow(FridaScriptListUiState())
    val state: StateFlow<FridaScriptListUiState> = _state.asStateFlow()

    fun load(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = store.list(pkg)
            _state.value = FridaScriptListUiState(packageName = pkg, scripts = list)
        }
    }

    fun create(pkg: String, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = store.create(pkg, "未命名脚本", FridaViewModel.DEFAULT_SCRIPT, enabled = true)
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun import(pkg: String, content: String, fileName: String, enabled: Boolean, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = store.import(pkg, content, fileName, enabled)
            _state.value = _state.value.copy(
                message = if (enabled) "已导入并启用：$fileName" else "已导入（未启用）：$fileName",
            )
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun toggle(pkg: String, id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            store.setEnabled(pkg, id, enabled)
            val list = store.list(pkg)
            _state.value = _state.value.copy(scripts = list)
        }
    }

    fun delete(pkg: String, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(pkg, id)
            val list = store.list(pkg)
            _state.value = _state.value.copy(scripts = list, message = "已删除")
        }
    }

    fun notify(msg: String) {
        _state.value = _state.value.copy(message = msg)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
