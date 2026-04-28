package com.xinkong.diary.rag.embedding

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * BERT 分词器的纯 Kotlin 实现
 * 用于将中文文本转换为 token IDs
 */
class BertTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val idToToken: Map<Int, String>
) {
    companion object {
        private const val TAG = "BertTokenizer"
        private const val UNK_TOKEN = "[UNK]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"
        
        /**
         * 从 assets 加载分词器
         */
        fun fromAssets(context: Context, vocabPath: String = "models/vocab.txt"): BertTokenizer {
            val vocab = mutableMapOf<String, Int>()
            val idToToken = mutableMapOf<Int, String>()
            
            context.assets.open(vocabPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var index = 0
                    reader.forEachLine { line ->
                        val token = line.trim()
                        if (token.isNotEmpty()) {
                            vocab[token] = index
                            idToToken[index] = token
                            index++
                        }
                    }
                }
            }
            
            Log.i(TAG, "词表加载完成: path=$vocabPath size=${vocab.size}")
            return BertTokenizer(vocab, idToToken)
        }
    }
    
    val vocabSize: Int get() = vocab.size
    val padTokenId: Int get() = vocab[PAD_TOKEN] ?: 0
    val unkTokenId: Int get() = vocab[UNK_TOKEN] ?: 100
    val clsTokenId: Int get() = vocab[CLS_TOKEN] ?: 101
    val sepTokenId: Int get() = vocab[SEP_TOKEN] ?: 102
    
    /**
     * 编码文本为 token IDs
     * @param text 输入文本
     * @param maxLength 最大长度（包括 [CLS] 和 [SEP]）
     * @return TokenizedResult 包含 input_ids, attention_mask, token_type_ids
     */
    fun encode(text: String, maxLength: Int = 512): TokenizedResult {
        val tokens = tokenize(text)
        val unknownCount = tokens.count { it == UNK_TOKEN }
        Log.d(
            TAG,
            "encode: textLen=${text.length} tokenCount=${tokens.size} unknownCount=$unknownCount preview=${text.take(48).replace("\n", " ")}"
        )
        
        // 截断到 maxLength - 2（留给 [CLS] 和 [SEP]）
        val truncatedTokens = if (tokens.size > maxLength - 2) {
            tokens.subList(0, maxLength - 2)
        } else {
            tokens
        }
        
        // 构建 input_ids: [CLS] + tokens + [SEP]
        val inputIds = mutableListOf<Int>()
        inputIds.add(clsTokenId)
        truncatedTokens.forEach { token ->
            inputIds.add(vocab[token] ?: unkTokenId)
        }
        inputIds.add(sepTokenId)
        
        // 填充到 maxLength
        val paddingLength = maxLength - inputIds.size
        val attentionMask = MutableList(inputIds.size) { 1 } + MutableList(paddingLength) { 0 }
        val tokenTypeIds = MutableList(maxLength) { 0 }
        
        repeat(paddingLength) {
            inputIds.add(padTokenId)
        }
        
        return TokenizedResult(
            inputIds = inputIds.map { it.toLong() }.toLongArray(),
            attentionMask = attentionMask.map { it.toLong() }.toLongArray(),
            tokenTypeIds = tokenTypeIds.map { it.toLong() }.toLongArray()
        )
    }
    
    /**
     * 基于字符的中文分词 + WordPiece 子词分词
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        
        // 先按字符分割（中文）和按词分割（英文/数字）
        val basicTokens = basicTokenize(text)
        
        // 对每个 token 进行 WordPiece 分词
        for (token in basicTokens) {
            tokens.addAll(wordPieceTokenize(token))
        }
        Log.d(TAG, "tokenize: basic=${basicTokens.size} final=${tokens.size} preview=${tokens.take(12).joinToString(" ")}")
        
        return tokens
    }
    
    /**
     * 基础分词：中文按字符，英文/数字按词
     */
    private fun basicTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val currentToken = StringBuilder()
        
        for (char in text.lowercase()) {
            when {
                char.isWhitespace() -> {
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken.toString())
                        currentToken.clear()
                    }
                }
                isChinese(char) -> {
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken.toString())
                        currentToken.clear()
                    }
                    tokens.add(char.toString())
                }
                char.isLetterOrDigit() || char == '_' -> {
                    currentToken.append(char)
                }
                else -> {
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken.toString())
                        currentToken.clear()
                    }
                    // 标点符号也作为 token
                    if (vocab.containsKey(char.toString())) {
                        tokens.add(char.toString())
                    }
                }
            }
        }
        
        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken.toString())
        }
        
        return tokens
    }
    
    /**
     * WordPiece 子词分词
     */
    private fun wordPieceTokenize(token: String): List<String> {
        if (token.length == 1 && isChinese(token[0])) {
            // 单个中文字符直接查表
            return if (vocab.containsKey(token)) listOf(token) else listOf(UNK_TOKEN)
        }
        
        // 尝试整词匹配
        if (vocab.containsKey(token)) {
            return listOf(token)
        }
        
        // WordPiece 分词
        val subTokens = mutableListOf<String>()
        var start = 0
        
        while (start < token.length) {
            var end = token.length
            var found = false
            
            while (start < end) {
                val substr = if (start > 0) "##${token.substring(start, end)}" else token.substring(start, end)
                if (vocab.containsKey(substr)) {
                    subTokens.add(substr)
                    found = true
                    break
                }
                end--
            }
            
            if (!found) {
                // 无法分词，使用 [UNK]
                subTokens.add(UNK_TOKEN)
                start++
            } else {
                start = end
            }
        }
        
        return subTokens
    }
    
    private fun isChinese(char: Char): Boolean {
        val cp = char.code
        return (cp in 0x4E00..0x9FFF) ||
                (cp in 0x3400..0x4DBF) ||
                (cp in 0x20000..0x2A6DF) ||
                (cp in 0x2A700..0x2B73F) ||
                (cp in 0x2B740..0x2B81F) ||
                (cp in 0x2B820..0x2CEAF) ||
                (cp in 0xF900..0xFAFF) ||
                (cp in 0x2F800..0x2FA1F)
    }
}

/**
 * 分词结果
 */
data class TokenizedResult(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TokenizedResult
        return inputIds.contentEquals(other.inputIds) &&
                attentionMask.contentEquals(other.attentionMask) &&
                tokenTypeIds.contentEquals(other.tokenTypeIds)
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        return result
    }
}
