package cz.vcely.zapisnik.varroa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Počítání kleštíků (Varroa) z fotky podložky.
 *
 * Postup: šedotón, "closing" (zmizí malé tmavé skvrny) dá odhad pozadí, kleštík je
 * dost tmavší než pozadí. Souvislé skvrny se spočítají; slepené kusy se dopočítají
 * podle plochy typického kleštíka. Nemění databázi, nic nezapisuje.
 *
 * Ladění: na ověřené fotce (≈1000 kleštíků) vychází citlivost 63 shodně se
 * dvěma nezávislými metodami. Nižší číslo = víc skvrn (včetně nečistot).
 */
object VarroaDetector {

    const val MAX_SIDE = 2000

    data class Blob(val x: Float, val y: Float, val area: Int, val n: Int)
    data class Result(val blobs: List<Blob>, val total: Int, val medianArea: Int)

    /** Načte fotku, opraví otočení podle EXIF a zmenší na delší stranu [MAX_SIDE]. */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        val scale = min(1f, MAX_SIDE.toFloat() / max(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return bmp
    }

    /** Maska uvnitř mnohoúhelníku (vrcholy v pixelech obrázku). */
    fun polygonMask(w: Int, h: Int, xs: FloatArray, ys: FloatArray): BooleanArray {
        val mask = BooleanArray(w * h)
        val k = xs.size
        if (k < 3) return mask
        for (y in 0 until h) {
            val yy = y + 0.5f
            val cross = ArrayList<Float>()
            var j = k - 1
            for (i in 0 until k) {
                if ((ys[i] > yy) != (ys[j] > yy)) {
                    cross.add((xs[j] - xs[i]) * (yy - ys[i]) / (ys[j] - ys[i]) + xs[i])
                }
                j = i
            }
            cross.sort()
            var c = 0
            while (c + 1 < cross.size) {
                val a = max(0, cross[c].roundToInt()); val b = min(w - 1, cross[c + 1].roundToInt())
                for (x in a..b) mask[y * w + x] = true
                c += 2
            }
        }
        return mask
    }

    fun detect(
        px: IntArray, w: Int, h: Int, roi: BooleanArray,
        diff: Int = 63, minArea: Int = 11, dark: Int = 75, win: Int = 11,
    ): Result {
        val n = w * h
        val g = IntArray(n)
        for (i in 0 until n) {
            val p = px[i]
            g[i] = (((p shr 16) and 255) * 0.299 + ((p shr 8) and 255) * 0.587 + (p and 255) * 0.114).toInt()
        }
        val r = win shr 1
        val t1 = IntArray(n); val t2 = IntArray(n)
        fun pass(src: IntArray, dst: IntArray, horiz: Boolean, isMax: Boolean) {
            val len = if (horiz) w else h
            val outer = if (horiz) h else w
            for (m in 0 until outer) for (l in 0 until len) {
                var best = if (isMax) 0 else 255
                val a = max(0, l - r); val b = min(len - 1, l + r)
                for (k in a..b) {
                    val v = if (horiz) src[m * w + k] else src[k * w + m]
                    if (if (isMax) v > best else v < best) best = v
                }
                if (horiz) dst[m * w + l] = best else dst[l * w + m] = best
            }
        }
        pass(g, t1, true, true); pass(t1, t2, false, true)    // dilatace
        pass(t2, t1, true, false); pass(t1, t2, false, false) // eroze => closing v t2

        val lab = IntArray(n)
        val stack = IntArray(n)
        val blobs = ArrayList<Blob>()
        val vals = IntArray(401)
        val areas = ArrayList<Int>()
        val raw = ArrayList<FloatArray>() // x, y, area
        var id = 0
        for (i in 0 until n) {
            if (lab[i] != 0 || !roi[i] || t2[i] - g[i] <= diff) continue
            id++
            var sp = 0
            stack[sp++] = i; lab[i] = id
            var a = 0; var sx = 0.0; var sy = 0.0
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % w; val y = p / w
                if (a < 401) vals[a] = g[p]
                a++; sx += x; sy += y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (lab[q] == 0 && roi[q] && t2[q] - g[q] > diff) { lab[q] = id; stack[sp++] = q }
                }
            }
            if (a < minArea || a > 400) continue
            val sorted = vals.copyOf(a).also { it.sort() }
            val kk = min(6, a)
            var d = 0.0
            for (j in 0 until kk) d += sorted[j]
            if (d / kk >= dark) continue
            raw.add(floatArrayOf((sx / a).toFloat(), (sy / a).toFloat(), a.toFloat()))
            if (a <= 26) areas.add(a)
        }
        areas.sort()
        val med = if (areas.isEmpty()) 17 else areas[areas.size / 2]
        var total = 0
        for (b in raw) {
            val a = b[2].toInt()
            val cnt = if (a <= 1.6 * med) 1 else max(2, (a.toDouble() / med).roundToInt())
            blobs.add(Blob(b[0], b[1], a, cnt)); total += cnt
        }
        return Result(blobs, total, med)
    }
}
