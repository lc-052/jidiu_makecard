package com.yike.ai.core.common

import com.yike.ai.domain.model.*
import java.security.MessageDigest

object CardRules {
    val cloze = Regex("\\{\\{c([1-9][0-9]*)::([^{}]+?)\\}\\}")

    fun ordinals(text: String) =
        cloze.findAll(text).map { it.groupValues[1].toInt() - 1 }.distinct().sorted().toList()

    fun renderCloze(text: String, ordinal: Int, answer: Boolean) =
        cloze.replace(text) { m ->
            val parts = m.groupValues[2].split("::", limit = 2)
            if (m.groupValues[1].toInt() - 1 != ordinal || answer) parts[0]
            else "[${parts.getOrNull(1)?:"…"}]"
        }

    fun clean(s: String) =
        s.replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    fun key(c: Candidate) =
        clean(if (c.type == "CLOZE") c.clozeText else c.front)
            .lowercase()
            .replace(Regex("[\\p{P}\\s]"), "")

    fun validate(raw: Candidate, document: DocumentContent? = null): Candidate {
        val c =
            raw.copy(
                front = clean(raw.front),
                back = clean(raw.back),
                clozeText = clean(raw.clozeText),
                explanation = clean(raw.explanation),
                tags =
                    raw.tags
                        .map { it.trim().replace(Regex("\\s+"), "_") }
                        .filter { it.isNotEmpty() }
                        .distinct(),
            )
        require(c.type in listOf("BASIC", "CLOZE", "COMPARISON")) { "不支持的卡型" }
        require(c.difficulty in 1..5) { "难度必须为 1–5" }
        if (c.type == "CLOZE") {
            require(ordinals(c.clozeText).isNotEmpty()) { "挖空卡需要合法的 {{c1::内容}}" }
            require(
                cloze.findAll(c.clozeText).all {
                    it.groupValues[2].substringBefore("::").isNotBlank()
                }
            ) {
                "挖空内容不能为空"
            }
            require(!c.clozeText.replace(cloze, "").contains("{{c")) { "挖空语法不完整" }
        } else {
            require(c.front.isNotBlank() && c.back.isNotBlank()) { "问题和答案不能为空" }
        }
        require(maxOf(c.front.length, c.back.length, c.clozeText.length) < 12000) { "卡片过长" }
        val warnings = c.validationWarnings.toMutableList()
        if ((if (c.type == "CLOZE") c.clozeText else c.back).length > 400) warnings += "答案较长，建议拆分"
        if (document != null) {
            require(c.sourceExcerpt.isNotBlank()) { "缺少可核对的原文引用" }
            val blocks =
                locateExcerpt(document, c.sourceExcerpt)
                    ?: throw IllegalArgumentException("引用无法在原文定位")
            return c.copy(
                sourceDocumentId = document.id,
                sourceSection = blocks.map { it.section }.distinct().joinToString("\n"),
                sourcePage = blocks.map { it.page }.distinct().singleOrNull(),
                validationWarnings = (warnings + "引用定位通过；事实准确性仍需人工审核").distinct(),
            )
        }
        return c.copy(validationWarnings = warnings.distinct())
    }

    /** Match only literal, consecutive source text. Never accept generated chapter markers. */
    private fun locateExcerpt(document: DocumentContent, excerpt: String): List<TextBlock>? {
        val quote = clean(excerpt)
        document.blocks
            .firstOrNull { clean(it.text).contains(quote) }
            ?.let {
                return listOf(it)
            }
        val text = StringBuilder()
        val ranges =
            document.blocks.map { block ->
                if (text.isNotEmpty()) text.append("\n\n")
                val start = text.length
                text.append(clean(block.text))
                start until text.length
            }
        val start = text.indexOf(quote)
        if (start < 0) return null
        val end = start + quote.length
        return document.blocks
            .filterIndexed { index, _ ->
                val range = ranges[index]
                !range.isEmpty() && range.first < end && range.last >= start
            }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * App-controlled feedback: never copy a provider error or document instruction into prompts.
     */
    fun generationRetryHint(reason: String?): String =
        when {
            reason?.contains("引用") == true || reason?.contains("原文依据") == true ->
                "引用校验失败：sourceExcerpt 必须从单个原文段落逐字摘录，不包含【章节：…】等应用添加的标题标记，不拼接不连续句子，不改写原文。"
            reason?.contains("挖空") == true -> "挖空校验失败：改用 BASIC 问答，或只挖1–2个短术语并保留完整上下文，不能遮盖整句。"
            else -> "上次输出校验失败，请检查 JSON 结构、必填字段与逐字原文依据。"
        }

    fun similarity(a: String, b: String): Double {
        fun grams(s: String) = if (s.length < 2) setOf(s) else s.windowed(2).toSet()
        val x = grams(a)
        val y = grams(b)
        return x.intersect(y).size.toDouble() / x.union(y).size.coerceAtLeast(1)
    }

    /** Additional checks for AI drafts only; existing/manual notes keep standard Anki syntax. */
    fun validateGenerated(raw: Candidate, document: DocumentContent, clozePercent: Int): Candidate {
        val c = validate(raw, document)
        if (c.type == "CLOZE") {
            require(clozePercent > 0) { "本次只生成问答卡" }
            val gaps = cloze.findAll(c.clozeText).toList()
            require(gaps.size in 1..2) { "挖空过多，请拆成独立卡片" }
            val answers = gaps.map { it.groupValues[2].substringBefore("::").trim() }
            require(answers.all { it.length in 1..80 && !it.contains('\n') }) { "挖空应为短术语、数值或关键短语" }
            val visible = cloze.replace(c.clozeText, "").replace(Regex("[\\p{P}\\s]"), "")
            require(visible.length >= 8) { "挖空缺少可独立理解的上下文" }
            require(
                answers.sumOf { it.length }.toDouble() /
                    renderCloze(c.clozeText, -1, true).length.coerceAtLeast(1) <= .6
            ) {
                "挖空遮盖过多，不能挖去整句或大段答案"
            }
        }
        return CardFormatting.styleGeneratedCard(c)
    }

    fun deduplicate(cards: List<Candidate>): List<Candidate> {
        val seen = mutableSetOf<String>()
        return cards
            .filter { seen.add(key(it)) }
            .map { c ->
                if (
                    cards.any {
                        it.id != c.id && key(it) != key(c) && similarity(key(it), key(c)) > 0.8
                    }
                )
                    c.copy(validationWarnings = c.validationWarnings + "与其他卡片相似，请检查")
                else c
            }
    }

    fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
