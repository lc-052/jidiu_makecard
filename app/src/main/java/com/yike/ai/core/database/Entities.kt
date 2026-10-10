package com.yike.ai.core.database

import androidx.room.*
import java.util.UUID
import kotlinx.serialization.Serializable

fun uid(): String = UUID.randomUUID().toString()

@Serializable
@Entity(
    tableName = "decks",
    indices = [Index("parentDeckId")],
    foreignKeys =
        [
            ForeignKey(
                entity = DeckEntity::class,
                parentColumns = ["id"],
                childColumns = ["parentDeckId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class DeckEntity(
    @PrimaryKey val id: String = uid(),
    val name: String,
    val description: String = "",
    val parentDeckId: String? = null,
    val color: Long = 0xFF6C5CE7,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "NULL") val templateId: String? = null,
)

@Serializable
@Entity(
    tableName = "notes",
    indices = [Index("deckId"), Index("sourceDocumentId"), Index(value = ["deckId", "noteType"])],
    foreignKeys =
        [
            ForeignKey(
                entity = DeckEntity::class,
                parentColumns = ["id"],
                childColumns = ["deckId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class NoteEntity(
    @PrimaryKey val id: String = uid(),
    val deckId: String,
    val noteType: String = "BASIC",
    val front: String = "",
    val back: String = "",
    val clozeText: String = "",
    val explanation: String = "",
    val tags: String = "",
    val sourceDocumentId: String? = null,
    val sourceSection: String = "",
    val sourcePage: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "NULL") val templateId: String? = null,
)

@Serializable
@Entity(
    tableName = "cards",
    indices =
        [
            Index("noteId"),
            Index(value = ["noteId", "cardOrdinal"], unique = true),
            Index(value = ["dueAt", "suspended", "state"]),
        ],
    foreignKeys =
        [
            ForeignKey(
                entity = NoteEntity::class,
                parentColumns = ["id"],
                childColumns = ["noteId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class CardEntity(
    @PrimaryKey val id: String = uid(),
    val noteId: String,
    val cardOrdinal: Int = 0,
    val dueAt: Long = System.currentTimeMillis(),
    val state: String = "NEW",
    val step: Int = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Double = 0.0,
    val scheduledDays: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewAt: Long? = null,
    val suspended: Boolean = false,
)

@Serializable
@Entity(
    tableName = "reviews",
    indices = [Index("cardId"), Index("reviewedAt")],
    foreignKeys =
        [
            ForeignKey(
                entity = CardEntity::class,
                parentColumns = ["id"],
                childColumns = ["cardId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String = uid(),
    val cardId: String,
    val rating: Int,
    val reviewedAt: Long,
    val previousState: String,
    val nextState: String,
    val elapsedTimeMs: Long,
)

@Serializable
@Entity(tableName = "documents", indices = [Index("checksum")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val filename: String,
    val mimeType: String,
    val checksum: String,
    val createdAt: Long = System.currentTimeMillis(),
    val parseStatus: String = "PARSED",
    val metadata: String = "",
)

@Serializable
@Entity(tableName = "generation_tasks", indices = [Index("documentId")])
data class GenerationTaskEntity(
    @PrimaryKey val id: String = uid(),
    val documentId: String,
    val providerId: String,
    val modelId: String,
    val configuration: String,
    val status: String = "QUEUED",
    val progress: Int = 0,
    val currentSection: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "'{}'") val metrics: String = "{}",
)

@Serializable
@Entity(
    tableName = "drafts",
    indices =
        [Index("taskId"), Index(value = ["taskId", "chunkIndex", "candidateId"], unique = true)],
    foreignKeys =
        [
            ForeignKey(
                entity = GenerationTaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class GenerationDraftEntity(
    @PrimaryKey val id: String = uid(),
    val taskId: String,
    val chunkIndex: Int,
    val candidateId: String,
    val payload: String,
    val selected: Boolean = true,
    val saved: Boolean = false,
)

@Serializable
@Entity(
    tableName = "chunks",
    primaryKeys = ["taskId", "ordinal"],
    foreignKeys =
        [
            ForeignKey(
                entity = GenerationTaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ChunkEntity(
    val taskId: String,
    val ordinal: Int,
    val section: String,
    val page: Int?,
    val text: String,
    val targetCount: Int,
    val completed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val analysis: String = "",
    @ColumnInfo(defaultValue = "NULL") val error: String? = null,
)

@Serializable
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String = uid(),
    val name: String = "OpenAI",
    val kind: String = "OPENAI",
    val baseUrl: String = "https://api.openai.com/v1",
    val modelId: String = "",
    val protocol: String = "CHAT",
    val timeoutSeconds: Int = 90,
    val maxTokens: Int = 4000,
    val temperature: Double? = null,
    val strictSchema: Boolean = true,
    val streaming: Boolean = false,
    val isDefault: Boolean = false,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    @ColumnInfo(defaultValue = "2") val concurrency: Int = 2,
)

data class DeckStats(
    @Embedded val deck: DeckEntity,
    val noteCount: Int,
    val cardCount: Int,
    val dueCount: Int,
    val learnedCount: Int,
)

data class ReviewItem(
    @Embedded val card: CardEntity,
    @Relation(parentColumn = "noteId", entityColumn = "id") val note: NoteEntity,
)

@Serializable
data class Backup(
    val format: String = "yike-backup",
    val version: Int = 1,
    val decks: List<DeckEntity>,
    val notes: List<NoteEntity>,
    val cards: List<CardEntity>,
    val reviews: List<ReviewLogEntity>,
)
