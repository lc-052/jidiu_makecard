package com.yike.ai

import androidx.room.Room
import com.yike.ai.core.database.*
import com.yike.ai.data.repository.StudyRepository
import com.yike.ai.domain.usecase.GetReviewQueue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    AppDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun notesCardsReviewCascade() = runBlocking {
        val repo = StudyRepository(db)
        val deck = DeckEntity(name = "中文牌组")
        db.study().putDeck(deck)
        val note =
            NoteEntity(
                deckId = deck.id,
                noteType = "CLOZE",
                clozeText = "{{c1::甲}} {{c2::乙}} {{c1::丙}}",
            )
        repo.saveNote(note)
        assertEquals(2, db.study().allCards().size)
        val now = System.currentTimeMillis() + 1000
        val card = db.study().allCards().first()
        val log = repo.rate(card.id, 4, 0.9, 1200, now)
        assertEquals(1, db.study().allLogs().size)
        assertEquals("REVIEW", db.study().card(card.id)!!.state)
        assertTrue(db.study().card(card.id)!!.dueAt > now)
        repo.undo(log.id)
        assertEquals(0, db.study().allLogs().size)
        assertEquals("NEW", db.study().card(card.id)!!.state)
        assertEquals(2, db.study().deckStats(now).first().first().dueCount)
        db.study().deleteDeck(deck.id)
        assertTrue(db.study().allCards().isEmpty())
        assertTrue(db.study().allNotes().isEmpty())
    }

    @Test
    fun editingKeepsSchedulingAndRemovesOrdinals() = runBlocking {
        val repo = StudyRepository(db)
        val d = DeckEntity(name = "d")
        db.study().putDeck(d)
        val n = NoteEntity(deckId = d.id, noteType = "CLOZE", clozeText = "{{c1::a}} {{c2::b}}")
        repo.saveNote(n)
        val c = db.study().noteCards(n.id).first { it.cardOrdinal == 0 }
        repo.rate(c.id, 4, 0.9, 100)
        repo.saveNote(n.copy(clozeText = "{{c1::a}}"))
        val kept = db.study().noteCards(n.id)
        assertEquals(1, kept.size)
        assertEquals(c.id, kept.first().id)
        assertEquals(1, kept.first().reps)
    }

    @Test
    fun queueHonorsNewAndReviewLimits() = runBlocking {
        val repo = StudyRepository(db)
        val d = DeckEntity(name = "d")
        db.study().putDeck(d)
        repeat(4) { repo.saveNote(NoteEntity(deckId = d.id, front = "Q$it", back = "A")) }
        assertEquals(2, GetReviewQueue(repo)(null, 2, 200).size)
        assertEquals(0, GetReviewQueue(repo)(null, 0, 200).size)
    }

    @Test
    fun damagedBackupDoesNotEraseOriginal() = runBlocking {
        val repo = StudyRepository(db)
        val d = DeckEntity(name = "原数据")
        db.study().putDeck(d)
        val broken =
            Backup(
                decks = emptyList(),
                notes = listOf(NoteEntity(deckId = "missing", front = "q", back = "a")),
                cards = emptyList(),
                reviews = emptyList(),
            )
        try {
            repo.restore(broken)
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("原数据", db.study().allDecks().single().name)
        }
    }

    @Test
    fun backupRoundTripHierarchy() = runBlocking {
        val repo = StudyRepository(db)
        val d = DeckEntity(name = "父")
        val child = DeckEntity(name = "子", parentDeckId = d.id)
        db.study().putDeck(d)
        db.study().putDeck(child)
        repo.saveNote(NoteEntity(deckId = child.id, front = "中文<&>", back = "答案"))
        val backup = repo.backup()
        repo.restore(backup)
        assertEquals(backup, repo.backup())
    }
}
