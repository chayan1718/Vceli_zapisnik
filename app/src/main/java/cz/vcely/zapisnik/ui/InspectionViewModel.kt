package cz.vcely.zapisnik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.vcely.zapisnik.data.AppDatabase
import cz.vcely.zapisnik.data.Hive
import cz.vcely.zapisnik.data.Inspection
import cz.vcely.zapisnik.export.CsvExporter
import cz.vcely.zapisnik.location.LocationHelper
import cz.vcely.zapisnik.parser.Draft
import cz.vcely.zapisnik.parser.InspectionParser
import cz.vcely.zapisnik.parser.LlmParser
import cz.vcely.zapisnik.speech.VoiceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class ScreenTab { RECORDS, OVERVIEW }

/** Souhrn jednoho úlu pro záložku Přehled. */
data class HiveSummary(
    val hiveNumber: Int,
    val count: Int,
    val lastTimestamp: Long,
    val lastStores: Double?,
    val lastBrood: Double?,
)

class InspectionViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    val inspections: StateFlow<List<Inspection>> =
        db.inspectionDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draft = MutableStateFlow<Draft?>(null)
    val lastHive = MutableStateFlow("")

    // --- Souvislý diktát ---
    val isListening = MutableStateFlow(false)
    val partialText = MutableStateFlow("")
    /** Pozastaveno hlasovým příkazem "api pauza" – mikrofon poslouchá dál, ale nic se nezapisuje. */
    val voicePaused = MutableStateFlow(false)

    // --- Navigace / filtr ---
    val tab = MutableStateFlow(ScreenTab.RECORDS)
    val hiveFilter = MutableStateFlow<Int?>(null)

    fun setListening(value: Boolean) {
        isListening.value = value
        if (value) voicePaused.value = false
    }

    /** Reaguje na hlasový příkaz z [cz.vcely.zapisnik.speech.SpeechRecognizerManager]. */
    fun onVoiceCommand(cmd: VoiceCommand) {
        voicePaused.value = when (cmd) {
            VoiceCommand.PAUSE -> true
            VoiceCommand.RESUME, VoiceCommand.STOP -> false
        }
    }
    fun setPartial(text: String) { partialText.value = text }
    fun setTab(t: ScreenTab) { tab.value = t }

    fun showHive(hive: Int) {
        hiveFilter.value = hive
        tab.value = ScreenTab.RECORDS
    }
    fun clearHiveFilter() { hiveFilter.value = null }

    /** Zpracuje přepis: zkusí AI parser (když je online a zapnutý), jinak offline. */
    fun onTranscript(text: String, defaultHive: Int?) {
        viewModelScope.launch {
            val d = LlmParser.parse(text, defaultHive) ?: InspectionParser.parse(text, defaultHive)
            if (d.hiveNumber.isBlank()) d.hiveNumber = lastHive.value
            // Doplníme polohu (poslední známou), pokud je oprávnění.
            withContext(Dispatchers.IO) { LocationHelper.lastKnown(getApplication()) }?.let { (la, lo) ->
                if (d.latitude.isBlank()) {
                    d.latitude = String.format(Locale.US, "%.5f", la)
                    d.longitude = String.format(Locale.US, "%.5f", lo)
                }
            }
            partialText.value = ""
            isListening.value = false
            draft.value = d
        }
    }

    fun updateDraft(transform: (Draft) -> Draft) {
        draft.value = draft.value?.let(transform)
    }

    fun setPhoto(path: String) = updateDraft { it.copy(photoPath = path) }

    fun discardDraft() { draft.value = null }

    fun saveDraft() {
        val d = draft.value ?: return
        viewModelScope.launch {
            val inspection = d.toInspection()
            if (inspection.hiveNumber > 0) {
                db.hiveDao().upsert(Hive(number = inspection.hiveNumber))
                lastHive.value = inspection.hiveNumber.toString()
            }
            db.inspectionDao().insert(inspection)
            draft.value = null
        }
    }

    fun deleteInspection(inspection: Inspection) {
        viewModelScope.launch { db.inspectionDao().delete(inspection) }
    }

    fun exportCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val list = db.inspectionDao().getAllForExport()
            val file = withContext(Dispatchers.IO) { CsvExporter.write(getApplication(), list) }
            onReady(file)
        }
    }
}
