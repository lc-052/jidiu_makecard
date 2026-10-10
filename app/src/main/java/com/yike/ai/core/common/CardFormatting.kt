package com.yike.ai.core.common

enum class CardBlockKind {
    PARAGRAPH,
    HEADING,
    BULLET,
    NUMBERED,
    QUOTE,
    CODE,
}

data class CardBlock(val kind: CardBlockKind, val text: String, val marker: String = "")

data class CardSpan(val text: String, val emphasis: Boolean = false, val code: Boolean = false)

/** A deliberately small, safe presentation dialect; the stored source remains unchanged. */
object CardFormatting {
    /** AI-only fallback. Source excerpts and Anki gap expressions are never rewritten. */
    fun styleGeneratedCard(c: com.yike.ai.domain.model.Candidate) =
        c.copy(
            front = stylePlainEnglish(c.front),
            back = stylePlainEnglish(c.back),
            explanation = stylePlainEnglish(c.explanation),
            clozeText = stylePlainEnglish(c.clozeText),
        )

    fun stylePlainEnglish(text: String): String {
        // Preserve existing Markdown/HTML styling rather than repeatedly decorating it.
        if (
            Regex(
                    "`|\\*\\*|__|==|(?m)^\\s*(?:#{1,6} |[-*+] |\\d+[.)] |>)|<[/A-Za-z]|\\[[^\\]]*\\]\\("
                )
                .containsMatchIn(text)
        )
            return text
        val protected =
            Regex(
                "https?://[^\\s，。；]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]+|\\{\\{c[1-9][0-9]*::[^{}]*\\}\\}|\\$\\$[\\s\\S]*?\\$\\$|\\$[^$\\n]+\\$|\\\\\\([\\s\\S]*?\\\\\\)|\\\\\\[[\\s\\S]*?\\\\\\]"
            )
        val english = Regex("[A-Za-z][A-Za-z0-9_]*(?:[./+#-][A-Za-z0-9_]+)*(?:\\(\\))?")
        val ranges = protected.findAll(text).map { it.range }.toList()
        return english.replace(text) { match ->
            val start = text.lastIndexOf('\n', match.range.first) + 1
            val end = text.indexOf('\n', match.range.last).let { if (it < 0) text.length else it }
            val line = text.substring(start, end)
            if (
                ranges.any { match.range.first in it } ||
                    !Regex("\\p{IsHan}").containsMatchIn(line) ||
                    Regex("[=<>]|[A-Za-z]\\s*[+*/^]\\s*[A-Za-z0-9]").containsMatchIn(line)
            )
                match.value
            else "`${match.value}`"
        }
    }

    const val generationGuide =
        "卡片排版：front 是一个独立明确的问题，不重复标题或答案；back 先用一两句给出核心答案，通常 30–100 字，必要的条件不可省略。多个独立知识点拆成多张卡，复杂说明放 explanation，不把解释堆进 back。确有并列信息时用换行的 - 列表，每卡通常 2–4 项；只对 1–3 个关键短语使用 **加粗**。段落之间用空行。解释按原理、适用条件、例子分成短段，可用简短标题。不要输出 HTML、表格、装饰符号、Emoji 或整段加粗。Cloze 保留标准语法，不把整段文字挖空；sourceExcerpt 保持原文逐字摘录，不加排版符号。所有换行遵循 JSON 字符串转义。" +
            "若字段没有 Markdown 排版样式，将中文语境中的英文术语用单个反引号包裹，例如：`Cache` 缓解 `CPU` 与主存的速度差异。已有样式、代码、链接、公式和 Cloze 挖空内容保持不变；不要给 sourceExcerpt 添加反引号。"
    private val bullet = Regex("^[-*•]\\s+(.+)$")
    private val numbered = Regex("^(\\d+[.、）)])\\s*(.+)$")
    private val heading = Regex("^#{1,4}\\s+(.+)$")
    private val inline = Regex("\\*\\*([^*\\n]+)\\*\\*|`([^`\\n]+)`|==([^=\\n]+)==")

    fun spans(text: String): List<CardSpan> {
        val result = mutableListOf<CardSpan>()
        var cursor = 0
        inline.findAll(text).forEach { m ->
            if (m.range.first > cursor) result += CardSpan(text.substring(cursor, m.range.first))
            result +=
                if (m.groupValues[2].isNotEmpty()) CardSpan(m.groupValues[2], code = true)
                else CardSpan(m.groupValues[1].ifEmpty { m.groupValues[3] }, emphasis = true)
            cursor = m.range.last + 1
        }
        if (cursor < text.length) result += CardSpan(text.substring(cursor))
        return result
    }

