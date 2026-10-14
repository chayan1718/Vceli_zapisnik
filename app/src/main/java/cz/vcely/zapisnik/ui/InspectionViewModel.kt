package cz.vcely.zapisnik.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.vcely.zapisnik.data.AppDatabase
import cz.vcely.zapisnik.data.Hive
import cz.vcely.zapisnik.data.Inspection
import cz.vcely.zapisnik.data.Stanoviste
import cz.vcely.zapisnik.data.UserPrefs
import cz.vcely.zapisnik.export.CsvExporter
import cz.vcely.zapisnik.location.LocationHelper
import cz.vcely.zapisnik.parser.Draft
import cz.vcely.zapisnik.parser.InspectionParser
import cz.vcely.zapisnik.parser.LlmParser
import cz.vcely.zapisnik.speech.VoiceCommand
import cz.vcely.zapisnik.weather.MotyliceWorker
import cz.vcely.zapisnik.weather.WeatherHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class ScreenTab { RECORDS, OVERVIEW, MOTYLICE, MONTHLY }

/** Čeká na zadání názvu nového stanoviště – appka nenašla shodu v okruhu 300 m. */
data class PendingStanoviste(val latitude: Double?, val longitude: Double?)

/** Souhrn jednoho úlu pro záložku Přehled. */
data class HiveSummary(
    val hiveId: String,
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

    /**
     * Známé úly (čísla i jména) – používá se k rozpoznání "úl Jana" v diktátu.
     * Eagerly: UI tenhle seznam nikde nezobrazuje (na rozdíl od [inspections]),
     * takže s WhileSubscribed by se dotaz do databáze nikdy nespustil a appka
     * by si žádná jména úlů nikdy nepamatovala.
     */
    val hives: StateFlow<List<Hive>> =
        db.hiveDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val draft = MutableStateFlow<Draft?>(null)
    val lastHive = MutableStateFlow("")

    // --- Uživatel (jméno/přezdívka) ---
    /** Jméno/přezdívka uživatele, uložené natrvalo. Prázdné, dokud appku nikdo nepojmenuje. */
    val userName = MutableStateFlow(UserPrefs.getName(app).orEmpty())
    /** True, dokud uživatel při prvním spuštění nezadá jméno – appka pak zobrazí dialog. */
    val showNamePrompt = MutableStateFlow(UserPrefs.getName(app) == null)

    fun setUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        UserPrefs.setName(getApplication(), trimmed)
        userName.value = trimmed
        showNamePrompt.value = false
    }

    // --- Hlídání motýlice (SMS upozornění na síření rámků) ---
    /** Telefonní číslo pro SMS upozornění, společné pro celou appku. Prázdné, dokud ho uživatel nenastaví. */
    val alertPhone = MutableStateFlow(UserPrefs.getAlertPhone(app).orEmpty())

    fun setAlertPhone(phone: String) {
        val trimmed = phone.trim()
        UserPrefs.setAlertPhone(getApplication(), trimmed)
        alertPhone.value = trimmed
    }

    /** Zapne/vypne hlídání motýlice pro dané stanoviště. */
    fun toggleMotyliceMonitoring(s: Stanoviste) {
        viewModelScope.launch {
            db.stanovisteDao().update(s.copy(motyliceMonitoring = !s.motyliceMonitoring))
        }
        // Při zapnutí rovnou natáhneme teploty, ať je uživatel hned vidí.
        if (!s.motyliceMonitoring && s.latitude != null && s.longitude != null) {
            loadMotyliceTemps(s)
        }
    }

    /** Maximální denní teploty (datum, °C) za posledních 7 dní, podle id stanoviště. */
    val motyliceTemps = MutableStateFlow<Map<Long, List<Pair<String, Double>>>>(emptyMap())
    /** Id stanovišť, pro která právě probíhá stahování teplot. */
    val motyliceLoading = MutableStateFlow<Set<Long>>(emptySet())

    /** Stáhne (znovu) max. denní teploty pro dané stanoviště – viz [WeatherHelper]. */
    fun loadMotyliceTemps(s: Stanoviste) {
        val lat = s.latitude ?: return
        val lon = s.longitude ?: return
        if (motyliceLoading.value.contains(s.id)) return
        motyliceLoading.value = motyliceLoading.value + s.id
        viewModelScope.launch {
            val temps = withContext(Dispatchers.IO) {
                WeatherHelper.fetchDailyMaxTemps(lat, lon, MotyliceWorker.WINDOW_DAYS)
            }
            motyliceTemps.value = motyliceTemps.value + (s.id to (temps ?: emptyList()))
            motyliceLoading.value = motyliceLoading.value - s.id
        }
    }

    /**
     * Uživatel potvrdil, že rámky na stanovišti právě rozšířil ("síření").
     * Appka si zapamatuje čas a od teď zase může poslat další upozornění,
     * až zase nastane podmínka (teplé dny), bez ohledu na to, že už jednou
     * v tomto "kole" upozornila.
     */
    fun confirmSireni(s: Stanoviste) {
        viewModelScope.launch {
            db.stanovisteDao().update(
                s.copy(lastSireniAt = System.currentTimeMillis(), alertAlreadySent = false)
            )
        }
    }

    // --- Stanoviště (GPS rozpoznání místa) ---
    /**
     * Eagerly ze stejného důvodu jako u [hives]: seznam stanovišť se v UI nikde
     * nezobrazuje, takže by se bez aktivního posluchače databázový dotaz nikdy
     * nespustil a appka by nové návštěvy stejného místa nikdy nerozpoznala.
     */
    val stanoviste: StateFlow<List<Stanoviste>> =
        db.stanovisteDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Stanoviště rozpoznané/zadané pro právě probíhající diktát. */
    val currentStanovisteName = MutableStateFlow<String?>(null)
    /** Nenulové, když appka nenašla shodu a čeká na ruční zadání názvu stanoviště. */
    val pendingNewStanoviste = MutableStateFlow<PendingStanoviste?>(null)
    /** True od zmáčknutí "Diktovat" do skutečného spuštění mikrofonu – appka mezitím zjišťuje polohu. */
    val isPreparingDictation = MutableStateFlow(false)

    private val stanovisteToleranceMeters = 300.0
    /** Co spustit, jakmile je stanoviště vyřešené (rozpoznané, nebo nově zadané). Nastaví [beginDictation]. */
    private var pendingReady: (() -> Unit)? = null

    /**
     * Zjistí GPS polohu a porovná ji se známými stanovišti. Pokud je shoda
     * (do [stanovisteToleranceMeters]), rovnou zavolá [onReady]. Jinak nastaví
     * [pendingNewStanoviste] a UI zobrazí dialog na zadání názvu; po jeho
     * potvrzení ([confirmNewStanoviste]) se [onReady] zavolá – bez opakování
     * GPS zjišťování (to způsobovalo, že se appka po uložení stanoviště
     * "zacyklila" a diktát se nespustil).
     *
     * [isPreparingDictation] se nastaví hned na začátku (tlačítko v UI na to
     * okamžitě zareaguje), aby zjišťování polohy nepůsobilo, že appka na klik
     * vůbec nereagovala.
     */
    fun beginDictation(context: Context, onReady: () -> Unit) {
        if (isPreparingDictation.value) return // klik už probíhá, druhý ignorujeme
        pendingReady = onReady
        isPreparingDictation.value = true
        viewModelScope.launch {
            // Poloha: nejdřív rychlá "poslední známá" (okamžitá, pokud appka
            // už má GPS provider aktivní) a jen když chybí, krátce aktivně
            // počkáme na čerstvou opravu – jinak by tlačítko "Diktovat"
            // viselo zbytečně dlouho na každém stisku.
            val loc = withContext(Dispatchers.IO) { LocationHelper.currentLocation(context) }
            val known = stanoviste.value.filter { it.latitude != null && it.longitude != null }
            val match = loc?.let { (la, lo) ->
                known.minByOrNull { LocationHelper.distanceMeters(la, lo, it.latitude!!, it.longitude!!) }
                    ?.takeIf { LocationHelper.distanceMeters(la, lo, it.latitude!!, it.longitude!!) <= stanovisteToleranceMeters }
            }
            if (match != null) {
                currentStanovisteName.value = match.name
                isPreparingDictation.value = false
                pendingReady?.invoke()
                pendingReady = null
            } else {
                // Nová poloha, nebo GPS není k dispozici -> zeptáme se ručně.
                isPreparingDictation.value = false
                pendingNewStanoviste.value = PendingStanoviste(loc?.first, loc?.second)
            }
        }
    }

    /** Uživatel potvrdil název nového stanoviště – uloží se a diktát pokračuje. */
    fun confirmNewStanoviste(name: String) {
        val pending = pendingNewStanoviste.value ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            db.stanovisteDao().insert(
                Stanoviste(
                    name = trimmed,
                    // Necháme null, pokud appka polohu nezískala – ať se to
                    // omylem nepáruje s (0, 0) při příštím diktátu.
                    latitude = pending.latitude,
                    longitude = pending.longitude,
                )
            )
            currentStanovisteName.value = trimmed
            pendingNewStanoviste.value = null
            pendingReady?.invoke()
            pendingReady = null
        }
    }

    fun cancelNewStanoviste() {
        pendingNewStanoviste.value = null
        pendingReady = null
    }

    // --- Souvislý diktát ---
    val isListening = MutableStateFlow(false)
    val partialText = MutableStateFlow("")
    /** Pozastaveno hlasovým příkazem "api pauza" – mikrofon poslouchá dál, ale nic se nezapisuje. */
    val voicePaused = MutableStateFlow(false)

    // --- Navigace / filtr ---
    val tab = MutableStateFlow(ScreenTab.RECORDS)
    val hiveFilter = MutableStateFlow<String?>(null)

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

    fun showHive(hive: String) {
        hiveFilter.value = hive
        tab.value = ScreenTab.RECORDS
    }
    fun clearHiveFilter() { hiveFilter.value = null }

    /** Zpracuje přepis: zkusí AI parser (když je online a zapnutý), jinak offline. */
    fun onTranscript(text: String, defaultHive: String?) {
        viewModelScope.launch {
            val knownIds = hives.value.map { it.id }
            val d = LlmParser.parse(text, defaultHive)
                ?: InspectionParser.parse(text, defaultHive, knownIds)
            if (d.hiveId.isBlank()) d.hiveId = lastHive.value
            // Doplníme polohu (poslední známou), pokud je oprávnění.
            withContext(Dispatchers.IO) { LocationHelper.lastKnown(getApplication()) }?.let { (la, lo) ->
                if (d.latitude.isBlank()) {
                    d.latitude = String.format(Locale.US, "%.5f", la)
                    d.longitude = String.format(Locale.US, "%.5f", lo)
                }
            }
            // Stanoviště rozpoznané (nebo zadané) při zahájení diktátu – viz beginDictation().
            if (d.stanovisteName.isBlank()) d.stanovisteName = currentStanovisteName.value.orEmpty()
            partialText.value = ""
            isListening.value = false
            draft.value = d
        }
    }

    fun updateDraft(transform: (Draft) -> Draft) {
        draft.value = draft.value?.let(transform)
    }

    fun setPhoto(path: String) = updateDraft { it.copy(photoPath = path) }

    fun discardDraft() { draft.value = null; editingInspection.value = null }

    val editingInspection = MutableStateFlow<Inspection?>(null)

    fun startEditing(inspection: Inspection) {
        editingInspection.value = inspection
        draft.value = cz.vcely.zapisnik.parser.Draft.fromInspection(inspection)
    }

    fun saveDraft() {
        val d = draft.value ?: return
        val editing = editingInspection.value
        viewModelScope.launch {
            if (editing != null) {
                val updated = d.toInspection().copy(
                    id = editing.id,
                    timestamp = editing.timestamp,
                    recordedBy = editing.recordedBy,
                )
                if (updated.hiveId.isNotBlank()) {
                    db.hiveDao().upsert(Hive(id = updated.hiveId))
                    lastHive.value = updated.hiveId
                }
                db.inspectionDao().update(updated)
                editingInspection.value = null
            } else {
                val inspection = d.toInspection().copy(recordedBy = userName.value.ifBlank { null })
                if (inspection.hiveId.isNotBlank()) {
                    db.hiveDao().upsert(Hive(id = inspection.hiveId))
                    lastHive.value = inspection.hiveId
                }
                db.inspectionDao().insert(inspection)
            }
            draft.value = null
        }
    }

    fun deleteInspection(inspection: Inspection) {
        viewModelScope.launch { db.inspectionDao().delete(inspection) }
    }

    /**
     * Hromadný zápis stejného úkonu (např. zaléčení, přidání medníku) do
     * všech úlů na daném stanovišti najednou. Pro každý úl vznikne vlastní
     * nový záznam, který lze pak jednotlivě upravit (ikona tužky).
     */
    fun bulkCreate(
        stanovisteName: String,
        treatment: String,
        interventions: String,
        feeding: String,
        tasksNextTime: String,
        note: String,
    ) {
        viewModelScope.launch {
            val hiveIds = inspections.value
                .filter { it.stanovisteName == stanovisteName }
                .map { it.hiveId }
                .distinct()
            if (hiveIds.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            val author = userName.value.ifBlank { null }
            hiveIds.forEach { hid ->
                db.inspectionDao().insert(
                    Inspection(
                        hiveId = hid,
                        timestamp = now,
                        stanovisteName = stanovisteName,
                        recordedBy = author,
                        interventions = interventions.trim().ifBlank { null },
                        feeding = feeding.trim().ifBlank { null },
                        treatment = treatment.trim().ifBlank { null },
                        tasksNextTime = tasksNextTime.trim().ifBlank { null },
                        note = note.trim().ifBlank { null },
                    )
                )
            }
        }
    }

    fun exportCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val list = db.inspectionDao().getAllForExport()
            val file = withContext(Dispatchers.IO) { CsvExporter.write(getApplication(), list) }
            onReady(file)
        }
    }
}
