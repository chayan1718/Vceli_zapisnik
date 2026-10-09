package cz.vcely.zapisnik.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * VOLITELNÝ parser přes jazykový model (LLM).
 *
 * Pravidlový [InspectionParser] funguje offline a zdarma, ale u hodně volných
 * formulací může něco minout. Když je telefon online, můžete místo něj (nebo
 * jako druhý pokus) použít tento parser – LLM rozumí přirozené řeči podstatně
 * lépe.
 *
 * Jak zapnout:
 *   1) Doplňte svůj API klíč do [apiKey] (ideálně přes BuildConfig / secrets,
 *      ne natvrdo do kódu v repozitáři).
 *   2) V InspectionViewModel zavolejte LlmParser.parse(...) a když vrátí null
 *      (offline / chyba), použijte InspectionParser.parse(...) jako zálohu.
 *
 * Příklad níže volá Anthropic Messages API. Endpoint/formát klidně vyměňte za
 * jiný (OpenAI apod.) – důležité je, že model vrátí JSON s poli Draftu.
 */
object LlmParser {

    // POZOR: klíč sem nedávejte natvrdo v produkci. Viz README.
    private const val apiKey: String = ""            // "" = vypnuto
    private const val model: String = "claude-3-5-haiku-latest"
    private const val endpoint: String = "https://api.anthropic.com/v1/messages"

    private val SCHEMA_HINT = """
        Vrať POUZE JSON objekt (bez komentářů) s těmito volitelnými klíči:
        hiveId (identifikátor úlu – číslo jako text, např. "5", nebo jméno/přezdívka úlu, např. "Jana"),
        cappedBroodFrames, openBroodFrames, storesFrames, occupiedSeams,
        honeyEstimateKg, framesRemoved (počty jako čísla, půlky povoleny),
        queenSeen ("ano"/"ne"), queenStatus, eggsSeen ("ano"/"ne"),
        temperament, diseaseNotes, miteDrop (celé číslo jako text),
        interventions, feeding, treatment, tasksNextTime, note.
        Klíče, které ve větě nejsou, vynech.
    """.trimIndent()

    fun isEnabled(): Boolean = apiKey.isNotBlank()

    /** Vrátí Draft, nebo null při vypnutí / chybě / offline. */
    suspend fun parse(transcript: String, defaultHive: String? = null): Draft? {
        if (!isEnabled()) return null
        return try {
            withContext(Dispatchers.IO) {
                val json = request(transcript)
                json?.let { fromJson(it, transcript, defaultHive) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun request(transcript: String): JSONObject? {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 400)
            val userText = "$SCHEMA_HINT\n\nVěta: \"$transcript\""
            put("messages", org.json.JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userText)
            }))
        }.toString()

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        if (conn.responseCode !in 200..299) return null
        val resp = conn.inputStream.bufferedReader().use { it.readText() }

        // Vytáhneme text odpovědi a z něj JSON blok.
        val root = JSONObject(resp)
        val text = root.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return JSONObject(text.substring(start, end + 1))
    }

    private fun fromJson(o: JSONObject, transcript: String, defaultHive: String?): Draft {
        fun s(key: String): String = if (o.has(key) && !o.isNull(key)) o.get(key).toString() else ""
        val draft = Draft(rawTranscript = transcript)
        draft.hiveId = s("hiveId").ifBlank { defaultHive.orEmpty() }
        draft.cappedBroodFrames = s("cappedBroodFrames")
        draft.openBroodFrames = s("openBroodFrames")
        draft.storesFrames = s("storesFrames")
        draft.queenSeen = s("queenSeen")
        draft.queenStatus = s("queenStatus")
        draft.eggsSeen = s("eggsSeen")
        draft.occupiedSeams = s("occupiedSeams")
        draft.temperament = s("temperament")
        draft.diseaseNotes = s("diseaseNotes")
        draft.miteDrop = s("miteDrop")
        draft.interventions = s("interventions")
        draft.feeding = s("feeding")
        draft.treatment = s("treatment")
        draft.honeyEstimateKg = s("honeyEstimateKg")
        draft.framesRemoved = s("framesRemoved")
        draft.tasksNextTime = s("tasksNextTime")
        draft.note = s("note")
        return draft
    }
}
