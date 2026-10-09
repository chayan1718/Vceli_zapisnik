package cz.vcely.zapisnik.parser

import java.text.Normalizer

/**
 * Pravidlový (offline) parser českého přepisu prohlídky úlu.
 *
 * Funguje bez internetu i bez API klíče. Rozpozná běžné formulace typu:
 *   "úl číslo pět, zavíčkovaný plod na dvou rámcích, zásoby tři rámky,
 *    matka nalezena v pořádku, mírné včelstvo, nakrmeno dvěma litry sirupu"
 *
 * Princip: text se převede na malá písmena a odstraní se diakritika (aby
 * rozbor fungoval i když rozpoznávání řeči vrátí text bez háčků/čárek).
 * Pak se hledají klíčová slova a poblíž nich číslovky.
 *
 * Pro maximální odolnost vůči volným formulacím lze místo tohoto parseru
 * použít [LlmParser] (jazykový model) – viz LlmParser.kt.
 */
object InspectionParser {

    fun parse(transcript: String, defaultHive: String? = null, knownHiveIds: List<String> = emptyList()): Draft {
        val original = transcript.trim()
        val norm = normalize(original)               // malá písmena, bez diakritiky
        val draft = Draft(rawTranscript = original)

        // --- Úl (číslo nebo jméno) ---
        val hive = parseHiveId(original, norm, knownHiveIds) ?: defaultHive
        if (hive != null) draft.hiveId = hive

        // --- Plodiště (počet rámků v plodišti celkem) ---
        framesNear(norm, listOf("plodist"))
            ?.let { draft.broodChamberFrames = fmt(it) }

        // --- Plod ---
        framesNear(norm, listOf("zavickovan", "zavickovaneho", "zavickovany", "vickovan"))
            ?.let { draft.cappedBroodFrames = fmt(it) }
        framesNear(norm, listOf("otevren", "otevreneho", "otevreny", "nezavickovan"))
            ?.let { draft.openBroodFrames = fmt(it) }
        // Samotné "plod na N rámcích" bez upřesnění bereme jako zavíčkovaný.
        // ("plodiště" obsahuje podřetězec "plod", proto sem nespadá, pokud už bylo rozpoznáno.)
        if (draft.cappedBroodFrames.isBlank() && draft.openBroodFrames.isBlank() && draft.broodChamberFrames.isBlank()) {
            framesNear(norm, listOf("plod", "plodu"))?.let { draft.cappedBroodFrames = fmt(it) }
        }

        // --- Zásoby ---
        framesNear(norm, listOf("zasob", "zasoby", "med a pyl", "zasobu"))
            ?.let { draft.storesFrames = fmt(it) }

        // --- Matka ---
        parseQueen(norm)?.let { (seen, status) ->
            draft.queenSeen = if (seen) "ano" else "ne"
            if (status.isNotBlank()) draft.queenStatus = status
        }

        // --- Vajíčka ---
        if (norm.contains("vajic")) {
            draft.eggsSeen = if (hasNegationBefore(norm, "vajic")) "ne" else "ano"
        }

        // --- Síla: obsednuté uličky / rámky ---
        framesNear(norm, listOf("obsedl", "obsednut", "ulicek", "ulicky", "ulic", "sila"))
            ?.let { draft.occupiedSeams = fmt(it) }

        // --- Mírnost / temperament ---
        when {
            norm.contains("mirn") -> draft.temperament = "mírné"
            norm.contains("bodav") || norm.contains("agresiv") || norm.contains("utoci") ->
                draft.temperament = "bodavé"
            norm.contains("nervoz") -> draft.temperament = "nervózní"
            norm.contains("klidn") -> draft.temperament = "klidné"
        }

        // --- Zdraví / nemoci ---
        val disease = buildList {
            if (norm.contains("varro") || norm.contains("klest")) add("varroáza")
            if (norm.contains("mor plodu") || Regex("\\bmor\\b").containsMatchIn(norm)) add("mor plodu")
            if (norm.contains("zvapenat") || norm.contains("vapenat")) add("zvápenatění")
            if (norm.contains("nosema") || norm.contains("prujem")) add("nosema / průjem")
            if (norm.contains("nemoc") || norm.contains("priznak")) add("příznaky nemoci")
        }
        if (disease.isNotEmpty()) draft.diseaseNotes = disease.joinToString(", ")

        // --- Spad roztočů ---
        numberNear(norm, listOf("spad", "roztoc", "klest"))?.let {
            // spad bereme jako celé číslo kusů
            if (it >= 0) draft.miteDrop = it.toInt().toString()
        }

        // --- Zásahy ---
        val interventions = buildList {
            if (norm.contains("rozsir")) add("rozšíření")
            if (norm.contains("zuzen") || norm.contains("zuzil")) add("zúžení")
            if (norm.contains("mezisten")) add("přidány mezistěny")
            if (norm.contains("medni")) add("nasazen medník")
            if (norm.contains("nastavek")) add("nastavek")
            if (norm.contains("odvicko") || norm.contains("odviccko")) add("odvíčkování")
            if (norm.contains("prehozen") || norm.contains("prelozen ram")) add("přeloženy rámky")
        }
        if (interventions.isNotEmpty()) draft.interventions = interventions.joinToString(", ")

        // --- Krmení ---
        if (norm.contains("krmen") || norm.contains("nakrm") || norm.contains("sirup") ||
            norm.contains("cukr") || norm.contains("krmit")
        ) {
            val liters = amountNear(norm, listOf("litr"), "l")
            val kg = amountNear(norm, listOf("kilo", "kilogram"), "kg")
            val what = when {
                norm.contains("sirup") -> "sirup"
                norm.contains("cukr") -> "cukr"
                norm.contains("mediny") || norm.contains("medn") -> "medina"
                else -> "krmivo"
            }
            draft.feeding = listOfNotNull(what, liters, kg).joinToString(" ").trim()
        }

        // --- Léčení ---
        if (norm.contains("lece") || norm.contains("lecen") || norm.contains("kyselin") ||
            norm.contains("gabon") || norm.contains("formidol") || norm.contains("mravenc") ||
            norm.contains("stavelov") || norm.contains("fumigac") || norm.contains("aerosol")
        ) {
            val agent = when {
                norm.contains("kyselina mravenc") || norm.contains("mravenc") -> "kyselina mravenčí"
                norm.contains("stavelov") || norm.contains("stavelov") -> "kyselina šťavelová"
                norm.contains("gabon") -> "Gabon"
                norm.contains("formidol") -> "Formidol"
                norm.contains("fumigac") -> "fumigace"
                norm.contains("aerosol") -> "aerosol"
                else -> "léčení"
            }
            draft.treatment = agent
        }

        // --- Med a odběry ---
        amountValueNear(norm, listOf("med", "medu", "vytoc", "vynos"), listOf("kilo", "kilogram", "kg"))
            ?.let { draft.honeyEstimateKg = fmt(it) }
        framesNear(norm, listOf("odebr", "odebral", "odebrany", "vytoc"))
            ?.let { draft.framesRemoved = fmt(it) }

        // --- Úkoly do příště ---
        val tasksIdx = indexOfAny(norm, listOf("pristi", "priste", "priste zkontrolovat", "ukol"))
        if (tasksIdx >= 0) {
            // vezmeme odpovídající kus původního textu (od klíčového slova dál)
            val mappedStart = mapNormIndexToOriginal(original, norm, tasksIdx)
            draft.tasksNextTime = original.substring(mappedStart).trim().trimStart(',', ' ')
        }

        return draft
    }

