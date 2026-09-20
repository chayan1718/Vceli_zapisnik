package cz.vcely.zapisnik.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.text.Normalizer

/** Hlasové příkazy pro ovládání diktátu, viz [SpeechRecognizerManager.detectCommand]. */
enum class VoiceCommand { RESUME, PAUSE, STOP }

/**
 * Souvislý diktát celého záznamu na jeden zátah.
 *
 * Android [SpeechRecognizer] rozpozná vždy jen jednu „větu" a po odmlce ji
 * uzavře. Aby šlo nadiktovat celý stav úlu (s pauzami mezi údaji), tato třída
 * po každém dílčím výsledku **sama znovu spustí poslech** a text si postupně
 * skládá do vyrovnávací paměti. Poslech běží, dokud uživatel neklepne na
 * Zastavit ([stop]) nebo neřekne hlasový příkaz „api stop"; teprve pak se
 * předá celý text najednou přes [onFinal].
 *
 * [onPartial] průběžně hlásí dosud rozpoznaný text (už složený dohromady),
 * takže uživatel vidí, jak záznam vzniká.
 *
 * ## Hlasové ovládání
 * Aby šlo diktát přerušit a znovu spustit i bez sahání na telefon (ruce
 * bývají u úlu zaneprázdněné), appka rozpozná krátké příkazy uvozené
 * aktivačním slovem **„api"**, řečené jako samostatná věta:
 *
 *  - „api diktuji" (nebo "pokračuj", "start", "zapni") – pokračovat v zápisu
 *  - „api pauza" (nebo "přestaň", "počkej", "pozastav") – pozastavit zápis;
 *    mikrofon dál poslouchá (jinak by nešlo příkazem pokračovat), ale co se
 *    řekne, se do záznamu nezapisuje
 *  - „api stop" (nebo "konec", "ukončit", "hotovo") – ukončit diktát úplně,
 *    stejně jako klepnutí na tlačítko Zastavit
 *
 * Na každý příkaz appka krátce odpoví hlasem přes [VoiceFeedback] ("Diktuji" /
 * "Pauza" / "Stop"), aby bylo jasné, že příkaz zaregistrovala.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onCommand: (VoiceCommand) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val voice = VoiceFeedback(context)
    private val buffer = StringBuilder()

    private var running = false        // diktát jako celek běží
    private var stopRequested = false  // uživatel klepl na Zastavit / řekl "api stop"
    private var activeSession = false  // právě probíhá jeden poslech
    private var paused = false         // "api pauza" – posloucháme dál, ale nezapisujeme

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Souvislé rozpoznávání není dostupné.")
            return
        }
        buffer.clear()
        stopRequested = false
        paused = false
        running = true
        ensureRecognizer()
        startOne()
    }

    /** Ukončí diktát; celý dosud nadiktovaný text dorazí přes onFinal. */
    fun stop() {
        if (!running) return
        stopRequested = true
        if (activeSession) {
            recognizer?.stopListening() // vyvolá onResults s poslední částí
        } else {
            finish()
        }
    }

    fun destroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        voice.destroy()
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    private fun startOne() {
        if (!running || stopRequested) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "cs-CZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        activeSession = true
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            activeSession = false
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!running || stopRequested) {
            finish()
            return
        }
        // krátká prodleva, aby rozpoznávač stihl uvolnit mikrofon
        main.postDelayed({ startOne() }, 250)
    }

    private fun finish() {
        if (!running) return
        running = false
        activeSession = false
        paused = false
        main.removeCallbacksAndMessages(null)
        onFinal(buffer.toString().trim())
    }

    private fun appendChunk(text: String?) {
        val t = text?.trim().orEmpty()
        if (t.isNotEmpty()) {
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(t)
        }
    }

    private fun combined(partial: String?): String {
        val p = partial?.trim().orEmpty()
        return when {
            buffer.isEmpty() -> p
            p.isEmpty() -> buffer.toString()
            else -> "$buffer $p"
        }
    }

    /**
     * Zpracuje jeden hlasový příkaz: nejdřív přehraje hlasovou odezvu a teprve
     * PO jejím dokončení buď obnoví poslech, nebo (u „stop") diktát ukončí.
     * Díky tomu appka mezitím neposlouchá – nemůže si tak splést vlastní hlas
     * s dalším příkazem.
     */
    private fun applyCommand(cmd: VoiceCommand) {
        val reply = when (cmd) {
            VoiceCommand.RESUME -> { paused = false; "Diktuji" }
            VoiceCommand.PAUSE -> { paused = true; "Pauza" }
            VoiceCommand.STOP -> { stopRequested = true; "Stop" }
        }
        onCommand(cmd)
        onPartial(combined(null))
        voice.speak(reply) {
            if (stopRequested) finish() else scheduleRestart()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(results: Bundle) {
            if (paused) return
            val p = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            onPartial(combined(p))
        }

        override fun onResults(results: Bundle) {
            activeSession = false
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val cmd = text?.let { detectCommand(it) }
            if (cmd != null) {
                applyCommand(cmd)
                return
            }
            if (!paused) {
                appendChunk(text)
                onPartial(combined(null))
            }
            if (stopRequested) finish() else scheduleRestart()
        }

        override fun onError(error: Int) {
            activeSession = false
            if (stopRequested) {
                finish()
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onError("Chybí oprávnění k mikrofonu.")
                    finish()
                }
                // Ticho / nic nerozpoznáno / rozpoznávač zaneprázdněn:
                // prostě posloucháme dál, dokud uživatel nedá Zastavit / api stop.
                else -> scheduleRestart()
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        private const val WAKE_WORD = "api"

        private val RESUME_WORDS = listOf("diktuj", "pokracuj", "start", "zapni")
        private val PAUSE_WORDS = listOf("pauza", "prestan", "pockej", "pozastav")
        private val STOP_WORDS = listOf("stop", "konec", "ukoncit", "hotovo")

        /**
         * Rozpozná hlasový příkaz s aktivačním slovem „api" na začátku věty
         * (např. „api pauza", „api diktuji"). Aktivační slovo musí být první
         * slovo věty – běžný diktát prohlídky (bez „api" na začátku) se tedy
         * nikdy omylem nevyhodnotí jako příkaz.
         */
        internal fun detectCommand(text: String): VoiceCommand? {
            val norm = normalize(text).trim()
            if (!norm.startsWith(WAKE_WORD)) return null
            // "api" musí být celé slovo, ne jen předpona (např. "apikultura")
            val afterWake = norm.substring(WAKE_WORD.length)
            if (afterWake.isNotEmpty() && !afterWake.startsWith(" ")) return null
            val rest = afterWake.trim()
            if (rest.isEmpty()) return null
            return when {
                RESUME_WORDS.any { rest.startsWith(it) } -> VoiceCommand.RESUME
                PAUSE_WORDS.any { rest.startsWith(it) } -> VoiceCommand.PAUSE
                STOP_WORDS.any { rest.startsWith(it) } -> VoiceCommand.STOP
                else -> null
            }
        }

        /** Malá písmena + odstranění diakritiky (stejný princip jako v InspectionParser). */
        private fun normalize(s: String): String {
            val lower = s.lowercase()
            val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        }
    }
}
