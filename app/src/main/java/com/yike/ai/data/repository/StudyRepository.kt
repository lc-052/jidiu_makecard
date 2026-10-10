package com.yike.ai.data.repository

import androidx.room.withTransaction
import com.yike.ai.core.common.CardRules
import com.yike.ai.core.database.*
import com.yike.ai.core.fsrs.FsrsScheduler
import com.yike.ai.domain.model.*
import kotlinx.serialization.encodeToString

class StudyRepository(val db: AppDatabase) {
    val dao = db.study()

    suspend fun saveNote(n: NoteEntity) =
        db.withTransaction {
            CardRules.validate(
                Candidate(
                    type = n.noteType,
                    front = n.front,
                    back = n.back,
                    clozeText = n.clozeText,
                )
            )
            require(dao.allDecks().any { it.id == n.deckId }) { "牌组不存在" }
            dao.putNote(n.copy(updatedAt = System.currentTimeMillis()))
            dao.deck(n.deckId)?.let { dao.putDeck(it.copy(updatedAt = System.currentTimeMillis())) }
            val ords = if (n.noteType == "CLOZE") CardRules.ordinals(n.clozeText) else listOf(0)
            val old = dao.noteCards(n.id)
            old.filter { it.cardOrdinal !in ords }.forEach { dao.deleteCard(it.id) }
            ords
                .filter { ord -> old.none { it.cardOrdinal == ord } }
                .forEach { dao.putCard(CardEntity(noteId = n.id, cardOrdinal = it)) }
        }

    suspend fun rate(
        cardId: String,
        rating: Int,
        retention: Double,
        elapsed: Long,
        now: Long = System.currentTimeMillis(),
    ): ReviewLogEntity =
        db.withTransaction {
            val previous = dao.card(cardId) ?: error("卡片不存在")
            val next = FsrsScheduler(retention).schedule(previous, rating, now)
            val log =
                ReviewLogEntity(
                    cardId = cardId,
                    rating = rating,
                    reviewedAt = now,
                    previousState = codec.encodeToString(previous),
                    nextState = codec.encodeToString(next),
                    elapsedTimeMs = elapsed.coerceIn(0, 600_000),
                )
            dao.putCard(next)
            dao.putLog(log)
            dao.note(previous.noteId)?.let { note ->
                dao.deck(note.deckId)?.let { dao.putDeck(it.copy(updatedAt = now)) }
            }
            log
        }

    suspend fun undo(expectedId: String): CardEntity =
        db.withTransaction {
            val log = dao.lastLog() ?: error("没有可撤销的评分")
            require(log.id == expectedId) { "已有后续评分，无法撤销此记录" }
            val old = codec.decodeFromString<CardEntity>(log.previousState)
            require(dao.card(old.id) != null) { "卡片已删除" }
            dao.putCard(old)
            dao.deleteLog(log.id)
            old
        }

    suspend fun backup(): Backup =
        db.withTransaction {
            Backup(
                decks = dao.allDecks(),
                notes = dao.allNotes(),
                cards = dao.allCards(),
                reviews = dao.allLogs(),
            )
        }

    suspend fun restore(b: Backup) {
        require(b.format == "yike-backup" && b.version == 1) { "不支持的备份版本" }
        val decks = b.decks.map { it.id }.toSet()
        val notes = b.notes.map { it.id }.toSet()
        val cards = b.cards.map { it.id }.toSet()
        require(
            decks.size == b.decks.size &&
                notes.size == b.notes.size &&
                cards.size == b.cards.size &&
                b.reviews.map { it.id }.toSet().size == b.reviews.size
        ) {
            "备份含重复标识"
        }
        require(
            b.notes.all { it.deckId in decks } &&
                b.cards.all { it.noteId in notes } &&
                b.reviews.all { it.cardId in cards }
        ) {
            "备份关联损坏"
        }
        b.notes.forEach {
            CardRules.validate(
                Candidate(
                    type = it.noteType,
                    front = it.front,
                    back = it.back,
                    clozeText = it.clozeText,
                )
            )
        }
        b.cards.forEach {
            require(
                it.stability.isFinite() &&
                    it.stability >= 0 &&
                    it.difficulty.isFinite() &&
                    it.state in listOf("NEW", "LEARNING", "REVIEW", "RELEARNING")
            ) {
                "备份调度数据无效"
            }
        }
        b.reviews.forEach { log ->
            require(log.rating in 1..4 && log.elapsedTimeMs in 0..600_000) { "复习记录无效" }
            val previous = codec.decodeFromString<CardEntity>(log.previousState)
            val next = codec.decodeFromString<CardEntity>(log.nextState)
            require(
                previous.id == log.cardId &&
                    next.id == log.cardId &&
                    previous.noteId == next.noteId &&
                    next.reps == previous.reps + 1
            ) {
                "复习快照损坏"
            }
        }
        val pending = b.decks.toMutableList()
        val ordered = mutableListOf<DeckEntity>()
        while (pending.isNotEmpty()) {
            val available =
                pending.filter {
                    it.parentDeckId == null || ordered.any { d -> d.id == it.parentDeckId }
                }
            require(available.isNotEmpty()) { "牌组层级包含循环或失效引用" }
            ordered += available
            pending.removeAll(available.toSet())
        }
        db.withTransaction {
            dao.clearStudy()
            ordered.forEach { dao.putDeck(it) }
            b.notes.forEach { dao.putNote(it) }
            b.cards.forEach { dao.putCard(it) }
            b.reviews.forEach { dao.putLog(it) }
        }
    }
}
