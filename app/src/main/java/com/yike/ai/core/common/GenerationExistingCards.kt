package com.yike.ai.core.common

import com.yike.ai.core.database.AppDatabase
import com.yike.ai.domain.model.*

/** Shared by planning and the worker; only cards from this deck become duplicate blockers. */
object GenerationExistingCards {
    suspend fun load(
        db: AppDatabase,
        hash: String,
        deckId: String,
        excludingTask: String? = null,
    ): List<Candidate> {
        val notes =
            db.study().notesForGeneration(deckId).map {
                Candidate(
                    id = it.id,
                    type = it.noteType,
                    front = it.front,
                    back = it.back,
                    clozeText = it.clozeText,
                    sourceSection =
                        if (
                            it.sourceDocumentId != null &&
                                db.generation().document(it.sourceDocumentId)?.checksum == hash
                        )
                            it.sourceSection
                        else "",
                )
            }
        val related =
            db.generation()
                .tasksForSource(hash)
                .filter {
                    it.id != excludingTask &&
                        runCatching {
                                codec.decodeFromString<GenerationConfig>(it.configuration).deckId ==
                                    deckId
                            }
                            .getOrDefault(false)
                }
                .flatMap { task ->
                    db.generation()
                        .allDrafts(task.id)
                        .filter { it.saved || it.selected }
                        .mapNotNull {
                            runCatching { codec.decodeFromString<Candidate>(it.payload) }
                                .getOrNull()
                        }
                }
        return CardRules.deduplicate(notes + related)
    }

    fun sectionCounts(cards: List<Candidate>): Map<String, Int> =
        cards
            .flatMap { it.sourceSection.lines().filter { name -> name.isNotBlank() }.distinct() }
            .groupingBy { it }
            .eachCount()
}
