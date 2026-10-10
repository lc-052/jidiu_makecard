package com.yike.ai

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.CardTemplateRepository
import com.yike.ai.feature.AppViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthoringUpgradeUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app
        get() = (ui.activity.application as YikeApplication).container

    private fun waitText(text: String) {
        ui.waitUntil(15000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun realPdfParagraphClozeSavedOfflineAndContinuesReading() {
        val deck = DeckEntity(name = "离线 PDF 制卡测试")
        val file = File(ui.activity.cacheDir, "manual-assist.pdf")
        val original = "Cache reduces speed mismatch between CPU and memory."
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 700f)
                stream.showText(original)
                stream.endText()
            }
            doc.save(file)
        }
        runBlocking { app.db.study().putDeck(deck) }
        try {
            ui.runOnUiThread {
                ViewModelProvider(ui.activity)[AppViewModel::class.java].importDocument(
                    Uri.fromFile(file)
                )
            }
            ui.onNodeWithText("手动制卡").performScrollTo().performClick()
            ui.onNodeWithText("制卡方式：空白制卡").performScrollTo().performClick()
            ui.onNodeWithText("文档辅助", useUnmergedTree = true).performClick()
            waitText("manual-assist.pdf")
            ui.onNodeWithText("制卡设置").assertExists()
            ui.onNodeWithText("创建目标牌组").assertDoesNotExist()
            val readerHeight =
                ui.onNodeWithTag("document-reading-area").fetchSemanticsNode().boundsInRoot.height
            assertTrue(
                "文档阅读区必须占页面一半以上",
                readerHeight > ui.onRoot().fetchSemanticsNode().boundsInRoot.height * .5f,
            )
            val capture =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .takeScreenshot()
            File(ui.activity.getExternalFilesDir(null), "document-reader-0.3.1.png")
                .outputStream()
                .use { capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            capture.recycle()
            ui.onNodeWithText(original).performScrollTo().performClick()
            ui.onNodeWithText("挖空内容（必须是原文中的连续文字）")
                .performScrollTo()
                .performTextInput("speed mismatch between CPU and memory")
            ui.onNodeWithText("选中文字生成填空卡").performScrollTo().performClick()
            ui.onNodeWithText("保存并继续阅读").performScrollTo().performClick()
            ui.waitUntil(15000) {
                runBlocking { app.db.study().allNotes().any { it.deckId == deck.id } }
            }
            runBlocking {
                val note = app.db.study().allNotes().single { it.deckId == deck.id }
                assertEquals("CLOZE", note.noteType)
                assertEquals(1, note.sourcePage)
                assertTrue(note.clozeText.contains("{{c1::speed mismatch between CPU and memory}}"))
                assertNotNull(app.db.generation().document(note.sourceDocumentId!!))
                assertEquals(1, app.db.study().noteCards(note.id).size)
            }
            ui.onNodeWithText("保存并继续阅读").assertIsNotEnabled()
        } finally {
            runBlocking { app.db.study().deleteDeck(deck.id) }
            file.delete()
        }
    }

    @Test
    fun allTemplatesSelectorAndNonSchedulingPreview() {
        ui.onNodeWithText("手动制卡").performScrollTo().performClick()
        ui.onNode(hasText("卡片模板：", substring = true)).performScrollTo().performClick()
        CardTemplateRepository.all.forEach { t ->
            ui.onNodeWithText(t.name).performScrollTo().performClick()
            ui.onNodeWithText("已选择").assertExists()
            val bitmap =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .takeScreenshot()
            File(ui.activity.getExternalFilesDir(null), "template-${t.id.name}.png")
                .outputStream()
                .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        ui.onNodeWithText("应用模板").performClick()
        ui.onNodeWithText("卡片模板：极简专注").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        val deck = DeckEntity(name = "连续预览测试", templateId = "FOREST")
        val note =
            NoteEntity(
                deckId = deck.id,
                front = "预览不评分吗？",
                back = "是，不改变调度数据。",
                templateId = "CLASSIC",
            )
        var before: CardEntity? = null
        runBlocking {
            app.db.study().putDeck(deck)
            app.study.saveNote(note)
            val card = app.db.study().noteCards(note.id).single()
            app.study.rate(card.id, 3, .9, 1000)
            before = app.db.study().card(card.id)
        }
        try {
            ui.onAllNodesWithText("牌组").onLast().performClick()
            waitText(deck.name)
            ui.onNodeWithText(deck.name).performClick()
            ui.onNodeWithText("预览全部卡片").performClick()
            ui.onNodeWithText("显示答案").performClick()
            ui.onNodeWithText(note.back).assertExists()
            runBlocking {
                assertEquals(before, app.db.study().noteCards(note.id).single())
                assertEquals(1, app.db.study().allLogs().count { it.cardId == before!!.id })
            }
        } finally {
            runBlocking { app.db.study().deleteDeck(deck.id) }
        }
    }

    @Test
    fun batchCsvPreviewRejectsInvalidAndSavesValid() {
        val deck = DeckEntity(name = "批量 CSV 测试")
        runBlocking { app.db.study().putDeck(deck) }
        try {
            ui.onNodeWithText("手动制卡").performScrollTo().performClick()
            ui.onNodeWithText("制卡方式：空白制卡").performClick()
            ui.onNodeWithText("批量文本", useUnmergedTree = true).performClick()
            ui.onNodeWithText("分隔格式：Tab").performScrollTo().performClick()
            ui.onNodeWithText("CSV", useUnmergedTree = true).performClick()
            ui.onNodeWithText("批量问答文本").performScrollTo().performTextInput("问题一,答案一\n缺字段")
            ui.onNodeWithText("预览解析结果").performScrollTo().performClick()
            ui.onNodeWithText("保存全部有效卡片").performScrollTo().assertIsNotEnabled()
            ui.onNodeWithText("批量问答文本").performScrollTo().performTextReplacement("问题一,答案一\n问题二,答案二")
            ui.onNodeWithText("预览解析结果").performScrollTo().performClick()
            ui.onNodeWithText("保存全部有效卡片").performScrollTo().performClick()
            ui.waitUntil(15000) {
                runBlocking { app.db.study().allNotes().count { it.deckId == deck.id } == 2 }
            }
        } finally {
            runBlocking { app.db.study().deleteDeck(deck.id) }
        }
    }
}
