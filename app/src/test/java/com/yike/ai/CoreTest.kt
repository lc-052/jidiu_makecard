package com.yike.ai

import com.yike.ai.core.common.CardRules
import com.yike.ai.core.database.*
import com.yike.ai.core.document.*
import com.yike.ai.core.fsrs.FsrsScheduler
import com.yike.ai.core.network.*
import com.yike.ai.domain.model.*
import com.yike.ai.domain.usecase.studyStats
import java.time.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test
    fun cleaning() {
        assertEquals("a b\n\nc", CardRules.clean(" a\t b\r\n\r\n\r\nc\u0000 "))
    }

    @Test
    fun clozeMultiple() {
        val text = "{{c1::甲}} 和 {{c3::乙::提示}} 与 {{c1::丙}}"
        assertEquals(listOf(0, 2), CardRules.ordinals(text))
        assertEquals("甲 和 [提示] 与 丙", CardRules.renderCloze(text, 2, false))
        assertEquals("甲 和 乙 与 丙", CardRules.renderCloze(text, 2, true))
    }

    @Test
    fun rejectMalformedCloze() {
        assertThrows(IllegalArgumentException::class.java) {
            CardRules.validate(Candidate(type = "CLOZE", clozeText = "{{c0::不合法}}"))
        }
    }

    @Test
    fun rejectsEmpty() {
        assertThrows(IllegalArgumentException::class.java) {
            CardRules.validate(Candidate(front = "问题"))
        }
    }

    @Test
    fun sourceVerification() {
        val doc =
            DocumentContent(
                "d",
                "x",
                "text/plain",
                listOf(TextBlock("光合作用需要光照。", "章一", 3)),
                11,
                emptyList(),
                0,
                "",
            )
        val c =
            CardRules.validate(Candidate(front = "条件？", back = "光照", sourceExcerpt = "需要光照"), doc)
        assertEquals(3, c.sourcePage)
        assertThrows(IllegalArgumentException::class.java) {
            CardRules.validate(c.copy(sourceExcerpt = "编造事实"), doc)
        }
    }

    @Test
    fun duplicateAndSimilar() {
        val a = Candidate(id = "1", front = "什么是记忆？", back = "保存信息")
        val b = a.copy(id = "2", front = "什么是记忆")
        assertEquals(1, CardRules.deduplicate(listOf(a, b)).size)
        assertEquals(1.0, CardRules.similarity("abcdef", "abcdef"), 0.0)
    }

    @Test
    fun chunkCoverageAndBudget() {
        val text = (1..3000).joinToString(" ") { "知识$it" }
        val doc =
            DocumentContent(
                "d",
                "x",
                "text/plain",
                listOf(TextBlock(text)),
                text.length,
                emptyList(),
                0,
                "",
            )
        val chunks = DocumentChunker.chunk(doc, 500, 50)
        assertTrue(chunks.size > 5)
        chunks.forEach { assertTrue(DocumentChunker.tokens(it.text) <= 502) }
        assertTrue(chunks.first().text.startsWith("知识1"))
        assertTrue(chunks.last().text.endsWith("知识3000"))
        assertEquals(25, DocumentChunker.allocate(chunks, 25).sum())
    }

    @Test
    fun strictJsonParsing() {
        val output = CardOutput(listOf(Candidate(front = "问题", back = "答案")))
        assertEquals(output, AiClient.parseCards("```json\n${codec.encodeToString(output)}\n```"))
        assertThrows(AppError::class.java) { AiClient.parseCards("{broken}") }
    }

    @Test
    fun errorMapping() {
        listOf(401, 403, 404, 429, 500, 503).forEach {
            assertTrue(AiClient.httpError(it).message!!.contains(it.toString()))
        }
        assertTrue(AiClient.httpError(429).retryable)
        assertFalse(AiClient.httpError(401).retryable)
    }

    @Test
    fun responsesParseRefusalAndTruncation() {
        assertThrows(AppError::class.java) {
            ResponsesAdapter.parse(
                codec.parseToJsonElement("""{"status":"incomplete"}""").jsonObject
            )
        }
        assertThrows(AppError::class.java) {
            ChatAdapter.parse(
                codec
                    .parseToJsonElement(
                        """{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}"""
                    )
                    .jsonObject
            )
        }
    }

    @Test
    fun utcAndDstStatistics() {
        val now = Instant.parse("2026-03-09T04:30:00Z").toEpochMilli()
        val zone = ZoneId.of("America/New_York")
        val old = CardEntity(noteId = "n")
        val logs =
            listOf("2026-03-08T05:30:00Z", "2026-03-09T04:15:00Z").map {
                ReviewLogEntity(
                    cardId = old.id,
                    rating = 3,
                    reviewedAt = Instant.parse(it).toEpochMilli(),
                    previousState = codec.encodeToString(old),
                    nextState = "",
                    elapsedTimeMs = 5000,
                )
            }
        val s = studyStats(logs, now, zone)
        assertEquals(1, s.today)
        assertEquals(2, s.streak)
        assertEquals(5000L, s.duration)
    }

    @Test
    fun backwardClockClamps() {
        val c =
            CardEntity(
                noteId = "n",
                state = "REVIEW",
                stability = 2.3,
                difficulty = 4.0,
                lastReviewAt = 100000,
            )
        val next = FsrsScheduler().schedule(c, 3, 90000)
        assertEquals(0.0, next.elapsedDays, 0.0)
        assertTrue(next.dueAt > 90000)
    }

    @Test
    fun forgettingCurveDefinition() {
        assertEquals(0.9, FsrsScheduler().retrievability(12.0, 12.0), 1e-12)
    }

    @Test
    fun officialFsrsReferenceVectors() {
        val json = javaClass.classLoader!!.getResource("fsrs-vectors.json")!!.readText()
        val cases = codec.decodeFromString<List<Vector>>(json)
        assertEquals(840, cases.size)
        cases.forEachIndexed { i, v ->
            val next =
                FsrsScheduler(v.retention)
                    .schedule(v.before.copy(id = "c", noteId = "n"), v.rating, v.now)
            assertEquals("case $i stability", v.expected.stability, next.stability, 1e-9)
            assertEquals("case $i difficulty", v.expected.difficulty, next.difficulty, 1e-9)
            assertEquals("case $i due", v.expected.dueAt, next.dueAt)
            assertEquals("case $i state", v.expected.state, next.state)
            assertEquals("case $i step", v.expected.step, next.step)
        }
    }

    @Serializable
    data class Vector(
        val before: CardEntity,
        val rating: Int,
        val now: Long,
        val retention: Double,
        val expected: CardEntity,
    )
}
