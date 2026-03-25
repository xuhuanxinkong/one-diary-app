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
    // 可自定义超时时长（如 60 秒）
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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
                    put("folder", JSONObject().apply {
                        put("type", "string")
                        put("description", "存放文件夹，如：我的笔记")
                    })
                    put("tag", JSONObject().apply {
                        put("type", "string")
                        put("description", "笔记标签，如：未分类")
                    })
                })
                put("required", JSONArray().put("title").put("content").put("folder").put("tag"))
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
                    put("folder", JSONObject().apply {
                        put("type", "string")
                        put("description", "修改后的文件夹")
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
                val body = buildRequestBody(config.model, messages, enabledTools)

                val requestBuilder = Request.Builder()
                    .url(chatUrl)
                    .post(body)
                    .header("Content-Type", "application/json")

                if (config.apiKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""
                
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

    private fun buildRequestBody(model: String, messages: List<Map<String, Any>>, enabledTools: Set<String>): RequestBody {
        val json = JSONObject().apply {
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
            if (toolsArray.length() > 0) {
                put("tools", toolsArray)
                put("tool_choice", "auto")
            }
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
