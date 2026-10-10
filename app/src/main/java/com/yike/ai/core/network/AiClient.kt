package com.yike.ai.core.network

import com.yike.ai.core.database.ProviderEntity
import com.yike.ai.domain.model.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class AiResponse(
    val text: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long? = null,
)

interface ProviderAdapter {
    fun request(p: ProviderEntity, system: String, user: String, schema: JsonObject?): JsonObject

    fun parse(body: JsonObject): AiResponse
}

private fun JsonObject.str(name: String) = get(name)?.jsonPrimitive?.contentOrNull

private fun JsonObject.num(name: String) = get(name)?.jsonPrimitive?.longOrNull ?: 0

object CardSchema {
    val compact: JsonObject
        get() {
            val root = value.toMutableMap()
            val properties = root.getValue("properties").jsonObject.toMutableMap()
            val cards = properties.getValue("cards").jsonObject.toMutableMap()
            val item = cards.getValue("items").jsonObject.toMutableMap()
            val keep =
                setOf("type", "front", "back", "clozeText", "explanation", "tags", "sourceExcerpt")
            item["properties"] =
                JsonObject(item.getValue("properties").jsonObject.filterKeys { it in keep })
            item["required"] = JsonArray(keep.map(::JsonPrimitive))
            cards["items"] = JsonObject(item)
            properties["cards"] = JsonObject(cards)
            root["properties"] = JsonObject(properties)
            return JsonObject(root)
        }

    val analysis: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JsonArray(listOf(JsonPrimitive("points"))))
        putJsonObject("properties") {
            putJsonObject("points") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    put(
                        "required",
                        JsonArray(listOf("concept", "excerpt", "cardCount").map(::JsonPrimitive)),
                    )
                    putJsonObject("properties") {
                        listOf("concept", "excerpt").forEach {
                            putJsonObject(it) { put("type", "string") }
                        }
                        putJsonObject("cardCount") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 10)
                        }
                    }
                }
            }
        }
    }
    val value: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("cards") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        listOf(
                                "id",
                                "front",
                                "back",
                                "clozeText",
                                "explanation",
                                "sourceDocumentId",
                                "sourceSection",
                                "sourceExcerpt",
                            )
                            .forEach { putJsonObject(it) { put("type", "string") } }
                        putJsonObject("type") {
                            put("type", "string")
                            put(
                                "enum",
                                JsonArray(
                                    listOf("BASIC", "CLOZE", "COMPARISON").map(::JsonPrimitive)
                                ),
                            )
                        }
                        listOf("tags", "validationWarnings").forEach {
                            putJsonObject(it) {
                                put("type", "array")
                                putJsonObject("items") { put("type", "string") }
                            }
                        }
                        putJsonObject("difficulty") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 5)
                        }
                        putJsonObject("sourcePage") {
                            put(
                                "type",
                                JsonArray(listOf(JsonPrimitive("integer"), JsonPrimitive("null"))),
                            )
                        }
                    }
                    put(
                        "required",
                        JsonArray(
                            listOf(
                                    "id",
                                    "type",
                                    "front",
                                    "back",
                                    "clozeText",
                                    "explanation",
                                    "tags",
                                    "difficulty",
                                    "sourceDocumentId",
                                    "sourceSection",
                                    "sourcePage",
                                    "sourceExcerpt",
                                    "validationWarnings",
                                )
                                .map(::JsonPrimitive)
                        ),
                    )
                }
            }
            putJsonObject("warnings") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("cards"), JsonPrimitive("warnings"))))
    }
}

object ChatAdapter : ProviderAdapter {
    override fun request(p: ProviderEntity, system: String, user: String, schema: JsonObject?) =
        buildJsonObject {
            put("model", p.modelId)
            put("stream", p.streaming)
            put(if (p.kind == "OPENAI") "max_completion_tokens" else "max_tokens", p.maxTokens)
            p.temperature?.let { put("temperature", it) }
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", "system")
                            put("content", system)
                        },
                        buildJsonObject {
                            put("role", "user")
                            put("content", user)
                        },
                    )
                ),
            )
            if (p.strictSchema && schema != null)
                putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", "memory_cards")
                        put("strict", true)
                        put("schema", schema)
                    }
                }
            if (p.streaming && p.kind == "OPENAI")
                putJsonObject("stream_options") { put("include_usage", true) }
        }

    override fun parse(body: JsonObject): AiResponse {
        val choice =
            body["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw AppError("接口返回缺少 choices，请检查协议")
        if (choice.str("finish_reason") == "length") throw AppError("输出被截断，请增加输出 Token 或减少目标卡片数")
        val message = choice["message"]?.jsonObject ?: throw AppError("响应缺少 message")
        if (
            message.str("refusal")?.isNotBlank() == true ||
                choice.str("finish_reason") == "content_filter"
        )
            throw AppError("模型拒绝生成该内容")
        val text = message.str("content")?.takeIf { it.isNotBlank() } ?: throw AppError("模型返回了空内容")
        val usage = body["usage"] as? JsonObject
        return AiResponse(
            text,
            usage?.num("prompt_tokens") ?: 0,
            usage?.num("completion_tokens") ?: 0,
            (usage?.get("completion_tokens_details") as? JsonObject)
                ?.get("reasoning_tokens")
                ?.jsonPrimitive
                ?.longOrNull,
        )
    }
}

