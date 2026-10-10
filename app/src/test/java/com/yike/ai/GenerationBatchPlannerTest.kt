package com.yike.ai

import com.yike.ai.core.database.ProviderEntity
import com.yike.ai.core.document.*
import com.yike.ai.core.network.*
import com.yike.ai.domain.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GenerationBatchPlannerTest {
    private fun doc(blocks: List<TextBlock>) =
        DocumentContent(
            "id",
            "x.md",
            "text/markdown",
            blocks,
            blocks.sumOf { it.text.length },
            emptyList(),
            0,
            "hash",
        )

    @Test
    fun packsQuestionsAndAnswersWithoutOneRequestPerParagraph() {
        val blocks =
            (1..200).map { TextBlock("知识${it}：" + "这是完整的资料和适用条件。".repeat(8), "题${(it-1)/4}") }
        val plan = GenerationBatchPlanner.plan(doc(blocks), 20, 4)
        assertEquals(20, plan.sumOf { it.target })
        assertTrue(plan.count { it.target > 0 } < 10)
        assertTrue(plan.all { it.target in 0..4 })
        blocks.forEach { b -> assertTrue(plan.any { it.block.text.contains(b.text) }) }
    }

    @Test
    fun shortSourceIsNotUploadedRepeatedlyToFillCount() {
        val plan = GenerationBatchPlanner.plan(doc(listOf(TextBlock("缓存缓解速度差异。"))), 20, 4)
        assertEquals(1, plan.size)
        assertEquals(4, plan.single().target)
    }

    @Test
    fun preservesPageOnlyWhenAccurate() {
        val same = listOf(TextBlock("第一段", "章节", 2), TextBlock("第二段", "章节", 2))
        assertEquals(2, GenerationBatchPlanner.plan(doc(same), 4, 4).single().block.page)
        assertNull(
            GenerationBatchPlanner.plan(doc(same + TextBlock("下一页", "后续", 3)), 4, 4)
                .single()
                .block
                .page
        )
    }

    @Test
    fun boundedHugePassageAndSeparatedOutputBudget() {
        val plan = GenerationBatchPlanner.plan(doc(listOf(TextBlock("知识内容".repeat(5000)))), 20, 4)
        assertTrue(plan.all { DocumentChunker.tokens(it.block.text) <= 6100 })
        assertTrue(plan.first().block.text.contains("知识内容"))
        assertEquals(3400, GenerationBatchPlanner.outputBudget(4, 64000))
        assertEquals(128, GenerationBatchPlanner.outputBudget(4, 128))
    }

    @Test
    fun thinkingCapabilityDoesNotLeakToOtherProvidersOrThinkingOnlyModels() {
        val p =
            ProviderEntity(
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                modelId = "qwen3.8-flash",
            )
        assertTrue(AiClient.supportsFastGeneration(p))
        assertFalse(AiClient.supportsFastGeneration(p.copy(baseUrl = "https://example.com/v1")))
        assertFalse(
            AiClient.supportsFastGeneration(p.copy(modelId = "qwen3-30b-a3b-thinking-2507"))
        )
        assertFalse(AiClient.supportsFastGeneration(p.copy(protocol = "RESPONSES")))
    }

    @Test
    fun tracksReportedReasoningSeparately() {
        val response =
            ChatAdapter.parse(
                codec
                    .parseToJsonElement(
                        """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":101,"completion_tokens_details":{"reasoning_tokens":90}}}"""
                    )
                    .jsonObject
            )
        assertEquals(90L, response.reasoningTokens)
        assertEquals(101L, response.outputTokens)
    }
}
