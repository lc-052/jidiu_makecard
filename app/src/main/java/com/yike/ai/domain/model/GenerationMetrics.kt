package com.yike.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RequestMetric(
    val batch: Int,
    val phase: String,
    val elapsedMs: Long,
    val uploadedCharacters: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val error: String? = null,
    val reasoningTokens: Long? = null,
    val outputLimit: Int? = null,
    val thinkingDisabled: Boolean = false,
)

@Serializable
data class GenerationMetrics(
    val parseMs: Long = 0,
    val cleanMs: Long = 0,
    val chunkMs: Long = 0,
    val startedAt: Long = 0,
    val firstBatchMs: Long? = null,
    val jsonMs: Long = 0,
    val databaseMs: Long = 0,
    val retries: Int = 0,
    val requests: List<RequestMetric> = emptyList(),
    val effectiveConcurrency: Int = 1,
    val notBefore: Long = 0,
)
