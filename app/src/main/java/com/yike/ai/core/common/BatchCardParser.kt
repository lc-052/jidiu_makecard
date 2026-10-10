package com.yike.ai.core.common

import com.yike.ai.domain.model.Candidate

data class BatchRow(val line: Int, val candidate: Candidate?, val error: String? = null)

object BatchCardParser {
    fun parse(text: String, separator: String): List<BatchRow> {
        require(separator.isNotEmpty() && '\n' !in separator && '\r' !in separator) { "分隔符不能为空或换行" }
        require(text.length <= 1_000_000) { "批量文本过大，请分批导入" }
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (quoted && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else quoted = !quoted
                    i++
                }
                !quoted && text.startsWith(separator, i) -> {
                    row += cell.toString()
                    cell.clear()
                    i += separator.length
                }
                !quoted && c == '\n' -> {
                    row += cell.toString().trimEnd('\r')
                    cell.clear()
                    rows += row.toList()
                    row.clear()
                    i++
                }
                else -> {
                    cell.append(c)
                    i++
                }
            }
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString().trimEnd('\r')
            rows += row.toList()
        }
        if (quoted) return listOf(BatchRow(rows.size + 1, null, "引号未闭合，请检查 CSV"))
        val seen = mutableSetOf<String>()
        return rows.mapIndexedNotNull { index, fields ->
            if (fields.all { it.isBlank() }) null
            else
                try {
                    require(fields.size in 2..5) { "需要 2–5 列：问题/挖空、答案、解释、标签、类型" }
                    val first = fields[0].trim()
                    val type =
                        fields.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }
                            ?: if (first.contains("{{c")) "CLOZE" else "BASIC"
                    val c =
                        CardRules.validate(
                            Candidate(
                                type = type,
                                front = if (type == "CLOZE") "" else first,
                                back = fields[1].trim(),
                                clozeText = if (type == "CLOZE") first else "",
                                explanation = fields.getOrNull(2)?.trim() ?: "",
                                tags =
                                    fields.getOrNull(3)?.split(Regex("[,，\\s]+"))?.filter {
                                        it.isNotBlank()
                                    } ?: emptyList(),
                            )
                        )
                    require(
                        seen.add("${c.type}|${CardRules.clean(first)}|${CardRules.clean(c.back)}")
                    ) {
                        "重复记录"
                    }
                    BatchRow(index + 1, c)
                } catch (e: Exception) {
                    BatchRow(index + 1, null, e.message ?: "格式错误")
                }
        }
    }

    fun cloze(text: String, gap: String): String {
        require(gap.isNotBlank() && text.contains(gap)) { "挖空内容必须是原文中的连续文字" }
        require(!gap.contains("{{") && !text.contains("{{c")) { "原文已有挖空，请直接编辑挖空文本" }
        return text.replaceFirst(gap, "{{c1::$gap}}")
    }
}
