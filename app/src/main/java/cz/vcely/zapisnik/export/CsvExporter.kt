package cz.vcely.zapisnik.export

import android.content.Context
import cz.vcely.zapisnik.data.Inspection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exportuje záznamy do CSV souboru (oddělovač ; kvůli českému Excelu). */
object CsvExporter {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("cs"))

    private val headers = listOf(
        "Datum", "Stanoviště", "Úl",
        "Plodiště (rám.)", "Zavíčkovaný plod (rám.)", "Otevřený plod (rám.)", "Zásoby (rám.)",
        "Matka nalezena", "Stav matky", "Vajíčka",
        "Obsednuto (uličky)", "Mírnost", "Nemoci", "Spad roztočů",
        "Zásahy", "Krmení", "Léčení",
        "Med (kg)", "Odebrané rámky", "Úkoly do příště",
        "GPS šířka", "GPS délka", "Fotka",
        "Poznámka", "Přepis řeči", "Zapsal(a)"
    )

    fun write(context: Context, list: List<Inspection>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale("cs")).format(Date())
        val file = File(dir, "vceli_zapisnik_$stamp.csv")

        val sb = StringBuilder()
        sb.append('﻿') // BOM, aby Excel poznal UTF-8
        sb.append(headers.joinToString(";") { csv(it) }).append("\r\n")

        for (r in list) {
            val row = listOf(
                dateFmt.format(Date(r.timestamp)),
                r.stanovisteName.orEmpty(),
                r.hiveId,
                num(r.broodChamberFrames),
                num(r.cappedBroodFrames), num(r.openBroodFrames), num(r.storesFrames),
                bool(r.queenSeen), r.queenStatus.orEmpty(), bool(r.eggsSeen),
                num(r.occupiedSeams), r.temperament.orEmpty(), r.diseaseNotes.orEmpty(),
                r.miteDrop?.toString().orEmpty(),
                r.interventions.orEmpty(), r.feeding.orEmpty(), r.treatment.orEmpty(),
                num(r.honeyEstimateKg), num(r.framesRemoved), r.tasksNextTime.orEmpty(),
                r.latitude?.toString().orEmpty(), r.longitude?.toString().orEmpty(),
                r.photoPath?.substringAfterLast('/').orEmpty(),
                r.note.orEmpty(), r.rawTranscript.orEmpty(), r.recordedBy.orEmpty()
            )
            sb.append(row.joinToString(";") { csv(it) }).append("\r\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun num(v: Double?): String =
        v?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString().replace('.', ',') }
            .orEmpty()

    private fun bool(v: Boolean?): String = when (v) {
        true -> "ano"; false -> "ne"; null -> ""
    }

    private fun csv(s: String): String {
        val needsQuote = s.contains(';') || s.contains('"') || s.contains('\n') || s.contains('\r')
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
