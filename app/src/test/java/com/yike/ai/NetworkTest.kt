package com.yike.ai

import com.yike.ai.core.database.ProviderEntity
import com.yike.ai.core.network.AiClient
import com.yike.ai.domain.model.AppError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    @Test
    fun cardOutputAcceptsMultipleCardsWithoutTruncating() {
        val output =
            AiClient.parseCards(
                """{"cards":[{"front":"问题一","back":"答案一"},{"front":"问题二","back":"答案二"},{"type":"CLOZE","clozeText":"缓存用来缓解{{c1::速度差异}}。"}]}"""
            )
        assertEquals(3, output.cards.size)
        assertEquals("问题二", output.cards[1].front)
        assertEquals("CLOZE", output.cards[2].type)
    }

    @Test
    fun disconnectWhileReadingBodyIsRetryable() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("x".repeat(8192))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            )
            val p = ProviderEntity(baseUrl = server.url("/v1").toString(), modelId = "test")
            val error =
                runCatching {
                        AiClient(allowLocalHttpForTests = true).call(p, "test-token", "s", "u")
                    }
                    .exceptionOrNull()
            assertTrue(error is AppError)
            assertTrue((error as AppError).retryable)
            assertTrue(error.message!!.contains("网络"))
            assertFalse(error.message!!.contains("test-token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun chatActualRequestAndUsage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"choices":[{"finish_reason":"stop","message":{"content":"连接成功"}}],"usage":{"prompt_tokens":5,"completion_tokens":2}}"""
                    )
            )
            val p =
                ProviderEntity(
                    kind = "COMPATIBLE",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "arbitrary-model",
                )
            val result =
                AiClient(allowLocalHttpForTests = true)
                    .call(p, "test-token", "system", "user", null)
            assertEquals("连接成功", result.text)
            assertEquals(5L, result.inputTokens)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertTrue(request.body.readUtf8().contains("arbitrary-model"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun responsesRequestAndUsage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"OK"}]}],"usage":{"input_tokens":8,"output_tokens":3}}"""
                    )
            )
            val p =
                ProviderEntity(
                    baseUrl = server.url("/v1").toString(),
                    modelId = "any",
                    protocol = "RESPONSES",
                )
            val result =
                AiClient(allowLocalHttpForTests = true).call(p, "test-token", "system", "user")
            assertEquals("OK", result.text)
            assertEquals(3L, result.outputTokens)
            val request = server.takeRequest()
            assertEquals("/v1/responses", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("json_schema"))
            assertTrue(body.contains("\"store\":false"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun chatStreaming() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"成功\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    )
            )
            val result =
                AiClient(allowLocalHttpForTests = true)
                    .call(
                        ProviderEntity(
                            baseUrl = server.url("/v1").toString(),
                            modelId = "any",
                            streaming = true,
                        ),
                        "test-token",
                        "s",
                        "u",
                    )
            assertEquals("成功", result.text)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun httpAndMalformedNoKeyDisclosure() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val p = ProviderEntity(baseUrl = server.url("/v1").toString(), modelId = "any")
            for (code in listOf(401, 403, 404, 429, 500)) {
                server.enqueue(
                    MockResponse().setResponseCode(code).setBody("do not show test-token")
                )
                try {
                    AiClient(allowLocalHttpForTests = true).call(p, "test-token", "s", "u")
                    fail("expected failure")
                } catch (e: AppError) {
                    assertFalse(e.message!!.contains("test-token"))
                    assertTrue(e.message!!.contains(code.toString()))
                }
            }
            server.enqueue(MockResponse().setBody("not json"))
            try {
                AiClient(allowLocalHttpForTests = true).call(p, "test-token", "s", "u")
                fail()
            } catch (e: AppError) {
                assertTrue(e.message!!.contains("格式"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsPlainHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            AiClient().validate(ProviderEntity(baseUrl = "http://example.com/v1", modelId = "any"))
        }
    }
}
