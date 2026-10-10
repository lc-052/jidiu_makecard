package com.yike.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yike.ai.core.database.DeckEntity
import com.yike.ai.core.database.NoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run seed, force-stop the app, launch it, then run verify in a second instrumentation process. */
@RunWith(AndroidJUnit4::class)
class ProcessPersistenceTest {
    @Test
    fun retainsAuthoredCardsAcrossSeparateProcesses(): Unit = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("persistenceStage")
        assumeTrue(stage == "seed" || stage == "verify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = (context.applicationContext as YikeApplication).container
        val deckId = "process-persistence-fixture"
        val noteId = "process-persistence-note"
        if (stage == "seed") {
            app.db.study().putDeck(DeckEntity(id = deckId, name = "进程重启验证"))
            app.study.saveNote(
                NoteEntity(
                    id = noteId,
                    deckId = deckId,
                    noteType = "CLOZE",
                    clozeText = "**重点**：{{c1::甲}} 与 {{c2::乙::提示}}",
                    explanation = "解释与答案分开展示。",
                    tags = "重启验证 中文",
                )
            )
        } else {
            val note = app.db.study().allNotes().single { it.id == noteId }
            assertEquals("**重点**：{{c1::甲}} 与 {{c2::乙::提示}}", note.clozeText)
            assertEquals("解释与答案分开展示。", note.explanation)
            assertEquals("重启验证 中文", note.tags)
            val cards = app.db.study().noteCards(noteId)
            assertEquals(2, cards.size)
            assertEquals(setOf(0, 1), cards.map { it.cardOrdinal }.toSet())
            assertTrue(app.db.study().allLogs().none { log -> cards.any { it.id == log.cardId } })
            app.db.study().deleteDeck(deckId)
        }
    }
}
