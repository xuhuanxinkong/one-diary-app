package com.xinkong.diary.rag.embedding

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * ONNX 模型推理封装
 * 加载 bge-small-zh-v1.5 模型并生成 embedding
 */
class OnnxEmbeddingModel private constructor(
    private val ortEnv: OrtEnvironment,
    private val session: OrtSession,
    private val tokenizer: BertTokenizer
) : AutoCloseable {
    
    companion object {
        private const val TAG = "OnnxEmbeddingModel"
        private const val QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："
        private const val USE_MEAN_POOLING = false
        private const val EMBEDDING_DIM = 512
        
        /**
         * 从 assets 加载模型
         */
        fun fromAssets(
            context: Context,
            modelPath: String = "models/model.onnx",
            vocabPath: String = "models/vocab.txt"
        ): OnnxEmbeddingModel {
            val ortEnv = OrtEnvironment.getEnvironment()

            val modelDir = File(context.filesDir, "onnx_model").apply {
                if (!exists()) mkdirs()
            }

            val modelFile = copyAssetToFile(context, modelPath, modelDir)
            val externalDataAssetPath = "$modelPath.data"
            if (assetExists(context, externalDataAssetPath)) {
                copyAssetToFile(context, externalDataAssetPath, modelDir)
            } else {
                Log.w(TAG, "未找到外部权重文件: $externalDataAssetPath")
            }
            
            // 加载 ONNX 模型
            val sessionOptions = OrtSession.SessionOptions().apply {
                // 优化选项
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(4)
            }
            val session = ortEnv.createSession(modelFile.absolutePath, sessionOptions)
            
            // 加载分词器
            val tokenizer = BertTokenizer.fromAssets(context, vocabPath)
            Log.i(
                TAG,
                "模型加载完成: modelPath=${modelFile.absolutePath} vocabSize=${tokenizer.vocabSize}"
            )
            
            return OnnxEmbeddingModel(ortEnv, session, tokenizer)
        }

        private fun copyAssetToFile(context: Context, assetPath: String, targetDir: File): File {
            val fileName = assetPath.substringAfterLast('/')
            val outFile = File(targetDir, fileName)
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outFile
        }

        private fun assetExists(context: Context, assetPath: String): Boolean {
            return try {
                context.assets.open(assetPath).use { }
                true
            } catch (_: Exception) {
                false
            }
        }
    }
    
    /**
     * 生成文本的 embedding
     * @param text 输入文本
     * @param normalize 是否 L2 归一化（推荐开启）
     * @return 512 维 embedding 向量
     */
    fun embed(text: String, normalize: Boolean = true): FloatArray {
        return embedInternal(text, normalize, isQuery = false)
    }

    /**
     * 生成查询文本的 embedding
     * BGE 系列建议为 query 添加检索指令前缀。
     */
    fun embedQuery(text: String, normalize: Boolean = true): FloatArray {
        return embedInternal(text, normalize, isQuery = true)
    }

    private fun embedInternal(text: String, normalize: Boolean, isQuery: Boolean): FloatArray {
        val inputText = if (isQuery) {
            QUERY_PREFIX + text.trim()
        } else {
            text
        }
        val tokenized = tokenizer.encode(inputText, maxLength = 512)
        Log.d(
            TAG,
            buildString {
                append(if (isQuery) "query" else "document")
                append(" embed: textLen=")
                append(inputText.length)
                append(" tokenCount=")
                append(tokenized.inputIds.size)
                append(" normalize=")
                append(normalize)
                append(" preview=")
                append(inputText.take(60).replace("\n", " "))
            }
        )
        
        // 创建输入张量 (batch_size=1, sequence_length)
        val inputIds = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(tokenized.inputIds),
            longArrayOf(1, tokenized.inputIds.size.toLong())
        )
        val attentionMask = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(tokenized.attentionMask),
            longArrayOf(1, tokenized.attentionMask.size.toLong())
        )
        val tokenTypeIds = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(tokenized.tokenTypeIds),
            longArrayOf(1, tokenized.tokenTypeIds.size.toLong())
        )
        
        // 运行推理
        val inputs = mapOf(
            "input_ids" to inputIds,
            "attention_mask" to attentionMask,
            "token_type_ids" to tokenTypeIds
        )
        
        val output = session.run(inputs)
        
        // 输出形状: (batch_size, sequence_length, hidden_size)
        val lastHiddenState = output[0].value as Array<Array<FloatArray>>
        val pooledEmbedding = if (USE_MEAN_POOLING) {
            meanPool(lastHiddenState[0], tokenized.attentionMask)
        } else {
            // BGE 常见推理方式默认使用 [CLS] 向量。
            lastHiddenState[0][0]
        }
        
        // 清理资源
        inputIds.close()
        attentionMask.close()
        tokenTypeIds.close()
        output.close()
        
        val finalEmbedding = if (normalize) l2Normalize(pooledEmbedding) else pooledEmbedding
        Log.d(
            TAG,
            buildString {
                append(if (isQuery) "query" else "document")
                append(" output: dim=")
                append(finalEmbedding.size)
                append(" pooling=")
                append(if (USE_MEAN_POOLING) "mean" else "cls")
                append(" norm=")
                append(String.format(java.util.Locale.US, "%.4f", vectorNorm(finalEmbedding)))
                append(" head=")
                append(finalEmbedding.take(6).joinToString(prefix = "[", postfix = "]") {
                    String.format(java.util.Locale.US, "%.4f", it)
                })
            }
        )
        return finalEmbedding
    }
    
    /**
     * 批量生成 embedding
     */
    fun embedBatch(texts: List<String>, normalize: Boolean = true): List<FloatArray> {
        // 简单实现：逐个处理
        // TODO: 优化为真正的批量推理
        return texts.map { embed(it, normalize) }
    }
    
    /**
     * L2 归一化
     */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.map { it * it }.sum())
        return if (norm > 0) {
            vector.map { it / norm }.toFloatArray()
        } else {
            vector
        }
    }

    private fun meanPool(sequenceOutput: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        if (sequenceOutput.isEmpty()) return FloatArray(EMBEDDING_DIM)

        val hiddenSize = sequenceOutput[0].size
        val pooled = FloatArray(hiddenSize)
        var validTokenCount = 0

        for (tokenIndex in sequenceOutput.indices) {
            if (tokenIndex >= attentionMask.size || attentionMask[tokenIndex] == 0L) {
                continue
            }
            val tokenVector = sequenceOutput[tokenIndex]
            for (dimension in 0 until hiddenSize) {
                pooled[dimension] += tokenVector[dimension]
            }
            validTokenCount++
        }

        if (validTokenCount == 0) {
            Log.w(TAG, "attention mask 没有有效 token，回退使用第一个 token")
            return sequenceOutput[0].copyOf()
        }

        for (dimension in pooled.indices) {
            pooled[dimension] /= validTokenCount.toFloat()
        }
        return pooled
    }

    private fun vectorNorm(vector: FloatArray): Float {
        var sum = 0f
        for (value in vector) {
            sum += value * value
        }
        return sqrt(sum)
    }
    
    override fun close() {
        session.close()
        ortEnv.close()
    }
}
