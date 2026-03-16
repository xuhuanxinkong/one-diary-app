package com.xinkong.diary.Http


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

enum class AiType {
    TEST, LOCAL, DEEPSEEK, CUSTOM
}

data class AiConfig(
    val type: AiType,
    val baseUrl: String,
    val model: String,
    val apiKey: String = ""
)

class AiHttp {
    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()

    // Default config (Test environment: Ollama)
    private var currentConfig = AiConfig(
        type = AiType.TEST,
        baseUrl = "http://10.0.2.2:11434/v1/chat/completions",
        model = "deepseek-r1:7b"
    )

    fun updateConfig(config: AiConfig) {
        currentConfig = config
    }

    suspend fun chatWithAi(message: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = createRequestBody(message)
                // 自动拼接chat/completions路径，兼容Ollama和OpenAI/DeepSeek
                val chatUrl = when {
                    currentConfig.baseUrl.endsWith("/chat/completions") -> currentConfig.baseUrl
                    currentConfig.baseUrl.endsWith("/v1") -> "${currentConfig.baseUrl}/chat/completions"
                    currentConfig.baseUrl.endsWith("/v1/") -> "${currentConfig.baseUrl}chat/completions"
                    else -> if (currentConfig.baseUrl.endsWith("/")) "${currentConfig.baseUrl}v1/chat/completions" else "${currentConfig.baseUrl}/v1/chat/completions"
                }
                val requestBuilder = Request.Builder()
                    .url(chatUrl)
                    .post(requestBody)
                    .header("Content-Type", "application/json")

                if (currentConfig.apiKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer ${currentConfig.apiKey}")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    val content = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    Result.success(content)
                } else {
                    Result.failure(IOException("请求失败${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    fun createRequestBody(message: String): RequestBody{
        val json = JSONObject().apply {
            put("model", currentConfig.model)
            put("messages", JSONObject.wrap(listOf(
                mapOf("role" to "user","content" to message)
            )))
            put("stream",false)
        }
        return json.toString().toRequestBody(mediaType)
    }

    suspend fun getModels(baseUrl: String, apiKey: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                // 自动拼接models路径，兼容Ollama和OpenAI/DeepSeek
                val modelsUrl = when {
                    baseUrl.endsWith("/models") -> baseUrl
                    baseUrl.endsWith("/v1") -> "$baseUrl/models"
                    baseUrl.endsWith("/v1/") -> "${baseUrl}models"
                    baseUrl.endsWith("/chat/completions") -> baseUrl.replace("/chat/completions", "/models")
                    else -> if (baseUrl.endsWith("/")) "${baseUrl}v1/models" else "$baseUrl/v1/models"
                }
                val requestBuilder = Request.Builder()
                    .url(modelsUrl)
                    .get()

                if (apiKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
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
}
