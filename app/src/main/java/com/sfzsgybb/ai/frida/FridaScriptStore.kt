package com.sfzsgybb.ai.frida

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Frida 脚本仓库（按应用隔离）。
 *
 * 【目录结构】
 * ```
 * files/frida/<包名>/
 *   ├── index.json         脚本清单（名称、启用状态、顺序）
 *   ├── <id>.js            脚本正文
 *   └── ...
 * ```
 *
 * 【为什么不用数据库】
 * 脚本是纯文本文件，用户可能想直接拷出来看或改。
 * 用文件存 + 一个轻量 JSON 索引，既方便导出，也不用为这点数据建表。
 *
 * 【为什么有了新结构还要兼容 main.js】
 * 旧版本只有一个固定文件 `main.js`。首次加载时把它转成一条
 * 「默认脚本」记录，老用户的脚本不会凭空消失。
 */
class FridaScriptStore(private val context: Context) {

    companion object {
        private const val TAG = "FridaScriptStore"
        private const val INDEX = "index.json"
        private const val LEGACY = "main.js"
    }

    /** 一条脚本记录。 */
    data class Script(
        val id: String,
        val name: String,
        val enabled: Boolean,
        /** 文件内的正文；列表页不需要，所以可能为空 */
        val code: String = "",
    )

    private fun dir(pkg: String): File =
        File(context.filesDir, "frida/$pkg").apply { mkdirs() }

    private fun indexPath(pkg: String) = File(dir(pkg), INDEX)

    // ---------------- 索引读写 ----------------

    private fun readIndex(pkg: String): MutableList<Script> {
        val f = indexPath(pkg)
        if (!f.exists()) return migrateLegacy(pkg)

        return runCatching {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Script>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Script(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    enabled = o.optBoolean("enabled", true),
                )
            }
            out
        }.getOrElse {
            Log.w(TAG, "读取脚本索引失败: ${it.message}")
            mutableListOf()
        }
    }

    private fun writeIndex(pkg: String, list: List<Script>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("enabled", s.enabled)
                })
            }
            indexPath(pkg).writeText(arr.toString(2))
        }.onFailure { Log.w(TAG, "写入脚本索引失败: ${it.message}") }
    }

    /**
     * 把旧版的单个 main.js 迁移成一条记录。
     *
     * 只在索引不存在时调用一次；之后 main.js 留着不影响。
     */
    private fun migrateLegacy(pkg: String): MutableList<Script> {
        val legacy = File(dir(pkg), LEGACY)
        if (!legacy.exists()) return mutableListOf()

        val id = newId()
        val script = Script(id = id, name = "默认脚本", enabled = true)
        runCatching { legacy.copyTo(File(dir(pkg), "$id.js"), overwrite = true) }
        val list = mutableListOf(script)
        writeIndex(pkg, list)
        Log.i(TAG, "已把旧版 main.js 迁移为脚本 $id")
        return list
    }

    // ---------------- 对外操作 ----------------

    /** 列出该应用的全部脚本（不含正文）。 */
    fun list(pkg: String): List<Script> = readIndex(pkg)

    /** 读脚本正文。 */
    fun read(pkg: String, id: String): String? {
        val f = File(dir(pkg), "$id.js")
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()
    }

    /** 新建脚本，返回新 id。 */
    fun create(pkg: String, name: String, code: String, enabled: Boolean): String {
        val id = newId()
        val list = readIndex(pkg)
        list += Script(id = id, name = name, enabled = enabled)
        runCatching { File(dir(pkg), "$id.js").writeText(code) }
        writeIndex(pkg, list)
        return id
    }

    /** 保存正文（同时可改名/改启用状态）。 */
    fun save(pkg: String, id: String, name: String, code: String, enabled: Boolean) {
        val list = readIndex(pkg)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = name, enabled = enabled)
            writeIndex(pkg, list)
        }
        runCatching { File(dir(pkg), "$id.js").writeText(code) }
    }

    /** 只要正文改动，不动元数据。 */
    fun saveCode(pkg: String, id: String, code: String) {
        runCatching { File(dir(pkg), "$id.js").writeText(code) }
    }

    /** 切换启用状态。 */
    fun setEnabled(pkg: String, id: String, enabled: Boolean) {
        val list = readIndex(pkg)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(enabled = enabled)
        writeIndex(pkg, list)
    }

    /** 删除脚本。最后一个删掉后目录会空，但不影响下次新建。 */
    fun delete(pkg: String, id: String): Boolean {
        val list = readIndex(pkg)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        list.removeAt(idx)
        runCatching { File(dir(pkg), "$id.js").delete() }
        writeIndex(pkg, list)
        return true
    }

    /**
     * 导入脚本（文件名去扩展名当脚本名）。
     *
     * @param content 文件内容
     * @param fileName 原始文件名，用于取默认名称
     * @param enabled 导入后是否立即启用（由用户决定）
     */
    fun import(pkg: String, content: String, fileName: String, enabled: Boolean): String {
        val name = fileName.substringBeforeLast('.').ifBlank { "导入的脚本" }
        return create(pkg, name, content, enabled)
    }

    /** 当前启用的脚本正文（按顺序拼接），供注入时使用。 */
    fun enabledCode(pkg: String): List<Pair<String, String>> {
        return readIndex(pkg).filter { it.enabled }.mapNotNull { s ->
            read(pkg, s.id)?.let { s.name to it }
        }
    }

    private fun newId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "").take(12)
}
