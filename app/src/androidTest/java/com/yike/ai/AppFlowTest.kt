package com.yike.ai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yike.ai.core.anki.ApkgExporter
import com.yike.ai.core.database.*
import com.yike.ai.core.security.KeyVault
import com.yike.ai.domain.model.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val container
        get() = (ui.activity.application as YikeApplication).container

    private fun waitText(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun homeShortcutAndBottomTabsShowDistinctPages() {
        ui.onAllNodesWithText("首页").onLast().performClick()
        ui.onNodeWithText("制卡工作台").assertExists()
        ui.onNodeWithText("把资料变成记忆卡片").performScrollTo().performClick()
        waitText("从资料到长期记忆")
        ui.onNodeWithText("制卡工作台").assertDoesNotExist()
        repeat(3) {
            ui.onAllNodesWithText("首页").onLast().performClick()
            waitText("制卡工作台")
            ui.onNodeWithText("从资料到长期记忆").assertDoesNotExist()
            ui.onAllNodesWithText("AI 制卡").onLast().performClick()
            waitText("从资料到长期记忆")
            ui.onNodeWithText("制卡工作台").assertDoesNotExist()
        }
        ui.onAllNodesWithText("首页").onLast().performClick()
        waitText("制卡工作台")
    }

    @Test
    fun homeNavigationCreateEditAndPersist() {
        runBlocking {
            container.db
                .study()
                .allDecks()
                .filter { it.name == "UI 验证牌组" }
                .forEach { container.db.study().deleteDeck(it.id) }
        }
        ui.onAllNodesWithText("牌组").onLast().performClick()
        ui.onNodeWithText("创建牌组").performClick()
        ui.onNodeWithText("名称").performTextInput("UI 验证牌组")
        ui.onNodeWithText("保存").performClick()
        waitText("UI 验证牌组")
        ui.onNodeWithText("UI 验证牌组").performClick()
        ui.onNodeWithText("添加卡片").performClick()
        ui.onNodeWithText("问题").performTextInput("主动回忆是什么？")
        ui.onNodeWithText("答案").performTextInput("不看答案，主动从记忆中提取知识。")
        ui.onNodeWithText("保存卡片").performScrollTo().performClick()
        ui.waitUntil(15000) {
            runBlocking { container.db.study().allNotes().any { it.front == "主动回忆是什么？" } }
        }
        ui.onNodeWithText("查看牌组卡片").performScrollTo().performClick()
        waitText("主动回忆是什么？")
        ui.activityRule.scenario.recreate()
        waitText("主动回忆是什么？")
        runBlocking {
            val deck = container.db.study().allDecks().first { it.name == "UI 验证牌组" }
            val note = container.db.study().allNotes().first { it.deckId == deck.id }
            assertEquals(1, container.db.study().noteCards(note.id).size)
            val cardIds = container.db.study().noteCards(note.id).map { it.id }.toSet()
            assertTrue(container.db.study().allLogs().none { it.cardId in cardIds })
            container.db.study().deleteDeck(deck.id)
        }
    }

    @Test
    fun generationConfigurationAndModelEditor() {
        ui.onAllNodesWithText("AI 制卡").onLast().performClick()
        ui.onNodeWithText("配置模型").performClick()
        ui.onNodeWithText("新增模型").performClick()
        ui.onNodeWithText("Model ID（任意）").performScrollTo().performTextInput("任意模型")
        ui.onNodeWithText("保存").assertIsDisplayed()
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("或粘贴资料")
            .performScrollTo()
            .performTextInput("# 学习\n\n主动回忆是从记忆中提取信息，而不是重新阅读答案。")
        ui.onNodeWithText("解析并预览").performScrollTo().performClick()
        waitText("清除预览")
        ui.onNodeWithText("清除预览").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualClozePreviewSaveAndNoLearningEntries() {
        runBlocking {
            container.db
                .study()
                .allDecks()
                .filter { it.name == "手动挖空验证" }
                .forEach { container.db.study().deleteDeck(it.id) }
        }
        ui.onAllNodesWithText("首页").onLast().performClick()
        ui.onNodeWithText("手动制卡").performScrollTo().performClick()
        ui.onNodeWithText("创建目标牌组").performScrollTo().performClick()
        ui.onNodeWithText("名称").performTextInput("手动挖空验证")
        ui.onNodeWithText("保存").performClick()
        waitText("目标牌组：手动挖空验证")
        ui.onNodeWithText("卡片类型：基础问答").performScrollTo().performClick()
        ui.onNodeWithText("挖空", useUnmergedTree = true).performClick()
        ui.onNodeWithText("挖空")
            .performScrollTo()
            .performTextInput("**重点**：{{c1::甲}} 与 {{c2::乙::提示}}")
        ui.onNodeWithText("显示答案").performScrollTo().performClick()
        ui.onNodeWithText("重点：甲 与 乙").assertExists()
        ui.onNodeWithText("保存卡片").performScrollTo().performClick()
        ui.waitUntil(15000) {
            runBlocking {
                container.db.study().allNotes().any {
                    it.clozeText == "**重点**：{{c1::甲}} 与 {{c2::乙::提示}}"
                }
            }
        }
        ui.onNodeWithText("查看牌组卡片").performScrollTo().performClick()
        waitText("**重点**：{{c1::甲}} 与 {{c2::乙::提示}}")
        ui.onNodeWithText("开始复习").assertDoesNotExist()
        runBlocking {
            val deck = container.db.study().allDecks().first { it.name == "手动挖空验证" }
            val note = container.db.study().allNotes().single { it.deckId == deck.id }
            assertEquals(2, container.db.study().noteCards(note.id).size)
            container.db.study().deleteDeck(deck.id)
        }
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onAllNodesWithText("设置").onLast().performClick()
        ui.onNodeWithText("每日复习提醒").assertDoesNotExist()
        ui.onNodeWithText("每日新卡片上限").assertDoesNotExist()
    }

    @Test
    fun generationExplainsMissingDeckAndEnablesAfterInlineCreation() {
        val provider = ProviderEntity(name = "生成按钮回归模型", modelId = "test-model", isDefault = true)
        runBlocking { container.db.generation().putProvider(provider) }
        container.vault.put(provider.id, "test-only-key")
        try {
            ui.onAllNodesWithText("AI 制卡").onLast().performClick()
            ui.onNodeWithText("或粘贴资料").performScrollTo().performTextInput("主动回忆通过从记忆中提取信息来巩固学习。")
            ui.onNodeWithText("解析并预览").performScrollTo().performClick()
            waitText("清除预览")
            ui.onNodeWithText("确认资料并生成").performScrollTo().assertIsEnabled().performClick()
            ui.onNodeWithText("上传所选资料？").assertExists()
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithText("创建目标牌组").assertDoesNotExist()
            ui.onNodeWithText("制作范围：整份资料 · 自动制作整套").performScrollTo().performClick()
            ui.onNodeWithText("自定义数量", useUnmergedTree = true).performClick()
            val hasDeck = runBlocking { container.db.study().allDecks().isNotEmpty() }
            if (hasDeck) {
                // Existing user data must not be removed to manufacture an empty-state test.
                ui.onNodeWithText("确认资料并生成").performScrollTo().assertIsEnabled()
            } else {
                ui.onNodeWithText("确认资料并生成").performScrollTo().assertIsNotEnabled()
                ui.onNodeWithText("请先创建或选择目标牌组，用于保存审核后的卡片。").assertExists()
            }
            ui.onNodeWithText("创建目标牌组").performScrollTo().performClick()
            ui.onNodeWithText("名称").performTextInput("制卡页新建牌组验证")
            ui.onNodeWithText("保存").performClick()
            waitText("目标牌组：制卡页新建牌组验证")
            ui.onNodeWithText("确认资料并生成").performScrollTo().assertIsEnabled().performClick()
            ui.onNodeWithText("上传所选资料？").assertExists()
            ui.onNodeWithText("取消").performClick()
        } finally {
            runBlocking {
                container.db
                    .study()
                    .allDecks()
                    .filter { it.name == "制卡页新建牌组验证" }
                    .forEach { container.db.study().deleteDeck(it.id) }
                container.db.generation().deleteProvider(provider.id)
            }
            container.vault.remove(provider.id)
        }
    }

    @Test
    fun draftReviewSaveAndSecureKey() {
        val deck = DeckEntity(name = "审核验证牌组")
        val task =
            GenerationTaskEntity(
                documentId = "test-document",
                providerId = "none",
                modelId = "none",
                configuration = codec.encodeToString(GenerationConfig(deck.id)),
                status = "REVIEW",
                progress = 100,
            )
        val c =
            Candidate(
                id = "audit-candidate",
                front = "最小信息原则是什么？",
                back = "每张卡片只考查一个知识点。",
                sourceSection = "学习原则",
                sourceExcerpt = "每张卡片只考查一个知识点。",
            )
        runBlocking {
            container.db.study().putDeck(deck)
            container.db.generation().putTask(task)
            container.db
                .generation()
                .putDraft(
                    GenerationDraftEntity(
                        taskId = task.id,
                        chunkIndex = 0,
                        candidateId = c.id,
                        payload = codec.encodeToString(c),
                    )
                )
        }
        ui.onAllNodesWithText("AI 制卡").onLast().performClick()
        ui.onNodeWithText("审核草稿").performScrollTo().performClick()
        waitText("最小信息原则是什么？")
        ui.onNodeWithText("编辑").performScrollTo().performClick()
        ui.onNodeWithText("答案").performTextReplacement("一张卡片聚焦一个知识点。")
        ui.onNodeWithText("保存").performClick()
        ui.onNodeWithText("保存选中卡片").performClick()
        waitText("已保存")
        runBlocking {
            assertEquals(
                "一张卡片聚焦一个知识点。",
                container.db.study().allNotes().first { it.deckId == deck.id }.back,
            )
            container.db.generation().deleteTask(task.id)
            container.db.study().deleteDeck(deck.id)
        }
        val id = "instrumentation-key-test"
        val secret = "test-only-secret"
        val vault = KeyVault(ui.activity)
        vault.put(id, secret)
        assertEquals(secret, vault.get(id))
        val encrypted =
            File(ui.activity.noBackupFilesDir, "credentials/$id")
                .readBytes()
                .toString(Charsets.UTF_8)
        assertFalse(encrypted.contains(secret))
        vault.remove(id)
    }

    @Test
    fun formattedSavedCardPreview() {
        val deck = DeckEntity(name = "排版预览验证")
        val note =
            NoteEntity(
                deckId = deck.id,
                front = "怎样把卡片写得清楚？",
                back = "**一个知识点，一张卡片。**\n\n- 问题明确、独立\n- 答案简短，保留必要条件\n- 详细说明放进解释",
                explanation = "解释内容单独展示。",
            )
        runBlocking {
            container.db.study().putDeck(deck)
            container.study.saveNote(note)
        }
        try {
            ui.onAllNodesWithText("牌组").onLast().performClick()
            waitText(deck.name)
            ui.onNodeWithText(deck.name).performClick()
            waitText(note.front)
            ui.onNodeWithText("预览").performClick()
            ui.onNodeWithText("显示答案").performClick()
            ui.onNodeWithText("一个知识点，一张卡片。").assertExists()
            ui.onNodeWithText("问题明确、独立").assertExists()
            ui.onNodeWithText("解释内容单独展示。").assertDoesNotExist()
            val screenshot =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .takeScreenshot()
            File(ui.activity.getExternalFilesDir(null), "authoring-preview.png")
                .outputStream()
                .use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            ui.onNodeWithText("查看解释").performClick()
            ui.onNodeWithText("解释内容单独展示。").assertExists()
            ui.onNodeWithText("关闭").performClick()
        } finally {
            runBlocking { container.db.study().deleteDeck(deck.id) }
        }
    }

    @Test
    fun exportRealPackageFixture() {
        val parent = DeckEntity(id = "integration-parent", name = "中文课程")
        val child = DeckEntity(id = "integration-child", name = "第一章", parentDeckId = parent.id)
        val notes =
            listOf(
                NoteEntity(
                    id = "integration-basic",
                    deckId = child.id,
                    front = "<b>中文 &amp; 特殊字符</b>",
                    back = "**核心结论**\n\n- 先理解条件\n- 再应用公式 \\(x^2\\)",
                    explanation = "解释与答案分开展示，避免文字堆在一起。",
                    tags = "中文 测试",
                ),
                NoteEntity(
                    id = "integration-cloze",
                    deckId = child.id,
                    noteType = "CLOZE",
                    clozeText = "{{c1::甲}} 和 {{c2::乙::提示}}",
                    explanation = "中文解释",
                    tags = "挖空",
                ),
            )
        val file = File(ui.activity.getExternalFilesDir(null), "compatibility.apkg")
        file.outputStream().use {
            ApkgExporter(ui.activity).export(listOf(parent, child), notes, it)
        }
        assertTrue(file.length() > 0)
        val visualNotes =
            com.yike.ai.core.designsystem.CardTemplateRepository.all.flatMap { d ->
                listOf(
                    NoteEntity(
                        id = "visual-${d.id}-basic",
                        deckId = child.id,
                        front = "${d.name} · 中文问题",
                        back = "**核心结论**\n\n- 先理解条件\n- 再应用公式 \\(x^2\\)",
                        explanation = "解释与补充。",
                        templateId = d.id.name,
                    ),
                    NoteEntity(
                        id = "visual-${d.id}-cloze",
                        deckId = child.id,
                        noteType = "CLOZE",
                        clozeText = "{{c1::甲}} 与 {{c2::乙::提示}}",
                        explanation = "中文解释",
                        templateId = d.id.name,
                    ),
                )
            }
        File(ui.activity.getExternalFilesDir(null), "templates.apkg").outputStream().use {
            ApkgExporter(ui.activity).export(listOf(parent, child), visualNotes, it)
        }
        File(ui.activity.getExternalFilesDir(null), "templates-switched.apkg").outputStream().use {
            ApkgExporter(ui.activity)
                .export(
                    listOf(parent, child),
                    visualNotes.mapIndexed { index, n ->
                        if (index == 0)
                            n.copy(
                                templateId = "FOREST",
                                updatedAt = System.currentTimeMillis() + 1500,
                            )
                        else n
                    },
                    it,
                )
        }
    }
}
