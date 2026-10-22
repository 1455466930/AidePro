package com.sfzsgybb.ai.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 一类可清理的缓存。
 */
data class CacheEntry(
    val name: String,
    val desc: String,
    val bytes: Long,
    val paths: List<File>,
)

/**
 * 缓存总览。
 *
 * @param entries 可清理的条目
 * @param totalDataBytes 应用数据总量（含不可清理部分），仅用于展示对比
 */
data class CacheOverview(
    val entries: List<CacheEntry>,
    val totalDataBytes: Long,
) {
    val cacheBytes: Long get() = entries.sumOf { it.bytes }
}

/**
 * 缓存统计与清理。
 *
 * 只有可以重新生成的数据才算缓存：
 * cache 目录、code_cache 目录、frida_logs 目录、frida-inject.tmp。
 *
 * 数据库、用户脚本、SharedPreferences 属于用户数据，不在此列，
 * 清理时不会被删掉。
 */
object AppCache {

    private const val TAG = "AppCache"

    /**
     * 统计各类缓存大小。
     */
    fun overview(context: Context): CacheOverview {
        val entries = ArrayList<CacheEntry>()

        val files = context.filesDir
        val cache = context.cacheDir

        val srcs = listOf(
            Triple("系统缓存", "临时文件与网络缓存", cache),
            Triple("代码缓存", "dex 优化产物，删后自动重建", File(cache.parentFile, "code_cache")),
            Triple("Frida 日志", "脚本 console 输出记录", File(files, "frida_logs")),
        )

        for (item in srcs) {
            val name = item.first
            val desc = item.second
            val dir = item.third
            if (!dir.exists()) continue
            val size = runCatching { sizeOf(dir) }.getOrDefault(0L)
            if (size <= 0L) continue
            entries += CacheEntry(name, desc, size, listOf(dir))
        }

        val tmp = File(files, "frida-inject.tmp")
        if (tmp.exists()) {
            entries += CacheEntry("临时安装包", "释放 frida 时的中间文件", tmp.length(), listOf(tmp))
        }

        return CacheOverview(
            entries = entries.sortedByDescending { it.bytes },
            totalDataBytes = runCatching { sizeOf(files) + sizeOf(cache) }.getOrDefault(0L),
        )
    }

    /**
     * 清理指定的缓存项。
     *
     * @return 实际释放的字节数
     */
    fun clear(context: Context, entries: List<CacheEntry>): Long {
        var freed = 0L
        for (e in entries) {
            for (f in e.paths) {
                val before = runCatching { sizeOf(f) }.getOrDefault(0L)
                val ok = runCatching {
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }.getOrDefault(false)
                if (ok) {
                    freed += before
                    Log.i(TAG, "已清理 " + f.name)
                } else {
                    Log.w(TAG, "清理失败: " + f.absolutePath)
                }
            }
        }
        return freed
    }

    /**
     * 清理全部可清缓存。
     */
    fun clearAll(context: Context): Long = clear(context, overview(context).entries)

    private fun sizeOf(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        return f.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * 人类可读的大小。
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
            else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }
}
