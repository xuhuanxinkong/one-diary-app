package com.xinkong.diary.Http


import com.xinkong.diary.Data.AiResponse
import com.xinkong.diary.Data.AiToolCall
import com.xinkong.diary.repository.AiChatConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AiHttp {
    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()
    private val readNotesToolSchema = JSONArray().put(
        JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "read_notes")
                put("description", "按关键词读取本地笔记摘要")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "搜索关键词，仅中英文单词")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "返回条数，1 到 5")
                            put("minimum", 1)
                            put("maximum", 5)
                        })
                    })
                    put("required", JSONArray().put("keyword"))
                    put("additionalProperties", false)
                })
            })
        }
    )

    /**
     * 发送对话请求（无状态，config 由调用方传入）
     * 群聊场景下，每个 AI 可传入各自的 config
     */
    suspend fun chatWithAi(
        config: AiChatConfig,
        messages: List<Map<String, Any>>
    ): Result<AiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val chatUrl = resolveCompletionsUrl(config.baseUrl)
                val body = buildRequestBody(config.model, messages)

                val requestBuilder = Request.Builder()
                    .url(chatUrl)
                    .post(body)
                    .header("Content-Type", "application/json")

                if (config.apiKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val message = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                    val content = message.optString("content", "")
                    val toolCalls = parseToolCalls(message.optJSONArray("tool_calls"))
                    Result.success(AiResponse.Message(content = content, toolCalls = toolCalls))
                } else {
                    Result.failure(IOException("请求失败${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getModels(baseUrl: String, apiKey: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val modelsUrl = resolveModelsUrl(baseUrl)
                val requestBuilder = Request.Builder()
                    .url(modelsUrl)
                    .get()

                if (apiKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val data = json.getJSONArray("data")
                    val models = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        models.add(data.getJSONObject(i).getString("id"))
                    }
                    Result.success(models)
                } else {
                    Result.failure(IOException("获取模型失败: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ---- 私有工具方法 ----

    private fun buildRequestBody(model: String, messages: List<Map<String, Any>>): RequestBody {
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONObject.wrap(messages))
            put("tools", readNotesToolSchema)
            put("tool_choice", "auto")
            put("stream", false)
        }
        return json.toString().toRequestBody(mediaType)
    }

    private fun parseToolCalls(toolCallsArray: JSONArray?): List<AiToolCall> {
        if (toolCallsArray == null) return emptyList()
        val toolCalls = mutableListOf<AiToolCall>()
        for (i in 0 until toolCallsArray.length()) {
            val callObj = toolCallsArray.optJSONObject(i) ?: continue
            val functionObj = callObj.optJSONObject("function") ?: continue
            val name = functionObj.optString("name")
            if (name.isEmpty()) continue
            toolCalls.add(
                AiToolCall(
                    id = callObj.optString("id"),
                    type = callObj.optString("type", "function"),
                    functionName = name,
                    arguments = functionObj.optString("arguments", "{}")
                )
            )
        }
        return toolCalls
    }

    private fun resolveCompletionsUrl(baseUrl: String): String = when {
        baseUrl.endsWith("/chat/completions") -> baseUrl
        baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
        baseUrl.endsWith("/v1/") -> "${baseUrl}chat/completions"
        else -> if (baseUrl.endsWith("/")) "${baseUrl}v1/chat/completions" else "$baseUrl/v1/chat/completions"
    }

    private fun resolveModelsUrl(baseUrl: String): String = when {
        baseUrl.endsWith("/models") -> baseUrl
        baseUrl.endsWith("/v1") -> "$baseUrl/models"
        baseUrl.endsWith("/v1/") -> "${baseUrl}models"
        baseUrl.endsWith("/chat/completions") -> baseUrl.replace("/chat/completions", "/models")
        else -> if (baseUrl.endsWith("/")) "${baseUrl}v1/models" else "$baseUrl/v1/models"
    }
}
