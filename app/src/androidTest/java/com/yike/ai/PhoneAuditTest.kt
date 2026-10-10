package com.yike.ai

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.yike.ai.core.common.CardFormatting
import com.yike.ai.core.common.CardRules
import com.yike.ai.core.designsystem.CardPreview
import com.yike.ai.core.designsystem.YikeTheme
import com.yike.ai.data.repository.PreferencesState
import com.yike.ai.domain.model.Candidate
import com.yike.ai.feature.AppViewModel
import com.yike.ai.feature.generation.AuditScreen
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*

/** Read-only phone diagnosis. No model requests, card text output, or draft mutations. */
class PhoneAuditTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test
    fun englishFallbackRendersOnPhone() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("phoneStyle") == "true")
        var activity: ComponentActivity? = null
        ui.waitUntil(30_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity =
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<ComponentActivity>()
                        .firstOrNull()
            }
            activity != null
        }
        val c =
            CardFormatting.styleGeneratedCard(
                Candidate(front = "Cache 与 CPU 有什么关系？", back = "Cache 缓解 CPU 与主存的速度差异。")
            )
        ui.runOnUiThread {
            activity!!.setContent { YikeTheme(PreferencesState()) { CardPreview(c.front, c.back) } }
        }
        fun check(text: String) {
            val node = ui.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
            val styled = node.config[SemanticsProperties.Text].single()
            Assert.assertEquals(
                2,
                styled.spanStyles.count { it.item.fontFamily == FontFamily.Monospace },
            )
            Assert.assertFalse(styled.text.contains('`'))
        }
        check("Cache 与 CPU 有什么关系？")
        ui.onNodeWithText("显示答案").performClick()
        check("Cache 缓解 CPU 与主存的速度差异。")
        ui.onNodeWithText("显示问题").performClick()
        check("Cache 与 CPU 有什么关系？")
    }

    @Test
    fun existingDraftsScrollAndEditWithoutChanges() {
        val args = InstrumentationRegistry.getArguments()
        val id = args.getString("auditProbe")
        Assume.assumeTrue(id != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = (context.applicationContext as YikeApplication).container.db.generation()
        val before = runBlocking { dao.allDrafts(id!!) }
        Assert.assertTrue("The named task must contain multiple drafts", before.size > 1)
        val hashes = before.associate { it.id to CardRules.sha256(it.payload) }
        var activity: ComponentActivity? = null
        ui.waitUntil(30_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity =
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<ComponentActivity>()
                        .firstOrNull()
            }
            activity != null
        }
        ui.runOnUiThread {
            val screen = activity!!
            val vm = ViewModelProvider(screen)[AppViewModel::class.java]
            screen.setContent { YikeTheme(PreferencesState()) { AuditScreen(vm, id!!) } }
        }
        ui.onNodeWithTag("audit-list").performScrollToIndex(before.size)
        val editable = !before.last().saved
        if (editable) {
            ui.onAllNodesWithText("编辑")
                .onLast()
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            ui.onNodeWithText("编辑草稿").assertIsDisplayed()
            ui.onNodeWithText("取消").performClick()
        } else {
            ui.onAllNodesWithText("已保存").onLast().performScrollTo().assertIsDisplayed()
        }
        ui.onNodeWithTag("audit-list").performTouchInput { swipeDown() }
        ui.onNodeWithTag("audit-list").performScrollToIndex(0)
        ui.onNodeWithText("保存选中卡片").performScrollTo().assertIsDisplayed()
        val after = runBlocking { dao.allDrafts(id!!) }
        Assert.assertEquals(hashes, after.associate { it.id to CardRules.sha256(it.payload) })
        Assert.assertEquals(
            before.map { it.saved to it.selected },
            after.map { it.saved to it.selected },
        )
        File(context.getExternalFilesDir(null), "phone-audit-0.3.4-metrics.json")
            .writeText(
                buildJsonObject {
                        put("taskId", id)
                        put("drafts", after.size)
                        put("lastDraftReached", true)
                        put("editOpenedAndCancelled", editable)
                        put("savedDrafts", after.count { it.saved })
                        put("returnedToTop", true)
                        put("draftsUnchanged", true)
                        put("realApiCalled", false)
                    }
                    .toString()
            )
    }
}
