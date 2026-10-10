package com.yike.ai.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query(
        "SELECT d.*, (SELECT count(*) FROM notes n WHERE n.deckId=d.id) AS noteCount, (SELECT count(*) FROM cards c JOIN notes n ON c.noteId=n.id WHERE n.deckId=d.id) AS cardCount, (SELECT count(*) FROM cards c JOIN notes n ON c.noteId=n.id WHERE n.deckId=d.id AND c.dueAt<=:now AND c.suspended=0) AS dueCount, (SELECT count(*) FROM cards c JOIN notes n ON c.noteId=n.id WHERE n.deckId=d.id AND c.reps>0) AS learnedCount FROM decks d ORDER BY updatedAt DESC"
    )
    fun deckStats(now: Long): Flow<List<DeckStats>>

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC") fun decks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM notes WHERE deckId=:deckId ORDER BY updatedAt DESC")
    fun notes(deckId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deckId=:deckId")
    suspend fun notesForGeneration(deckId: String): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun noteList(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id=:id") suspend fun note(id: String): NoteEntity?

    @Query("SELECT * FROM decks WHERE id=:id") suspend fun deck(id: String): DeckEntity?

    @Query("SELECT * FROM reviews ORDER BY reviewedAt DESC") fun logs(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM cards") fun cards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM decks") suspend fun allDecks(): List<DeckEntity>

    @Query("SELECT * FROM notes") suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT * FROM cards") suspend fun allCards(): List<CardEntity>

    @Query("SELECT * FROM reviews") suspend fun allLogs(): List<ReviewLogEntity>

    @Query("SELECT * FROM cards WHERE id=:id") suspend fun card(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE noteId=:id")
    suspend fun noteCards(id: String): List<CardEntity>

    @Transaction
    @Query(
        "SELECT c.* FROM cards c JOIN notes n ON n.id=c.noteId WHERE c.suspended=0 AND c.dueAt<=:now AND (:deckId IS NULL OR n.deckId=:deckId) ORDER BY CASE WHEN c.state='NEW' THEN 1 ELSE 0 END, c.dueAt"
    )
    suspend fun due(now: Long, deckId: String?): List<ReviewItem>

    @Query("SELECT * FROM reviews ORDER BY reviewedAt DESC LIMIT 1")
    suspend fun lastLog(): ReviewLogEntity?

    @Upsert suspend fun putDeck(deck: DeckEntity)

    @Upsert suspend fun putNote(note: NoteEntity)

    @Upsert suspend fun putCard(card: CardEntity)

    @Insert suspend fun putLog(log: ReviewLogEntity)

    @Query("DELETE FROM reviews WHERE id=:id") suspend fun deleteLog(id: String)

    @Query("DELETE FROM decks WHERE id=:id") suspend fun deleteDeck(id: String)

    @Query("DELETE FROM notes WHERE id IN (:ids)") suspend fun deleteNotes(ids: List<String>)

    @Query("DELETE FROM cards WHERE id=:id") suspend fun deleteCard(id: String)

    @Query("UPDATE notes SET deckId=:deck, updatedAt=:now WHERE id IN (:ids)")
    suspend fun moveNotes(ids: List<String>, deck: String, now: Long)

    @Query("DELETE FROM decks") suspend fun clearStudy()
}

@Dao
interface GenerationDao {
    @Query("SELECT * FROM chunks WHERE taskId=:id ORDER BY ordinal")
    fun observeChunks(id: String): Flow<List<ChunkEntity>>

    @Query("UPDATE generation_tasks SET metrics=:metrics WHERE id=:id")
    suspend fun updateMetrics(id: String, metrics: String)

    @Query(
        "UPDATE generation_tasks SET status=:status,progress=:progress,currentSection=:section,errorMessage=:error,updatedAt=:now WHERE id=:id AND status!='CANCELLED'"
    )
    suspend fun updateStage(
        id: String,
        status: String,
        progress: Int,
        section: String,
        error: String?,
        now: Long,
    )

    @Query("SELECT COUNT(*) FROM drafts WHERE saved=0") fun pendingDraftCount(): Flow<Int>

    @Query("SELECT * FROM providers ORDER BY isDefault DESC,name")
    fun providers(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id=:id") suspend fun provider(id: String): ProviderEntity?

    @Upsert suspend fun putProvider(p: ProviderEntity)

    @Query("DELETE FROM providers WHERE id=:id") suspend fun deleteProvider(id: String)

    @Query("UPDATE providers SET isDefault=(id=:id)") suspend fun setDefault(id: String)

    @Query(
        "UPDATE providers SET inputTokens=inputTokens+:input,outputTokens=outputTokens+:output WHERE id=:id"
    )
    suspend fun addUsage(id: String, input: Long, output: Long)

    @Upsert suspend fun putDocument(d: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id=:id") suspend fun document(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE checksum=:checksum LIMIT 1")
    suspend fun documentByHash(checksum: String): DocumentEntity?

    @Query("SELECT * FROM generation_tasks ORDER BY createdAt DESC")
    fun tasks(): Flow<List<GenerationTaskEntity>>

    @Query("SELECT * FROM generation_tasks WHERE id=:id")
    suspend fun task(id: String): GenerationTaskEntity?

    @Query(
        "SELECT t.* FROM generation_tasks t JOIN documents d ON d.id=t.documentId WHERE d.checksum=:hash"
    )
    suspend fun tasksForSource(hash: String): List<GenerationTaskEntity>

    @Upsert suspend fun putTask(t: GenerationTaskEntity)

    @Upsert suspend fun putChunk(c: ChunkEntity)

    @Query("SELECT * FROM chunks WHERE taskId=:id ORDER BY ordinal")
    suspend fun chunks(id: String): List<ChunkEntity>

    @Query("SELECT * FROM drafts WHERE taskId=:id ORDER BY chunkIndex,id")
    fun drafts(id: String): Flow<List<GenerationDraftEntity>>

    @Query("SELECT * FROM drafts WHERE taskId=:id")
    suspend fun allDrafts(id: String): List<GenerationDraftEntity>

    @Upsert suspend fun putDraft(d: GenerationDraftEntity)

    @Query("DELETE FROM drafts WHERE id=:id") suspend fun deleteDraft(id: String)

    @Query("DELETE FROM generation_tasks WHERE id=:id") suspend fun deleteTask(id: String)
}

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chunks ADD COLUMN analysis TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_notes_deckId_noteType ON notes(deckId,noteType)"
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE decks ADD COLUMN templateId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE notes ADD COLUMN templateId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE generation_tasks ADD COLUMN metrics TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE chunks ADD COLUMN error TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE providers ADD COLUMN concurrency INTEGER NOT NULL DEFAULT 2")
        }
    }

@Database(
    entities =
        [
            DeckEntity::class,
            NoteEntity::class,
            CardEntity::class,
            ReviewLogEntity::class,
            DocumentEntity::class,
            GenerationTaskEntity::class,
            GenerationDraftEntity::class,
            ChunkEntity::class,
            ProviderEntity::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun study(): StudyDao

    abstract fun generation(): GenerationDao
}
