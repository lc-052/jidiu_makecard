package com.yike.ai.core.document

import com.yike.ai.domain.model.DocumentContent
import com.yike.ai.domain.model.TextBlock
import kotlin.math.ceil

data class GenerationBatch(val block: TextBlock, val target: Int)

/**
 * Input budget is independent of the provider's output ceiling. Includes the entire selected text.
 */
object GenerationBatchPlanner {
    /** Conservative local estimate, not a promise of factual coverage or a forced card count. */
    fun wholeDocumentPlan(
        document: DocumentContent,
        batchSize: Int,
        existingBySection: Map<String, Int> = emptyMap(),
    ): List<GenerationBatch> {
        require(batchSize in 1..24)
        val sections = document.blocks.filter { it.text.isNotBlank() }.groupBy { it.section }
        require(sections.size <= 500) { "资料超过500个知识单元，请按章节拆分文件" }
        val desired =
            sections.mapValues { (_, blocks) ->
                ceil(blocks.sumOf { it.text.replace(Regex("\\s+"), "").length }.toDouble() / 220)
                    .toInt()
                    .coerceAtLeast(1)
            }
        val quotas = sections.keys.associateWith { 1 }.toMutableMap()
        var remaining = minOf(500, desired.values.sum()) - sections.size
        while (remaining > 0) {
            val next =
                sections.keys
                    .filter { quotas.getValue(it) < desired.getValue(it) }
                    .maxByOrNull { desired.getValue(it).toDouble() / quotas.getValue(it) } ?: break
            quotas[next] = quotas.getValue(next) + 1
            remaining--
        }
        val batches =
            sections.flatMap { (section, blocks) ->
                val count =
                    (quotas.getValue(section) - (existingBySection[section] ?: 0)).coerceAtLeast(0)
                if (count == 0) emptyList()
                else plan(document.copy(blocks = blocks), count, batchSize)
            }
        val packed = mutableListOf<GenerationBatch>()
        batches.forEach { next ->
            val previous = packed.lastOrNull()
            if (
                previous != null &&
                    previous.target + next.target <= batchSize &&
                    DocumentChunker.tokens(previous.block.text) +
                        DocumentChunker.tokens(next.block.text) +
                        2 <= 6000
            ) {
                packed[packed.lastIndex] =
                    GenerationBatch(
                        TextBlock(
                            previous.block.text + "\n\n" + next.block.text,
                            (previous.block.section.lines() + next.block.section.lines())
                                .distinct()
                                .joinToString("\n"),
                            previous.block.page.takeIf { it == next.block.page },
                        ),
                        previous.target + next.target,
                    )
            } else packed += next
        }
        return packed
    }

    fun outputBudget(cards: Int, ceiling: Int): Int = (600 + cards * 700).coerceAtMost(ceiling)

    fun plan(document: DocumentContent, count: Int, batchSize: Int): List<GenerationBatch> {
        require(count in 1..500 && batchSize in 1..24)
        if (document.blocks.isEmpty()) return emptyList()
        val desired = ceil(count.toDouble() / batchSize).toInt()
        val total =
            document.blocks.sumOf { DocumentChunker.tokens(it.text) + 2 } +
                document.blocks
                    .map { it.section }
                    .distinct()
                    .sumOf { DocumentChunker.tokens("【章节：$it】\n") }
        val budget = ceil(total.toDouble() / desired).toInt().coerceIn(600, 6000)
        // Split only oversized passages first, then pack consecutive paragraphs/questions.
        val pieces = DocumentChunker.chunk(document, budget, minOf(120, budget / 4))
        val groups = mutableListOf<TextBlock>()
        var text = StringBuilder()
        var sections = linkedSetOf<String>()
        var pages = linkedSetOf<Int?>()
        var cost = 0
        fun flush() {
            if (text.isEmpty()) return
            groups += TextBlock(text.toString(), sections.joinToString("\n"), pages.singleOrNull())
            text = StringBuilder()
            sections = linkedSetOf()
            pages = linkedSetOf()
            cost = 0
        }
        pieces.forEach { piece ->
            val label = if (piece.section !in sections) "【章节：${piece.section}】\n" else ""
            val addition = label + piece.text
            val size = DocumentChunker.tokens(addition) + 2
            if (cost + size > budget && text.isNotEmpty()) flush()
            if (text.isNotEmpty()) text.append("\n\n")
            // Repeat the label after flushing so a new batch remains independent.
            val actual =
                if (piece.section !in sections) "【章节：${piece.section}】\n${piece.text}"
                else piece.text
            text.append(actual)
            sections += piece.section
            pages += piece.page
            cost += DocumentChunker.tokens(actual) + 2
        }
        flush()
        // Allocate whole batches rather than scattering one card across every paragraph.
        val allocations = IntArray(groups.size)
        var remaining = count
        val selected =
            if (groups.size <= count) groups.indices.toList()
            else List(count) { ((it + 0.5) * groups.size / count).toInt() }
        selected.forEachIndexed { index, i ->
            val share =
                ceil(remaining.toDouble() / (selected.size - index)).toInt().coerceAtMost(batchSize)
            allocations[i] = share
            remaining -= share
        }
        // Do not repeat a short source merely to reach the requested count.
        return groups.indices.map { GenerationBatch(groups[it], allocations[it]) }
    }
}
