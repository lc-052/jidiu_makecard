package com.yike.ai

import com.yike.ai.core.common.GenerationExistingCards
import com.yike.ai.core.document.*
import com.yike.ai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WholeDocumentPlanTest {
    private fun document(blocks: List<TextBlock>) =
        DocumentContent(
            "d",
            "课程.md",
            "text/markdown",
            blocks,
            blocks.sumOf { it.text.length },
            emptyList(),
            0,
            "hash",
        )

    @Test
    fun includesEverySectionBeyondTwentyCards() {
        val blocks = (1..45).map { TextBlock("知识点$it。" + "这是独立知识的原理和条件。".repeat(40), "题$it") }
        val plan = GenerationBatchPlanner.wholeDocumentPlan(document(blocks), 8)
        assertTrue(plan.sumOf { it.target } > 100)
        assertEquals(45, plan.flatMap { it.block.section.lines() }.distinct().size)
        assertTrue("Adjacent small sections share requests", plan.size < 45)
        assertTrue(plan.all { it.target in 1..8 })
        blocks.forEach { original ->
            assertTrue(plan.any { it.block.text.contains(original.text) })
        }
    }

    @Test
    fun fillsOnlyMissingQuotas() {
        val d = document(listOf(TextBlock("甲".repeat(440), "甲章"), TextBlock("乙".repeat(440), "乙章")))
        val plan = GenerationBatchPlanner.wholeDocumentPlan(d, 8, mapOf("甲章" to 2, "乙章" to 1))
        assertEquals(1, plan.sumOf { it.target })
        assertEquals("乙章", plan.single().block.section)
        assertTrue(
            GenerationBatchPlanner.wholeDocumentPlan(d, 8, mapOf("甲章" to 2, "乙章" to 2)).isEmpty()
        )
    }

    @Test
    fun doesNotSkipTinySectionsNextToLargeSection() {
        val d = document(listOf(TextBlock("大".repeat(10000), "大章"), TextBlock("小", "小章")))
        val plan = GenerationBatchPlanner.wholeDocumentPlan(d, 8)
        assertTrue(plan.any { "小章" in it.block.section.lines() && it.target > 0 })
        assertTrue(plan.all { it.target in 1..8 })
    }

    @Test
    fun capsQuotasWithoutDroppingTopics() {
        val d = document((1..100).map { TextBlock("知识".repeat(1000), "章$it") })
        val plan = GenerationBatchPlanner.wholeDocumentPlan(d, 8)
        assertTrue(plan.sumOf { it.target } <= 500)
        assertEquals(100, plan.flatMap { it.block.section.lines() }.distinct().size)
        assertThrows(IllegalArgumentException::class.java) {
            GenerationBatchPlanner.wholeDocumentPlan(
                document((1..501).map { TextBlock("知识", "章$it") }),
                8,
            )
        }
    }

    @Test
    fun oldTaskConfigurationStaysCompatible() {
        val old = codec.decodeFromString<GenerationConfig>("""{"deckId":"deck","targetCount":20}""")
        assertFalse(old.wholeDocument)
        assertFalse(old.createDocumentDeck)
    }

    @Test
    fun crossParagraphSourceCountsAreDistinctAndEmptySourcesIgnored() {
        assertEquals(
            mapOf("甲" to 2, "乙" to 1),
            GenerationExistingCards.sectionCounts(
                listOf(
                    Candidate(sourceSection = "甲\n乙\n甲"),
                    Candidate(sourceSection = "甲"),
                    Candidate(sourceSection = ""),
                )
            ),
        )
    }
}
