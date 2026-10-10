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
class MigrationTest {
    @Test
    fun versionOneToTwoPreservesDeck() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema =
            codec
                .parseToJsonElement(javaClass.classLoader!!.getResource("db-v1.json")!!.readText())
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
            db.execSQL("INSERT INTO decks VALUES ('d','迁移保留数据','',NULL,0,1,1)")
            db.version = 1
        }
        val room =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
        try {
            assertEquals("迁移保留数据", room.study().allDecks().single().name)
            assertEquals(3, room.openHelper.writableDatabase.version)
        } finally {
            room.close()
        }
    }
}
