package com.sfzsgybb.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 主题。
 *
 * 原版是 Material Components + XML 主题（Theme.blue），
 * 且因为用了 AppCompat 而无法做动态取色。
 * 这里用 Compose Material3，支持 Android 12+ 动态取色。
 */

private val BluePrimary = Color(0xFF2563EB)
private val BluePrimaryDark = Color(0xFF60A5FA)
private val TealSecondary = Color(0xFF0D9488)
private val AmberTertiary = Color(0xFFF59E0B)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = TealSecondary,
    onSecondary = Color.White,
    tertiary = AmberTertiary,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

/**
 * 语义色令牌。
 *
 * 【为什么需要】
 * 之前状态码颜色在 Theme.kt 和 CaptureScreens.kt 各定义一套，而且互相矛盾
 * （一个说 300-399 蓝、另一个说橙；一个说 ≥500 红、另一个说紫）。
 * 同一个状态码在两个页面颜色不同，用户会以为是不同含义。
 *
 * 现在所有语义色（成功/警告/危险/信息）集中在这里，
 * container 色从各自的 content 色按透明度派生，深色浅色都能自适应，
 * 屏幕文件里不再出现裸 Color(0x...)。
 */
@Immutable
data class SemanticTokens(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    /** 日志分组配色（加密/网络/存储/界面/系统） */
    val groupColors: List<Color>,
)

private fun containerOf(content: Color, light: Boolean): Color =
    content.copy(alpha = if (light) 0.14f else 0.24f)

private fun buildTokens(dark: Boolean): SemanticTokens {
    val success = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val warning = if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val danger = if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    val info = if (dark) Color(0xFF60A5FA) else Color(0xFF0EA5E9)
    val neutral = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)

    return SemanticTokens(
        success = success,
        successContainer = containerOf(success, !dark),
        warning = warning,
        warningContainer = containerOf(warning, !dark),
        danger = danger,
        dangerContainer = containerOf(danger, !dark),
        info = info,
        infoContainer = containerOf(info, !dark),
        neutral = neutral,
        neutralContainer = containerOf(neutral, !dark),
        groupColors = listOf(
            neutral,                              // 默认
            if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED), // 加密
            info,                                 // 网络
            success,                              // 存储
            warning,                              // 界面
            danger,                               // 系统
        ),
    )
}

/** 当前主题的语义色。屏幕里用 `Semantic.success` 取，不再手写十六进制。 */
val LocalSemantic = staticCompositionLocalOf { buildTokens(dark = false) }

object Semantic {
    val success: Color @Composable get() = LocalSemantic.current.success
    val successContainer: Color @Composable get() = LocalSemantic.current.successContainer
    val warning: Color @Composable get() = LocalSemantic.current.warning
    val warningContainer: Color @Composable get() = LocalSemantic.current.warningContainer
    val danger: Color @Composable get() = LocalSemantic.current.danger
    val dangerContainer: Color @Composable get() = LocalSemantic.current.dangerContainer
    val info: Color @Composable get() = LocalSemantic.current.info
    val infoContainer: Color @Composable get() = LocalSemantic.current.infoContainer
    val neutral: Color @Composable get() = LocalSemantic.current.neutral
    val neutralContainer: Color @Composable get() = LocalSemantic.current.neutralContainer
}

/** [Semantic] 的短别名，屏幕里写 Sem.success 更短。 */
typealias Sem = Semantic

/** 日志/报文详情用的等宽字体样式。 */
val MonoTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 17.sp,
)

val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun AideProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Android 12+ 跟随系统壁纸取色 */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    // 之前这里算了个 scheme 变量但没用（死代码），动态取色实际从未生效。
    // 现在真接 dynamicLight/DarkColorScheme。
    val colors = when {
        dynamicColor && supportsDynamic -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Android 15 起强制的 edge-to-edge 会忽略 statusBarColor/navigationBarColor，
    // 只需要控制图标明暗即可，颜色赋值删掉（enableEdgeToEdge 已处理背景）。
    val view = (context as? Activity)?.window
    if (view != null) {
        SideEffect {
            WindowCompat.getInsetsController(view, view.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSemantic provides buildTokens(darkTheme)) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}

/** 分组配色，用于日志分组标签（从语义令牌取，深色浅色自适应）。 */
object GroupColors {
    @Composable
    fun of(group: Int): Color =
        LocalSemantic.current.groupColors.getOrElse(group) { LocalSemantic.current.neutral }
}

/**
 * HTTP 状态码配色。
 *
 * 【统一口径】之前两个页面各写一套且互相矛盾，现在只在这里定义：
 *   2xx 成功 / 3xx 信息 / 4xx 警告 / 5xx 危险 / 0 未知
 */
@Composable
fun statusColor(code: Int): Color {
    val sem = LocalSemantic.current
    return when {
        code == 0 -> sem.neutral
        code in 200..299 -> sem.success
        code in 300..399 -> sem.info
        code in 400..499 -> sem.warning
        code >= 500 -> sem.danger
        else -> sem.neutral
    }
}

val ScreenPadding = 16.dp
val CardCorner = 12.dp
