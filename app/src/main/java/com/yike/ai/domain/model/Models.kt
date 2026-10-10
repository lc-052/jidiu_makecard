package com.yike.ai.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val codec = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class TextBlock(val text: String, val section: String = "正文", val page: Int? = null)

@Serializable
data class DocumentContent(
    val id: String,
    val filename: String,
    val mimeType: String,
    val blocks: List<TextBlock>,
    val characterCount: Int,
    val warnings: List<String>,
    val parsedAt: Long,
    val hash: String,
    val parseMs: Long = 0,
    val cleanMs: Long = 0,
)

@Serializable
data class Candidate(
    val id: String = "",
    val type: String = "BASIC",
    val front: String = "",
    val back: String = "",
    val clozeText: String = "",
    val explanation: String = "",
    val tags: List<String> = emptyList(),
    val difficulty: Int = 2,
    val sourceDocumentId: String = "",
    val sourceSection: String = "",
    val sourcePage: Int? = null,
    val sourceExcerpt: String = "",
    val validationWarnings: List<String> = emptyList(),
    val templateId: String? = null,
)

@Serializable
data class CardOutput(val cards: List<Candidate>, val warnings: List<String> = emptyList())

@Serializable
data class KnowledgePoint(val concept: String, val excerpt: String, val cardCount: Int)

@Serializable data class KnowledgeAnalysis(val points: List<KnowledgePoint>)

@Serializable
data class GenerationConfig(
    val deckId: String,
    val targetCount: Int = 20,
    val language: String = "简体中文",
    val clozePercent: Int = 0,
    val mode: String = "基础理解",
    val difficulty: Int = 2,
    val explanations: Boolean = true,
    val sources: Boolean = true,
    val tags: Boolean = true,
    val analyzeFirst: Boolean = false,
    val batchSize: Int = 4,
    val sections: List<String> = emptyList(),
    val fastMode: Boolean = true,
    val wholeDocument: Boolean = false,
    val createDocumentDeck: Boolean = false,
)

class AppError(message: String, val retryable: Boolean = false, val retryAfterMs: Long? = null) :
    Exception(message)
