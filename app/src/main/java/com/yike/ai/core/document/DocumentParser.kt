package com.yike.ai.core.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.yike.ai.core.common.CardRules
import com.yike.ai.core.database.uid
import com.yike.ai.domain.model.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

class DocumentParser(private val context: Context) {
    fun parse(uri: Uri): DocumentContent {
        val started = System.nanoTime()
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "文档"
        resolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
            ?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) require(c.getLong(1) <= MAX_BYTES) { "文件超过 20 MB" }
                }
            }
        val bytes =
            resolver.openInputStream(uri)?.use { readBounded(it, MAX_BYTES) }
                ?: throw AppError("没有文件读取权限")
        val ext = name.substringAfterLast('.').lowercase()
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val warnings = mutableListOf<String>()
        val blocks =
            when (ext) {
                "pdf" ->
                    PDDocument.load(bytes).use { pdf ->
                        require(!pdf.isEncrypted) { "无法解析加密 PDF" }
                        require(pdf.numberOfPages <= 1000) { "PDF 页数超过 1000" }
                        val stripper = PDFTextStripper()
                        (1..pdf.numberOfPages)
                            .mapNotNull { page ->
                                stripper.startPage = page
                                stripper.endPage = page
                                CardRules.clean(stripper.getText(pdf))
                                    .takeIf { it.isNotBlank() }
                                    ?.let { TextBlock(it, "第 $page 页", page) }
                            }
                            .also {
                                if (it.size < pdf.numberOfPages)
                                    warnings += "部分页面没有文本；扫描内容需 OCR，本版不识别"
                            }
                    }
                "docx" -> parseDocx(bytes)
                "txt",
                "md",
                "markdown" -> structure(decodeText(bytes))
                else -> throw AppError("支持 PDF、DOCX、TXT、Markdown；请检查文件扩展名")
            }
        require(blocks.isNotEmpty()) { "未提取到文字。扫描 PDF 需要 OCR；请使用文本型文档" }
        val cleanStarted = System.nanoTime()
        val unique = blocks.map { it.copy(text = CardRules.clean(it.text)) }.distinctBy { it.text }
        val cleanMs = (System.nanoTime() - cleanStarted) / 1_000_000
        if (unique.size != blocks.size) warnings += "已移除 ${blocks.size-unique.size} 个完全重复文本块"
        val text = unique.joinToString("\n") { it.text }
        require(text.length <= 1_500_000) { "文档提取文本过大，请拆分文档" }
        return DocumentContent(
            uid(),
            name,
            mime,
            unique,
            text.length,
            warnings,
            System.currentTimeMillis(),
            CardRules.sha256(text),
            parseMs = (System.nanoTime() - started) / 1_000_000,
            cleanMs = cleanMs,
        )
    }

    fun paste(text: String): DocumentContent {
        val started = System.nanoTime()
        val clean = CardRules.clean(text)
        val cleanMs = (System.nanoTime() - started) / 1_000_000
        require(clean.isNotEmpty()) { "请先输入资料" }
        require(clean.length <= 1_500_000) { "文本过大" }
        val blocks = structure(clean)
        return DocumentContent(
            uid(),
            "粘贴资料",
            "text/plain",
            blocks,
            clean.length,
            emptyList(),
            System.currentTimeMillis(),
            CardRules.sha256(clean),
            parseMs = (System.nanoTime() - started) / 1_000_000,
            cleanMs = cleanMs,
        )
    }

    fun parseDocx(bytes: ByteArray): List<TextBlock> {
        var xml: ByteArray? = null
        var total = 0
        var decompressed = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(++total < 5000) { "DOCX 文件条目过多" }
                val content = readBounded(zip, MAX_BYTES - decompressed)
                decompressed += content.size
                if (entry.name == "word/document.xml") {
                    xml = content
                    break
                }
                entry = zip.nextEntry
            }
        }
        val data = xml ?: throw AppError("DOCX 缺少 word/document.xml")
        require(!data.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
            "不接受带外部实体的 XML"
        }
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), "UTF-8")
        val blocks = mutableListOf<TextBlock>()
        var paragraph = StringBuilder()
        var section = "正文"
        var heading = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG)
                when (parser.name) {
                    "p" -> {
                        paragraph = StringBuilder()
                        heading = false
                    }
                    "pStyle" -> {
                        heading =
                            (0 until parser.attributeCount).any {
                                parser.getAttributeValue(it).contains("Heading", true)
                            }
                    }
                    "t" -> paragraph.append(parser.nextText())
                    "tab" -> paragraph.append(' ')
                    "br" -> paragraph.append('\n')
                }
            if (event == XmlPullParser.END_TAG && parser.name == "p") {
                val s = CardRules.clean(paragraph.toString())
                if (s.isNotBlank()) {
                    if (heading) section = s
                    blocks += TextBlock(s, section)
                }
            }
            event = parser.next()
        }
        return blocks
    }

    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024

        fun readBounded(input: java.io.InputStream, max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= max) { "文件解压或读取超过大小限制" }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        fun decodeText(bytes: ByteArray): String =
            when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                    bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                    bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
                else ->
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes))
                        .toString()
                        .removePrefix("\uFEFF")
            }

        fun structure(text: String): List<TextBlock> {
            var section = "正文"
            return CardRules.clean(text)
                .split(Regex("\n\\s*\n"))
                .filter { it.isNotBlank() }
                .map { s ->
                    val first = s.lineSequence().first()
                    if (first.startsWith("#") || first.matches(Regex("第.{1,12}[章节].*")))
                        section = first.trimStart('#', ' ')
                    TextBlock(s, section)
                }
        }
    }
}

object DocumentChunker {
    /**
     * Conservative 1 char/token for CJK; ASCII roughly 4 chars/token. Reserve request/output budget
     * externally.
     */
    fun tokens(s: String) = s.sumOf { if (it.code > 127) 1.0 else 0.25 }.toInt() + 1

    fun chunk(document: DocumentContent, budget: Int = 2400, overlap: Int = 120): List<TextBlock> {
        require(budget >= 200 && overlap >= 0 && overlap < budget / 2)
        val result = mutableListOf<TextBlock>()
        for (block in document.blocks) {
            var start = 0
            while (start < block.text.length) {
                var end = start
                var cost = 0.0
                while (end < block.text.length && cost < budget) {
                    cost += if (block.text[end].code > 127) 1.0 else 0.25
                    end++
                }
                if (end < block.text.length) {
                    val boundary = block.text.lastIndexOf('\n', end - 1)
                    if (boundary > start + (end - start) / 2) end = boundary + 1
                }
                result += block.copy(text = block.text.substring(start, end))
                if (end == block.text.length) break
                start = maxOf(start + 1, end - overlap)
            }
        }
        return result
    }

    fun allocate(chunks: List<TextBlock>, count: Int): List<Int> {
        require(count in 1..500)
        val weights = chunks.map { tokens(it.text) }
        val total = weights.sum().coerceAtLeast(1)
        val targets = weights.map { count * it / total }.toMutableList()
        var remaining = count - targets.sum()
        weights.indices
            .sortedByDescending { count * weights[it] % total }
            .forEach {
                if (remaining > 0) {
                    targets[it]++
                    remaining--
                }
            }
        return targets
    }
}
