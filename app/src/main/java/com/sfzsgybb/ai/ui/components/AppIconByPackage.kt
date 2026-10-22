package com.sfzsgybb.ai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用图标缓存。
 *
 * 【为什么需要缓存】
 * 日志列表一屏几十行，每行都调 PackageManager.getApplicationIcon
 * 会导致滚动掉帧 —— 那是个跨进程调用。按包名缓存后，
 * 同一个应用的所有日志共享一张位图。
 *
 * 用 ConcurrentHashMap 而不是 LruCache：应用数量有限（通常几十个），
 * 不会无限增长；位图本身由系统 Drawable 派生，内存占用可接受。
 */
private object AppIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap?>()

    fun get(pkg: String): ImageBitmap? = cache[pkg]

    fun contains(pkg: String): Boolean = cache.containsKey(pkg)

    fun put(pkg: String, bmp: ImageBitmap?) {
        cache[pkg] = bmp
    }
}

/**
 * 按包名加载应用图标。
 *
 * @param packageName 目标应用包名
 * @param size 图标边长，列表里用 20dp 左右够了
 */
@Composable
fun AppIconByPackage(
    packageName: String,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var bmp by remember(packageName) {
        mutableStateOf(if (AppIconCache.contains(packageName)) AppIconCache.get(packageName) else null)
    }

    LaunchedEffect(packageName) {
        if (AppIconCache.contains(packageName)) {
            bmp = AppIconCache.get(packageName)
            return@LaunchedEffect
        }
        val loaded = runCatching {
            val d = ctx.packageManager.getApplicationIcon(packageName)
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 96
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 96
            val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            b.asImageBitmap()
        }.getOrNull()
        AppIconCache.put(packageName, loaded)
        bmp = loaded
    }

    val shape = RoundedCornerShape(size / 5)

    if (bmp != null) {
        Image(
            bitmap = bmp!!,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        // 加载中或应用已卸载：占位方块，不占额外空间
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                packageName.takeLast(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
