package cz.vcely.zapisnik.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Souvislý diktát celého záznamu na jeden zátah.
 *
 * Android [SpeechRecognizer] rozpozná vždy jen jednu „větu" a po odmlce ji
 * uzavře. Aby šlo nadiktovat celý stav úlu (s pauzami mezi údaji), tato třída
 * po každém dílčím výsledku **sama znovu spustí poslech** a text si postupně
 * skládá do vyrovnávací paměti. Poslech běží, dokud uživatel neklepne na
 * Zastavit ([stop]); teprve pak se předá celý text najednou přes [onFinal].
 *
 * [onPartial] průběžně hlásí dosud rozpoznaný text (už složený dohromady),
 * takže uživatel vidí, jak záznam vzniká.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val buffer = StringBuilder()

    private var running = false        // diktát jako celek běží
    private var stopRequested = false  // uživatel klepl na Zastavit
    private var activeSession = false  // právě probíhá jeden poslech

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Souvislé rozpoznávání není dostupné.")
            return
        }
        buffer.clear()
        stopRequested = false
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

    private val listener = object : RecognitionListener {
        override fun onPartialResults(results: Bundle) {
            val p = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            onPartial(combined(p))
        }

        override fun onResults(results: Bundle) {
            activeSession = false
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            appendChunk(text)
            onPartial(combined(null))
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
                // prostě posloucháme dál, dokud uživatel nedá Zastavit.
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
    }
}
