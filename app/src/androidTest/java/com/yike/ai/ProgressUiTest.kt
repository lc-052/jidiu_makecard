package com.yike.ai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import com.yike.ai.core.database.*
import com.yike.ai.domain.model.*
import com.yike.ai.feature.generation.GenerationWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app
        get() = (ui.activity.application as YikeApplication).container

    @Test
    fun partialResultsActualCountsCancelAndRecreate() {
        val deck = DeckEntity(name = "进度界面测试")
        val docId = uid()
        val task =
            GenerationTaskEntity(
                documentId = docId,
                providerId = "test-ui-no-network",
                modelId = "fixture",
                configuration = codec.encodeToString(GenerationConfig(deck.id)),
                status = "GENERATING",
                currentSection = (1..100).joinToString("\n") { "长文档章节 $it" },
            )
        val c =
            Candidate(
                front = "已经返回的第一批卡片？",
                back = "立即显示，其他批次继续处理。",
                sourceSection = (1..100).joinToString("\n") { "长文档章节 $it" },
            )
        runBlocking {
            app.db.study().putDeck(deck)
            app.db
                .generation()
                .putDocument(DocumentEntity(docId, "进度测试资料.txt", "text/plain", "ui-test"))
            app.db.generation().putTask(task)
            app.db
                .generation()
                .putChunk(ChunkEntity(task.id, 0, "第一章", null, "原文", 1, completed = true))
            app.db.generation().putChunk(ChunkEntity(task.id, 1, "第二章", null, "原文二", 1))
            app.db
                .generation()
                .putDraft(
                    GenerationDraftEntity(
                        id = "${task.id}-000",
                        taskId = task.id,
                        chunkIndex = 0,
                        candidateId = uid(),
                        payload = codec.encodeToString(c),
                    )
                )
            (2..12).forEach { index ->
                app.db
                    .generation()
                    .putDraft(
                        GenerationDraftEntity(
                            id = "${task.id}-${index.toString().padStart(3, '0')}",
                            taskId = task.id,
                            chunkIndex = 0,
                            candidateId = uid(),
                            payload = codec.encodeToString(c.copy(front = "测试草稿第 $index 张？")),
                        )
                    )
            }
        }
        WorkManager.getInstance(ui.activity)
            .enqueueUniqueWork(
                "generation-${task.id}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GenerationWorker>()
                    .setInputData(workDataOf("task" to task.id))
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build(),
            )
            .result
            .get()
        try {
            ui.onAllNodesWithText("AI 制卡").onLast().performClick()
            ui.onNodeWithText("打开生成进度").performScrollTo().performClick()
            ui.onNodeWithText("12 张草稿 · 完成 1/2 批").assertExists()
            ui.onNodeWithText("等待后台执行或恢复").assertExists()
            ui.onNodeWithText(c.front).performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("下一张草稿").performScrollTo().performClick()
            ui.onNodeWithText("草稿预览 · 2/12").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("取消生成").performScrollTo().performClick()
            ui.waitUntil(15000) {
                runBlocking { app.db.generation().task(task.id)?.status == "CANCELLED" }
            }
            ui.activityRule.scenario.recreate()
            ui.onNodeWithText("已取消，可恢复").assertExists()
            ui.onNodeWithText("审核草稿").performScrollTo().performClick()
            ui.onNodeWithTag("audit-list").performScrollToNode(hasText("测试草稿第 12 张？"))
            ui.onNodeWithText("测试草稿第 12 张？").assertIsDisplayed()
            ui.onAllNodesWithText("编辑").onLast().performClick()
            ui.onNodeWithText("编辑草稿").assertExists()
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithTag("audit-list").performScrollToIndex(0)
            ui.onNodeWithText("保存选中卡片").performClick()
            ui.waitUntil(15000) {
                runBlocking { app.db.generation().allDrafts(task.id).all { it.saved } }
            }
            assertEquals(12, runBlocking { app.db.study().notes(deck.id).first().size })
        } finally {
            WorkManager.getInstance(ui.activity)
                .cancelUniqueWork("generation-${task.id}")
                .result
                .get()
            runBlocking {
                app.db.generation().deleteTask(task.id)
                app.db.study().deleteDeck(deck.id)
            }
        }
    }
}
