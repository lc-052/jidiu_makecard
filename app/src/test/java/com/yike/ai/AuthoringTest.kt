package com.yike.ai

import androidx.compose.ui.graphics.luminance
import com.yike.ai.core.common.BatchCardParser
import com.yike.ai.core.designsystem.CardTemplateRepository
import org.junit.Assert.*
import org.junit.Test

class AuthoringTest {
    @Test
    fun csvQuotedMultilineAndTags() {
        val rows = BatchCardParser.parse("\"问题,包含逗号\",\"答案\n下一行\",解释,标签\n另一问,另一答", ",")
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.error == null })
        assertEquals("答案\n下一行", rows[0].candidate!!.back)
    }

    @Test
    fun tabCustomMissingAndDuplicates() {
        assertEquals(2, BatchCardParser.parse("甲|乙\n丙|丁", "|").size)
        val rows = BatchCardParser.parse("甲\t乙\n甲\t乙\n缺少答案", "\t")
        assertNull(rows[0].error)
        assertNotNull(rows[1].error)
        assertNotNull(rows[2].error)
    }

    @Test
    fun clozeSelectionAndSyntax() {
        val value = BatchCardParser.cloze("Cache 缓解 CPU 与主存之间的速度差异。", "缓解 CPU 与主存之间的速度差异")
        assertTrue(value.contains("{{c1::缓解 CPU 与主存之间的速度差异}}"))
        assertNull(BatchCardParser.parse("$value\t", "\t").single().error)
        assertNotNull(BatchCardParser.parse("{{c0::错误}}\t", "\t").single().error)
        assertNotNull(BatchCardParser.parse("\"未结束,答案", ",").single().error)
        assertTrue(runCatching { BatchCardParser.cloze("原文", "不存在") }.isFailure)
    }

    @Test
    fun sixDistinctReadableStylesAndPriority() {
        val templates = CardTemplateRepository.all
        assertEquals(6, templates.size)
        assertEquals(6, templates.map { it.style.background }.distinct().size)
        templates.forEach { d ->
            val s = d.style
            val a = s.background.luminance()
            val b = s.foreground.luminance()
            assertTrue(d.name, (maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 4.5)
            assertTrue(s.borderWidth >= 1f)
            assertTrue(s.padding >= 20)
        }
        assertEquals("PAPER", CardTemplateRepository.resolve("PAPER", "FOREST", "NEON").id.name)
        assertEquals("FOREST", CardTemplateRepository.resolve(null, "FOREST", "NEON").id.name)
        assertEquals("CLASSIC", CardTemplateRepository.resolve(null, null, "CLASSIC").id.name)
        val css = CardTemplateRepository.css()
        templates.forEach { assertTrue(css.contains(".jt-${it.id.name.lowercase()}")) }
    }
}
