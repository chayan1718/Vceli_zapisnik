package cz.vcely.zapisnik.parser

import cz.vcely.zapisnik.data.Inspection

/**
 * Rozpracovaný záznam. Všechna pole jsou textová – tak je snadno zobrazit
 * a upravit v UI. Prázdný řetězec znamená "nevyplněno".
 *
 * Parser sem zapisuje výsledek rozboru řeči, uživatel může cokoli přepsat
 * a při uložení se Draft převede na [Inspection].
 */
data class Draft(
    var hiveId: String = "",        // číslo úlu ("5") nebo jméno ("Jana")
    var stanovisteName: String = "",
    var broodChamberFrames: String = "",
    var cappedBroodFrames: String = "",
    var openBroodFrames: String = "",
    var storesFrames: String = "",
    var queenSeen: String = "",      // "ano" / "ne" / ""
    var queenStatus: String = "",
    var eggsSeen: String = "",        // "ano" / "ne" / ""
    var occupiedSeams: String = "",
    var temperament: String = "",
    var diseaseNotes: String = "",
    var miteDrop: String = "",
    var interventions: String = "",
    var feeding: String = "",
    var treatment: String = "",
    var honeyEstimateKg: String = "",
    var framesRemoved: String = "",
    var tasksNextTime: String = "",
    var note: String = "",
    var photoPath: String = "",
    var latitude: String = "",
    var longitude: String = "",
    var rawTranscript: String = "",
    var timestamp: Long = 0L,        // datum a čas záznamu (ms); 0 = teď, při uložení
) {
    fun toInspection(): Inspection {
        fun d(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
        fun i(s: String): Int? = s.trim().toIntOrNull()
        fun b(s: String): Boolean? = when (s.trim().lowercase()) {
            "ano", "true", "1" -> true
            "ne", "false", "0" -> false
            else -> null
        }
        fun t(s: String): String? = s.trim().ifBlank { null }

        return Inspection(
            hiveId = hiveId.trim(),
            stanovisteName = t(stanovisteName),
            broodChamberFrames = d(broodChamberFrames),
            cappedBroodFrames = d(cappedBroodFrames),
            openBroodFrames = d(openBroodFrames),
            storesFrames = d(storesFrames),
            queenSeen = b(queenSeen),
            queenStatus = t(queenStatus),
            eggsSeen = b(eggsSeen),
            occupiedSeams = d(occupiedSeams),
            temperament = t(temperament),
            diseaseNotes = t(diseaseNotes),
            miteDrop = i(miteDrop),
            interventions = t(interventions),
            feeding = t(feeding),
            treatment = t(treatment),
            honeyEstimateKg = d(honeyEstimateKg),
            framesRemoved = d(framesRemoved),
            tasksNextTime = t(tasksNextTime),
            photoPath = t(photoPath),
            latitude = d(latitude),
            longitude = d(longitude),
            note = t(note),
            rawTranscript = t(rawTranscript),
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
        )
    }

    companion object {
        /** Zpětné naplnění Draftu z existujícího záznamu – pro editaci. */
        fun fromInspection(i: Inspection): Draft {
            fun s(v: Double?): String = v?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            }.orEmpty()
            fun s(v: Int?): String = v?.toString().orEmpty()
            fun s(v: Boolean?): String = when (v) { true -> "ano"; false -> "ne"; null -> "" }

            return Draft(
                hiveId = i.hiveId,
                stanovisteName = i.stanovisteName.orEmpty(),
                broodChamberFrames = s(i.broodChamberFrames),
                cappedBroodFrames = s(i.cappedBroodFrames),
                openBroodFrames = s(i.openBroodFrames),
                storesFrames = s(i.storesFrames),
                queenSeen = s(i.queenSeen),
                queenStatus = i.queenStatus.orEmpty(),
                eggsSeen = s(i.eggsSeen),
                occupiedSeams = s(i.occupiedSeams),
                temperament = i.temperament.orEmpty(),
                diseaseNotes = i.diseaseNotes.orEmpty(),
                miteDrop = s(i.miteDrop),
                interventions = i.interventions.orEmpty(),
                feeding = i.feeding.orEmpty(),
                treatment = i.treatment.orEmpty(),
                honeyEstimateKg = s(i.honeyEstimateKg),
                framesRemoved = s(i.framesRemoved),
                tasksNextTime = i.tasksNextTime.orEmpty(),
                note = i.note.orEmpty(),
                photoPath = i.photoPath.orEmpty(),
                latitude = s(i.latitude),
                longitude = s(i.longitude),
                rawTranscript = i.rawTranscript.orEmpty(),
                timestamp = i.timestamp,
            )
        }
    }
}