    fun blocks(raw: String): List<CardBlock> {
        val result = mutableListOf<CardBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false
        fun flush() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString("\n").trim()
            paragraph.clear()
            // Old prose gets visual paragraph breaks only at complete Chinese sentences.
            // Never split a formula, cloze expression or code region.
            if (
                text.length <= 140 ||
                    text.contains("\\(") ||
                    text.contains("\\[") ||
                    text.contains("{{c")
            ) {
                if (text.isNotEmpty()) result += CardBlock(CardBlockKind.PARAGRAPH, text)
            } else {
                var start = 0
                var end = 0
                text.forEachIndexed { i, c ->
                    end = i + 1
                    if (c in "。！？" && end - start >= 80) {
                        result +=
                            CardBlock(CardBlockKind.PARAGRAPH, text.substring(start, end).trim())
                        start = end
                    }
                }
                if (start < end)
                    result += CardBlock(CardBlockKind.PARAGRAPH, text.substring(start).trim())
            }
        }
        raw.replace("\r\n", "\n").split('\n').forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    result += CardBlock(CardBlockKind.CODE, code.joinToString("\n"))
                    code.clear()
                } else flush()
                inCode = !inCode
            } else if (inCode) code += line
            else if (trimmed.isBlank()) flush()
            else {
                val h = heading.matchEntire(trimmed)
                val b = bullet.matchEntire(trimmed)
                val n = numbered.matchEntire(trimmed)
                when {
                    h != null -> {
                        flush()
                        result += CardBlock(CardBlockKind.HEADING, h.groupValues[1])
                    }
                    b != null -> {
                        flush()
                        result += CardBlock(CardBlockKind.BULLET, b.groupValues[1], "•")
                    }
                    n != null -> {
                        flush()
                        result +=
                            CardBlock(CardBlockKind.NUMBERED, n.groupValues[2], n.groupValues[1])
                    }
                    trimmed.startsWith("> ") -> {
                        flush()
                        result += CardBlock(CardBlockKind.QUOTE, trimmed.drop(2))
                    }
                    else -> paragraph += line
                }
            }
        }
        flush()
        if (inCode && code.isNotEmpty())
            result += CardBlock(CardBlockKind.CODE, code.joinToString("\n"))
        return result
    }

    fun clozePreview(text: String, answer: Boolean): String =
        CardRules.cloze.replace(text) { m ->
            val parts = m.groupValues[2].split("::", limit = 2)
            "**${if (answer) parts[0] else "[${parts.getOrNull(1) ?: "…"}]"}**"
        }

    fun escape(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun inlineHtml(text: String) =
        spans(text).joinToString("") {
            val value = escape(it.text).replace("\n", "<br>")
            when {
                it.code -> "<code>$value</code>"
                it.emphasis -> "<strong>$value</strong>"
                else -> value
            }
        }

    fun toHtml(text: String): String {
        // Existing user-authored rich text is already an Anki HTML field.
        if (
            Regex(
                    "</?(?:p|div|br|b|strong|i|em|span|ul|ol|li|h[1-6]|table|sub|sup)(?:\\s[^>]*|/?)>",
                    RegexOption.IGNORE_CASE,
                )
                .containsMatchIn(text)
        )
            return text.replace("\n", "<br>")
        val out = StringBuilder()
        var list: CardBlockKind? = null
        fun closeList() {
            if (list != null) {
                out.append(if (list == CardBlockKind.BULLET) "</ul>" else "</ol>")
                list = null
            }
        }
        blocks(text).forEach { b ->
            if (b.kind in listOf(CardBlockKind.BULLET, CardBlockKind.NUMBERED)) {
                if (list != b.kind) {
                    closeList()
                    list = b.kind
                    out.append(if (list == CardBlockKind.BULLET) "<ul>" else "<ol>")
                }
                out.append("<li>${inlineHtml(b.text)}</li>")
            } else {
                closeList()
                out.append(
                    when (b.kind) {
                        CardBlockKind.HEADING -> "<h3>${inlineHtml(b.text)}</h3>"
                        CardBlockKind.QUOTE -> "<blockquote>${inlineHtml(b.text)}</blockquote>"
                        CardBlockKind.CODE -> "<pre><code>${escape(b.text)}</code></pre>"
                        else -> "<p>${inlineHtml(b.text)}</p>"
                    }
                )
            }
        }
        closeList()
        return out.toString()
    }
}
