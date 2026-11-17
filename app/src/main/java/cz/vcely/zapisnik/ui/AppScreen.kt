package cz.vcely.zapisnik.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Card
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.vcely.zapisnik.data.Inspection
import cz.vcely.zapisnik.data.Stanoviste
import cz.vcely.zapisnik.parser.Draft
import cz.vcely.zapisnik.tasks.SeasonalTasks
import java.text.SimpleDateFormat
import java.util.Calendar
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
    onBulkDictate: (String) -> Unit,
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
    val appContext = LocalContext.current
    val appVersion = remember {
        try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) { "" }
    }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    val bulkDraft by viewModel.bulkDraft.collectAsState()
    val bulkHives by viewModel.bulkHives.collectAsState()
    val bulkStanoviste by viewModel.bulkStanoviste.collectAsState()
    val overviewStanoviste by viewModel.overviewStanoviste.collectAsState()
    val overviewHive by viewModel.overviewHive.collectAsState()
    // Systémové tlačítko Zpět v Přehledu jde o úroveň výš (úl -> stanoviště -> seznam stanovišť).
    BackHandler(enabled = draft == null && tab == ScreenTab.OVERVIEW && overviewStanoviste != null) {
        if (overviewHive != null) viewModel.overviewHive.value = null
        else viewModel.overviewStanoviste.value = null
    }
    var showVarroa by remember { mutableStateOf(false) }
    // Smazání záznamu se vždy potvrzuje, ať se nesmaže omylem.
    var pendingDelete by remember { mutableStateOf<Inspection?>(null) }
    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Smazat záznam?") },
            text = {
                Text(
                    "Úl ${hiveLabel(rec.hiveId)}, ${listDateFmt.format(Date(rec.timestamp))}. " +
                        "Smazání nejde vrátit zpět."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteInspection(rec); pendingDelete = null }) { Text("Ano") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Ne") }
            }
        )
    }

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
    bulkDraft?.let { bd ->
        BulkDialog(
            draft = bd,
            hives = bulkHives,
            onSave = { viewModel.saveBulk(it) },
            onDiscard = { viewModel.discardBulk() },
            onUpdate = { viewModel.updateBulkDraft(it) },
            onReparse = { viewModel.reparseBulkDraft() }
        )
    }
    if (showVarroa) {
        VarroaScreen(
            onUse = { count, days, photo -> viewModel.applyVarroa(count, days, photo); showVarroa = false },
            onClose = { showVarroa = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Včelí zápisník" + if (appVersion.isBlank()) "" else " v$appVersion")
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
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            val current = draft
            if (current != null) {
                DraftForm(
                    draft = current,
                    onChange = { viewModel.updateDraft(it) },
                    onSave = { viewModel.saveDraft() },
                    onDiscard = { viewModel.discardDraft() },
                    onCapturePhoto = onCapturePhoto,
                    onReparse = { viewModel.reparseDraft() }
                )
            } else {
                if (isListening || partial.isNotBlank()) {
                    ListeningBanner(
                        isListening = isListening,
                        paused = voicePaused,
                        partial = partial,
                        stanoviste = bulkStanoviste?.let { "$it (hromadný záznam pro všechny úly)" } ?: currentStanoviste
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
                    Tab(
                        selected = tab == ScreenTab.MONTHLY,
                        onClick = { viewModel.setTab(ScreenTab.MONTHLY) },
                        text = { Text("Úkoly") }
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
                        InspectionList(list, onDelete = { pendingDelete = it }, onEdit = { viewModel.startEditing(it) })
                    }
                    ScreenTab.OVERVIEW -> {
                        OverviewByStanovisteScreen(
                            inspections = inspections,
                            selectedStanoviste = overviewStanoviste,
                            selectedHive = overviewHive,
                            onSelectStanoviste = { viewModel.overviewStanoviste.value = it; viewModel.overviewHive.value = null },
                            onSelectHive = { viewModel.overviewHive.value = it },
                            onBulkDictate = onBulkDictate,
                            onEdit = { viewModel.startEditing(it) },
                            onDelete = { pendingDelete = it }
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
                            onLoadTemps = { viewModel.loadMotyliceTemps(it) },
                            onRename = { s, newName -> viewModel.renameStanoviste(s, newName) }
                        )
                    }
                    ScreenTab.MONTHLY -> {
                        MonthlyTasksScreen()
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
            r.varroaPhotoPath?.takeIf { it.isNotBlank() }?.let {
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
private fun OverviewByStanovisteScreen(
    inspections: List<Inspection>,
    selectedStanoviste: String?,
    selectedHive: String?,
    onSelectStanoviste: (String?) -> Unit,
    onSelectHive: (String?) -> Unit,
    onBulkDictate: (String) -> Unit,
    onEdit: (Inspection) -> Unit,
    onDelete: (Inspection) -> Unit,
) {
    val sel = selectedStanoviste
    // Přepínač "Úly / Časová osa" na úrovni stanoviště (drží se i po otevření úlu a návratu zpět).
    var showTimeline by remember(selectedStanoviste) { mutableStateOf(false) }
    if (sel == null) {
        // 1. úroveň: stanoviště
        val stanovisteOverview = remember(inspections) { buildStanovisteOverview(inspections) }
        StanovisteOverviewList(stanovisteOverview, onClick = { onSelectStanoviste(it) }, onBulk = onBulkDictate)
    } else if (selectedHive == null) {
        // 2. úroveň: úly stanoviště
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                IconButton(onClick = { onSelectStanoviste(null) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět na stanoviště")
                }
                Text(sel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (sel != UNKNOWN_STANOVISTE) {
                    OutlinedButton(onClick = { onBulkDictate(sel) }) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hromadný záznam")
                    }
                }
            }
            val recordsHere = remember(inspections, sel) {
                inspections.filter { (it.stanovisteName?.takeIf { n -> n.isNotBlank() } ?: UNKNOWN_STANOVISTE) == sel }
            }
            val hivesHere = remember(recordsHere) { buildOverview(recordsHere) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChip(selected = !showTimeline, onClick = { showTimeline = false }, label = { Text("Úly") })
                FilterChip(selected = showTimeline, onClick = { showTimeline = true }, label = { Text("Časová osa") })
            }
            if (showTimeline) {
                InspectionTimeline(
                    records = recordsHere,
                    groupByDay = true,
                    onHiveClick = { onSelectHive(it) },
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            } else {
                OverviewList(hivesHere, onHiveClick = { onSelectHive(it) })
            }
        }
    } else {
        // 3. úroveň: všechny záznamy úlu od nejnovějšího (včetně hromadných)
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                IconButton(onClick = { onSelectHive(null) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět na úly stanoviště")
                }
                Column(Modifier.weight(1f)) {
                    Text("Úl ${hiveLabel(selectedHive)}", style = MaterialTheme.typography.titleMedium)
                    Text(sel, style = MaterialTheme.typography.labelSmall)
                }
            }
            val records = remember(inspections, selectedHive) {
                inspections.filter { it.hiveId == selectedHive }.sortedByDescending { it.timestamp }
            }
            InspectionTimeline(
                records = records,
                groupByDay = false,
                onHiveClick = null,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun StanovisteOverviewList(
    summaries: List<StanovisteOverview>,
    onClick: (String) -> Unit,
    onBulk: (String) -> Unit,
) {
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
                        if (s.name != UNKNOWN_STANOVISTE) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { onBulk(s.name) }) {
                                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hromadný záznam")
                            }
                        }
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
    showHive: Boolean = true,
    showPhoto: Boolean = true,
    saveLabel: String = "Uložit",
    discardLabel: String? = "Zahodit",
    showButtons: Boolean = true,
    modifier: Modifier = Modifier,
    onReparse: (() -> Unit)? = null,
) {
    val nowTs = remember { System.currentTimeMillis() }
    var confirmReparse by remember { mutableStateOf(false) }
    if (confirmReparse) {
        AlertDialog(
            onDismissRequest = { confirmReparse = false },
            title = { Text("Znovu načíst pole z přepisu?") },
            text = {
                Text(
                    "Pole záznamu (rámky, matka, krmení, léčení, poznámka…) se přepíšou tím, " +
                        "co se rozpozná z opraveného přepisu. Úl, stanoviště, datum a fotka zůstanou."
                )
            },
            confirmButton = { TextButton(onClick = { confirmReparse = false; onReparse?.invoke() }) { Text("Přepsat pole") } },
            dismissButton = { TextButton(onClick = { confirmReparse = false }) { Text("Zrušit") } }
        )
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text("Náhled záznamu", style = MaterialTheme.typography.titleLarge)
        Text(
            "Zkontrolujte a případně opravte. Prázdná pole se neuloží.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        if (showHive) field("Úl (číslo nebo jméno)", draft.hiveId) { v -> onChange { it.copy(hiveId = v) } }
        field("Stanoviště", draft.stanovisteName) { v -> onChange { it.copy(stanovisteName = v) } }
        Spacer(Modifier.height(4.dp))
        DateTimeField("Datum a čas záznamu", if (draft.timestamp > 0) draft.timestamp else nowTs) { ts ->
            onChange { it.copy(timestamp = ts) }
        }

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

        if (showPhoto) {
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
            if (draft.varroaPhotoPath.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                PhotoThumb(draft.varroaPhotoPath, 56.dp)
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

        }

        section("Poznámka")
        field("Volná poznámka", draft.note) { v -> onChange { it.copy(note = v) } }

        section("Přepis hlasového záznamu")
        OutlinedTextField(
            value = draft.rawTranscript,
            onValueChange = { v -> onChange { it.copy(rawTranscript = v) } },
            label = { Text("Přepis řeči (opravte chyby)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        if (onReparse != null && draft.rawTranscript.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { confirmReparse = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Znovu načíst pole z přepisu")
            }
        }

        Spacer(Modifier.height(16.dp))
        if (showButtons) {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(saveLabel) }
            if (discardLabel != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text(discardLabel) }
            }
        }
        Spacer(Modifier.height(if (showButtons) 24.dp else 120.dp))
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
    var showFull by remember { mutableStateOf(false) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Fotka (klepnutím otevřete)",
            modifier = Modifier.size(size).clickable { showFull = true },
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.size(size))
    }
    if (showFull) PhotoFullDialog(path) { showFull = false }
}

/** Fotka na celou obrazovku: dvěma prsty se přibližuje a posouvá, "Zavřít" ji zavře. */
@Composable
private fun PhotoFullDialog(path: String, onClose: () -> Unit) {
    val bitmap = remember(path) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            var bmp = BitmapFactory.decodeFile(path, opts)
            if (bmp != null) {
                val rotation = try {
                    when (android.media.ExifInterface(path)
                        .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } catch (e: Exception) { 0f }
                if (rotation != 0f) {
                    val m = android.graphics.Matrix().apply { postRotate(rotation) }
                    bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
            }
            bmp?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Fotka",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomGestures(
                                onTransform = { zoom, panX, panY ->
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                                    else { offsetX += panX; offsetY += panY }
                                }
                            )
                            .graphicsLayer(
                                scaleX = scale, scaleY = scale,
                                translationX = offsetX, translationY = offsetY
                            )
                    )
                } else {
                    Text(
                        "Fotku se nepodařilo otevřít (soubor chybí).",
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) { Text("Zavřít", color = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
}

private fun Modifier.zoomGestures(onTransform: (zoom: Float, panX: Float, panY: Float) -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan.x, pan.y) }
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
    onRename: (Stanoviste, String) -> Unit,
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
                        onLoadTemps = { onLoadTemps(s) },
                        onRename = { newName -> onRename(s, newName) }
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
    onRename: (String) -> Unit,
) {
    val hasLocation = s.latitude != null && s.longitude != null
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        RenameStanovisteDialog(
            current = s.name,
            onConfirm = { newName -> onRename(newName); showRenameDialog = false },
            onCancel = { showRenameDialog = false }
        )
    }

    // Dokud je hlídání zapnuté a máme polohu, teploty si natáhneme (jen jednou,
    // dokud je nemáme a zrovna se nestahují – viewModel sám hlídá duplicitní volání).
    LaunchedEffect(s.id, s.motyliceMonitoring, hasLocation) {
        if (s.motyliceMonitoring && hasLocation) onLoadTemps()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Přejmenovat stanoviště")
                }
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

// ---------------------------------------------------------------------------
// Hromadný záznam: návrh pro všechny úly stanoviště
// ---------------------------------------------------------------------------
@Composable
private fun BulkDialog(
    draft: Draft,
    hives: List<String>,
    onSave: (List<String>) -> Unit,
    onDiscard: () -> Unit,
    onUpdate: ((Draft) -> Draft) -> Unit,
    onReparse: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val nowTs = remember { System.currentTimeMillis() }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    val selected = hives.filter { it !in excluded }
    val rec = draft.toInspection()
    val lines = buildList {
        addAll(summaryLines(rec))
        rec.interventions?.let { add("zásah: $it") }
        rec.diseaseNotes?.let { add("nemoci: $it") }
        rec.miteDrop?.let { add("spad roztočů $it") }
        rec.tasksNextTime?.let { add("úkol: $it") }
        rec.note?.let { add("poznámka: $it") }
    }
    if (editing) {
        Dialog(onDismissRequest = { editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
                    // Tlačítko je nahoře mimo posuvnou část, aby bylo vždy na dosah.
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Úprava hromadného záznamu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(onClick = { editing = false }) { Text("Hotovo") }
                    }
                    DraftForm(
                        draft = draft,
                        onChange = onUpdate,
                        onSave = { editing = false },
                        onDiscard = { editing = false },
                        onCapturePhoto = {},
                        showHive = false,
                        showPhoto = false,
                        showButtons = false,
                        modifier = Modifier.weight(1f),
                        onReparse = onReparse
                    )
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { /* nezavírat omylem klepnutím mimo okno */ },
        title = { Text("Hromadný záznam: ${draft.stanovisteName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Záznam se uloží zvlášť pro každý zaškrtnutý úl.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (lines.isEmpty()) {
                    Text("V diktátu jsem nic nerozpoznal.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                draft.rawTranscript.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Přepis: „$it“", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                DateTimeField("Datum a čas záznamu", if (draft.timestamp > 0) draft.timestamp else nowTs) { ts ->
                    onUpdate { it.copy(timestamp = ts) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upravit návrh")
                }
                Spacer(Modifier.height(12.dp))
                Text("Úly na stanovišti", style = MaterialTheme.typography.titleSmall)
                if (hives.isEmpty()) {
                    Text("Na tomto stanovišti zatím není žádný úl se záznamem.", style = MaterialTheme.typography.bodyMedium)
                }
                hives.forEach { id ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { excluded = if (id in excluded) excluded - id else excluded + id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = id !in excluded,
                            onCheckedChange = { excluded = if (it) excluded - id else excluded + id }
                        )
                        Text("Úl ${hiveLabel(id)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() && lines.isNotEmpty(),
                onClick = { onSave(selected) }
            ) { Text("Uložit pro ${selected.size}") }
        },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Zahodit") } }
    )
}

// ---------------------------------------------------------------------------
// Výběr data a času (klepnutí na pole otevře kalendář, pak hodiny)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(label: String, millis: Long, onChange: (Long) -> Unit) {
    var step by remember { mutableStateOf(0) } // 0 nic, 1 kalendář, 2 hodiny
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val zone = java.time.ZoneId.systemDefault()
    val current = java.time.Instant.ofEpochMilli(millis).atZone(zone)

    Text(label, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
        Text(listDateFmt.format(Date(millis)))
    }

    if (step == 1) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate()
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val sel = state.selectedDateMillis
                    if (sel != null) {
                        pickedDate = java.time.Instant.ofEpochMilli(sel).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        step = 2
                    } else step = 0
                }) { Text("Dál") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Zrušit") } }
        ) { DatePicker(state = state) }
    }
    if (step == 2) {
        val time = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("Čas záznamu") },
            text = { TimePicker(state = time) },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate?.let { d ->
                        onChange(d.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli())
                    }
                    step = 0
                }) { Text("Použít") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Zrušit") } }
        )
    }
}

// ---------------------------------------------------------------------------
// Časová osa úlu a stanoviště
// Na prvním místě je síla včelstva (obsazené uličky), pak matka a léčení.
// ---------------------------------------------------------------------------
private enum class TimelineFilter(val label: String) {
    ALL("Vše"), STRENGTH("Síla"), QUEEN("Matka"), TREATMENT("Léčení"), PHOTO("S fotkou")
}

private sealed interface TimelineRow {
    data class Day(val key: String, val label: String, val hiveCount: Int) : TimelineRow
    data class Entry(val record: Inspection) : TimelineRow
}

private val dayFmt = SimpleDateFormat("EEEE d. M. yyyy", Locale("cs"))
private val dayKeyFmt = SimpleDateFormat("yyyyMMdd", Locale("cs"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale("cs"))
private val shortDateFmt = SimpleDateFormat("d. M.", Locale("cs"))

private fun Inspection.matches(f: TimelineFilter): Boolean = when (f) {
    TimelineFilter.ALL -> true
    TimelineFilter.STRENGTH -> occupiedSeams != null
    TimelineFilter.QUEEN -> queenSeen != null || !queenStatus.isNullOrBlank() || eggsSeen != null
    TimelineFilter.TREATMENT -> !treatment.isNullOrBlank()
    TimelineFilter.PHOTO -> !photoPath.isNullOrBlank() || !varroaPhotoPath.isNullOrBlank()
}

/** Změna síly proti předchozímu záznamu stejného úlu, který má sílu vyplněnou (podle id záznamu). */
private fun strengthDeltas(all: List<Inspection>): Map<Long, Double> {
    val out = HashMap<Long, Double>()
    all.groupBy { it.hiveId }.forEach { (_, recs) ->
        var prev: Double? = null
        recs.sortedBy { it.timestamp }.forEach { r ->
            val cur = r.occupiedSeams
            if (cur != null) {
                val p = prev
                if (p != null) out[r.id] = cur - p
                prev = cur
            }
        }
    }
    return out
}

private fun seamsWord(v: Double): String {
    if (v != v.toLong().toDouble()) return "uličky"
    return when (v.toLong()) { 1L -> "ulička"; 2L, 3L, 4L -> "uličky"; else -> "uliček" }
}

private fun relativeAge(ts: Long): String {
    val days = ((System.currentTimeMillis() - ts) / 86_400_000L).coerceAtLeast(0)
    return when {
        days == 0L -> "dnes"
        days == 1L -> "včera"
        days < 14 -> "před $days dny"
        days < 60 -> "před ${days / 7} týdny"
        else -> "před ${days / 30} měsíci"
    }
}

private fun buildTimelineRows(list: List<Inspection>, groupByDay: Boolean): List<TimelineRow> {
    val sorted = list.sortedByDescending { it.timestamp }
    if (!groupByDay) return sorted.map { TimelineRow.Entry(it) }
    val out = ArrayList<TimelineRow>()
    sorted.groupBy { dayKeyFmt.format(Date(it.timestamp)) }.forEach { (key, recs) ->
        out.add(
            TimelineRow.Day(
                key,
                dayFmt.format(Date(recs.first().timestamp)).replaceFirstChar { it.uppercase() },
                recs.map { it.hiveId }.distinct().size
            )
        )
        recs.sortedWith(
            compareBy<Inspection>(
                { it.hiveId.toIntOrNull() == null },
                { it.hiveId.toIntOrNull() ?: 0 },
                { it.hiveId },
                { -it.timestamp }
            )
        ).forEach { out.add(TimelineRow.Entry(it)) }
    }
    return out
}

@Composable
private fun TimelineTag(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TimelineSummary(records: List<Inspection>) {
    if (records.isEmpty()) return
    val withSeams = records.filter { it.occupiedSeams != null }.sortedByDescending { it.timestamp }
    val withMites = records.filter { it.miteDrop != null }.sortedByDescending { it.timestamp }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${records.size} prohlídek  ·  poslední ${relativeAge(records.maxOf { it.timestamp })}",
                style = MaterialTheme.typography.bodyMedium
            )
            withSeams.firstOrNull()?.let { last ->
                val v = last.occupiedSeams ?: return@let
                val before = withSeams.getOrNull(1)?.occupiedSeams
                Text(
                    "Síla: ${numFmt(v)} ${seamsWord(v)} (${shortDateFmt.format(Date(last.timestamp))})" +
                        (before?.let { ", dříve ${numFmt(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            withMites.firstOrNull()?.let { last ->
                val before = withMites.getOrNull(1)?.miteDrop
                Text(
                    "Spad roztočů: " + (before?.let { "$it → " } ?: "") + last.miteDrop,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionTimeline(
    records: List<Inspection>,
    groupByDay: Boolean,
    onHiveClick: ((String) -> Unit)?,
    onEdit: (Inspection) -> Unit,
    onDelete: (Inspection) -> Unit,
) {
    var filter by remember { mutableStateOf(TimelineFilter.ALL) }
    val deltas = remember(records) { strengthDeltas(records) }
    val rows = remember(records, filter, groupByDay) {
        buildTimelineRows(records.filter { it.matches(filter) }, groupByDay)
    }
    Column(Modifier.fillMaxSize()) {
        if (!groupByDay) TimelineSummary(records)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimelineFilter.values().forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(
                if (records.isEmpty()) "Zatím žádné záznamy." else "Žádné záznamy pro tento filtr.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn {
                itemsIndexed(
                    rows,
                    key = { _, row ->
                        when (row) {
                            is TimelineRow.Day -> "d${row.key}"
                            is TimelineRow.Entry -> "e${row.record.id}"
                        }
                    }
                ) { i, row ->
                    TimelineRowView(
                        row = row,
                        isFirst = i == 0,
                        isLast = i == rows.lastIndex,
                        delta = (row as? TimelineRow.Entry)?.let { deltas[it.record.id] },
                        groupByDay = groupByDay,
                        onHiveClick = onHiveClick,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineRowView(
    row: TimelineRow,
    isFirst: Boolean,
    isLast: Boolean,
    delta: Double?,
    groupByDay: Boolean,
    onHiveClick: ((String) -> Unit)?,
    onEdit: (Inspection) -> Unit,
    onDelete: (Inspection) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val lineColor = cs.outlineVariant
    val dotColor = when (row) {
        is TimelineRow.Day -> cs.outline
        is TimelineRow.Entry -> when {
            row.record.queenSeen == false -> cs.error
            !row.record.treatment.isNullOrBlank() -> cs.tertiary
            else -> cs.primary
        }
    }
    val isDay = row is TimelineRow.Day
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotY = 14.dp.toPx()
                    drawLine(
                        lineColor,
                        Offset(x, if (isFirst) dotY else 0f),
                        Offset(x, if (isLast) dotY else size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    if (isDay) drawCircle(dotColor, 6.dp.toPx(), Offset(x, dotY), style = Stroke(2.dp.toPx()))
                    else drawCircle(dotColor, 5.dp.toPx(), Offset(x, dotY))
                }
        )
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 12.dp)) {
            when (row) {
                is TimelineRow.Day -> {
                    Text(row.label, style = MaterialTheme.typography.titleSmall)
                    Text("${row.hiveCount} úlů", style = MaterialTheme.typography.labelSmall)
                }
                is TimelineRow.Entry -> {
                    val r = row.record
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (groupByDay) {
                                Text(
                                    "Úl ${hiveLabel(r.hiveId)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (onHiveClick != null) cs.primary else cs.onSurface,
                                    modifier = if (onHiveClick != null) Modifier.clickable { onHiveClick(r.hiveId) } else Modifier
                                )
                                Text(timeFmt.format(Date(r.timestamp)), style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text(listDateFmt.format(Date(r.timestamp)), style = MaterialTheme.typography.titleSmall)
                                Text(relativeAge(r.timestamp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { onEdit(r) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Upravit")
                        }
                        IconButton(onClick = { onDelete(r) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Smazat")
                        }
                    }

                    // Hlavní údaje: síla, matka, léčení
                    val queenText = buildString {
                        when (r.queenSeen) {
                            true -> append("Matka ✓")
                            false -> append("Matka nenalezena")
                            null -> if (!r.queenStatus.isNullOrBlank() || r.eggsSeen != null) append("Matka")
                        }
                        if (isNotEmpty()) {
                            r.queenStatus?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                            if (r.eggsSeen == true) append("  ·  vajíčka ✓")
                        }
                    }
                    val treatment = r.treatment?.takeIf { it.isNotBlank() }
                    if (r.occupiedSeams != null || queenText.isNotEmpty() || treatment != null) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            r.occupiedSeams?.let { v ->
                                val d = delta?.takeIf { it != 0.0 }
                                val change = d?.let { " (${if (it > 0) "+" else "−"}${numFmt(kotlin.math.abs(it))})" }.orEmpty()
                                TimelineTag("Síla: ${numFmt(v)} ${seamsWord(v)}$change", cs.primaryContainer, cs.onPrimaryContainer)
                            }
                            if (queenText.isNotEmpty()) {
                                if (r.queenSeen == false) TimelineTag(queenText, cs.errorContainer, cs.onErrorContainer)
                                else TimelineTag(queenText, cs.secondaryContainer, cs.onSecondaryContainer)
                            }
                            treatment?.let { TimelineTag("Léčení: $it", cs.tertiaryContainer, cs.onTertiaryContainer) }
                        }
                    }

                    // Ostatní údaje
                    val other = buildList {
                        r.miteDrop?.let { add("spad $it") }
                        r.broodChamberFrames?.let { add("plodiště ${numFmt(it)}") }
                        r.cappedBroodFrames?.let { add("zavíč. plod ${numFmt(it)}") }
                        r.openBroodFrames?.let { add("otev. plod ${numFmt(it)}") }
                        r.storesFrames?.let { add("zásoby ${numFmt(it)}") }
                        r.honeyEstimateKg?.let { add("med ${numFmt(it)} kg") }
                        r.framesRemoved?.let { add("odebráno ${numFmt(it)} rámků") }
                        r.feeding?.takeIf { it.isNotBlank() }?.let { add("krmení: $it") }
                        r.temperament?.takeIf { it.isNotBlank() }?.let { add(it) }
                        r.diseaseNotes?.takeIf { it.isNotBlank() }?.let { add("nemoci: $it") }
                        r.interventions?.takeIf { it.isNotBlank() }?.let { add("zásah: $it") }
                        r.tasksNextTime?.takeIf { it.isNotBlank() }?.let { add("do příště: $it") }
                    }
                    if (other.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(other.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
                    }
                    r.note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }

                    val photos = listOfNotNull(
                        r.photoPath?.takeIf { it.isNotBlank() },
                        r.varroaPhotoPath?.takeIf { it.isNotBlank() }
                    )
                    if (photos.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photos.forEach { PhotoThumb(it, 56.dp) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Měsíční úkoly včelaře (sezónní kalendář)
// ---------------------------------------------------------------------------
@Composable
private fun MonthlyTasksScreen() {
    val currentMonth = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }
    var month by remember { mutableStateOf(currentMonth) }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            IconButton(onClick = { month = if (month == 1) 12 else month - 1 }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Předchozí měsíc")
            }
            Text(
                SeasonalTasks.monthNames[month - 1],
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { month = if (month == 12) 1 else month + 1 }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Další měsíc")
            }
        }
        if (month == currentMonth) {
            Text(
                "Aktuální měsíc",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SeasonalTasks.tasks[month].orEmpty()) { task ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(task, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun RenameStanovisteDialog(current: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Přejmenovat stanoviště") },
        text = {
            Column {
                Text(
                    "Nový název se použije pro všechny úly tohoto stanoviště i ve " +
                        "všech přehledech a záznamech.",
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
