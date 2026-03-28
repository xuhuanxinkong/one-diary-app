package com.xinkong.diary.Http


import com.xinkong.diary.data.AiResponse
import com.xinkong.diary.data.AiToolCall
import com.xinkong.diary.repository.AiChatConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.util.Log

class AiHttp {
    companion object {
        private const val TAG = "AiHttp"
    }

    // 可自定义超时时长（如 60 秒）
    private val client = OkHttpClient.Builder()
        .connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val mediaType = "application/json".toMediaType()
    private val readNotesToolSchema = JSONObject().apply {
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

    private val writeNoteToolSchema = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "write_note")
            put("description", "新增本地笔记")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "笔记标题")
                    })
                    put("content", JSONObject().apply {
                        put("type", "string")
                        put("description", "笔记内容")
                    })
                    put("tag", JSONObject().apply {
                        put("type", "string")
                        put("description", "笔记标签，如：未分类")
                    })
                })
                put("required", JSONArray().put("title").put("content").put("tag"))
                put("additionalProperties", false)
            })
        })
    }

    private val editNoteToolSchema = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "edit_note")
            put("description", "修改已有的本地笔记")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply {
                        put("type", "integer")
                        put("description", "笔记ID")
                    })
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "修改后的标题")
                    })
                    put("content", JSONObject().apply {
                        put("type", "string")
                        put("description", "修改后的内容")
                    })
                    put("tag", JSONObject().apply {
                        put("type", "string")
                        put("description", "修改后的标签")
                    })
                })
                put("required", JSONArray().put("id"))
                put("additionalProperties", false)
            })
        })
    }

    private val getTagsAndFoldersToolSchema = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "get_tags_and_folders")
            put("description", "获取本地所有的文件夹和标签，以了解现有的分类结构")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
                put("additionalProperties", false)
            })
        })
    }

    private val queryChatHistoryToolSchema = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "query_chat_history")
            put("description", "自动查询此对话中之前的历史记录（提供超长记忆上下文）。支持按关键词查询，获取由于限制未被带入当前的历史信息。")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("keyword", JSONObject().apply {
                        put("type", "string")
                        put("description", "要搜索的聊天记录关键词，可以为空字符串以获取最近的历史。")
                    })
                    put("limit", JSONObject().apply {
                        put("type", "integer")
                        put("description", "返回最多几条。")
                        put("default", 20)
                    })
                    put("startDate", JSONObject().apply {
                        put("type", "string")
                        put("description", "指定时间范围的开始日期，格式如 'yyyy-MM-dd HH:mm'")
                    })
                    put("endDate", JSONObject().apply {
                        put("type", "string")
                        put("description", "指定时间范围的结束日期，格式如 'yyyy-MM-dd HH:mm'")
                    })
                })
                put("required", JSONArray().put("keyword"))
                put("additionalProperties", false)
            })
        })
    }

    private val webSearchBaiduToolSchema = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "web_search_baidu")
            put("description", "使用百度智能云进行搜索，获取最新的网页信息。当你需要了解超出训练数据截止日期的事情、实时动态或检索具体资料时使用。")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("keyword", JSONObject().apply {
                        put("type", "string")
                        put("description", "要搜索的关键词")
                    })
                })
                put("required", JSONArray().put("keyword"))
                put("additionalProperties", false)
            })
        })
    }

    /**
     * 发送对话请求（无状态，config 由调用方传入）
     * 群聊场景下，每个 AI 可传入各自的 config
     */
    suspend fun chatWithAi(
        config: AiChatConfig,
        messages: List<Map<String, Any>>,
        enabledTools: Set<String> = emptySet()
    ): Result<AiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val chatUrl = resolveCompletionsUrl(config.baseUrl)
                val requestPayload = buildRequestJson(config.model, messages, enabledTools)
                val body = buildRequestBody(config.model, messages, enabledTools)

                Log.d(TAG, "[chatWithAi] url=$chatUrl, model=${config.model}, stream=false, messages=$messages, enabledTools=$enabledTools")
                Log.d(TAG, "[chatWithAi] request payload: $requestPayload")

                val requestBuilder = Request.Builder()
                    .url(chatUrl)
                    .post(body)
                    .header("Content-Type", "application/json")

                if (config.apiKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "[chatWithAi] response code=${response.code}, body=$responseBody")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val choicesArray = json.optJSONArray("choices")
                    if (choicesArray != null && choicesArray.length() > 0) {
                        val message = choicesArray.getJSONObject(0).optJSONObject("message")
                        if (message != null) {
                            val content = message.optString("content", "")
                            val toolCalls = parseToolCalls(message.optJSONArray("tool_calls"))
                            Result.success(AiResponse.Message(content = content, toolCalls = toolCalls))
                        } else {
                            Result.failure(IOException("解析失败：message 字段为空"))
                        }
                    } else {
                        // 尝试读取 error 信息
                        val errorObj = json.optJSONObject("error")
                        val errorMsg = errorObj?.optString("message") ?: "API 未返回有效的 choices 数据"
                        Result.failure(IOException("响应异常：$errorMsg"))
                    }
                } else {
                    Result.failure(IOException("请求失败 ${response.code}：$responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun chatWithAiStream(
        config: AiChatConfig,
        messages: List<Map<String, Any>>,
        enabledTools: Set<String> = emptySet()
    ): Flow<AiResponse.StreamChunk> = flow {
        val chatUrl = resolveCompletionsUrl(config.baseUrl)
        val requestPayload = buildRequestJson(config.model, messages, enabledTools, isStream = true)
        val body = buildRequestBody(config.model, messages, enabledTools, isStream = true)

        Log.d(TAG, "[chatWithAiStream] url=$chatUrl, model=${config.model}, stream=true, messages=$messages, enabledTools=$enabledTools")
        Log.d(TAG, "[chatWithAiStream] request payload: $requestPayload")

        val requestBuilder = Request.Builder()
            .url(chatUrl)
            .post(body)
            .header("Content-Type", "application/json")

        if (config.apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            Log.d(TAG, "[chatWithAiStream] response code=${response.code}")
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "[chatWithAiStream] error body: $errBody")
                throw IOException("请求失败 ${response.code}：$errBody")
            }

            val reader = response.body?.source()?.inputStream()?.bufferedReader()
            val toolCallsBuffer = mutableMapOf<Int, JSONObject>()

            reader?.use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    Log.v(TAG, "[chatWithAiStream] SSE line: $line")
                    if (line!!.startsWith("data:")) {
                        val data = line!!.removePrefix("data:").trim()
                        if (data == "[DONE]") break

                        try {
                            val json = JSONObject(data)
                            val choicesArray = json.optJSONArray("choices") ?: continue
                            if (choicesArray.length() == 0) continue
                            val delta = choicesArray.getJSONObject(0).optJSONObject("delta") ?: continue

                            // 1. Text Content
                            if (delta.has("content") && !delta.isNull("content")) {
                                emit(AiResponse.StreamChunk.Content(delta.optString("content", "")))
                            }

                            // 2. Tool Calls
                            if (delta.has("tool_calls")) {
                                val tcArray = delta.getJSONArray("tool_calls")
                                for (i in 0 until tcArray.length()) {
                                    val tc = tcArray.getJSONObject(i)
                                    val index = tc.optInt("index")
                                    val existing = toolCallsBuffer.getOrPut(index) { JSONObject() }

                                    if (tc.has("id")) existing.put("id", tc.getString("id"))
                                    if (tc.has("type")) existing.put("type", tc.getString("type"))
                                    if (tc.has("function")) {
                                        val func = tc.getJSONObject("function")
                                        val existingFunc = if (existing.has("function")) {
                                            existing.getJSONObject("function")
                                        } else {
                                            JSONObject().also { existing.put("function", it) }
                                        }
                                        if (func.has("name")) existingFunc.put("name", func.getString("name"))
                                        if (func.has("arguments")) {
                                            val currentArgs = existingFunc.optString("arguments", "")
                                            existingFunc.put("arguments", currentArgs + func.getString("arguments"))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "[chatWithAiStream] SSE parse error: ${e.message}")
                        }
                    }
                }
            }

            if (toolCallsBuffer.isNotEmpty()) {
                val finalToolCalls = JSONArray()
                toolCallsBuffer.keys.sorted().forEach { finalToolCalls.put(toolCallsBuffer[it]) }
                emit(AiResponse.StreamChunk.ToolCalls(parseToolCalls(finalToolCalls)))
            }

            emit(AiResponse.StreamChunk.End)
        }
    }.flowOn(Dispatchers.IO)

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

    suspend fun performBaiduWebSearch(keyword: String, apiKey: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://qianfan.baidubce.com/v2/ai_search/web_search"
                
                val reqJson = JSONObject().apply {
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", keyword)
                    }))
                    put("search_source", "baidu_search_v2")
                    put("resource_type_filter", JSONArray().put(JSONObject().apply {
                        put("type", "web")
                        put("top_k", 20)
                    }))
                    put("search_recency_filter", "year")
                }
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .post(reqJson.toString().toRequestBody(mediaType))
                    .build()
                    
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "")
                    val content = respJson.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "未返回结果")
                    Result.success(content ?: "未返回结果")
                } else {
                    Result.failure(IOException("搜索请求失败: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ---- 私有工具方法 ----

    private fun buildRequestJson(
        model: String,
        messages: List<Map<String, Any>>,
        enabledTools: Set<String>,
        isStream: Boolean = false
    ): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("messages", JSONObject.wrap(messages))
            val toolsArray = JSONArray()
            if (enabledTools.contains("read_notes")) {
                toolsArray.put(readNotesToolSchema)
                toolsArray.put(getTagsAndFoldersToolSchema)
            }
            if (enabledTools.contains("write_note")) {
                toolsArray.put(writeNoteToolSchema)
            }
            if (enabledTools.contains("edit_note")) {
                toolsArray.put(editNoteToolSchema)
            }
            if (enabledTools.contains("query_chat_history")) {
                toolsArray.put(queryChatHistoryToolSchema)
            }
            if (enabledTools.contains("web_search_baidu")) {
                toolsArray.put(webSearchBaiduToolSchema)
            }
            if (toolsArray.length() > 0) {
                put("tools", toolsArray)
                put("tool_choice", "auto")
            }
            put("stream", isStream)
        }
    }

    private fun buildRequestBody(model: String, messages: List<Map<String, Any>>, enabledTools: Set<String>, isStream: Boolean = false): RequestBody {
        val json = buildRequestJson(model, messages, enabledTools, isStream)
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
