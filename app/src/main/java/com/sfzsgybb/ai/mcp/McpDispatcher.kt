package com.sfzsgybb.ai.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * MCP 方法分发器。
 *
 * 实现 JSON-RPC 2.0 + MCP 2024-11-05 协议：
 *   initialize / notifications/initialized / ping / tools/list / tools/call
 *
 * 相比原版 native 实现的改进：
 *  1. 支持 notifications（无 id 请求不返回响应体，原版会回 200 空 body 但结构不规范）
 *  2. 工具入参按 schema 校验后再执行，错误定位更准
 *  3. 每个工具调用带超时与异常隔离，单个工具挂掉不影响服务
 */
class McpDispatcher(
    private val tools: List<McpToolHandler>,
    private val serverInfo: McpServerInfo = McpServerInfo(),
) {
    private val json = McpJson.instance
    private val toolMap = tools.associateBy { it.name }

    /** 当前可用的工具名（供错误提示与 UI 使用）。 */
    fun toolNames(): List<String> = tools.filter { it.availability() }.map { it.name }

    /** 全部工具名（含当前不可用的），用于诊断。 */
    fun allToolNames(): List<String> = tools.map { it.name }

    /**
     * 当前工具集合的指纹。
     *
     * 抓包开关、Frida 安装状态变化时指纹会变，
     * 据此判断是否需要给客户端发 tools/list_changed 通知。
     */
    fun availabilityFingerprint(): Int =
        tools.filter { it.availability() }.map { it.name }.hashCode()

    /** 供 UI 展示的工具清单。 */
    fun toolMetaList(): List<McpToolMeta> = tools.map { McpToolMeta(it.name, it.description, it.paginated) }

    /**
     * 处理一条 JSON-RPC 消息。
     * @return 响应 JSON 字符串；返回 null 表示这是一条 notification，不需要响应。
     */
    suspend fun handle(rawBody: String): String? {
        val request = try {
            json.parseToJsonElement(rawBody).jsonObject
        } catch (t: Throwable) {
            return errorResponse(null, JsonRpcError.parseError("请求不是合法 JSON: ${t.message}"))
        }

        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull()
            ?: return errorResponse(id, JsonRpcError.invalidRequest("缺少 method 字段"))
        val params = request["params"]?.let { if (it is JsonNull) null else it.jsonObject }

        // notification：无 id，不返回响应
        val isNotification = id == null || id is JsonNull

        val result: JsonElement? = when (method) {
            "initialize" -> handleInitialize(params)
            "notifications/initialized", "initialized" -> return null
            "ping" -> buildJsonObject { }
            "tools/list" -> handleToolsList(params)
            "tools/call" -> handleToolsCall(params)
            "notifications/cancelled" -> return null
            else -> return if (isNotification) null
            else errorResponse(id, JsonRpcError.methodNotFound(method))
        }

        if (isNotification) return null
        return successResponse(id, result)
    }

    private fun handleInitialize(params: JsonObject?): JsonElement {
        val requested = params?.get("protocolVersion")?.jsonPrimitive?.contentOrNull()
        // 协议版本协商：不支持时回退到我们实现的版本（而不是像原版直接报错断开）
        val version = if (requested != null && requested in SUPPORTED_VERSIONS) requested else McpConstants.PROTOCOL_VERSION
        return json.encodeToJsonElement(
            McpInitializeResult.serializer(),
            McpInitializeResult(
                protocolVersion = version,
                serverInfo = serverInfo,
                capabilities = McpCapabilities(),
                instructions = McpConstants.INSTRUCTIONS,
            ),
        )
    }

    private fun handleToolsList(params: JsonObject?): JsonElement = buildJsonObject {
        putJsonArray("tools") {
            // 【按需暴露】只列出当前可用的工具。
            // 57 个工具的 schema 约占 14000 token，什么都没开时也全塞进去
            // 既挤上下文又让模型容易选错。裁剪后空闲状态只剩 9 个。
            tools.filter { it.availability() }.forEach { t ->
                add(buildJsonObject {
                    put("name", t.name)
                    put("description", t.description)
                    put("inputSchema", t.inputSchema)
                })
            }
        }
    }

    private suspend fun handleToolsCall(params: JsonObject?): JsonElement {
        val name = params?.get("name")?.jsonPrimitive?.contentOrNull()
            ?: return toolError("缺少工具名 name")
        val args = params["arguments"]?.let { if (it is JsonNull) JsonObject(emptyMap()) else it.jsonObject }
            ?: JsonObject(emptyMap())

        val tool = toolMap[name] ?: return toolError("未知工具: $name。可用工具: ${toolNames().joinToString()}")

        // 【兜底】部分客户端不支持 tools/list_changed 通知，
        // 会拿着首次拉取的旧列表调用已经隐藏的工具。
        // 此时不能报「不存在」，要说明为什么不可用、怎么让它可用。
        if (!tool.availability()) {
            return toolError("工具 $name 当前不可用：${tool.unavailableHint()}")
        }

        return try {
            validateRequired(tool, args)
            val text = tool.execute(args)
            buildJsonObject {
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                }
                put("isError", false)
            }
        } catch (t: McpArgException) {
            toolError(t.message ?: "参数错误")
        } catch (t: Throwable) {
            toolError("工具 $name 执行失败: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** 按 schema 的 required 做基础校验，比原版「出错才报」更早暴露问题。 */
    private fun validateRequired(tool: McpToolHandler, args: JsonObject) {
        val required = (tool.inputSchema["required"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() } ?: return
        val missing = required.filter { key ->
            val v = args[key]
            v == null || v is JsonNull || (v is JsonPrimitive && v.isString && v.content.isEmpty())
        }
        if (missing.isNotEmpty()) {
            throw McpArgException("缺少必填参数: ${missing.joinToString()}。schema: ${tool.inputSchema}")
        }
    }

    private fun toolError(message: String): JsonElement = buildJsonObject {
        putJsonArray("content") {
            add(buildJsonObject {
                put("type", "text")
                put("text", message)
            })
        }
        put("isError", true)
    }

    private fun successResponse(id: JsonElement?, result: JsonElement?): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result ?: buildJsonObject { })
    }.toString()

    private fun errorResponse(id: JsonElement?, error: JsonRpcError): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        putJsonObject("error") {
            put("code", error.code)
            put("message", error.message)
            error.data?.let { put("data", it) }
        }
    }.toString()

    companion object {
        val SUPPORTED_VERSIONS = setOf("2024-11-05", "2024-10-07", "2025-03-26", "2025-06-18")
    }
}

private fun JsonPrimitive.contentOrNull(): String? =
    if (this is JsonNull) null else content
