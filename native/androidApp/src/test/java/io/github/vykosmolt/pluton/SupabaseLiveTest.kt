package io.github.vykosmolt.pluton

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.Rating
import io.github.vykosmolt.pluton.core.ReviewEntry
import io.github.vykosmolt.pluton.core.Scheduler
import io.github.vykosmolt.pluton.core.State
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.SyncEngine
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.createEmptyCard
import io.github.vykosmolt.pluton.core.db.PlutonDb
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end account sync against a local Supabase (`npx supabase start`) configured in .env.smoke.local.
 * Skipped when that file or the local stack is absent; the web app's smoke test uses the same stack and Mailpit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SupabaseLiveTest {
    private fun envFile(): File? {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(4) {
            val f = dir?.resolve(".env.smoke.local")
            if (f?.exists() == true) return f
            dir = dir?.parentFile
        }
        return null
    }

    private fun env(): Pair<String, String>? {
        val file = envFile() ?: return null
        val text = file.readText()
        val url = Regex("""VITE_SUPABASE_URL="?([^"\n]+)""").find(text)?.groupValues?.get(1) ?: return null
        val key = Regex("""VITE_SUPABASE_ANON_KEY="?([^"\n]+)""").find(text)?.groupValues?.get(1) ?: return null
        return url to key
    }

    private fun http(method: String, url: String, body: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        check(status in 200..299) { "HTTP $status from $url: $text" }
        return text
    }

    private fun codeFor(email: String): String {
        repeat(40) {
            val messages = JSONObject(http("GET", "http://127.0.0.1:54324/api/v1/messages")).getJSONArray("messages")
            for (i in 0 until messages.length()) {
                val message = messages.getJSONObject(i)
                val to = message.getJSONArray("To")
                val mine = (0 until to.length()).any { to.getJSONObject(it).getString("Address") == email }
                if (!mine) continue
                val code = Regex("""\d{6}""").find(message.getString("Subject"))?.value ?: continue
                runCatching {
                    http("DELETE", "http://127.0.0.1:54324/api/v1/messages", """{"IDs":["${message.getString("ID")}"]}""")
                }
                return code
            }
            Thread.sleep(500)
        }
        error("no sign-in email arrived")
    }

    private fun store(): Store {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlutonDb.Schema.create(driver).value
        return Store(PlutonDb(driver))
    }

    @Test
    fun twoStoresConvergeThroughTheLocalStack() = runBlocking {
        val env = env()
        assumeTrue("local Supabase env not present", env != null)
        val (url, key) = env!!
        assumeTrue("live test only runs against the local Supabase stack", url.contains("127.0.0.1") || url.contains("localhost"))
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = SyncPrefs(context)
        prefs.clearSession()
        prefs.saveConfig(SupabaseConfig(url, key))
        val client = SupabaseClient(prefs)
        try {
            assumeTrue("local Supabase not reachable", runCatching { http("GET", "$url/auth/v1/health") }.isSuccess)
            val email = "native-smoke-${System.currentTimeMillis()}@pluton.test"
            client.sendCode(email)
            client.verifyCode(email, codeFor(email))

            val now = System.currentTimeMillis()
            val a = store()
            a.addCard(Word("chat", pos = "noun", sentence = "Le chat dort.", form = "chat"), "chat", null, now)
            val reviewed = Scheduler().next(createEmptyCard(now), now, Rating.Good)
            a.saveAnswer(CardState("chat", reviewed, added = now), ReviewEntry("chat", 3, "cloze", now))
            a.addText("Titre", "Bonjour", now)
            SyncEngine(a, client).sync()

            val b = store()
            SyncEngine(b, client).sync()
            assertEquals(1, b.card("chat")?.fsrs?.reps)
            assertEquals("Titre", b.texts().single().title)

            val second = Scheduler().next(b.card("chat")!!.fsrs, now + 1000, Rating.Good)
            b.saveAnswer(b.card("chat")!!.copy(fsrs = second), ReviewEntry("chat", 3, "cloze", now + 1000))
            SyncEngine(b, client).sync()
            SyncEngine(a, client).sync()
            assertEquals(2, a.card("chat")?.fsrs?.reps)
        } finally {
            runCatching { client.deleteAccount() }
            prefs.clear()
        }
    }
}
