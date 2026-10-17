package com.sfzsgybb.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.sfzsgybb.ai.ui.theme.CardCorner
import com.sfzsgybb.ai.ui.theme.Sem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AideTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 小标签，用于分组 / 状态 / 开关名。 */
@Composable
fun Chip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color.copy(alpha = 0.15f) else Color.Transparent)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 等宽文本块，用于 payload / 报文内容。 */
@Composable
fun MonoBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(10.dp),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = maxLines,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 键值行，用于 HTTP 头等展示。 */
@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.62f),
        )
    }
}

/** 空状态占位。 */
@Composable
fun EmptyState(
    title: String,
    hint: String = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 节标题。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

/** 统计卡片。 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
            )
        }
    }
}

/** 状态指示点。 */
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        if (active) Sem.success else Sem.neutral,
        label = "statusDot",
    )
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

/**
 * 统一卡片。
 *
 * 【为什么需要】
 * 之前所有卡片都是 elevation=0 + containerColor=surface（纯白），
 * 贴在 #F8FAFC 背景上既不投影也不描边，一屏几张卡视觉上糊成一片。
 * 现在统一用 surfaceContainer 底色 + 1dp outline 描边，层级立刻分开。
 */
@Composable
fun AideCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(CardCorner)
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    val border = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
    )
    val contentModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Card(
        modifier = contentModifier,
        shape = shape,
        colors = colors,
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * 给现有 Card 加统一描边。
 *
 * 【为什么不直接换成 AideCard】
 * 各屏的 Card 内边距、clickable、条件底色各不相同，
 * 硬换成 AideCard 会把「VPN 运行时用 primaryContainer」这类
 * 有意义的变体一起抹平。这里只补描边，卡片内部结构一点不动。
 */
@Composable
fun Modifier.cardOutline(): Modifier = this.border(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    shape = RoundedCornerShape(CardCorner),
)

/**
 * 复制到剪贴板。失败静默（个别机型剪贴板服务会被限制）。
 */
fun copyToClipboard(context: android.content.Context, text: String) {
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("aidepro", text))
    }
}

/**
 * 可复制的等宽文本块。
 *
 * 【为什么正文要限高】
 * HTTP 响应体动辄几百 KB，把整串塞进一个 Text 节点会让 LazyColumn 的
 * 单 item 撑到几万像素，滚动掉帧、甚至测量超限直接崩。
 * 这里默认只显示 [collapsedHeight] 高，点「展开全文」才放全高。
 *
 * 复制操作永远复制完整内容，不受折叠影响。
 *
 * 【为什么复制后没有 toast】
 * 这个组件可能出现在没有 SnackbarHost 的页面里。
 * 复制成功与否用「按钮文字短暂变成已复制」来反馈，不需要宿主配合。
 */
@Composable
fun CopyableMonoBlock(
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    collapsedHeight: Dp = 240.dp,
) {
    val ctx = LocalContext.current
    var expanded by remember(text) { mutableStateOf(false) }
    var copied by remember(text) { mutableStateOf(false) }
    val isLong = text.length > 1200

    // 复制提示 1.2 秒后自动复位
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1200)
            copied = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${text.length} 字符",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (copied) "已复制" else "复制",
                fontSize = 12.sp,
                color = if (copied) Sem.success else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    // 点击区放大一点，纯文字太小不好点
                    .clickable {
                        copyToClipboard(ctx, text)
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        val mod = if (!expanded && isLong) {
            modifier.fillMaxWidth().heightIn(max = collapsedHeight)
        } else {
            modifier.fillMaxWidth()
        }

        Box {
            MonoBlock(text, modifier = mod)
            if (!expanded && isLong) {
                // 底部渐隐条，暗示「下面还有」
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        )
                )
            }
        }

        if (isLong) {
            Text(
                if (expanded) "收起" else "展开全文",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

