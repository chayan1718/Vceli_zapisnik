package cz.vcely.zapisnik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Jeden záznam z prohlídky úlu. Pokrývá všechny čtyři skupiny kolonek:
 * základ (plod, zásoby, matka), síla a zdraví, zásahy a krmení, med a odběry.
 *
 * Číselné údaje o rámcích jsou Double kvůli půlkám ("na dvou a půl rámcích").
 * Všechna pole kromě identifikátorů jsou nullable – nediktované položky zůstávají prázdné.
 */
@Entity(tableName = "inspections")
data class Inspection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hiveNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // --- Základ: plod, zásoby, matka ---
    val cappedBroodFrames: Double? = null,   // zavíčkovaný plod (rámky)
    val openBroodFrames: Double? = null,     // otevřený plod (rámky)
    val storesFrames: Double? = null,        // zásoby (rámky)
    val queenSeen: Boolean? = null,          // matka nalezena?
    val queenStatus: String? = null,         // stav matky (v pořádku, klade, trubčí…)
    val eggsSeen: Boolean? = null,           // vajíčka nalezena?

    // --- Síla a zdraví ---
    val occupiedSeams: Double? = null,       // obsednuté uličky / rámky
    val temperament: String? = null,         // mírnost (mírná, bodavá…)
    val diseaseNotes: String? = null,        // příznaky nemocí (varroáza, mor…)
    val miteDrop: Int? = null,               // spad roztočů

    // --- Zásahy a krmení ---
    val interventions: String? = null,       // rozšíření/zúžení, mezistěny, medník…
    val feeding: String? = null,             // krmení (cukr/sirup, množství)
    val treatment: String? = null,           // léčení

    // --- Med a odběry ---
    val honeyEstimateKg: Double? = null,     // odhad / výnos medu (kg)
    val framesRemoved: Double? = null,       // odebrané medné rámky
    val tasksNextTime: String? = null,       // úkoly do příště

    // --- Fotka a poloha ---
    val photoPath: String? = null,           // cesta k pořízené fotce
    val latitude: Double? = null,            // GPS šířka
    val longitude: Double? = null,           // GPS délka

    // --- Metadata ---
    val rawTranscript: String? = null,       // původní přepis řeči
    val note: String? = null,                // volná poznámka
)
