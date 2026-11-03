package io.github.vykosmolt.pluton

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {
    @Test
    fun openAiBodyCarriesSystemAndTurns() {
        val body = Json.parseToJsonElement(
            openAiBody(
                "some-model",
                "You are Pluton.",
                listOf(ChatMessage("user", "Bonjour"), ChatMessage("assistant", "Salut !"), ChatMessage("user", "Ça va ?")),
            ),
        ).jsonObject
        assertEquals("some-model", body["model"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("You are Pluton.", messages[0]["content"]!!.jsonPrimitive.content)
        assertEquals("Ça va ?", messages[3]["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun openAiReplyContentIsExtracted() {
        val reply = """{"choices":[{"message":{"role":"assistant","content":"  Bonjour !  "}}]}"""
        assertEquals("Bonjour !", parseOpenAiContent(reply))
    }

    @Test
    fun emptyOrMalformedRepliesAreReadableErrors() {
        for (body in listOf("{}", """{"choices":[]}""", "not json")) {
            val e = runCatching { parseOpenAiContent(body) }.exceptionOrNull()
            assertTrue("expected AiError for $body, got $e", e is AiError)
        }
        val truncated = """{"choices":[{"finish_reason":"length","message":{"content":"A partial correction"}}]}"""
        val error = runCatching { parseOpenAiContent(truncated) }.exceptionOrNull()
        assertTrue(error is AiError)
        assertTrue(error?.message.orEmpty().contains("cut off"))
    }

    @Test
    fun providerDefaultsAndReadiness() {
        val anthropic = AiConfig()
        assertEquals("https://api.anthropic.com", baseOf(anthropic))
        assertFalse(ready(anthropic)) // no key yet
        assertTrue(ready(anthropic.copy(key = "sk-ant-...")))
        assertTrue(ready(AiConfig(provider = "ollama", model = "llama3.2")))
        assertTrue(ready(AiConfig(provider = "custom", model = "local", base = "https://example.test/v1")))
        assertFalse(ready(AiConfig(provider = "custom", model = "local")))
        assertFalse(ready(AiConfig(model = "")))
        assertEquals("https://example.test/v1", baseOf(AiConfig(provider = "openai", base = "https://example.test/v1/")))
    }

    @Test
    fun httpErrorsSayWhatToFix() {
        val config = AiConfig(model = "claude-opus-5-5")
        assertTrue(httpError(401, "", config).contains("rejected the API key"))
        assertTrue(httpError(404, "", config).contains("claude-opus-5-5"))
        assertTrue(httpError(429, "", config).contains("Rate limited"))
        val providerMessage = """{"error":{"message":"context length exceeded"}}"""
        assertEquals("context length exceeded", httpError(400, providerMessage, config))
        assertEquals("The AI request failed (HTTP 500).", httpError(500, "oops", config))
    }

    @Test
    fun effortIsOnlySentToModelsThatSupportIt() {
        assertTrue(supportsEffort("claude-opus-5-5"))
        assertTrue(supportsEffort("claude-sonnet-4-6"))
        assertFalse(supportsEffort("claude-3-5-sonnet-latest"))
        assertFalse(supportsEffort("gpt-4.1"))
    }

    @Test
    fun changingProvidersClearsCredentialsAndEndpoint() {
        val previous = AiConfig(key = "secret-for-anthropic", base = "https://old.example.test", model = "old-model")
        for (provider in AI_PROVIDERS.filter { it.id != previous.provider }) {
            val switched = configForProvider(previous, provider.id)
            assertEquals(provider.id, switched.provider)
            assertEquals("", switched.key)
            assertEquals("", switched.base)
            assertEquals(provider.defaultModel, switched.model)
        }
        assertEquals(previous, configForProvider(previous, previous.provider))
    }
}
