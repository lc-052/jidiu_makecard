package com.yike.ai

import com.yike.ai.core.common.CardRules
import com.yike.ai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SourceSpanTest {
    private fun doc(vararg blocks: TextBlock) =
        DocumentContent(
            "source",
            "fixture.md",
            "text/markdown",
            blocks.toList(),
            0,
            emptyList(),
            0,
            "hash",
        )

    private fun card(excerpt: String) =
        Candidate(front = "缓存有什么作用？", back = "缓解速度差异。", sourceExcerpt = excerpt)

    @Test
    fun adjacentParagraphsLocateWithHonestMetadata() {
        val source = doc(TextBlock("缓存缓解速度差异。", "原理", 1), TextBlock("命中率影响平均访问时间。", "性能", 2))
        val result = CardRules.validate(card("缓存缓解速度差异。\n\n命中率影响平均访问时间。"), source)
        assertEquals("原理\n性能", result.sourceSection)
        assertNull(result.sourcePage)
        assertEquals("source", result.sourceDocumentId)
    }

    @Test
    fun samePageAndSectionRetained() {
        val source = doc(TextBlock("第一句。", "正文", 3), TextBlock("第二句。", "正文", 3))
        val result = CardRules.validate(card("第一句。\n\n第二句。"), source)
        assertEquals(3, result.sourcePage)
        assertEquals("正文", result.sourceSection)
    }

    @Test
    fun singleParagraphAndPunctuationRemainExact() {
        val source = doc(TextBlock("公式：T = h * Tc + (1-h) * Tm。", "公式", 5))
        assertEquals(5, CardRules.validate(card("h * Tc + (1-h) * Tm"), source).sourcePage)
        assertThrows(IllegalArgumentException::class.java) {
            CardRules.validate(card("h * Tc - (1-h) * Tm"), source)
        }
    }

    @Test
    fun fabricatedOmittedReorderedAndInjectedMarkersRejected() {
        val source = doc(TextBlock("甲。"), TextBlock("必须保留的条件。"), TextBlock("乙。"))
        listOf("甲。\n\n乙。", "乙。\n\n甲。", "甲。\n\n【章节：正文】\n必须保留的条件。", "不存在的事实", "").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                CardRules.validate(card(it), source)
            }
        }
    }

    @Test
    fun unknownPageCannotBecomeAnAccuratePage() {
        val result =
            CardRules.validate(card("甲。\n\n乙。"), doc(TextBlock("甲。", page = 1), TextBlock("乙。")))
        assertNull(result.sourcePage)
    }

    @Test
    fun retryFeedbackContainsOnlyAppInstructions() {
        assertTrue(CardRules.generationRetryHint("引用无法在原文定位").contains("单个原文段落"))
        assertTrue(CardRules.generationRetryHint("挖空过多").contains("BASIC"))
        assertFalse(CardRules.generationRetryHint("untrusted provider secret").contains("secret"))
    }
}
