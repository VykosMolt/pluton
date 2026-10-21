package io.github.vykosmolt.pluton

import android.content.Context
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Bring-your-own-key AI. The key lives in this app's private prefs only (Android backup is off), is sent only to the
// provider the user picked, and is never part of the JSON backup. Claude uses the official Anthropic SDK; the other
// providers speak the OpenAI chat-completions format over plain HTTP.

@Serializable
data class AiConfig(
    val provider: String = "anthropic",
    val key: String = "",
    val model: String = "claude-opus-5-5",
    val base: String = "",
)

data class AiProvider(
    val id: String,
    val name: String,
    val base: String,
    val defaultModel: String = "",
    val noKey: Boolean = false,
)

val AI_PROVIDERS = listOf(
    AiProvider("anthropic", "Claude (Anthropic)", "https://api.anthropic.com", "claude-opus-5-5"),
    AiProvider("openai", "OpenAI", "https://api.openai.com/v1"),
    AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com"),
    AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
    AiProvider("ollama", "Ollama (local, free)", "http://localhost:11434/v1", noKey = true),
    AiProvider("custom", "Other OpenAI-compatible server", ""),
)

data class ChatMessage(val role: String, val content: String)

class AiError(message: String) : Exception(message)

internal fun providerOf(id: String): AiProvider = AI_PROVIDERS.find { it.id == id } ?: AI_PROVIDERS.first()

internal fun baseOf(config: AiConfig): String =
    (config.base.ifBlank { providerOf(config.provider).base }).trimEnd('/')

internal fun ready(config: AiConfig?): Boolean {
    if (config == null || config.provider.isBlank() || config.model.isBlank()) return false
    if (config.provider == "custom" && config.base.isBlank()) return false
    val provider = providerOf(config.provider)
    return config.key.isNotBlank() || provider.noKey || config.provider == "custom"
}

class AiStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_keys", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AiConfig? =
        prefs.getString("config", null)?.let { runCatching { json.decodeFromString<AiConfig>(it) }.getOrNull() }

    fun save(config: AiConfig) {
        prefs.edit().putString("config", json.encodeToString(config)).apply()
    }

    fun clear() {
        prefs.edit().remove("config").apply()
    }
}

/**
 * One non-streaming reply. Chat turns are short, so the extra latency of streaming buys nothing yet. `effort` only
 * applies to Claude: "low" for chat and stories, "medium" for judging a retelling (the web app's choices).
 */
suspend fun aiChat(config: AiConfig, system: String, messages: List<ChatMessage>, effort: String = "low"): String =
    withContext(Dispatchers.IO) {
        if (config.provider == "anthropic") anthropicChat(config, system, messages, effort)
        else openAiChat(config, system, messages)
    }

private fun anthropicChat(config: AiConfig, system: String, messages: List<ChatMessage>, effort: String): String {
    try {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(config.key)
            .baseUrl(baseOf(config))
            .maxRetries(1)
            .build()
        val thinking = when (effort) {
            "medium" -> OutputConfig.Effort.MEDIUM
            "high" -> OutputConfig.Effort.HIGH
            else -> OutputConfig.Effort.LOW
        }
        val builder = MessageCreateParams.builder()
            .model(config.model)
            .maxTokens(1024L)
            .system(system)
            .outputConfig(OutputConfig.builder().effort(thinking).build())
        for (m in messages) {
            if (m.role == "assistant") builder.addAssistantMessage(m.content) else builder.addUserMessage(m.content)
        }
        val response = client.messages().create(builder.build())
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) throw AiError("The model declined to answer that one.")
        return response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()
    } catch (e: AiError) {
        throw e
    } catch (e: UnauthorizedException) {
        throw AiError("The provider rejected the API key.")
    } catch (e: PermissionDeniedException) {
        throw AiError("This key is not allowed to use that model.")
    } catch (e: NotFoundException) {
        throw AiError("Model \"${config.model}\" was not found. Pick another in Settings.")
    } catch (e: RateLimitException) {
        throw AiError("Rate limited or out of credit. Try again in a minute, or check your balance.")
    } catch (e: AnthropicIoException) {
        throw AiError("Couldn't reach the provider. Are you offline?")
    } catch (e: AnthropicServiceException) {
        throw AiError(e.message ?: "The AI request failed.")
    }
}

internal fun openAiBody(model: String, system: String, messages: List<ChatMessage>): String = buildJsonObject {
    put("model", model)
    putJsonArray("messages") {
        addJsonObject {
            put("role", "system")
            put("content", system)
        }
        for (m in messages) {
            addJsonObject {
                put("role", m.role)
                put("content", m.content)
            }
        }
    }
}.toString()

internal fun parseOpenAiContent(body: String): String {
    val content = runCatching {
        Json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    if (content.isNullOrBlank()) throw AiError("The model returned an empty reply. Try again.")
    return content.trim()
}

internal fun httpError(status: Int, body: String, config: AiConfig): String = when (status) {
    401, 403 -> "The provider rejected the API key."
    404 -> "Model \"${config.model}\" was not found. Pick another in Settings."
    429 -> "Rate limited or out of credit. Try again in a minute, or check your balance."
    else -> runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: "The AI request failed (HTTP $status)."
}

private fun openAiChat(config: AiConfig, system: String, messages: List<ChatMessage>): String {
    val base = baseOf(config)
    if (base.isBlank()) throw AiError("Set the server URL in Settings first.")
    val body = openAiBody(config.model, system, messages)
    val connection = try {
        URL("$base/chat/completions").openConnection() as HttpURLConnection
    } catch (e: Exception) {
        throw AiError("That server URL doesn't look right.")
    }
    connection.requestMethod = "POST"
    connection.connectTimeout = 60_000
    connection.readTimeout = 120_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    if (config.key.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${config.key}")
    try {
        connection.outputStream.use { it.write(body.toByteArray()) }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw AiError(httpError(status, text, config))
        return parseOpenAiContent(text)
    } catch (e: AiError) {
        throw e
    } catch (e: IOException) {
        throw AiError("Couldn't reach the provider. Are you offline?")
    } finally {
        connection.disconnect()
    }
}
