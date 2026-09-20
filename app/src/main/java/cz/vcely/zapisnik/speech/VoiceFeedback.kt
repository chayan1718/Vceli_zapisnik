package cz.vcely.zapisnik.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Krátká hlasová odezva na hlasové příkazy ("Diktuji", "Pauza", "Stop"), aby
 * uživatel u úlu poznal, že appka příkaz zaregistrovala, i bez pohledu na displej.
 *
 * Důležité: dokud appka mluví, [SpeechRecognizerManager] mikrofon neposlouchá
 * (viz [speak] – restart poslechu čeká na [onDone]), aby appka neuslyšela
 * sama sebe a nespletla si vlastní hlas s dalším příkazem.
 */
class VoiceFeedback(context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pendingDone: (() -> Unit)? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) engine?.language = Locale("cs", "CZ")
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                pendingDone?.invoke()
                pendingDone = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingDone?.invoke()
                pendingDone = null
            }
        })
    }

    /**
     * Přečte [text] nahlas; [onDone] se zavolá vždy po dokončení (i při chybě
     * nebo když TTS není dostupné), aby volající mohl bezpečně pokračovat.
     */
    fun speak(text: String, onDone: () -> Unit) {
        val e = engine
        if (e == null || !ready) {
            onDone()
            return
        }
        pendingDone = onDone
        e.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    fun destroy() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
