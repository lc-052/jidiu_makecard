package com.yike.ai

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.yike.ai.core.database.*
import com.yike.ai.domain.model.codec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemplateMigrationTest {
    @Test
    fun versionTwoPreservesSchedulingAndTemplateEdits() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "template-migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema =
            codec
                .parseToJsonElement(javaClass.classLoader!!.getResource("db-v2.json")!!.readText())
                .jsonObject["database"]!!
                .jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema["entities"]!!.jsonArray.forEach { entity ->
                val e = entity.jsonObject
                db.execSQL(
                    e["createSql"]!!
                        .jsonPrimitive
                        .content
                        .replace("\${TABLE_NAME}", e["tableName"]!!.jsonPrimitive.content)
                )
                e["indices"]?.jsonArray?.forEach {
                    db.execSQL(
                        it.jsonObject["createSql"]!!
                            .jsonPrimitive
                            .content
                            .replace("\${TABLE_NAME}", e["tableName"]!!.jsonPrimitive.content)
                    )
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO decks VALUES ('d','旧牌组','',NULL,0,1,1)")
            db.execSQL(
                "INSERT INTO notes VALUES ('n','d','BASIC','旧问题','旧答案','','', '', NULL, '', NULL,1,1)"
            )
            db.execSQL(
                "INSERT INTO cards VALUES ('c','n',0,999999,'REVIEW',0,42.5,6.7,3,25,12,2,888888,0)"
            )
            db.execSQL("INSERT INTO reviews VALUES ('r','c',3,888888,'{}','{}',1500)")
            db.version = 2
        }
        val room =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
        try {
            val dao = room.study()
            val old = dao.card("c")!!
            assertEquals(42.5, old.stability, 0.0)
            assertEquals(999999L, old.dueAt)
            assertEquals(12, old.reps)
            assertNull(dao.note("n")!!.templateId)
            assertNull(dao.deck("d")!!.templateId)
            StudyRepositoryForTest(room).save()
            assertEquals(old, dao.card("c"))
            assertEquals(1, dao.allLogs().size)
            assertEquals("旧答案", dao.note("n")!!.back)
        } finally {
            room.close()
        }
    }

    private class StudyRepositoryForTest(val db: AppDatabase) {
        suspend fun save() {
            com.yike.ai.data.repository
                .StudyRepository(db)
                .saveNote(db.study().note("n")!!.copy(templateId = "FOREST"))
            db.study().putDeck(db.study().deck("d")!!.copy(templateId = "PAPER"))
        }
    }
}
