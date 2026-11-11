package cz.vcely.zapisnik.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import cz.vcely.zapisnik.varroa.VarroaDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.max

/**
 * Počítání kleštíků z fotky podložky. Nic neukládá sám: až po "Přidat do záznamu"
 * předá počet (a počet dní) dál a appka ho vloží do rozpracovaného záznamu.
 */
@Composable
fun VarroaScreen(
    onUse: (count: Int, days: Int?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pixels by remember { mutableStateOf<IntArray?>(null) }
    val pts = remember { mutableStateListOf<Offset>() }
    var roiSet by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VarroaDetector.Result?>(null) }
    val off = remember { mutableStateListOf<Int>() }
    val manual = remember { mutableStateListOf<Offset>() }
    var sens by remember { mutableStateOf(63f) }
    var zoom by remember { mutableStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var daysText by remember { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun total(): Int {
        val r = result ?: return 0
        var removed = 0
        off.forEach { removed += r.blobs[it].n }
        return r.total - removed + manual.size
    }

    fun runDetection() {
        val bmp = bitmap ?: return
        val px = pixels ?: return
        busy = true
        scope.launch {
            val res = withContext(Dispatchers.Default) {
                val mask = VarroaDetector.polygonMask(
                    bmp.width, bmp.height,
                    FloatArray(pts.size) { pts[it].x }, FloatArray(pts.size) { pts[it].y }
                )
                VarroaDetector.detect(px, bmp.width, bmp.height, mask, diff = sens.toInt())
            }
            result = res; off.clear(); manual.clear(); busy = false
        }
    }

    fun loadPhoto(uri: Uri) {
        busy = true; error = null
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { VarroaDetector.loadBitmap(context, uri) }
            if (bmp == null) {
                error = "Fotku se nepodařilo načíst."
            } else {
                val px = withContext(Dispatchers.Default) {
                    IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                }
                bitmap = bmp; pixels = px
                pts.clear(); roiSet = false; result = null; off.clear(); manual.clear()
            }
            busy = false
        }
    }

    // Výběr z galerie
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    // Přímé vyfocení (fotka se uloží jen do dočasné paměti appky, do záznamu se nekopíruje)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { loadPhoto(it) }
    }
    fun takePhoto() {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "podlozka_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        } catch (e: Exception) {
            error = "Fotoaparát není dostupný."
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Kleštíci na podložce", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Zavřít") }
                }
                val bmp = bitmap
                if (bmp == null) {
                    Text(
                        "Vyberte fotku podložky: kolmo shora, bez blesku, celá podložka v záběru, ostře.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { takePhoto() }) { Text("Vyfotit") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Z galerie") }
                    }
                } else {
                    val res = result
                    Text(
                        when {
                            res != null -> "Klepnutím na značku ji odeberete nebo vrátíte, klepnutím do prázdna kleštíka přidáte."
                            roiSet -> "Počítám…"
                            else -> "Klepejte do rohů podložky (3 až 8 bodů), ať se nepočítá lišta ani okolí."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!roiSet) {
                            Button(enabled = pts.size >= 3, onClick = { roiSet = true; runDetection() }) { Text("Spočítat") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { if (pts.isNotEmpty()) pts.removeAt(pts.lastIndex) }) { Text("Zpět") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                pts.clear()
                                pts.addAll(listOf(
                                    Offset(0f, 0f), Offset(bmp.width.toFloat(), 0f),
                                    Offset(bmp.width.toFloat(), bmp.height.toFloat()), Offset(0f, bmp.height.toFloat())
                                ))
                                roiSet = true; runDetection()
                            }) { Text("Celý snímek") }
                        } else {
                            OutlinedButton(onClick = { roiSet = false; result = null; pts.clear() }) { Text("Změnit podložku") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { takePhoto() }) { Text("Vyfotit znovu") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Jiná z galerie") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Přiblížení", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                    }

                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Počítám…")
                        }
                    } else if (result != null) {
                        val r = result!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(total().toString(), style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "automaticky ${r.total}, ručně +${manual.size} / −${off.sumOf { r.blobs[it].n }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("Citlivost ${sens.toInt()} (nižší číslo hlásí víc skvrn)", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = sens, onValueChange = { sens = it }, valueRange = 40f..90f,
                            onValueChangeFinished = { runDetection() }
                        )
                        OutlinedTextField(
                            value = daysText, onValueChange = { daysText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Dní na podložce (nepovinné)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onUse(total(), daysText.toIntOrNull()?.takeIf { it > 0 }) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Přidat do záznamu") }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Fotka s označením; při přiblížení se dá posouvat.
                    BoxWithConstraints(Modifier.height(420.dp).fillMaxWidth()) {
                        val image = remember(bmp) { bmp.asImageBitmap() }
                        val canvasWidth = maxWidth * zoom   // mimo Column, jinak maxWidth není dostupný
                        Column(
                            Modifier.fillMaxSize()
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                        ) {
                            Canvas(
                                Modifier
                                    .width(canvasWidth)
                                    .aspectRatio(bmp.width.toFloat() / bmp.height)
                                    .onSizeChanged { canvasSize = it }
                                    .pointerInput(bmp) {
                                        detectTapGestures { tap ->
                                            if (canvasSize.width == 0) return@detectTapGestures
                                            val k = bmp.width.toFloat() / canvasSize.width
                                            val p = Offset(tap.x * k, tap.y * k)
                                            val r = result
                                            if (!roiSet) {
                                                if (pts.size < 8) pts.add(p)
                                            } else if (r != null) {
                                                val reach = max(7f, bmp.width / 150f) * 1.3f
                                                var hit = -1; var best = Float.MAX_VALUE
                                                r.blobs.forEachIndexed { i, b ->
                                                    val d = hypot(b.x - p.x, b.y - p.y)
                                                    if (d < reach && d < best) { best = d; hit = i }
                                                }
                                                if (hit >= 0) {
                                                    if (off.contains(hit)) off.remove(hit) else off.add(hit)
                                                } else {
                                                    val mi = manual.indexOfFirst { hypot(it.x - p.x, it.y - p.y) < reach }
                                                    if (mi >= 0) manual.removeAt(mi) else manual.add(p)
                                                }
                                            }
                                        }
                                    }
                            ) {
                                drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                                val k = size.width / bmp.width
                                val rad = max(7f, bmp.width / 150f) * k
                                val lw = max(1.5f, bmp.width / 700f) * k * 1.6f
                                // okraj podložky
                                for (i in pts.indices) {
                                    val a = pts[i]
                                    val b = if (i + 1 < pts.size) pts[i + 1] else if (roiSet) pts[0] else null
                                    if (b != null) drawLine(Color(0xFFFFB300), Offset(a.x * k, a.y * k), Offset(b.x * k, b.y * k), strokeWidth = lw)
                                    if (!roiSet) drawCircle(Color(0xFFFFB300), rad * 0.8f, Offset(a.x * k, a.y * k))
                                }
                                result?.blobs?.forEachIndexed { i, b ->
                                    val c = when {
                                        off.contains(i) -> Color(0xFFAAAAAA)
                                        b.n > 1 -> Color(0xFFFF4D4D)
                                        else -> Color(0xFF2FBF6B)
                                    }
                                    drawCircle(c, rad, Offset(b.x * k, b.y * k), style = Stroke(lw))
                                }
                                manual.forEach { drawCircle(Color(0xFF4DA3FF), rad, Offset(it.x * k, it.y * k), style = Stroke(lw)) }
                            }
                        }
                    }

                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