    // ---------------------------------------------------------------------
    // Úl – číslo, nebo jméno/přezdívka (např. "úl Jana")
    // ---------------------------------------------------------------------
    /** Slova hned za "úl(…)", která se přeskočí, než přijde vlastní číslo/jméno. */
    private val HIVE_FILLER_WORDS = setOf("cislo", "c", "oznaceni", "oznacen")

    /**
     * Rozpozná identifikátor úlu (číslo nebo jméno) po libovolně skloňovaném
     * tvaru "úl" ("úl", "úlu", "úlek", "úlku", "úly"…), případně "včelstvo".
     * Pracuje po celých slovech (ne po znacích), takže se nespletě jako dřív
     * u "u úlu Jana" – tam se předchozí přístup ustřihl doprostřed slova
     * "úlu" a jako jméno vzal jen zbylé "u", což pak spadlo zpátky na
     * poslední použité číslo úlu.
     */
    private fun parseHiveId(original: String, norm: String, knownHiveIds: List<String>): String? {
        data class Tok(val normWord: String, val start: Int, val end: Int)

        val tokens = Regex("\\S+").findAll(original).mapNotNull { m ->
            val stripped = m.value.trim { !it.isLetterOrDigit() }
            if (stripped.isEmpty()) null
            else Tok(normalize(stripped), m.range.first, m.range.last + 1)
        }.toList()

        val knownByNorm = knownHiveIds.associateBy { normalize(it) }

        for (i in tokens.indices) {
            val w = tokens[i].normWord
            val isMarker = (w.startsWith("ul") && w.length <= 6) || w == "vcelstvo"
            if (!isMarker) continue

            var j = i + 1
            while (j < tokens.size && tokens[j].normWord in HIVE_FILLER_WORDS) j++
            if (j >= tokens.size) continue
            val candidate = tokens[j]

            // Nejdřív zkusíme číslo – to je nejběžnější případ ("úl číslo pět").
            wordToNumber(candidate.normWord)?.let { return it.toInt().toString() }

            // Odpovídá-li už existujícímu úlu, použijeme přesně jeho id (zachová velká písmena/diakritiku).
            knownByNorm[candidate.normWord]?.let { return it }

            // Jinak vezmeme slovo z PŮVODNÍHO textu (zachová diakritiku a velká písmena) jako nové jméno úlu.
            val name = original.substring(candidate.start, candidate.end).trim { !it.isLetter() }
            if (name.isNotBlank()) return name
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Matka
    // ---------------------------------------------------------------------
    private fun parseQueen(norm: String): Pair<Boolean, String>? {
        if (!norm.contains("matk") && !norm.contains("matka") && !norm.contains("kralovn")) return null

        val negFound = norm.contains("matka nenalez") || norm.contains("matku nenalez") ||
            norm.contains("nenasel") || norm.contains("nenasla") ||
            norm.contains("matku jsem nevidel") || norm.contains("matka nevidena") ||
            norm.contains("bez matky") || norm.contains("matka neni")
        val seen = !negFound

        val status = when {
            norm.contains("neklade") -> "neklade"
            norm.contains("klade") -> "klade"
            norm.contains("trubci") -> "trubčí (trubcokladná)"
            norm.contains("v poradku") || norm.contains("vporadku") || norm.contains("porade") -> "v pořádku"
            norm.contains("znacen") || norm.contains("znaceno") -> "značená"
            norm.contains("mlada") -> "mladá"
            norm.contains("stara") -> "stará"
            else -> ""
        }
        return seen to status
    }

    // ---------------------------------------------------------------------
    // Pomocné: hledání čísel poblíž klíčových slov
    // ---------------------------------------------------------------------

    /**
     * Počet rámků poblíž klíčového slova. Nejdřív hledá číslo ZA klíčovým
     * slovem ("zásoby tři rámky"), teprve pak malé okno PŘED ním
     * ("na dvou rámcích zavíčkovaného plodu"). Tím se zabrání tomu, aby si
     * pole vzalo číslo z předchozí věty.
     */
    private fun framesNear(norm: String, keys: List<String>): Double? {
        for (k in keys) {
            val idx = norm.indexOf(k)
            if (idx < 0) continue
            val fwd = norm.substring(idx, minOf(norm.length, idx + k.length + 45))
            numberBeforeUnit(fwd, listOf("ram"))?.let { return it }
            firstNumberToken(fwd)?.let { return it }
            val bwd = norm.substring(maxOf(0, idx - 22), idx + k.length)
            numberBeforeUnit(bwd, listOf("ram"))?.let { return it }
        }
        return null
    }

    /** Obecné číslo poblíž klíčového slova (bez jednotky). */
    private fun numberNear(norm: String, keys: List<String>): Double? {
        for (k in keys) {
            val idx = norm.indexOf(k)
            if (idx >= 0) {
                val end = minOf(norm.length, idx + k.length + 25)
                val window = norm.substring(idx, end)
                firstNumberToken(window)?.let { return it }
            }
        }
        return null
    }

    /** Množství s jednotkou jako text, např. "2 l" nebo "3 kg", poblíž klíčů. */
    private fun amountNear(norm: String, unitWords: List<String>, unitLabel: String): String? {
        for (u in unitWords) {
            val idx = norm.indexOf(u)
            if (idx >= 0) {
                val start = maxOf(0, idx - 20)
                val v = numberBeforeUnit(norm.substring(start, idx + u.length), unitWords)
                    ?: firstNumberToken(norm.substring(start, minOf(norm.length, idx + u.length + 10)))
                if (v != null) return "${fmt(v)} $unitLabel"
            }
        }
        return null
    }

    /** Hodnota (Double) s jednotkou poblíž tématických klíčů – např. med 15 kg. */
    private fun amountValueNear(norm: String, topicKeys: List<String>, unitWords: List<String>): Double? {
        val topicIdx = indexOfAny(norm, topicKeys)
        if (topicIdx < 0) return null
        val end = minOf(norm.length, topicIdx + 40)
        val window = norm.substring(topicIdx, end)
        return numberBeforeUnit(window, unitWords) ?: run {
            // číslo hned za tématem i bez jednotky
            firstNumberToken(window)
        }
    }

    /** Najde "<číslo> <jednotka>" a vrátí to číslo. Umí i "dva a půl rámku". */
    private fun numberBeforeUnit(window: String, units: List<String>): Double? {
        val toks = tokenize(window)
        for (i in toks.indices) {
            val isUnit = units.any { toks[i].startsWith(it) }
            if (isUnit && i > 0) {
                // "<základ> a půl <jednotka>" -> základ + 0,5
                if (toks[i - 1] == "pul" && i >= 3 && toks[i - 2] == "a") {
                    wordToNumber(toks[i - 3])?.let { return it + 0.5 }
                }
                wordToNumber(toks[i - 1])?.let { return it }
                if (i >= 2) wordToNumber(toks[i - 2])?.let { return it }
            }
        }
        return null
    }

    /** První číselný token (číslice nebo české slovo) v úseku. */
    private fun firstNumberToken(window: String): Double? {
        val toks = tokenize(window)
        for (i in toks.indices) {
            val n = wordToNumber(toks[i]) ?: continue
            // "dva a pul"
            if (i + 2 < toks.size && toks[i + 1] == "a" && toks[i + 2].startsWith("pul")) {
                return n + 0.5
            }
            return n
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Číslovky
    // ---------------------------------------------------------------------
    private fun wordToNumber(tok: String): Double? {
        tok.toIntOrNull()?.let { return it.toDouble() }
        tok.replace(',', '.').toDoubleOrNull()?.let { return it }
        return NUMBERS[tok]
    }

    private val NUMBERS: Map<String, Double> = buildMap {
        put("nula", 0.0)
        listOf("jeden", "jedna", "jednu", "jednom", "jednoho", "jedne").forEach { put(it, 1.0) }
        listOf("dva", "dve", "dvou", "dvema", "dvoje").forEach { put(it, 2.0) }
        listOf("tri", "trech", "tremi").forEach { put(it, 3.0) }
        listOf("ctyri", "ctyr", "ctyrech", "ctyrmi").forEach { put(it, 4.0) }
        listOf("pet", "peti").forEach { put(it, 5.0) }
        listOf("sest", "sesti").forEach { put(it, 6.0) }
        listOf("sedm", "sedmi").forEach { put(it, 7.0) }
        listOf("osm", "osmi").forEach { put(it, 8.0) }
        listOf("devet", "deviti").forEach { put(it, 9.0) }
        listOf("deset", "deseti").forEach { put(it, 10.0) }
        listOf("jedenact", "jedenacti").forEach { put(it, 11.0) }
        listOf("dvanact", "dvanacti").forEach { put(it, 12.0) }
        listOf("trinact", "trinacti").forEach { put(it, 13.0) }
        listOf("ctrnact", "ctrnacti").forEach { put(it, 14.0) }
        listOf("patnact", "patnacti").forEach { put(it, 15.0) }
        listOf("sestnact", "sestnacti").forEach { put(it, 16.0) }
        listOf("sedmnact", "sedmnacti").forEach { put(it, 17.0) }
        listOf("osmnact", "osmnacti").forEach { put(it, 18.0) }
        listOf("devatenact", "devatenacti").forEach { put(it, 19.0) }
        listOf("dvacet", "dvaceti").forEach { put(it, 20.0) }
        put("pul", 0.5)
    }

    // ---------------------------------------------------------------------
    // Textové utility
    // ---------------------------------------------------------------------
    private fun hasNegationBefore(norm: String, key: String): Boolean {
        val idx = norm.indexOf(key)
        if (idx < 0) return false
        val start = maxOf(0, idx - 15)
        val before = norm.substring(start, idx)
        return before.contains("bez") || before.contains("zadn") || before.contains("ne")
    }

    private fun indexOfAny(norm: String, keys: List<String>): Int {
        var best = -1
        for (k in keys) {
            val idx = norm.indexOf(k)
            if (idx >= 0 && (best < 0 || idx < best)) best = idx
        }
        return best
    }

    private fun tokenize(s: String): List<String> =
        s.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

    /** Malá písmena + odstranění diakritiky, aby rozbor nezávisel na háčcích. */
    private fun normalize(s: String): String {
        val lower = s.lowercase()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    /** Formátování čísla: 2.0 -> "2", 2.5 -> "2,5" (české desetinné čárky). */
    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else v.toString().replace('.', ',')

    /**
     * Přibližné namapování pozice v normalizovaném textu zpět na původní text.
     * Diakritika mění délku minimálně, proto stačí zarovnat podle počtu slov.
     */
    private fun mapNormIndexToOriginal(original: String, norm: String, normIdx: Int): Int {
        val wordsBefore = norm.substring(0, normIdx).count { it == ' ' }
        var seen = 0
        var i = 0
        while (i < original.length && seen < wordsBefore) {
            if (original[i] == ' ') seen++
            i++
        }
        return i.coerceIn(0, original.length)
    }
}
