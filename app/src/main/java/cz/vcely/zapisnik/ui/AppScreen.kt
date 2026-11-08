package cz.vcely.zapisnik.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.vcely.zapisnik.data.Inspection
import cz.vcely.zapisnik.data.Stanoviste
import cz.vcely.zapisnik.parser.Draft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val listDateFmt = SimpleDateFormat("d. M. yyyy HH:mm", Locale("cs"))

/** Číselný úl zobrazíme jako "č. 5", pojmenovaný jako "„Jana“". */
private fun hiveLabel(id: String?): String =
    if (id.isNullOrBlank()) "?"
    else if (id.toIntOrNull() != null) "č. $id" else "„$id“"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    viewModel: InspectionViewModel,
    onToggleMic: () -> Unit,
    onCapturePhoto: () -> Unit,
    onExport: () -> Unit,
    onBackupSave: () -> Unit,
    onBackupRestore: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val inspections by viewModel.inspections.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isPreparingDictation by viewModel.isPreparingDictation.collectAsState()
    val voicePaused by viewModel.voicePaused.collectAsState()
    val partial by viewModel.partialText.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val hiveFilter by viewModel.hiveFilter.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val showNamePrompt by viewModel.showNamePrompt.collectAsState()
    val pendingNewStanoviste by viewModel.pendingNewStanoviste.collectAsState()
    val currentStanoviste by viewModel.currentStanovisteName.collectAsState()
    val stanovisteList by viewModel.stanoviste.collectAsState()
    val alertPhone by viewModel.alertPhone.collectAsState()
    val motyliceTemps by viewModel.motyliceTemps.collectAsState()
    val motyliceLoading by viewModel.motyliceLoading.collectAsState()
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showVarroa by remember { mutableStateOf(false) }

    if (showNamePrompt) {
        NamePromptDialog(onConfirm = { viewModel.setUserName(it) })
    }
    if (pendingNewStanoviste != null) {
        NewStanovisteDialog(
            hasLocation = pendingNewStanoviste?.latitude != null,
            onConfirm = { name -> viewModel.confirmNewStanoviste(name) },
            onCancel = { viewModel.cancelNewStanoviste() }
        )
    }
    if (showPhoneDialog) {
        AlertPhoneDialog(
            current = alertPhone,
            onConfirm = { phone -> viewModel.setAlertPhone(phone); showPhoneDialog = false },
            onCancel = { showPhoneDialog = false }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Záloha a obnova") },
            text = {
                Text(
                    "Záloha uloží všechny záznamy, úly, stanoviště a fotky do jednoho souboru ZIP. " +
                        "Obnova data ze zálohy přidá zpět a nic nemaže."
                )
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false; onBackupSave() }) { Text("Uložit zálohu") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showBackupDialog = false; onBackupRestore() }) { Text("Obnovit ze zálohy") }
                    TextButton(onClick = { showBackupDialog = false }) { Text("Zavřít") }
                }
            }
        )
    }
    if (showVarroa) {
        VarroaScreen(
            onUse = { count, days -> viewModel.applyVarroa(count, days); showVarroa = false },
            onClose = { showVarroa = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Včelí zápisník")
                        if (userName.isNotBlank()) {
                            Text(userName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showPhoneDialog = true }) {
                        Icon(Icons.Filled.Phone, contentDescription = "Telefon pro SMS upozornění")
                    }
                    IconButton(onClick = { showVarroa = true }) {
                        Icon(Icons.Filled.BugReport, contentDescription = "Počítání kleštíků z fotky")
                    }
                    IconButton(onClick = { showBackupDialog = true }) {
                        Icon(Icons.Filled.Backup, contentDescription = "Záloha a obnova")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            if (draft == null) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            when {
                                isListening -> "Zastavit"
                                isPreparingDictation -> "Zjišťuji polohu…"
                                else -> "Diktovat"
                            }
                        )
                    },
                    icon = {
                        if (isPreparingDictation && !isListening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(if (isListening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null)
                        }
                    },
                    // I bez GPS zpoždění appka na klik reaguje ihned (viz isPreparingDictation) –
                    // druhý klik během zjišťování polohy se ignoruje v MainActivity.toggleMic().
                    onClick = onToggleMic,
                    containerColor = when {
                        isListening -> MaterialTheme.colorScheme.error
                        isPreparingDictation -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            val current = draft
            if (current != null) {
                DraftForm(
                    draft = current,
                    onChange = { viewModel.updateDraft(it) },
                    onSave = { viewModel.saveDraft() },
                    onDiscard = { viewModel.discardDraft() },
                    onCapturePhoto = onCapturePhoto
                )
            } else {
                if (isListening || partial.isNotBlank()) {
                    ListeningBanner(
                        isListening = isListening,
                        paused = voicePaused,
                        partial = partial,
                        stanoviste = currentStanoviste
                    )
                }
                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == ScreenTab.RECORDS,
                        onClick = { viewModel.setTab(ScreenTab.RECORDS) },
                        text = { Text("Záznamy") }
                    )
                    Tab(
                        selected = tab == ScreenTab.OVERVIEW,
                        onClick = { viewModel.setTab(ScreenTab.OVERVIEW) },
                        text = { Text("Přehled úlů") }
                    )
                    Tab(
                        selected = tab == ScreenTab.MOTYLICE,
                        onClick = { viewModel.setTab(ScreenTab.MOTYLICE) },
                        text = { Text("Motýlice") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    ScreenTab.RECORDS -> {
                        if (hiveFilter != null) {
                            AssistChip(
                                onClick = { viewModel.clearHiveFilter() },
                                label = { Text("Úl ${hiveLabel(hiveFilter)}  ✕") }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val list = hiveFilter?.let { h -> inspections.filter { it.hiveId == h } }
                            ?: inspections
                        InspectionList(list, onDelete = { viewModel.deleteInspection(it) }, onEdit = { viewModel.startEditing(it) })
                    }
                    ScreenTab.OVERVIEW -> {
                        OverviewByStanovisteScreen(
                            inspections = inspections,
                            onHiveClick = { viewModel.showHive(it) }
                        )
                    }
                    ScreenTab.MOTYLICE -> {
                        MotyliceList(
                            stanoviste = stanovisteList,
                            temps = motyliceTemps,
                            loading = motyliceLoading,
                            phoneSet = alertPhone.isNotBlank(),
                            onToggle = { viewModel.toggleMotyliceMonitoring(it) },
                            onSireni = { viewModel.confirmSireni(it) },
                            onSetPhone = { showPhoneDialog = true },
                            onLoadTemps = { viewModel.loadMotyliceTemps(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningBanner(isListening: Boolean, paused: Boolean, partial: String, stanoviste: String?) {
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when {
                    paused -> "\u23F8\uFE0F Pozastaveno \u2013 řekněte „api diktuji“ pro pokračování"
                    isListening -> "\uD83C\uDF99\uFE0F Poslouchám… mluvte"
                    else -> "Zpracovávám…"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
            if (!stanoviste.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "📍 Stanoviště: $stanoviste",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (!paused && partial.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(partial, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Hlasové ovládání: „api pauza“ · „api diktuji“ · „api stop“",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------------------
// Dialogy: jméno uživatele, nové stanoviště
// ---------------------------------------------------------------------------
@Composable
private fun NamePromptDialog(onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { /* nelze zavřít bez zadání jména */ },
        title = { Text("Jak se jmenujete?") },
        text = {
            Column {
                Text(
                    "Vaše jméno nebo přezdívka se uloží k záznamům (např. do exportu CSV).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Jméno / přezdívka") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Uložit") }
        }
    )
}

@Composable
private fun NewStanovisteDialog(hasLocation: Boolean, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Nové stanoviště") },
        text = {
            Column {
                Text(
                    if (hasLocation)
                        "Tuto polohu appka ještě nezná. Jak se stanoviště jmenuje (např. „Vidlákova Lhota“, „Benešov Vršky“)?"
                    else
                        "Polohu se nepodařilo zjistit (chybí GPS signál nebo oprávnění). Zadejte název stanoviště ručně.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Název stanoviště") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Uložit") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Zrušit") }
        }
    )
}

// ---------------------------------------------------------------------------
// Záložka Záznamy
// ---------------------------------------------------------------------------
@Composable
private fun InspectionList(inspections: List<Inspection>, onDelete: (Inspection) -> Unit, onEdit: (Inspection) -> Unit) {
    if (inspections.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Zatím žádné záznamy.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Klepněte na „Diktovat“ a řekněte např.:\n" +
                    "„úl číslo pět, zavíčkovaný plod na dvou rámcích, " +
                    "zásoby tři rámky, matka nalezena v pořádku“.\n" +
                    "Úl jde pojmenovat i jménem, např. „úl Jana“.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(inspections, key = { it.id }) { r -> InspectionCard(r, onDelete = { onDelete(r) }, onEdit = { onEdit(r) }) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun InspectionCard(r: Inspection, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            r.photoPath?.takeIf { it.isNotBlank() }?.let {
                PhotoThumb(it, 64.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Úl ${hiveLabel(r.hiveId)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(listDateFmt.format(Date(r.timestamp)), style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Upravit")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Smazat")
                    }
                }
                val lines = summaryLines(r)
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(lines.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
                }
                if (!r.stanovisteName.isNullOrBlank() || (r.latitude != null && r.longitude != null)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append("📍 ")
                            if (!r.stanovisteName.isNullOrBlank()) append(r.stanovisteName)
                            if (r.latitude != null && r.longitude != null) {
                                if (!r.stanovisteName.isNullOrBlank()) append("  ·  ")
                                append("${r.latitude}, ${r.longitude}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                r.rawTranscript?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "„$it“",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun summaryLines(r: Inspection): List<String> = buildList {
    r.broodChamberFrames?.let { add("plodiště ${numFmt(it)}") }
    r.cappedBroodFrames?.let { add("zavíč. plod ${numFmt(it)}") }
    r.openBroodFrames?.let { add("otev. plod ${numFmt(it)}") }
    r.storesFrames?.let { add("zásoby ${numFmt(it)}") }
    when (r.queenSeen) {
        true -> add("matka ✓${r.queenStatus?.let { " ($it)" } ?: ""}")
        false -> add("matka ✗")
        null -> {}
    }
    r.honeyEstimateKg?.let { add("med ${numFmt(it)} kg") }
    r.feeding?.let { add("krmení: $it") }
    r.treatment?.let { add("léčení: $it") }
}

// ---------------------------------------------------------------------------
// Záložka Přehled úlů
// ---------------------------------------------------------------------------
private fun buildOverview(list: List<Inspection>): List<HiveSummary> =
    list.groupBy { it.hiveId }
        .map { (hive, records) ->
            val newest = records.maxByOrNull { it.timestamp }
            HiveSummary(
                hiveId = hive,
                count = records.size,
                lastTimestamp = newest?.timestamp ?: 0L,
                lastStores = newest?.storesFrames,
                lastBrood = newest?.cappedBroodFrames,
            )
        }
        .sortedBy { it.hiveId }

/** Souhrn jednoho stanoviště pro záložku Přehled – kolik úlů a záznamů k němu patří. */
private data class StanovisteOverview(
    val name: String,
    val hiveCount: Int,
    val recordCount: Int,
    val lastTimestamp: Long,
)

private const val UNKNOWN_STANOVISTE = "Neznámé stanoviště"

private fun buildStanovisteOverview(list: List<Inspection>): List<StanovisteOverview> =
    list.groupBy { it.stanovisteName?.takeIf { s -> s.isNotBlank() } ?: UNKNOWN_STANOVISTE }
        .map { (name, records) ->
            StanovisteOverview(
                name = name,
                hiveCount = records.map { it.hiveId }.distinct().size,
                recordCount = records.size,
                lastTimestamp = records.maxOfOrNull { it.timestamp } ?: 0L,
            )
        }
        .sortedBy { it.name }

/**
 * Záložka "Přehled úlů": nejdřív seznam stanovišť, po rozkliknutí stanoviště
 * seznam úlů, které k němu mají alespoň jeden záznam. Kliknutí na úl pak
 * (stejně jako dřív) přefiltruje záložku Záznamy na tento úl.
 */
@Composable
private fun OverviewByStanovisteScreen(inspections: List<Inspection>, onHiveClick: (String) -> Unit) {
    var selectedStanoviste by remember { mutableStateOf<String?>(null) }
    val sel = selectedStanoviste
    if (sel == null) {
        val stanovisteOverview = remember(inspections) { buildStanovisteOverview(inspections) }
        StanovisteOverviewList(stanovisteOverview, onClick = { selectedStanoviste = it })
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                IconButton(onClick = { selectedStanoviste = null }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět na stanoviště")
                }
                Text(sel, style = MaterialTheme.typography.titleMedium)
            }
            val hivesHere = remember(inspections, sel) {
                buildOverview(
                    inspections.filter { (it.stanovisteName?.takeIf { n -> n.isNotBlank() } ?: UNKNOWN_STANOVISTE) == sel }
                )
            }
            OverviewList(hivesHere, onHiveClick = onHiveClick)
        }
    }
}

@Composable
private fun StanovisteOverviewList(summaries: List<StanovisteOverview>, onClick: (String) -> Unit) {
    if (summaries.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Zatím žádná stanoviště.", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(summaries, key = { it.name }) { s ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClick(s.name) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        val detail = buildList {
                            add("${s.hiveCount} úlů")
                            add("${s.recordCount} záznamů")
                            if (s.lastTimestamp > 0) add("posl. ${listDateFmt.format(Date(s.lastTimestamp))}")
                        }
                        Text(detail.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun OverviewList(summaries: List<HiveSummary>, onHiveClick: (String) -> Unit) {
    if (summaries.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Zatím žádné úly.", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(summaries, key = { it.hiveId }) { s ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onHiveClick(s.hiveId) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Úl ${hiveLabel(s.hiveId)}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        val detail = buildList {
                            add("${s.count} záznamů")
                            if (s.lastTimestamp > 0) add("posl. ${listDateFmt.format(Date(s.lastTimestamp))}")
                            s.lastStores?.let { add("zásoby ${numFmt(it)}") }
                            s.lastBrood?.let { add("plod ${numFmt(it)}") }
                        }
                        Text(detail.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Formulář náhledu záznamu
// ---------------------------------------------------------------------------
@Composable
private fun DraftForm(
    draft: Draft,
    onChange: ((Draft) -> Draft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text("Náhled záznamu", style = MaterialTheme.typography.titleLarge)
        Text(
            "Zkontrolujte a případně opravte. Prázdná pole se neuloží.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        field("Úl (číslo nebo jméno)", draft.hiveId) { v -> onChange { it.copy(hiveId = v) } }
        field("Stanoviště", draft.stanovisteName) { v -> onChange { it.copy(stanovisteName = v) } }

        section("Základ")
        field("Počet rámků v plodišti", draft.broodChamberFrames) { v -> onChange { it.copy(broodChamberFrames = v) } }
        field("Zavíčkovaný plod (rámky)", draft.cappedBroodFrames) { v -> onChange { it.copy(cappedBroodFrames = v) } }
        field("Otevřený plod (rámky)", draft.openBroodFrames) { v -> onChange { it.copy(openBroodFrames = v) } }
        field("Zásoby (rámky)", draft.storesFrames) { v -> onChange { it.copy(storesFrames = v) } }
        field("Matka nalezena (ano/ne)", draft.queenSeen) { v -> onChange { it.copy(queenSeen = v) } }
        field("Stav matky", draft.queenStatus) { v -> onChange { it.copy(queenStatus = v) } }
        field("Vajíčka (ano/ne)", draft.eggsSeen) { v -> onChange { it.copy(eggsSeen = v) } }

        section("Síla a zdraví")
        field("Obsednuto (uličky)", draft.occupiedSeams) { v -> onChange { it.copy(occupiedSeams = v) } }
        field("Mírnost", draft.temperament) { v -> onChange { it.copy(temperament = v) } }
        field("Nemoci / příznaky", draft.diseaseNotes) { v -> onChange { it.copy(diseaseNotes = v) } }
        field("Spad roztočů", draft.miteDrop) { v -> onChange { it.copy(miteDrop = v) } }

        section("Zásahy a krmení")
        field("Zásahy", draft.interventions) { v -> onChange { it.copy(interventions = v) } }
        field("Krmení", draft.feeding) { v -> onChange { it.copy(feeding = v) } }
        field("Léčení", draft.treatment) { v -> onChange { it.copy(treatment = v) } }

        section("Med a odběry")
        field("Med (kg)", draft.honeyEstimateKg) { v -> onChange { it.copy(honeyEstimateKg = v) } }
        field("Odebrané rámky", draft.framesRemoved) { v -> onChange { it.copy(framesRemoved = v) } }
        field("Úkoly do příště", draft.tasksNextTime) { v -> onChange { it.copy(tasksNextTime = v) } }

        section("Fotka a poloha")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCapturePhoto) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (draft.photoPath.isBlank()) "Vyfotit" else "Přefotit")
            }
            if (draft.photoPath.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                PhotoThumb(draft.photoPath, 56.dp)
            }
        }
        if (draft.latitude.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("${draft.latitude}, ${draft.longitude}", style = MaterialTheme.typography.bodySmall)
            }
        }

        section("Poznámka")
        field("Volná poznámka", draft.note) { v -> onChange { it.copy(note = v) } }

        draft.rawTranscript.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("Přepis: „$it“", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Uložit") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Zahodit") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhotoThumb(path: String, size: Dp) {
    val bitmap = remember(path) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Fotka",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.size(size))
    }
}

/** 2.0 -> "2", 2.5 -> "2,5" (české desetinné čárky). */
private fun numFmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else v.toString().replace('.', ',')

@Composable
private fun section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    )
}

// ---------------------------------------------------------------------------
// Záložka Motýlice (hlídání síření rámků proti vosce voskové)
// ---------------------------------------------------------------------------
private val sireniDateFmt = SimpleDateFormat("d. M. yyyy", Locale("cs"))

@Composable
private fun MotyliceList(
    stanoviste: List<Stanoviste>,
    temps: Map<Long, List<Pair<String, Double>>>,
    loading: Set<Long>,
    phoneSet: Boolean,
    onToggle: (Stanoviste) -> Unit,
    onSireni: (Stanoviste) -> Unit,
    onSetPhone: () -> Unit,
    onLoadTemps: (Stanoviste) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (!phoneSet) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Telefonní číslo pro SMS upozornění ještě není zadané.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onSetPhone) { Text("Zadat telefon") }
                }
            }
        }
        if (stanoviste.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Zatím žádná stanoviště.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stanoviště se vytvoří automaticky při prvním diktátu na novém místě.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stanoviste, key = { it.id }) { s ->
                    MotyliceCard(
                        s = s,
                        temps = temps[s.id],
                        loading = loading.contains(s.id),
                        onToggle = { onToggle(s) },
                        onSireni = { onSireni(s) },
                        onLoadTemps = { onLoadTemps(s) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/** "2026-10-07" -> "7. 10." (pokud se nepovede naparsovat, vrátí vstup beze změny). */
private fun shortDate(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2].toInt()}. ${parts[1].toInt()}." else iso
}

@Composable
private fun MotyliceCard(
    s: Stanoviste,
    temps: List<Pair<String, Double>>?,
    loading: Boolean,
    onToggle: () -> Unit,
    onSireni: () -> Unit,
    onLoadTemps: () -> Unit,
) {
    val hasLocation = s.latitude != null && s.longitude != null

    // Dokud je hlídání zapnuté a máme polohu, teploty si natáhneme (jen jednou,
    // dokud je nemáme a zrovna se nestahují – viewModel sám hlídá duplicitní volání).
    LaunchedEffect(s.id, s.motyliceMonitoring, hasLocation) {
        if (s.motyliceMonitoring && hasLocation) onLoadTemps()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = s.motyliceMonitoring,
                    onCheckedChange = { onToggle() },
                    enabled = hasLocation
                )
            }
            if (!hasLocation) {
                Text(
                    "Chybí GPS souřadnice – hlídání nelze zapnout.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (s.motyliceMonitoring) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (s.lastSireniAt != null)
                        "Poslední síření: ${sireniDateFmt.format(Date(s.lastSireniAt))}"
                    else
                        "Síření ještě nebylo zaznamenáno.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSireni) { Text("Síření provedeno") }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Max. teploty – stanoviště „${s.name}“",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                when {
                    loading && temps == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Stahuji teploty…", style = MaterialTheme.typography.bodySmall)
                    }
                    temps == null -> Text("Teploty se zatím nenačetly.", style = MaterialTheme.typography.bodySmall)
                    temps.isEmpty() -> Text(
                        "Data o počasí se nepodařilo stáhnout (appka je možná offline).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> {
                        val now = System.currentTimeMillis()
                        val daysSinceSireni = s.lastSireniAt?.let {
                            ((now - it) / (24L * 60 * 60 * 1000)).toInt() + 1
                        }
                        val windowDays = (daysSinceSireni ?: 7).coerceIn(1, 7)
                        val recent = temps.takeLast(windowDays)
                        val hotDays = recent.count { it.second > 10.0 }
                        Text(
                            "$hotDays z ${recent.size} dní " +
                                (if (s.lastSireniAt != null) "od posledního síření" else "za posledních 7 dní") +
                                " nad 10 °C",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hotDays >= 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        recent.forEach { (date, t) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    shortDate(date),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${numFmt(t)} °C",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (t > 10.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertPhoneDialog(current: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Telefon pro SMS upozornění") },
        text = {
            Column {
                Text(
                    "Na toto číslo appka pošle SMS, když u hlídaného stanoviště hrozí nutnost síření rámků proti motýlici.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Telefonní číslo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Uložit") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Zrušit") }
        }
    )
}
