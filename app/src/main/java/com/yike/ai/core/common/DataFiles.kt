package com.yike.ai.core.common

import com.yike.ai.core.database.Backup
import com.yike.ai.core.database.NoteEntity
import com.yike.ai.domain.model.codec
import java.io.OutputStream
import kotlinx.serialization.encodeToString

object DataFiles {
    fun backup(b: Backup, out: OutputStream) =
        out.bufferedWriter().use { it.write(codec.encodeToString(b)) }

    fun csv(notes: List<NoteEntity>, out: OutputStream) {
        fun cell(s: String): String {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF类型,问题,答案,挖空,解释,标签,章节\r\n")
            notes.forEach { n ->
                writer.write(
                    listOf(
                            n.noteType,
                            n.front,
                            n.back,
                            n.clozeText,
                            n.explanation,
                            n.tags,
                            n.sourceSection,
                        )
                        .joinToString(",") { cell(it) } + "\r\n"
                )
            }
        }
    }
}
