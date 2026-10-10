package com.yike.ai

import com.yike.ai.core.common.*
import org.junit.Assert.*
import org.junit.Test

class CardFormattingTest {
    @Test
    fun paragraphsListsAndHeadings() {
        val blocks = CardFormatting.blocks("核心答案。\n\n### 条件\n- **第一项**\n- 第二项\n1. 操作\n> 补充")
        assertEquals(
            listOf(
                CardBlockKind.PARAGRAPH,
                CardBlockKind.HEADING,
                CardBlockKind.BULLET,
                CardBlockKind.BULLET,
                CardBlockKind.NUMBERED,
                CardBlockKind.QUOTE,
            ),
            blocks.map { it.kind },
        )
    }

    @Test
    fun inlineMarkupLeavesReadableWords() {
        val spans = CardFormatting.spans("记住 **重点**，使用 `x < y` 和 ==条件==。")
        assertEquals("记住 重点，使用 x < y 和 条件。", spans.joinToString("") { it.text })
        assertEquals(2, spans.count { it.emphasis })
        assertEquals(1, spans.count { it.code })
    }

    @Test
    fun oldProseBreaksOnlyAtSentenceBoundaries() {
        val raw = "这是旧卡片中的一个完整句子，需要保留每个事实与条件。".repeat(9)
        val blocks = CardFormatting.blocks(raw)
        assertTrue(blocks.size > 1)
        assertEquals(raw, blocks.joinToString("") { it.text })
        assertTrue(blocks.dropLast(1).all { it.text.endsWith("。") })
    }

    @Test
    fun codeAndFormulaNotRewritten() {
        val raw = "a".repeat(160) + " \\(x < y\\)"
        assertEquals(raw, CardFormatting.blocks(raw).single().text)
        val code = CardFormatting.blocks("```\n  a < b\n    c\n```").single()
        assertEquals(CardBlockKind.CODE, code.kind)
        assertEquals("  a < b\n    c", code.text)
    }

    @Test
    fun htmlExportIsStructuredAndEscaped() {
        val html = CardFormatting.toHtml("**重点**\n\n- x < y & z\n- 第二项\n\n<script>example</script>")
        assertTrue(html.contains("<strong>重点</strong>"))
        assertTrue(html.contains("<ul><li>"))
        assertTrue(html.contains("x &lt; y &amp; z"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun clozeSurvivesFormattingAndHintsStayHidden() {
        val text = "**概念**：{{c1::甲}} 与 {{c2::乙::提示}}"
        val html = CardFormatting.toHtml(text)
        assertEquals(listOf(0, 1), CardRules.ordinals(html))
        assertTrue(CardFormatting.clozePreview(text, false).contains("[提示]"))
        assertFalse(CardFormatting.clozePreview(text, false).contains("乙"))
        assertTrue(CardFormatting.clozePreview(text, true).contains("乙"))
    }

    @Test
    fun existingHtmlAndMathFieldsPreserved() {
        assertEquals("<b>中文 &amp; 测试</b>", CardFormatting.toHtml("<b>中文 &amp; 测试</b>"))
        assertTrue(CardFormatting.toHtml("答案 \\(x^2\\)").contains("\\(x^2\\)"))
    }
}
