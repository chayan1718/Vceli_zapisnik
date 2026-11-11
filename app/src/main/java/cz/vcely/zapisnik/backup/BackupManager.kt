package cz.vcely.zapisnik.backup

import android.content.Context
import cz.vcely.zapisnik.data.AppDatabase
import cz.vcely.zapisnik.data.Hive
import cz.vcely.zapisnik.data.Inspection
import cz.vcely.zapisnik.data.Stanoviste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Úplná záloha a obnova dat: úly, stanoviště, záznamy z prohlídek a fotky.
 *
 * Záloha je ZIP s [JSON_ENTRY] (všechny tři tabulky) a složkou photos/.
 * Obnova data jen DOPLŇUJE: záznamy se stejným id se přepíšou, ostatní se
 * nemažou. Nic se při obnově nevymaže ani neodinstaluje.
 */
object BackupManager {

    private const val JSON_ENTRY = "backup.json"
    private const val FORMAT = 1

    data class Summary(val inspections: Int, val hives: Int, val stanoviste: Int, val photos: Int)

    suspend fun write(context: Context, out: OutputStream): Summary = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val inspections = db.inspectionDao().getAllForExport()
        val hives = db.hiveDao().getAll()
        val stanoviste = db.stanovisteDao().getAll()

        val photoFiles = LinkedHashMap<String, File>()
        for (i in inspections) {
            val f = i.photoPath?.let { File(it) }
            if (f != null && f.isFile) photoFiles[f.name] = f
        }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("createdAt", System.currentTimeMillis())
            .put("hives", JSONArray().also { a -> hives.forEach { a.put(JSONObject().put("id", it.id)) } })
            .put("stanoviste", JSONArray().also { a -> stanoviste.forEach { a.put(it.toJson()) } })
            .put("inspections", JSONArray().also { a -> inspections.forEach { a.put(it.toJson()) } })

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, file) in photoFiles) {
                zip.putNextEntry(ZipEntry("photos/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        Summary(inspections.size, hives.size, stanoviste.size, photoFiles.size)
    }

    suspend fun restore(context: Context, input: InputStream): Summary = withContext(Dispatchers.IO) {
        val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
        var json: String? = null
        var photos = 0
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (name == JSON_ENTRY) {
                    json = zip.readBytes().toString(Charsets.UTF_8)
                } else if (name.startsWith("photos/") && !e.isDirectory) {
                    // File(...).name zahodí případné "../" a podobné cesty.
                    val target = File(photoDir, File(name).name)
                    target.outputStream().use { zip.copyTo(it) }
                    photos++
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        val text = json ?: throw IllegalArgumentException("Soubor není záloha Včelího zápisníku.")
        val root = JSONObject(text)
        if (root.optInt("format", 0) != FORMAT) {
            throw IllegalArgumentException("Neznámý formát zálohy.")
        }

        val hives = root.getJSONArray("hives").objects().map { Hive(id = it.getString("id")) }
        val stanoviste = root.getJSONArray("stanoviste").objects().map { it.toStanoviste() }
        val inspections = root.getJSONArray("inspections").objects().map { it.toInspection(photoDir) }

        val db = AppDatabase.get(context)
        db.hiveDao().upsertAll(hives)
        db.stanovisteDao().upsertAll(stanoviste)
        db.inspectionDao().upsertAll(inspections)
        Summary(inspections.size, hives.size, stanoviste.size, photos)
    }

    // --- JSON převody -------------------------------------------------------

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.lng(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.bool(k: String): Boolean? = if (isNull(k)) null else getBoolean(k)

    private fun Stanoviste.toJson() = JSONObject()
        .put("id", id).put("name", name)
        .put("latitude", latitude ?: JSONObject.NULL).put("longitude", longitude ?: JSONObject.NULL)
        .put("createdAt", createdAt).put("motyliceMonitoring", motyliceMonitoring)
        .put("lastSireniAt", lastSireniAt ?: JSONObject.NULL).put("alertAlreadySent", alertAlreadySent)

    private fun JSONObject.toStanoviste() = Stanoviste(
        id = getLong("id"), name = getString("name"),
        latitude = dbl("latitude"), longitude = dbl("longitude"),
        createdAt = lng("createdAt") ?: System.currentTimeMillis(),
        motyliceMonitoring = optBoolean("motyliceMonitoring", false),
        lastSireniAt = lng("lastSireniAt"),
        alertAlreadySent = optBoolean("alertAlreadySent", false),
    )

    private fun Inspection.toJson(): JSONObject {
        val n = JSONObject.NULL
        return JSONObject()
            .put("id", id).put("hiveId", hiveId).put("timestamp", timestamp)
            .put("stanovisteName", stanovisteName ?: n).put("recordedBy", recordedBy ?: n)
            .put("broodChamberFrames", broodChamberFrames ?: n).put("cappedBroodFrames", cappedBroodFrames ?: n)
            .put("openBroodFrames", openBroodFrames ?: n).put("storesFrames", storesFrames ?: n)
            .put("queenSeen", queenSeen ?: n).put("queenStatus", queenStatus ?: n).put("eggsSeen", eggsSeen ?: n)
            .put("occupiedSeams", occupiedSeams ?: n).put("temperament", temperament ?: n)
            .put("diseaseNotes", diseaseNotes ?: n).put("miteDrop", miteDrop ?: n)
            .put("interventions", interventions ?: n).put("feeding", feeding ?: n).put("treatment", treatment ?: n)
            .put("honeyEstimateKg", honeyEstimateKg ?: n).put("framesRemoved", framesRemoved ?: n)
            .put("tasksNextTime", tasksNextTime ?: n)
            .put("photoFile", photoPath?.let { File(it).name } ?: n)
            .put("latitude", latitude ?: n).put("longitude", longitude ?: n)
            .put("rawTranscript", rawTranscript ?: n).put("note", note ?: n)
    }

    private fun JSONObject.toInspection(photoDir: File) = Inspection(
        id = getLong("id"), hiveId = getString("hiveId"),
        timestamp = lng("timestamp") ?: System.currentTimeMillis(),
        stanovisteName = str("stanovisteName"), recordedBy = str("recordedBy"),
        broodChamberFrames = dbl("broodChamberFrames"), cappedBroodFrames = dbl("cappedBroodFrames"),
        openBroodFrames = dbl("openBroodFrames"), storesFrames = dbl("storesFrames"),
        queenSeen = bool("queenSeen"), queenStatus = str("queenStatus"), eggsSeen = bool("eggsSeen"),
        occupiedSeams = dbl("occupiedSeams"), temperament = str("temperament"),
        diseaseNotes = str("diseaseNotes"), miteDrop = int("miteDrop"),
        interventions = str("interventions"), feeding = str("feeding"), treatment = str("treatment"),
        honeyEstimateKg = dbl("honeyEstimateKg"), framesRemoved = dbl("framesRemoved"),
        tasksNextTime = str("tasksNextTime"),
        photoPath = str("photoFile")?.let { File(photoDir, it).absolutePath },
        latitude = dbl("latitude"), longitude = dbl("longitude"),
        rawTranscript = str("rawTranscript"), note = str("note"),
    )
}