object ResponsesAdapter : ProviderAdapter {
    override fun request(p: ProviderEntity, system: String, user: String, schema: JsonObject?) =
        buildJsonObject {
            put("model", p.modelId)
            put("instructions", system)
            put("input", user)
            put("max_output_tokens", p.maxTokens)
            put("stream", p.streaming)
            put("store", false)
            p.temperature?.let { put("temperature", it) }
            if (p.strictSchema && schema != null)
                putJsonObject("text") {
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("name", "memory_cards")
                        put("strict", true)
                        put("schema", schema)
                    }
                }
        }

    override fun parse(body: JsonObject): AiResponse {
        if (body.str("status") == "incomplete") throw AppError("输出被截断，请提高 Token 上限或减少卡片数")
        if (body.str("status") == "failed" || body["error"] is JsonObject)
            throw AppError("模型调用失败，请检查服务商和模型配置")
        val text = StringBuilder()
        body["output"]?.jsonArray?.forEach { item ->
            item.jsonObject["content"]?.jsonArray?.forEach { part ->
                val content = part.jsonObject
                if (content.str("type") == "refusal") throw AppError("模型拒绝生成该内容")
                if (content.str("type") == "output_text") text.append(content.str("text") ?: "")
            }
        }
        if (text.isBlank()) throw AppError("Responses 接口没有返回文本")
        val usage = body["usage"] as? JsonObject
        return AiResponse(
            text.toString(),
            usage?.num("input_tokens") ?: 0,
            usage?.num("output_tokens") ?: 0,
        )
    }
}

