package com.yike.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.yike.ai.core.database.*
import com.yike.ai.core.network.AiClient
import com.yike.ai.domain.model.*
import com.yike.ai.feature.generation.GenerationWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerationPerformanceTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compareIdenticalDelayedHttpsAndCancellation() = runBlocking {
        val app = (context.applicationContext as YikeApplication).container
        val dao = app.db.generation()
        val server = MockWebServer()
        val cert =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server.useHttps(tls.sslSocketFactory(), false)
        server.start()
        val client =
            AiClient(
                OkHttpClient.Builder()
                    .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
                    .build()
            )
        val deck = DeckEntity(name = "模拟性能测试")
        val p =
            ProviderEntity(
                name = "模拟服务",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                modelId = "fixture",
            )
        val blocks =
            listOf(
                TextBlock("缓存用于缓解处理器与主存的速度差异。", "缓存"),
                TextBlock("总线连接计算机的不同部件。", "总线"),
                TextBlock("指令描述处理器需要执行的操作。", "指令"),
                TextBlock("寄存器保存处理器当前使用的数据。", "寄存器"),
            )
        val fronts = listOf("缓存主要解决什么问题？", "总线连接哪些对象？", "指令描述什么？", "寄存器保存什么数据？")
        val doc =
            DocumentContent(
                uid(),
                "benchmark.txt",
                "text/plain",
                blocks,
                blocks.sumOf { it.text.length },
                emptyList(),
                1,
                "fixture",
            )
        val file = File(File(context.filesDir, "documents").apply { mkdirs() }, "${doc.id}.json")
        var responseDelay = 150L
        var slowFirst = false
        val dispatched = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Long>>()
        val limitNext = java.util.concurrent.atomic.AtomicBoolean(false)
        val cooldownNext = java.util.concurrent.atomic.AtomicBoolean(false)
        val unavailable = java.util.concurrent.atomic.AtomicBoolean(false)
        val includeBadCloze = java.util.concurrent.atomic.AtomicBoolean(false)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (unavailable.get()) return MockResponse().setResponseCode(503)
                    if (cooldownNext.getAndSet(false))
                        return MockResponse().setResponseCode(429).setHeader("Retry-After", "60")
                    if (limitNext.getAndSet(false)) return MockResponse().setResponseCode(429)
                    val body = request.body.readUtf8()
                    val user =
                        codec
                            .parseToJsonElement(body)
                            .jsonObject["messages"]!!
                            .jsonArray
                            .last()
                            .jsonObject["content"]!!
                            .jsonPrimitive
                            .content
                    val index = blocks.indexOfFirst { user.contains(it.text) }.coerceAtLeast(0)
                    dispatched += index to android.os.SystemClock.elapsedRealtime()
                    val content =
                        if (body.contains("\"points\""))
                            codec.encodeToString(
                                KnowledgeAnalysis(
                                    listOf(
                                        KnowledgePoint(blocks[index].section, blocks[index].text, 1)
                                    )
                                )
                            )
                        else
                            codec.encodeToString(
                                CardOutput(
                                    listOf(
                                        Candidate(
                                            front = fronts[index],
                                            back = blocks[index].text,
                                            sourceExcerpt = blocks[index].text,
                                        )
                                    ) +
                                        if (includeBadCloze.get())
                                            listOf(
                                                Candidate(
                                                    type = "CLOZE",
                                                    clozeText = "{{c1::${blocks[index].text}}}",
                                                    sourceExcerpt = blocks[index].text,
                                                )
                                            )
                                        else emptyList()
                                )
                            )
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBodyDelay(
                            if (slowFirst && index == 0) 2000L else responseDelay,
                            TimeUnit.MILLISECONDS,
                        )
                        .setBody(
                            "{\"choices\":[{\"message\":{\"content\":${codec.encodeToString(content)}},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20}}"
                        )
                }
            }
        fun worker(id: String, attempt: Int = 0) =
            TestListenableWorkerBuilder<GenerationWorker>(context)
                .setInputData(workDataOf("task" to id))
                .setRunAttemptCount(attempt)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = GenerationWorker(appContext, workerParameters, client)
                    }
                )
                .build()
        val ids = mutableListOf<String>()
        val results = mutableListOf<JsonObject>()
        suspend fun seed(analysis: Boolean, concurrency: Int): GenerationTaskEntity {
            dao.putProvider(p.copy(concurrency = concurrency))
            val task =
                GenerationTaskEntity(
                    documentId = doc.id,
                    providerId = p.id,
                    modelId = p.modelId,
                    configuration =
                        codec.encodeToString(
                            GenerationConfig(deck.id, targetCount = 4, analyzeFirst = analysis)
                        ),
                )
            ids += task.id
            dao.putTask(task)
            blocks.forEachIndexed { i, b ->
                dao.putChunk(ChunkEntity(task.id, i, b.section, null, b.text, 1))
            }
            return task
        }
        try {
            app.db.study().putDeck(deck)
            dao.putDocument(DocumentEntity(doc.id, doc.filename, doc.mimeType, doc.hash))
            app.vault.put(p.id, "test-only-key")
            file.writeText(codec.encodeToString(doc))
            // Alternate order to expose warmup effects; report all samples, no claimed real-model
            // speedup.
            repeat(3) { trial ->
                for (legacy in if (trial % 2 == 0) listOf(true, false) else listOf(false, true)) {
                    val task = seed(legacy, if (legacy) 1 else 2)
                    val start = System.nanoTime()
                    assertEquals(ListenableWorker.Result.success(), worker(task.id).doWork())
                    val elapsed = (System.nanoTime() - start) / 1_000_000
                    val m = codec.decodeFromString<GenerationMetrics>(dao.task(task.id)!!.metrics)
                    assertEquals(if (legacy) 8 else 4, m.requests.size)
                    assertEquals(4, dao.allDrafts(task.id).size)
                    assertEquals(0, m.retries)
                    assertNotNull(m.firstBatchMs)
                    results += buildJsonObject {
                        put("trial", trial)
                        put(
                            "pipeline",
                            if (legacy) "baseline-two-stage-serial" else "fused-two-concurrent",
                        )
                        put("totalMs", elapsed)
                        put("metrics", codec.parseToJsonElement(codec.encodeToString(m)))
                    }
                }
            }
            slowFirst = true
            val refill = seed(false, 2)
            val originals = dao.chunks(refill.id)
            // Alternating zero-quota records must not occupy network slots.
            originals.forEachIndexed { index, block ->
                dao.putChunk(block.copy(ordinal = index * 2, targetCount = 0))
                dao.putChunk(block.copy(ordinal = index * 2 + 1))
            }
            dispatched.clear()
            assertEquals(ListenableWorker.Result.success(), worker(refill.id).doWork())
            val firstTime = dispatched.first { it.first == 0 }.second
            val thirdTime = dispatched.first { it.first == 2 }.second
            assertTrue(
                "Idle request slot should refill before the slow first response",
                thirdTime - firstTime < 1500,
            )
            assertEquals(4, dispatched.size)
            assertTrue(dao.chunks(refill.id).all { it.completed })
            slowFirst = false
            limitNext.set(true)
            val limited = seed(false, 2)
            assertEquals(ListenableWorker.Result.success(), worker(limited.id).doWork())
            val limitedMetrics =
                codec.decodeFromString<GenerationMetrics>(dao.task(limited.id)!!.metrics)
            assertEquals(1, limitedMetrics.effectiveConcurrency)
            assertEquals(1, limitedMetrics.retries)
            assertEquals(4, dao.allDrafts(limited.id).size)
            cooldownNext.set(true)
            val cooling = seed(false, 1)
            assertEquals(ListenableWorker.Result.retry(), worker(cooling.id).doWork())
            val cooledRequests = server.requestCount
            assertEquals(ListenableWorker.Result.retry(), worker(cooling.id).doWork())
            assertEquals(cooledRequests, server.requestCount)
            val saved = dao.task(cooling.id)!!
            val cooled = codec.decodeFromString<GenerationMetrics>(saved.metrics)
            dao.putTask(saved.copy(metrics = codec.encodeToString(cooled.copy(notBefore = 0))))
            assertEquals(ListenableWorker.Result.success(), worker(cooling.id).doWork())
            unavailable.set(true)
            val interrupted = seed(false, 2)
            assertEquals(ListenableWorker.Result.retry(), worker(interrupted.id).doWork())
            assertEquals("QUEUED", dao.task(interrupted.id)!!.status)
            val exhausted = seed(false, 2)
            assertEquals(ListenableWorker.Result.failure(), worker(exhausted.id, 3).doWork())
            assertEquals("FAILED", dao.task(exhausted.id)!!.status)
            unavailable.set(false)
            assertEquals(ListenableWorker.Result.success(), worker(interrupted.id).doWork())
            assertEquals(4, dao.allDrafts(interrupted.id).size)
            assertEquals(ListenableWorker.Result.success(), worker(interrupted.id).doWork())
            assertEquals(4, dao.allDrafts(interrupted.id).size)
            includeBadCloze.set(true)
            val mixed = seed(false, 2)
            dao.putTask(
                mixed.copy(
                    configuration =
                        codec.encodeToString(
                            GenerationConfig(deck.id, targetCount = 8, clozePercent = 30)
                        )
                )
            )
            dao.chunks(mixed.id).forEach { dao.putChunk(it.copy(targetCount = 2)) }
            val beforeMixed = server.requestCount
            assertEquals(ListenableWorker.Result.success(), worker(mixed.id).doWork())
            assertEquals(4, server.requestCount - beforeMixed)
            val kept = dao.allDrafts(mixed.id).map { codec.decodeFromString<Candidate>(it.payload) }
            assertEquals(4, kept.size)
            assertTrue(
                kept.all {
                    it.type == "BASIC" &&
                        it.validationWarnings.any { warning -> warning.contains("已过滤") }
                }
            )
            includeBadCloze.set(false)
            // Whole-document continuation must suppress cards already produced by earlier tasks.
            val continuation = seed(false, 2)
            dao.putTask(
                continuation.copy(
                    configuration =
                        codec.encodeToString(
                            GenerationConfig(deck.id, targetCount = 4, wholeDocument = true)
                        )
                )
            )
            val beforeContinuation = server.requestCount
            assertEquals(ListenableWorker.Result.success(), worker(continuation.id).doWork())
            assertEquals(4, server.requestCount - beforeContinuation)
            assertTrue(
                "Existing same-source drafts must not be saved twice",
                dao.allDrafts(continuation.id).isEmpty(),
            )
            assertTrue(dao.chunks(continuation.id).all { it.completed })
            assertTrue("Original drafts survive continuation", dao.allDrafts(mixed.id).size == 4)
            responseDelay = 3000
            val task = seed(false, 2)
            val before = server.requestCount
            val job = async { worker(task.id).doWork() }
            withContext(Dispatchers.IO) { while (server.requestCount == before) Thread.sleep(20) }
            dao.putTask(dao.task(task.id)!!.copy(status = "CANCELLED"))
            job.cancelAndJoin()
            delay(200)
            assertTrue(server.requestCount - before <= 2)
            assertTrue(dao.allDrafts(task.id).isEmpty())
            val result = buildJsonObject {
                put(
                    "network",
                    "MockWebServer HTTPS, identical 150ms response body delay, four source blocks",
                )
                put("realApiTest", false)
                put("samples", JsonArray(results))
                put("cancelledRequests", server.requestCount - before)
            }
            File(context.getExternalFilesDir(null), "generation-benchmark.json")
                .writeText(result.toString())
        } finally {
            ids.forEach { dao.deleteTask(it) }
            app.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM documents WHERE id = ?",
                arrayOf(doc.id),
            )
            dao.deleteProvider(p.id)
            app.vault.remove(p.id)
            app.db.study().deleteDeck(deck.id)
            file.delete()
            server.shutdown()
        }
    }
}
