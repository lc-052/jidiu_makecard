package com.yike.ai

import android.database.sqlite.SQLiteDatabase
import com.yike.ai.core.anki.ApkgExporter
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.CardTemplateRepository
import java.io.*
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemplateExportTest {
    @Test
    fun mixedTemplatesShareStableModelsAndDoNotAlterContent() {
        val context = RuntimeEnvironment.getApplication()
        val deck = DeckEntity(id = "visual-deck", name = "六模板", templateId = "FOREST")
        val notes =
            CardTemplateRepository.all.flatMap { d ->
                listOf(
                    NoteEntity(
                        id = "${d.id}-basic",
                        deckId = deck.id,
                        front = "${d.name}问题",
                        back = "**答案**\n\n- 第一项\n- 第二项",
                        templateId = d.id.name,
                    ),
                    NoteEntity(
                        id = "${d.id}-cloze",
                        deckId = deck.id,
                        noteType = "CLOZE",
                        clozeText = "{{c1::甲}} 与 {{c2::乙}}",
                        templateId = d.id.name,
                    ),
                )
            }
        fun extract(input: ByteArray): File {
            val file = File.createTempFile("template-test-", ".sqlite", context.cacheDir)
            ZipInputStream(ByteArrayInputStream(input)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == "collection.anki2") file.writeBytes(zip.readBytes())
                    e = zip.nextEntry
                }
            }
            return file
        }
        val out = ByteArrayOutputStream()
        ApkgExporter(context).export(listOf(deck), notes, out)
        val file = extract(out.toByteArray())
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT count(distinct mid),count(*) FROM notes", null).use {
                    it.moveToFirst()
                    assertEquals(2, it.getInt(0))
                    assertEquals(12, it.getInt(1))
                }
                notes.forEach { n ->
                    db.rawQuery(
                            "SELECT flds FROM notes WHERE id=${ApkgExporter.stableId("note:${n.id}")}",
                            null,
                        )
                        .use {
                            it.moveToFirst()
                            assertTrue(it.getString(0).contains("jt-${n.templateId!!.lowercase()}"))
                            if (n.noteType == "CLOZE")
                                assertTrue(it.getString(0).contains("{{c2::乙}}"))
                        }
                }
            }
        } finally {
            file.delete()
        }
        val switched =
            notes.map { if (it.id == notes.first().id) it.copy(templateId = "PAPER") else it }
        val changed = ByteArrayOutputStream()
        ApkgExporter(context).export(listOf(deck), switched, changed)
        val second = extract(changed.toByteArray())
        try {
            SQLiteDatabase.openDatabase(second.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                        "SELECT id,mid,flds FROM notes WHERE id=${ApkgExporter.stableId("note:${notes.first().id}")}",
                        null,
                    )
                    .use {
                        it.moveToFirst()
                        assertEquals(
                            ApkgExporter.stableId("note:${notes.first().id}"),
                            it.getLong(0),
                        )
                        assertTrue(it.getString(2).contains("jt-paper"))
                    }
            }
        } finally {
            second.delete()
        }
    }
}
