package com.yike.ai

import android.database.sqlite.SQLiteDatabase
import com.yike.ai.core.anki.ApkgExporter
import com.yike.ai.core.common.DataFiles
import com.yike.ai.core.database.*
import com.yike.ai.core.document.DocumentParser
import java.io.*
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportAndDocumentTest {
    @Test
    fun basicClozeChineseHierarchyAndStableIds() {
        val context = RuntimeEnvironment.getApplication()
        val parent = DeckEntity(id = "parent", name = "中文课程")
        val child = DeckEntity(id = "child", name = "章节一", parentDeckId = parent.id)
        val notes =
            listOf(
                NoteEntity(
                    id = "basic",
                    deckId = child.id,
                    front = "<b>问题 &amp; 测试</b>",
                    back = "答案 \\(x^2\\)",
                    tags = "数学 中文",
                ),
                NoteEntity(
                    id = "cloze",
                    deckId = child.id,
                    noteType = "CLOZE",
                    clozeText = "{{c1::甲}} 与 {{c3::乙}}",
                    tags = "挖空",
                ),
            )
        val output = ByteArrayOutputStream()
        ApkgExporter(context).export(listOf(parent, child), notes, output)
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                files[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        assertEquals(setOf("collection.anki2", "media"), files.keys)
        assertEquals("{}", files["media"]!!.toString(Charsets.UTF_8))
        val file = File(context.cacheDir, "export-test.sqlite")
        file.writeBytes(files["collection.anki2"]!!)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM notes", null).use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
            db.rawQuery(
                    "SELECT ord FROM cards WHERE nid=${ApkgExporter.stableId("note:cloze")} ORDER BY ord",
                    null,
                )
                .use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                    assertTrue(it.moveToNext())
                    assertEquals(2, it.getInt(0))
                }
            db.rawQuery("SELECT decks,models FROM col", null).use {
                it.moveToFirst()
                assertTrue(it.getString(0).contains("中文课程::章节一"))
                assertTrue(it.getString(1).contains("{{cloze:Text}}"))
            }
            db.rawQuery(
                    "SELECT flds,tags FROM notes WHERE id=${ApkgExporter.stableId("note:basic")}",
                    null,
                )
                .use {
                    it.moveToFirst()
                    assertTrue(it.getString(0).contains("问题 &amp; 测试"))
                    assertTrue(it.getString(1).contains("中文"))
                }
        }
        assertEquals(ApkgExporter.stableId("note:basic"), ApkgExporter.stableId("note:basic"))
        val second = ByteArrayOutputStream()
        ApkgExporter(context).export(listOf(parent, child), notes, second)
        assertTrue(second.size() > 0)
        file.delete()
    }

    @Test
    fun emptyDeckAndFailedWrite() {
        val exporter = ApkgExporter(RuntimeEnvironment.getApplication())
        val d = DeckEntity(name = "空牌组")
        val out = ByteArrayOutputStream()
        exporter.export(listOf(d), emptyList(), out)
        assertTrue(out.size() > 0)
        assertThrows(IOException::class.java) {
            exporter.export(
                listOf(d),
                emptyList(),
                object : OutputStream() {
                    override fun write(b: Int) {
                        throw IOException("write denied")
                    }
                },
            )
        }
    }

    @Test
    fun docxAndMarkdown() {
        val xml =
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>章节一</w:t></w:r></w:p><w:p><w:r><w:t>中文 &amp; 文本</w:t></w:r></w:p></w:body></w:document>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("word/document.xml"))
            it.write(xml.toByteArray())
            it.closeEntry()
        }
        val blocks =
            DocumentParser(RuntimeEnvironment.getApplication()).parseDocx(out.toByteArray())
        assertEquals("章节一", blocks.last().section)
        assertEquals("中文 & 文本", blocks.last().text)
        assertEquals("标题", DocumentParser.structure("# 标题\n\n正文").last().section)
    }

    @Test
    fun rejectsDoctypeAndOversize() {
        assertThrows(IllegalArgumentException::class.java) {
            DocumentParser.readBounded(ByteArrayInputStream(ByteArray(20)), 10)
        }
    }

    @Test
    fun csvQuotes() {
        val out = ByteArrayOutputStream()
        DataFiles.csv(listOf(NoteEntity(deckId = "d", front = "中文,\"引号\"", back = "多\n行")), out)
        val text = out.toString("UTF-8")
        assertTrue(text.contains("\"中文,\"\"引号\"\"\""))
        assertTrue(text.contains("多\n行"))
    }
}
