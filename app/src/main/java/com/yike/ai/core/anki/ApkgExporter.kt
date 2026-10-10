package com.yike.ai.core.anki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yike.ai.core.common.CardFormatting
import com.yike.ai.core.common.CardRules
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.CardTemplateRepository
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*

/** Legacy v11 deck package, based on genanki MIT schema. Exports content as new cards. */
class ApkgExporter(private val context: Context) {
    fun export(
        decks: List<DeckEntity>,
        notes: List<NoteEntity>,
        output: OutputStream,
        hierarchyDecks: List<DeckEntity> = decks,
        defaultTemplate: String = "NEON",
        templateUpdatedAt: Long = 0,
    ) {
        require(decks.isNotEmpty()) { "没有牌组可导出" }
        val file = File.createTempFile("anki-", ".sqlite", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.beginTransaction()
                try {
                    listOf("apkg_schema", "apkg_col").forEach { name ->
                        val sql =
                            context.assets.open("anki/$name.sql").bufferedReader().use {
                                it.readText().removePrefix("\uFEFF")
                            }
                        sql.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                    }
                    val now = System.currentTimeMillis() / 1000
                    val deckJson = buildJsonObject {
                        put("1", deck(1, "Default", "", now))
                        decks.forEach { d ->
                            put(
                                stableId("deck:${d.id}").toString(),
                                deck(
                                    stableId("deck:${d.id}"),
                                    hierarchy(d, hierarchyDecks),
                                    d.description,
                                    now,
                                ),
                            )
                        }
                    }
                    val modelJson = buildJsonObject {
                        put(BASIC.toString(), model(BASIC, false, now))
                        put(CLOZE.toString(), model(CLOZE, true, now))
                    }
                    db.execSQL(
                        "UPDATE col SET crt=?,mod=?,scm=?,models=?,decks=?",
                        arrayOf<Any>(
                            now,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            modelJson.toString(),
                            deckJson.toString(),
                        ),
                    )
                    var position = 1
                    for (n in notes) {
                        require(decks.any { it.id == n.deckId }) { "笔记引用了未导出的牌组" }
                        val isCloze = n.noteType == "CLOZE"
                        val fields =
                            if (isCloze) listOf(n.clozeText, n.explanation)
                            else listOf(n.front, n.back, n.explanation)
                        val template =
                            CardTemplateRepository.resolve(
                                    n.templateId,
                                    decks.find { it.id == n.deckId }?.templateId,
                                    defaultTemplate,
                                )
                                .id
                                .name
                        val formattedFields =
                            fields.map {
                                if (it.isBlank()) ""
                                else
                                    CardTemplateRepository.html(template, CardFormatting.toHtml(it))
                            }
                        val first = fields[0]
                        val stripped = first.replace(Regex("<[^>]+>"), "").trim()
                        val csum =
                            MessageDigest.getInstance("SHA-1")
                                .digest(stripped.toByteArray())
                                .take(4)
                                .fold(0L) { a, b -> (a shl 8) + (b.toInt() and 255) }
                        val nid = stableId("note:${n.id}")
                        val tags =
                            n.tags
                                .split(Regex("[,，\\s]+"))
                                .filter { it.isNotBlank() }
                                .joinToString(" ", prefix = " ", postfix = " ")
                        db.execSQL(
                            "INSERT INTO notes VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                            arrayOf<Any>(
                                nid,
                                n.id.replace("-", ""),
                                if (isCloze) CLOZE else BASIC,
                                maxOf(
                                    n.updatedAt,
                                    decks.find { it.id == n.deckId }?.updatedAt ?: 0L,
                                    templateUpdatedAt,
                                ) / 1000,
                                -1,
                                tags,
                                formattedFields.joinToString("\u001f"),
                                stripped,
                                csum,
                                0,
                                "",
                            ),
                        )
                        val ords = if (isCloze) CardRules.ordinals(n.clozeText) else listOf(0)
                        require(ords.isNotEmpty()) { "挖空笔记没有合法挖空" }
                        ords.forEach { ord ->
                            db.execSQL(
                                "INSERT INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                arrayOf<Any>(
                                    stableId("card:${n.id}:$ord"),
                                    nid,
                                    stableId("deck:${n.deckId}"),
                                    ord,
                                    now,
                                    -1,
                                    0,
                                    0,
                                    position++,
                                    0,
                                    2500,
                                    0,
                                    0,
                                    0,
                                    0,
                                    0,
                                    0,
                                    "",
                                ),
                            )
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.rawQuery("PRAGMA integrity_check", null).use {
                    require(it.moveToFirst() && it.getString(0) == "ok") { "APKG 数据库完整性检查失败" }
                }
            }
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("collection.anki2"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("media"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        } finally {
            if (file.exists()) file.delete()
        }
    }

    private fun hierarchy(
        d: DeckEntity,
        all: List<DeckEntity>,
        seen: Set<String> = emptySet(),
    ): String {
        require(d.id !in seen) { "牌组层级循环" }
        val name = d.name.replace("::", ":")
        val parent = all.find { it.id == d.parentDeckId }
        return if (parent == null) name else hierarchy(parent, all, seen + d.id) + "::" + name
    }

    private fun deck(id: Long, name: String, desc: String, now: Long) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("desc", desc)
        put("mod", now)
        put("usn", -1)
        put("dyn", 0)
        put("conf", 1)
        put("collapsed", false)
        put("extendNew", 10)
        put("extendRev", 50)
        listOf("lrnToday", "newToday", "revToday", "timeToday").forEach {
            put(it, JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0))))
        }
    }

    private fun model(id: Long, cloze: Boolean, now: Long) = buildJsonObject {
        put("id", id.toString())
        put("name", if (cloze) "寄丢 Cloze v1" else "寄丢 Basic v1")
        put("type", if (cloze) 1 else 0)
        put("mod", now)
        put("usn", -1)
        put("sortf", 0)
        put("did", 1)
        put("tags", JsonArray(emptyList()))
        put("vers", JsonArray(emptyList()))
        put("latexsvg", false)
        put(
            "latexPre",
            "\\documentclass[12pt]{article}\\usepackage{amssymb,amsmath}\\pagestyle{empty}\\begin{document}",
        )
        put("latexPost", "\\end{document}")
        put(
            "css",
            ".card{font-family:sans-serif;font-size:20px;line-height:1.7;text-align:left;padding:12px;max-width:680px;margin:auto;}p{margin:0 0 14px;}ul,ol{padding-left:24px;}li{margin-bottom:8px;}strong,.cloze{font-weight:600;}h3{font-size:21px;margin:16px 0 10px;}code{font-family:monospace;}pre{white-space:pre-wrap;} .extra{margin-top:18px;}" +
                CardTemplateRepository.css(),
        )
        put(
            "flds",
            JsonArray(
                (if (cloze) listOf("Text", "Extra") else listOf("Front", "Back", "Extra"))
                    .mapIndexed { i, name ->
                        buildJsonObject {
                            put("name", name)
                            put("ord", i)
                            put("font", "Arial")
                            put("size", 22)
                            put("rtl", false)
                            put("sticky", false)
                            put("media", JsonArray(emptyList()))
                        }
                    }
            ),
        )
        put(
            "tmpls",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("name", if (cloze) "Cloze" else "Card 1")
                        put("ord", 0)
                        put(
                            "qfmt",
                            if (cloze) "<div class='jt-front'>{{cloze:Text}}</div>"
                            else "<div class='jt-front'>{{Front}}</div>",
                        )
                        put(
                            "afmt",
                            if (cloze)
                                "<div class='jt-back'>{{cloze:Text}}{{#Extra}}<details class='extra'><summary>解释与补充</summary>{{Extra}}</details>{{/Extra}}</div>"
                            else
                                "{{FrontSide}}<hr id=answer><div class='jt-back'>{{Back}}{{#Extra}}<details class='extra'><summary>解释与补充</summary>{{Extra}}</details>{{/Extra}}</div>",
                        )
                        put("bqfmt", "")
                        put("bafmt", "")
                        put("bfont", "")
                        put("bsize", 0)
                        put("did", JsonNull)
                    }
                )
            ),
        )
        put(
            "req",
            JsonArray(
                listOf(
                    JsonArray(
                        listOf(
                            JsonPrimitive(0),
                            JsonPrimitive("all"),
                            JsonArray(listOf(JsonPrimitive(0))),
                        )
                    )
                )
            ),
        )
    }

    companion object {
        const val BASIC = 1760000000001L
        const val CLOZE = 1760000000002L

        fun stableId(value: String) =
            (ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
                .long and 0x001FFFFFFFFFFFFFL) + 1000
    }
}