class AiClient(
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val allowLocalHttpForTests: Boolean = false,
) {
    fun validate(p: ProviderEntity) {
        require(p.concurrency in 1..4) { "并发批次必须为 1–4" }
        val url = p.baseUrl.toHttpUrlOrNullSafe() ?: throw AppError("Base URL 格式不正确")
        require(
            url.scheme == "https" ||
                (allowLocalHttpForTests && url.host in listOf("localhost", "127.0.0.1", "::1"))
        ) {
            "仅允许 HTTPS"
        }
        require(
            url.username.isEmpty() &&
                url.password.isEmpty() &&
                url.query == null &&
                url.fragment == null
        ) {
            "Base URL 不应包含认证信息或查询参数"
        }
        require(p.modelId.isNotBlank()) { "请填写 Model ID" }
        require(p.timeoutSeconds in 10..600 && p.maxTokens in 128..64000) { "超时或 Token 上限超出范围" }
        require(p.temperature == null || p.temperature in 0.0..2.0)
    }

    suspend fun call(
        p: ProviderEntity,
        key: String,
        system: String,
        user: String,
        schema: JsonObject? = CardSchema.value,
        fastGeneration: Boolean = false,
    ): AiResponse {
        validate(p)
        require(key.isNotBlank()) { "请先配置 API Key" }
        val adapter = if (p.protocol == "RESPONSES") ResponsesAdapter else ChatAdapter
        val client =
            baseClient
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(p.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(p.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
        val request =
            Request.Builder()
                .url(
                    p.baseUrl.trimEnd('/') +
                        if (p.protocol == "RESPONSES") "/responses" else "/chat/completions"
                )
                .header("Authorization", "Bearer $key")
                .post(
                    JsonObject(
                            adapter.request(p, system, user, schema).toMutableMap().apply {
                                if (fastGeneration && supportsFastGeneration(p))
                                    put("enable_thinking", JsonPrimitive(false))
                            }
                        )
                        .toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive)
                            continuation.resumeWithException(
                                AppError(
                                    if (
                                        e is java.net.SocketTimeoutException ||
                                            e.message?.contains("timeout", true) == true
                                    )
                                        "请求超时，请检查网络或增加超时时间"
                                    else "网络连接失败，请检查网络和 Base URL",
                                    true,
                                )
                            )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { r ->
                                if (!r.isSuccessful)
                                    throw httpError(
                                        r.code,
                                        r.header("Retry-After")
                                            ?.toLongOrNull()
                                            ?.coerceIn(0, 3600)
                                            ?.times(1000),
                                    )
                                val b = r.body ?: throw AppError("响应体为空")
                                val result =
                                    if (p.streaming) readStream(b, p, adapter)
                                    else {
                                        val raw =
                                            com.yike.ai.core.document.DocumentParser.readBounded(
                                                    b.byteStream(),
                                                    MAX_BODY.toInt(),
                                                )
                                                .toString(Charsets.UTF_8)
                                        require(raw.isNotBlank()) { "响应体为空" }
                                        adapter.parse(codec.parseToJsonElement(raw).jsonObject)
                                    }
                                if (continuation.isActive) continuation.resume(result)
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive)
                                continuation.resumeWithException(
                                    if (e is AppError) e
                                    else if (e is IOException)
                                        AppError("网络响应中断或超时，已保留完成批次，将重试", true)
                                    else AppError("响应格式损坏或协议不兼容，请检查接口类型和结构化输出设置")
                                )
                        }
                    }
                }
            )
        }
    }

    private fun readStream(
        body: ResponseBody,
        p: ProviderEntity,
        adapter: ProviderAdapter,
    ): AiResponse {
        var bytes = 0
        val text = StringBuilder()
        var finish: String? = null
        var usage: JsonObject? = null
        var completed: AiResponse? = null
        val source = body.source()
        var data = StringBuilder()
        fun event() {
            if (data.isEmpty()) return
            val raw = data.toString().trim()
            data = StringBuilder()
            if (raw == "[DONE]") return
            val obj = codec.parseToJsonElement(raw).jsonObject
            if (p.protocol == "RESPONSES") {
                when (obj.str("type")) {
                    "response.output_text.delta" -> text.append(obj.str("delta") ?: "")
                    "response.refusal.delta" -> throw AppError("模型拒绝生成")
                    "response.completed" -> completed = adapter.parse(obj["response"]!!.jsonObject)
                    "response.incomplete" -> throw AppError("输出被截断")
                    "error",
                    "response.failed" -> throw AppError("流式接口报告错误")
                }
            } else {
                if (obj["error"] is JsonObject) throw AppError("流式接口报告错误")
                usage = obj["usage"] as? JsonObject ?: usage
                obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.let { choice ->
                    val delta = choice["delta"] as? JsonObject
                    if (delta?.str("refusal")?.isNotBlank() == true) throw AppError("模型拒绝生成")
                    text.append(delta?.str("content") ?: "")
                    choice.str("finish_reason")?.let { finish = it }
                }
            }
        }
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            bytes += line.length
            require(bytes <= MAX_BODY) { "响应过大" }
            if (line.isBlank()) event()
            else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
        }
        event()
        completed?.let {
            return it
        }
        if (p.protocol == "RESPONSES") throw AppError("流式响应中断，未收到完成事件", true)
        if (finish == "length") throw AppError("输出被截断")
        if (finish == "content_filter") throw AppError("模型拒绝生成")
        if (finish == null) throw AppError("流式响应中断", true)
        if (text.isBlank()) throw AppError("流式响应为空")
        return AiResponse(
            text.toString(),
            usage?.num("prompt_tokens") ?: 0,
            usage?.num("completion_tokens") ?: 0,
        )
    }

    companion object {
        /** Only documented official DashScope Qwen hybrid models receive this extension. */
        fun supportsFastGeneration(p: ProviderEntity): Boolean {
            val host = p.baseUrl.toHttpUrlOrNull()?.host ?: return false
            val official =
                host == "dashscope.aliyuncs.com" ||
                    host == "dashscope-intl.aliyuncs.com" ||
                    host.endsWith(".maas.aliyuncs.com")
            return official &&
                p.protocol == "CHAT" &&
                (p.modelId.startsWith("qwen3") ||
                    p.modelId in listOf("qwen-plus", "qwen-flash", "qwen-turbo")) &&
                !p.modelId.contains("thinking", true) &&
                !p.modelId.contains("-vl", true)
        }

        const val MAX_BODY = 4L * 1024 * 1024

        fun httpError(code: Int, retryAfterMs: Long? = null) =
            AppError(
                when (code) {
                    401,
                    403 -> "身份认证失败（$code），检查 API Key 和权限"
                    404 -> "路径或模型不存在（404），检查 Base URL / Model ID"
                    429 -> "请求限流或额度不足（429），稍后重试"
                    in 500..599 -> "服务商暂时异常（$code），稍后重试"
                    400 -> "请求参数不兼容（400），尝试关闭严格 Schema、温度或流式响应"
                    else -> "接口返回 HTTP $code"
                },
                code == 429 || code >= 500,
                retryAfterMs,
            )

        fun parseCards(raw: String): CardOutput {
            val text =
                raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            return try {
                codec.decodeFromString<CardOutput>(text)
            } catch (e: Exception) {
                throw AppError("卡片 JSON 格式错误；请重试或更换模型")
            }
        }
    }
}

private fun String.toHttpUrlOrNullSafe(): HttpUrl? = toHttpUrlOrNull()
