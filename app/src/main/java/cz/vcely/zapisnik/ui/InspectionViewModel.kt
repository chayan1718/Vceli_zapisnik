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

enum class ScreenTab { RECORDS, OVERVIEW, MOTYLICE }

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
            // Hromadný záznam: návrh se nevloží do běžného náhledu, ale do hromadného dialogu.
            val bulk = bulkStanoviste.value
            if (bulk != null) {
                d.hiveId = ""
                d.stanovisteName = bulk
                partialText.value = ""
                isListening.value = false
                bulkHives.value = hivesAtStanoviste(bulk)
                bulkDraft.value = d
                bulkStanoviste.value = null   // režim se spotřeboval, další diktát je zase běžný
                return@launch
            }
            partialText.value = ""
            isListening.value = false
            draft.value = d
        }
    }

    // --- Hromadný záznam pro všechny úly stanoviště ---
    /** Název stanoviště, pro které právě probíhá hromadný diktát (null = běžný diktát). */
    val bulkStanoviste = MutableStateFlow<String?>(null)
    /** Návrh hromadného záznamu čekající na potvrzení (bez čísla úlu). */
    val bulkDraft = MutableStateFlow<Draft?>(null)
    /** Úly, které se v návrhu nabízejí (uživatel může některé odškrtnout). */
    val bulkHives = MutableStateFlow<List<String>>(emptyList())

    /**
     * Úly stanoviště = úly, jejichž NEJNOVĚJŠÍ záznam je z tohoto stanoviště
     * (úl, který se přestěhoval, se u původního stanoviště už nenabízí).
     */
    fun hivesAtStanoviste(name: String): List<String> =
        inspections.value
            .groupBy { it.hiveId }
            .mapNotNull { (id, recs) -> recs.maxByOrNull { it.timestamp }?.let { id to it } }
            .filter { (id, r) -> id.isNotBlank() && r.stanovisteName == name }
            .map { it.first }
            .sortedWith(compareBy({ it.toIntOrNull() == null }, { it.toIntOrNull() ?: 0 }, { it }))

    // --- Navigace v záložce Přehled úlů: stanoviště -> úly -> záznamy úlu ---
    // Drží se ve ViewModelu, aby se po úpravě záznamu přehled vrátil na stejné místo.
    val overviewStanoviste = MutableStateFlow<String?>(null)
    val overviewHive = MutableStateFlow<String?>(null)

    // --- Oprava přepisu hlasu a znovunačtení polí z opraveného přepisu ---
    private suspend fun reparse(d: Draft): Draft {
        val text = d.rawTranscript.trim()
        if (text.isBlank()) return d
        val hive = d.hiveId.ifBlank { null }
        val parsed = LlmParser.parse(text, hive)
            ?: InspectionParser.parse(text, hive, hives.value.map { it.id })
        // Co se nemá z přepisu měnit: úl, stanoviště, datum, fotka, poloha a sám přepis.
        return parsed.copy(
            hiveId = d.hiveId, stanovisteName = d.stanovisteName, timestamp = d.timestamp,
            photoPath = d.photoPath, latitude = d.latitude, longitude = d.longitude,
            rawTranscript = d.rawTranscript,
        )
    }

    fun reparseDraft() {
        val d = draft.value ?: return
        viewModelScope.launch { draft.value = reparse(d) }
    }

    fun reparseBulkDraft() {
        val d = bulkDraft.value ?: return
        viewModelScope.launch { bulkDraft.value = reparse(d) }
    }

    /** Úprava návrhu hromadného záznamu před schválením (datum, léčení, krmení…). */
    fun updateBulkDraft(transform: (Draft) -> Draft) {
        bulkDraft.value = bulkDraft.value?.let(transform)
    }

    fun beginBulk(stanovisteName: String) {
        bulkDraft.value = null
        bulkStanoviste.value = stanovisteName
        currentStanovisteName.value = stanovisteName
    }

    /** Vypne režim hromadného diktátu (např. při běžném diktátu nebo chybě mikrofonu). */
    fun cancelBulkMode() { bulkStanoviste.value = null }

    fun discardBulk() {
        bulkStanoviste.value = null
        bulkDraft.value = null
        bulkHives.value = emptyList()
    }

    /** Uloží stejný záznam pro každý vybraný úl (každý úl dostane vlastní záznam). */
    fun saveBulk(selectedHives: List<String>) {
        val d = bulkDraft.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (id in selectedHives) {
                db.hiveDao().upsert(Hive(id = id))
                db.inspectionDao().insert(
                    d.toInspection().copy(
                        hiveId = id,
                        timestamp = if (d.timestamp > 0) d.timestamp else now,
                        recordedBy = userName.value.ifBlank { null },
                    )
                )
            }
            discardBulk()
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

    fun exportCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val list = db.inspectionDao().getAllForExport()
            val file = withContext(Dispatchers.IO) { CsvExporter.write(getApplication(), list) }
            onReady(file)
        }
    }

    // --- Záloha a obnova (soubor ZIP: JSON + fotky) ---
    fun backupTo(uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val app = getApplication<Application>()
                val sum = app.contentResolver.openOutputStream(uri)?.use {
                    cz.vcely.zapisnik.backup.BackupManager.write(app, it)
                } ?: throw IllegalStateException("Do zvoleného místa nejde zapisovat.")
                "Záloha uložena: ${sum.inspections} záznamů, ${sum.hives} úlů, ${sum.stanoviste} stanovišť, ${sum.photos} fotek."
            } catch (e: Exception) {
                "Záloha se nepovedla: ${e.message}"
            }
            onDone(msg)
        }
    }

    fun restoreFrom(uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val app = getApplication<Application>()
                val sum = app.contentResolver.openInputStream(uri)?.use {
                    cz.vcely.zapisnik.backup.BackupManager.restore(app, it)
                } ?: throw IllegalStateException("Soubor nejde otevřít.")
                "Obnoveno: ${sum.inspections} záznamů, ${sum.hives} úlů, ${sum.stanoviste} stanovišť, ${sum.photos} fotek."
            } catch (e: Exception) {
                "Obnova se nepovedla: ${e.message}"
            }
            onDone(msg)
        }
    }

    // --- Kleštíci z fotky podložky: výsledek jde do rozpracovaného (nebo nového) záznamu ---
    fun applyVarroa(count: Int, days: Int?) {
        val line = buildString {
            append("Kleštíci z fotky podložky: ").append(count)
            if (days != null && days > 0) {
                append(" za ").append(days).append(" dní (")
                append(String.format(java.util.Locale("cs"), "%.1f", count.toDouble() / days))
                append(" za den)")
            }
        }
        val cur = draft.value
        if (cur != null) {
            draft.value = cur.copy(
                miteDrop = count.toString(),
                note = listOf(cur.note, line).filter { it.isNotBlank() }.joinToString("\n")
            )
        } else {
            draft.value = Draft(
                hiveId = lastHive.value,
                stanovisteName = currentStanovisteName.value.orEmpty(),
                miteDrop = count.toString(),
                note = line
            )
        }
    }
}
