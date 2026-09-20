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
    var hiveNumber: String = "",
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
            hiveNumber = i(hiveNumber) ?: 0,
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
        )
    }
}
