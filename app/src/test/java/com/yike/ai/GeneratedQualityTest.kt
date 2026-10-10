package com.yike.ai

import com.yike.ai.core.common.CardRules
import com.yike.ai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GeneratedQualityTest {
    private val source = "Cache 用于缓解 CPU 与主存之间的速度差异。"
    private val doc =
        DocumentContent(
            "d",
            "source.txt",
            "text/plain",
            listOf(TextBlock(source, "缓存")),
            source.length,
            emptyList(),
            1,
            "hash",
        )

    private fun card(text: String) =
        Candidate(type = "CLOZE", clozeText = text, sourceExcerpt = source)

    @Test
    fun rejectsWholeSentenceAndMissingContext() {
        assertTrue(
            runCatching { CardRules.validateGenerated(card("{{c1::$source}}"), doc, 30) }.isFailure
        )
        assertTrue(
            runCatching { CardRules.validateGenerated(card("答案为{{c1::缓存}}。"), doc, 30) }.isFailure
        )
    }

    @Test
    fun allowsShortGapWithContextAndKeepsManualCompatibility() {
        val text = "{{c1::Cache}} 用于缓解 CPU 与主存之间的速度差异。"
        val generated = CardRules.validateGenerated(card(text), doc, 30)
        assertEquals("{{c1::Cache}} 用于缓解 `CPU` 与主存之间的速度差异。", generated.clozeText)
        assertEquals(source, generated.sourceExcerpt)
        assertEquals(CardRules.ordinals(text), CardRules.ordinals(generated.clozeText))
        assertTrue(runCatching { CardRules.validateGenerated(card(text), doc, 0) }.isFailure)
        assertEquals("{{c1::缓存}}", CardRules.validate(card("{{c1::缓存}}")).clozeText)
    }

    @Test
    fun rejectsExcessiveGapsAndDefaultsToBasic() {
        assertTrue(
            runCatching {
                    CardRules.validateGenerated(
                        card("缓存处理器与主存速度差异涉及{{c1::甲}}、{{c2::乙}}、{{c3::丙}}。"),
                        doc,
                        30,
                    )
                }
                .isFailure
        )
        assertEquals(0, GenerationConfig("deck").clozePercent)
        assertEquals(4, GenerationConfig("deck").batchSize)
    }
}
