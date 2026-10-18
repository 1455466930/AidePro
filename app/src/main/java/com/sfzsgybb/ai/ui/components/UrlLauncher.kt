package com.sfzsgybb.ai.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 用系统浏览器（或任何能处理该 scheme 的应用）打开链接。
 *
 * 【为什么要 NEW_TASK】
 * 设置页、首页这类地方拿到的 Context 可能不是 Activity，
 * 不加这个标志会抛 AndroidRuntimeException。
 *
 * 【为什么全走 runCatching】
 * 部分定制 ROM 卸载了浏览器，startActivity 会抛 ActivityNotFoundException。
 * 这里统一兜底成 Toast，不让用户看到崩溃。
 */
fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        Toast.makeText(ctx, "没有可打开链接的应用", Toast.LENGTH_SHORT).show()
    }
}
