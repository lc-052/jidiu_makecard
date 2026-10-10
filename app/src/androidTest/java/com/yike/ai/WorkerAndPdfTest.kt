package com.yike.ai

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.yike.ai.core.database.*
import com.yike.ai.core.document.DocumentParser
import com.yike.ai.core.network.AiClient
import com.yike.ai.domain.model.*
import com.yike.ai.feature.generation.GenerationWorker
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkerAndPdfTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun extractsRealTextPdfWithPageNumbers() = runBlocking {
        val file = File(context.cacheDir, "text-pdf-test.pdf")
        try {
            PDDocument().use { doc ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(40f, 700f)
                    stream.showText("Active recall retrieves information from memory.")
                    stream.endText()
                }
                doc.save(file)
            }
            val parsed = DocumentParser(context).parse(Uri.fromFile(file))
            assertTrue(parsed.blocks.any { it.page == 1 && it.text.contains("Active recall") })
            assertTrue(parsed.characterCount > 30)
        } finally {
            file.delete()
        }
    }

    @Test
    fun persistsChunkCheckpointAndResumesWithoutSavingUnreviewedCards() = runBlocking {
        val app = (context.applicationContext as YikeApplication).container
        val dao = app.db.generation()
        val server = MockWebServer()
        val certificate =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls =
            HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val client =
            AiClient(
                OkHttpClient.Builder()
                    .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                    .build()
            )
        val deck = DeckEntity(name = "任务恢复测试")
        val provider =
            ProviderEntity(
                name = "测试 HTTPS",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                modelId = "test-model",
                concurrency = 1,
            )
        val doc =
            DocumentContent(
                uid(),
                "fixture.txt",
                "text/plain",
                listOf(TextBlock("稳定性描述记忆保持的时间。", "稳定性"), TextBlock("困难度描述学习材料的难度。", "困难度")),
                32,
                emptyList(),
                1L,
                "test",
            )
        val task =
            GenerationTaskEntity(
                documentId = doc.id,
                providerId = provider.id,
                modelId = provider.modelId,
                configuration =
                    codec.encodeToString(
                        GenerationConfig(deck.id, targetCount = 2, analyzeFirst = true)
                    ),
            )
        val file = File(File(context.filesDir, "documents").apply { mkdirs() }, "${doc.id}.json")
        fun response(content: String) =
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    "{\"choices\":[{\"message\":{\"content\":${codec.encodeToString(content)}},\"finish_reason\":\"stop\"}]}"
                )
        fun analysis(index: Int) =
            codec.encodeToString(
                KnowledgeAnalysis(
                    listOf(KnowledgePoint(doc.blocks[index].section, doc.blocks[index].text, 1))
                )
            )
        fun cards(index: Int) =
            codec.encodeToString(
                CardOutput(
                    listOf(
                        Candidate(
                            id = "candidate-$index",
                            front = if (index == 0) "记忆稳定性表示什么？" else "学习困难度表示什么？",
                            back = doc.blocks[index].text,
                            sourceDocumentId = doc.id,
                            sourceSection = doc.blocks[index].section,
                            sourceExcerpt = doc.blocks[index].text,
                        )
                    )
                )
            )
        fun worker(): GenerationWorker =
            TestListenableWorkerBuilder<GenerationWorker>(context)
                .setInputData(workDataOf("task" to task.id))
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
        try {
            app.db.study().putDeck(deck)
            dao.putProvider(provider)
            app.vault.put(provider.id, "test-key-no-real-account")
            file.writeText(codec.encodeToString(doc))
            dao.putTask(task)
            doc.blocks.forEachIndexed { i, b ->
                dao.putChunk(ChunkEntity(task.id, i, b.section, null, b.text, 1))
            }
            server.enqueue(response(analysis(0)))
            server.enqueue(response(cards(0)))
            server.enqueue(MockResponse().setResponseCode(401))
            assertEquals(ListenableWorker.Result.failure(), worker().doWork())
            assertEquals(1, dao.chunks(task.id).count { it.completed })
            assertEquals(1, dao.allDrafts(task.id).size)
            assertTrue(app.db.study().allNotes().none { it.deckId == deck.id })
            dao.putTask(dao.task(task.id)!!.copy(status = "QUEUED"))
            server.enqueue(response(analysis(1)))
            server.enqueue(response(cards(1)))
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals("REVIEW", dao.task(task.id)!!.status)
            assertEquals(2, dao.allDrafts(task.id).size)
            assertEquals(5, server.requestCount)
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(5, server.requestCount)
            assertTrue(app.db.study().allNotes().none { it.deckId == deck.id })
        } finally {
            dao.deleteTask(task.id)
            dao.deleteProvider(provider.id)
            app.vault.remove(provider.id)
            app.db.study().deleteDeck(deck.id)
            file.delete()
            server.shutdown()
        }
    }
}
