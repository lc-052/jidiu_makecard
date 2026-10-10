package com.yike.ai

import com.yike.ai.core.common.CardFormatting
import com.yike.ai.domain.model.Candidate
import org.junit.Assert.*
import org.junit.Test

class PlainEnglishStyleTest {
    @Test
    fun mixedChineseTermsGetInlineCode() {
        assertEquals(
            "`Cache` 缓解 `CPU` 与主存之间的速度差异。",
            CardFormatting.stylePlainEnglish("Cache 缓解 CPU 与主存之间的速度差异。"),
        )
        assertEquals(
            "使用 `StatefulWidget` 和 `setState()`。",
            CardFormatting.stylePlainEnglish("使用 StatefulWidget 和 setState()。"),
        )
    }

    @Test
    fun existingStylesAndEnglishOnlyStayUnchanged() {
        listOf(
                "**缓存**与 CPU",
                "使用 `Cache` 与 CPU",
                "- 使用 CPU",
                "纯中文内容。",
                "Cache reduces memory latency.",
                "参见 [Flutter](https://flutter.dev)",
            )
            .forEach { assertEquals(it, CardFormatting.stylePlainEnglish(it)) }
    }

    @Test
    fun linksEmailsAndFormulaProtected() {
        assertEquals(
            "查询 `API`：https://example.com 或 help@example.com",
            CardFormatting.stylePlainEnglish("查询 API：https://example.com 或 help@example.com"),
        )
        listOf("公式 T = h * Tc", "公式 \\(x+y\\)", "公式 \$E=mc^2\$", "公式\n\$\$\nx+y\n\$\$").forEach {
            assertEquals(it, CardFormatting.stylePlainEnglish(it))
        }
    }

    @Test
    fun gapSyntaxAndExcerptNeverChanged() {
        val c =
            Candidate(
                front = "CPU 的作用？",
                back = "执行 CPU 指令。",
                clozeText = "CPU 使用 {{c1::Cache::缓存}}。",
                sourceExcerpt = "CPU 使用 Cache。",
                tags = listOf("CPU"),
            )
        val styled = CardFormatting.styleGeneratedCard(c)
        assertEquals("`CPU` 使用 {{c1::Cache::缓存}}。", styled.clozeText)
        assertEquals(c.sourceExcerpt, styled.sourceExcerpt)
        assertEquals(c.tags, styled.tags)
        assertEquals(c.id, styled.id)
    }

    @Test
    fun applyingTwiceDoesNotDoubleWrap() {
        val once = CardFormatting.stylePlainEnglish("CPU 与 Cache。")
        assertEquals(once, CardFormatting.stylePlainEnglish(once))
    }

    @Test
    fun inlineCodeRendersAndExports() {
        val styled = CardFormatting.stylePlainEnglish("CPU 与 Cache。")
        assertEquals(2, CardFormatting.spans(styled).count { it.code })
        assertTrue(CardFormatting.toHtml(styled).contains("<code>CPU</code>"))
    }
}
