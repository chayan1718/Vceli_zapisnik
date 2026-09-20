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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.vcely.zapisnik.data.Inspection
import cz.vcely.zapisnik.parser.Draft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val listDateFmt = SimpleDateFormat("d. M. yyyy HH:mm", Locale("cs"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    viewModel: InspectionViewModel,
    onToggleMic: () -> Unit,
    onCapturePhoto: () -> Unit,
    onExport: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val inspections by viewModel.inspections.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val voicePaused by viewModel.voicePaused.collectAsState()
    val partial by viewModel.partialText.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val hiveFilter by viewModel.hiveFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Včelí zápisník") },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            if (draft == null) {
                ExtendedFloatingActionButton(
                    text = { Text(if (isListening) "Zastavit" else "Diktovat") },
                    icon = {
                        Icon(if (isListening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null)
                    },
                    onClick = onToggleMic,
                    containerColor = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
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
                    ListeningBanner(isListening = isListening, paused = voicePaused, partial = partial)
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
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    ScreenTab.RECORDS -> {
                        if (hiveFilter != null) {
                            AssistChip(
                                onClick = { viewModel.clearHiveFilter() },
                                label = { Text("Úl č. $hiveFilter  ✕") }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val list = hiveFilter?.let { h -> inspections.filter { it.hiveNumber == h } }
                            ?: inspections
                        InspectionList(list, onDelete = { viewModel.deleteInspection(it) })
                    }
                    ScreenTab.OVERVIEW -> {
                        val overview = remember(inspections) { buildOverview(inspections) }
                        OverviewList(overview, onHiveClick = { viewModel.showHive(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningBanner(isListening: Boolean, paused: Boolean, partial: String) {
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
// Záložka Záznamy
// ---------------------------------------------------------------------------
@Composable
private fun InspectionList(inspections: List<Inspection>, onDelete: (Inspection) -> Unit) {
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
                    "zásoby tři rámky, matka nalezena v pořádku“.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(inspections, key = { it.id }) { r -> InspectionCard(r, onDelete = { onDelete(r) }) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun InspectionCard(r: Inspection, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            r.photoPath?.takeIf { it.isNotBlank() }?.let {
                PhotoThumb(it, 64.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Úl č. ${r.hiveNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(listDateFmt.format(Date(r.timestamp)), style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Smazat")
                    }
                }
                val lines = summaryLines(r)
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(lines.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
                }
                if (r.latitude != null && r.longitude != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "📍 ${r.latitude}, ${r.longitude}",
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
    list.groupBy { it.hiveNumber }
        .map { (hive, records) ->
            val newest = records.maxByOrNull { it.timestamp }
            HiveSummary(
                hiveNumber = hive,
                count = records.size,
                lastTimestamp = newest?.timestamp ?: 0L,
                lastStores = newest?.storesFrames,
                lastBrood = newest?.cappedBroodFrames,
            )
        }
        .sortedBy { it.hiveNumber }

@Composable
private fun OverviewList(summaries: List<HiveSummary>, onHiveClick: (Int) -> Unit) {
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
            items(summaries, key = { it.hiveNumber }) { s ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onHiveClick(s.hiveNumber) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Úl č. ${s.hiveNumber}", style = MaterialTheme.typography.titleMedium)
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

        field("Úl č.", draft.hiveNumber) { v -> onChange { it.copy(hiveNumber = v) } }

        section("Základ")
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
