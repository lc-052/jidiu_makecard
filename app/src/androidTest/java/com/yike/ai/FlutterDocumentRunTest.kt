package com.yike.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yike.ai.core.common.CardRules
import com.yike.ai.core.database.DeckEntity
import com.yike.ai.core.document.DocumentChunker
import com.yike.ai.core.document.GenerationBatchPlanner
import com.yike.ai.domain.model.*
import com.yike.ai.feature.AppViewModel
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** User-selected real document; no mock cards and no secret values in the report. */
@RunWith(AndroidJUnit4::class)
class FlutterDocumentRunTest {
    @Test
    fun retryExplicitFailedProbe() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(
            args.getString("realAi") == "true" && args.getString("retryProbe") != null
        )
        val id = args.getString("retryProbe")!!
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as YikeApplication
        val dao = application.container.db.generation()
        val previousReport = File(context.getExternalFilesDir(null), "flutter-real-run-0.3.2.json")
        require(
            codec
                .parseToJsonElement(previousReport.readText())
                .jsonObject
                .getValue("taskId")
                .jsonPrimitive
                .content == id
        )
        require(dao.task(id)?.status == "FAILED")
        val oldDrafts = dao.allDrafts(id).associate { it.id to CardRules.sha256(it.payload) }
        val oldCompleted = dao.chunks(id).filter { it.completed }.map { it.ordinal }.toSet()
        val oldRequests =
            codec.decodeFromString<GenerationMetrics>(dao.task(id)!!.metrics).requests.size
        val vm = withContext(Dispatchers.Main) { AppViewModel(application) }
        val started = System.currentTimeMillis()
        withContext(Dispatchers.Main) { vm.retryTask(id) }
        withTimeout(30_000) {
            dao.tasks().first { list -> list.any { it.id == id && it.status != "FAILED" } }
        }
        val result = File(context.getExternalFilesDir(null), "flutter-retry-metrics-0.3.3.json")
        withTimeout(240_000) {
            while (true) {
                val task = dao.task(id)!!
                val chunks = dao.chunks(id)
                val drafts = dao.allDrafts(id)
                val preserved =
                    oldDrafts.all { (draftId, hash) ->
                        drafts.any { it.id == draftId && CardRules.sha256(it.payload) == hash }
                    }
                val metrics = codec.decodeFromString<GenerationMetrics>(task.metrics)
                result.writeText(
                    buildJsonObject {
                            put("taskId", id)
                            put("status", task.status)
                            put("wallMs", System.currentTimeMillis() - started)
                            put("generatedCards", drafts.size)
                            put("previousDraftsPreserved", preserved)
                            put(
                                "previousCompletedBatchesPreserved",
                                oldCompleted.all { ordinal ->
                                    chunks.any { it.ordinal == ordinal && it.completed }
                                },
                            )
                            put(
                                "completedBatches",
                                chunks.count { it.completed && it.targetCount > 0 },
                            )
                            put("requestBatches", chunks.count { it.targetCount > 0 })
                            put("newRequests", metrics.requests.size - oldRequests)
                            put(
                                "newRequestMetrics",
                                codec.encodeToJsonElement(metrics.requests.drop(oldRequests)),
                            )
                        }
                        .toString()
                )
                if (
                    task.status in listOf("REVIEW", "COMPLETED", "PARTIAL", "FAILED", "CANCELLED")
                ) {
                    assertTrue("Previously completed drafts must survive retry", preserved)
                    assertTrue(
                        "Only failed batches can issue new requests",
                        metrics.requests.drop(oldRequests).all { it.batch !in oldCompleted },
                    )
                    assertTrue(
                        "Generation must reach a reviewable result",
                        task.status in listOf("REVIEW", "COMPLETED", "PARTIAL"),
                    )
                    break
                }
                delay(1000)
            }
        }
    }

    @Test
    fun cancelExplicitProbe() = runBlocking {
        val id = InstrumentationRegistry.getArguments().getString("cancelProbe")
        org.junit.Assume.assumeTrue(
            "Only explicitly named diagnostic tasks can be stopped",
            id != null,
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = (context.applicationContext as YikeApplication).container
        val reportFile =
            File(
                context.getExternalFilesDir(null),
                InstrumentationRegistry.getArguments().getString("reportName")
                    ?: "flutter-real-run.json",
            )
        val report = codec.parseToJsonElement(reportFile.readText()).jsonObject
        require(report.getValue("taskId").jsonPrimitive.content == id)
        val vm =
            withContext(Dispatchers.Main) {
                AppViewModel(context.applicationContext as YikeApplication)
            }
        withContext(Dispatchers.Main) { vm.cancelTask(id!!) }
        val task =
            withTimeout(15_000) {
                app.db
                    .generation()
                    .tasks()
                    .first { tasks -> tasks.any { it.id == id && it.status == "CANCELLED" } }
                    .first { it.id == id }
            }
        delay(1000)
        val latest = app.db.generation().task(id!!)!!
        val updated = report.toMutableMap()
        updated["status"] = JsonPrimitive(latest.status)
        updated["terminationReason"] =
            JsonPrimitive(
                "Diagnostic stopped after repeated 90-second timeouts; completed drafts retained"
            )
        updated["metrics"] = codec.parseToJsonElement(latest.metrics)
        updated["cards"] =
            JsonArray(
                app.db.generation().allDrafts(id).map { codec.parseToJsonElement(it.payload) }
            )
        updated["generatedCards"] = JsonPrimitive(app.db.generation().allDrafts(id).size)
        reportFile.writeText(JsonObject(updated).toString())
        assertEquals("CANCELLED", task.status)
    }

    @Test
    fun generateActualDocumentWithConfiguredProvider() = runBlocking {
        org.junit.Assume.assumeTrue(
            "Real paid API testing requires explicit instrumentation opt-in",
            InstrumentationRegistry.getArguments().getString("realAi") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as YikeApplication
        val app = application.container
        val dao = app.db.generation()
        val usable =
            dao.providers().first().filter {
                runCatching { app.vault.get(it.id).isNotBlank() }.getOrDefault(false)
            }
        val p = usable.firstOrNull { it.isDefault } ?: usable.singleOrNull()
        requireNotNull(p) { "Set a default configured model in the phone app first" }
        val vm = withContext(Dispatchers.Main) { AppViewModel(application) }
        val deck = DeckEntity(name = "Flutter 文档实测 · ${System.currentTimeMillis()}")
        app.db.study().putDeck(deck)
        withTimeout(30_000) { vm.decks.first { list -> list.any { it.id == deck.id } } }
        val source = File(context.getExternalFilesDir(null), "Flutter面试题详细答案.md")
        withContext(Dispatchers.Main) { vm.importDocument(Uri.fromFile(source)) }
        val doc = withTimeout(30_000) { vm.document.first { it != null }!! }
        withTimeout(30_000) { vm.busy.first { !it } }
        val config = GenerationConfig(deckId = deck.id, targetCount = 20, clozePercent = 30)
        val taskId = CompletableDeferred<String>()
        val clickedAt = System.currentTimeMillis()
        withContext(Dispatchers.Main) { vm.generate(config, p.id) { taskId.complete(it) } }
        val id = withTimeout(30_000) { taskId.await() }
        val reportFile =
            File(
                context.getExternalFilesDir(null),
                InstrumentationRegistry.getArguments().getString("reportName")
                    ?: "flutter-real-run.json",
            )
        val deadline = System.currentTimeMillis() + 12 * 60_000
        while (true) {
            val task = dao.task(id)!!
            val chunks = dao.chunks(id)
            val drafts = dao.allDrafts(id)
            val report = buildJsonObject {
                put("source", doc.filename)
                put("characters", doc.characterCount)
                put("paragraphBlocks", doc.blocks.size)
                put("taskId", id)
                put("deckId", deck.id)
                put("model", p.modelId)
                put("protocol", p.protocol)
                put("configuredConcurrency", p.concurrency)
                put("timeoutSeconds", p.timeoutSeconds)
                put("maxOutputTokens", p.maxTokens)
                put("strictSchema", p.strictSchema)
                put("streaming", p.streaming)
                put("configuration", codec.parseToJsonElement(codec.encodeToString(config)))
                put("status", task.status)
                put("error", task.errorMessage?.let(::JsonPrimitive) ?: JsonNull)
                put("wallMs", System.currentTimeMillis() - clickedAt)
                put("chunks", chunks.size)
                put("requestBatches", chunks.count { it.targetCount > 0 })
                put("oneCardBatches", chunks.count { it.targetCount == 1 })
                put("completedBatches", chunks.count { it.completed && it.targetCount > 0 })
                put("generatedCards", drafts.size)
                put("metrics", codec.parseToJsonElement(task.metrics))
                put("cards", JsonArray(drafts.map { codec.parseToJsonElement(it.payload) }))
            }
            withContext(Dispatchers.IO) { reportFile.writeText(report.toString()) }
            if (
                task.status in listOf("COMPLETED", "REVIEW", "FAILED", "CANCELLED", "PARTIAL") ||
                    System.currentTimeMillis() >= deadline
            ) {
                if (
                    System.currentTimeMillis() >= deadline &&
                        task.status !in listOf("REVIEW", "FAILED", "CANCELLED")
                ) {
                    withContext(Dispatchers.Main) { vm.cancelTask(id) }
                    withTimeout(15_000) {
                        dao.tasks().first { tasks ->
                            tasks.any { it.id == id && it.status == "CANCELLED" }
                        }
                    }
                }
                break
            }
            delay(2000)
        }
    }

    @Test
    fun parseAndPlanActualDocument() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = (context.applicationContext as YikeApplication).container
        val source = File(context.getExternalFilesDir(null), "Flutter面试题详细答案.md")
        assertTrue("Push the user-selected source document first", source.exists())
        val d = withContext(Dispatchers.IO) { app.parser.parse(Uri.fromFile(source)) }
        assertTrue(d.characterCount > 10000)
        val providers = app.db.generation().providers().first()
        val usable =
            providers.filter {
                runCatching { app.vault.get(it.id).isNotBlank() }.getOrDefault(false)
            }
        val scenarios =
            listOf(20 to 4, 20 to 8, 20 to 24, 50 to 4, 50 to 8, 50 to 24).map { (count, batch) ->
                val started = System.nanoTime()
                val budget = (8000 - 4096).coerceIn(600, 2400)
                val actualBudget =
                    (budget * batch / count.coerceAtLeast(batch)).coerceIn(250, budget)
                val chunks = DocumentChunker.chunk(d, actualBudget)
                val targets = DocumentChunker.allocate(chunks, count)
                buildJsonObject {
                    put("targetCount", count)
                    put("batchSize", batch)
                    put("inputBudget", actualBudget)
                    put("chunks", chunks.size)
                    put("chunksRequiringRequests", targets.count { it > 0 })
                    put("zeroTargetChunks", targets.count { it == 0 })
                    put("oneCardRequests", targets.count { it == 1 })
                    put("allocatedCards", targets.sum())
                    put(
                        "coveredSections",
                        chunks.indices
                            .filter { targets[it] > 0 }
                            .map { chunks[it].section }
                            .distinct()
                            .size,
                    )
                    put("chunkAndAllocationMs", (System.nanoTime() - started) / 1_000_000)
                    put(
                        "batches",
                        JsonArray(
                            chunks.indices
                                .filter { targets[it] > 0 }
                                .map { i ->
                                    buildJsonObject {
                                        put("index", i)
                                        put("section", chunks[i].section)
                                        put("characters", chunks[i].text.length)
                                        put("cards", targets[i])
                                    }
                                }
                        ),
                    )
                }
            }
        val optimized = GenerationBatchPlanner.plan(d, 20, 4)
        assertTrue(
            "The real Flutter document should now use fewer than ten request batches",
            optimized.count { it.target > 0 } < 10,
        )
        assertEquals(20, optimized.sumOf { it.target })
        val whole = GenerationBatchPlanner.wholeDocumentPlan(d, 8)
        assertTrue(whole.sumOf { it.target } > 20)
        assertEquals(
            d.blocks.map { it.section }.toSet(),
            whole.flatMap { it.block.section.lines() }.toSet(),
        )
        assertTrue(whole.all { it.target in 1..8 })
        val report = buildJsonObject {
            put("wholeDocumentTarget", whole.sumOf { it.target })
            put("wholeDocumentRequests", whole.size)
            put("wholeDocumentSections", whole.flatMap { it.block.section.lines() }.distinct().size)
            put("optimizedRequests", optimized.count { it.target > 0 })
            put(
                "optimizedBatchTargets",
                JsonArray(optimized.filter { it.target > 0 }.map { JsonPrimitive(it.target) }),
            )
            put("source", source.name)
            put("bytes", source.length())
            put("characters", d.characterCount)
            put("paragraphBlocks", d.blocks.size)
            put("sections", d.blocks.map { it.section }.distinct().size)
            put("parseMs", d.parseMs)
            put("cleanMs", d.cleanMs)
            put("providerCount", providers.size)
            put("usableProviderCount", usable.size)
            put("realApiCalled", false)
            put("generatedCards", 0)
            put("status", if (usable.isEmpty()) "BLOCKED_NO_API_KEY" else "LOCAL_PLAN_ONLY")
            put("maxOutputTokensForPlanning", 4096)
            put("warnings", JsonArray(d.warnings.map(::JsonPrimitive)))
            put("scenarios", JsonArray(scenarios))
        }
        File(context.getExternalFilesDir(null), "flutter-document-run.json")
            .writeText(report.toString())
    }
}
