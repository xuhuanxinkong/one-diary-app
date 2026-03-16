package com.xinkong.diary.Http


import com.xinkong.diary.Data.AiResponse
import com.xinkong.diary.repository.AiChatConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AiHttp {
    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()

    /**
     * 发送对话请求（无状态，config 由调用方传入）
     * 群聊场景下，每个 AI 可传入各自的 config
     */
    suspend fun chatWithAi(
        config: AiChatConfig,
        messages: List<Map<String, String>>
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
                    val content = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    Result.success(AiResponse.Text(content))
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

    private fun buildRequestBody(model: String, messages: List<Map<String, String>>): RequestBody {
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONObject.wrap(messages))
            put("stream", false)
        }
        return json.toString().toRequestBody(mediaType)
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
