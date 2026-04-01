package com.xinkong.diary.rag.embedding

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
            
            // 加载 ONNX 模型
            val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
            val sessionOptions = OrtSession.SessionOptions().apply {
                // 优化选项
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(4)
            }
            val session = ortEnv.createSession(modelBytes, sessionOptions)
            
            // 加载分词器
            val tokenizer = BertTokenizer.fromAssets(context, vocabPath)
            
            return OnnxEmbeddingModel(ortEnv, session, tokenizer)
        }
    }
    
    /**
     * 生成文本的 embedding
     * @param text 输入文本
     * @param normalize 是否 L2 归一化（推荐开启）
     * @return 512 维 embedding 向量
     */
    fun embed(text: String, normalize: Boolean = true): FloatArray {
        val tokenized = tokenizer.encode(text, maxLength = 512)
        
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
        
        // 获取 [CLS] token 的输出作为句子 embedding
        // 输出形状: (batch_size, sequence_length, hidden_size)
        val lastHiddenState = output[0].value as Array<Array<FloatArray>>
        val clsEmbedding = lastHiddenState[0][0] // 第一个 batch，第一个 token ([CLS])
        
        // 清理资源
        inputIds.close()
        attentionMask.close()
        tokenTypeIds.close()
        output.close()
        
        return if (normalize) l2Normalize(clsEmbedding) else clsEmbedding
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
    
    override fun close() {
        session.close()
        ortEnv.close()
    }
}
