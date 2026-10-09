package cz.vcely.zapisnik.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Odečet polohy – přímo z [LocationManager], bez Google Play Services.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Rychlý odhad z poslední známé (může chybět, např. hned po startu appky, nebo být stará). */
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        try {
            for (provider in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
        } catch (e: SecurityException) {
            return null
        }
        return best?.let { it.latitude to it.longitude }
    }

    /**
     * Poloha pro rozpoznání stanoviště. Nejdřív zkusí [lastKnown] – tu appka
     * po prvním úspěšném odečtu GPS obvykle už má, takže je to okamžité, bez
     * čekání při každém stisku "Diktovat". Jen když poslední známá poloha
     * úplně chybí (typicky hned po instalaci appky, nebo po restartu telefonu,
     * než GPS provider cokoliv zachytí), krátce (až [timeoutMs] ms) aktivně
     * počká na čerstvou opravu – jinak by se první nové stanoviště uložilo
     * s prázdnými souřadnicemi a příští diktát na stejném místě by ho už
     * nikdy nenašel.
     */
    suspend fun currentLocation(context: Context, timeoutMs: Long = 4000): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        lastKnown(context)?.let { return it }
        return withTimeoutOrNull(timeoutMs) { requestFreshFix(context) }
    }

    private suspend fun requestFreshFix(context: Context): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null) {
                if (cont.isActive) cont.resume(null) { }
                return@suspendCancellableCoroutine
            }
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                if (cont.isActive) cont.resume(null) { }
                return@suspendCancellableCoroutine
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(location.latitude to location.longitude) { }
                    try { lm.removeUpdates(this) } catch (e: SecurityException) { }
                }
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
                override fun onProviderEnabled(provider: String) { }
                override fun onProviderDisabled(provider: String) { }
            }
            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null) { }
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                try { lm.removeUpdates(listener) } catch (e: SecurityException) { }
            }
        }

    /** Vzdálenost dvou GPS bodů v metrech (Haversine). Slouží k rozpoznání stanoviště (tolerance ~300 m). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // poloměr Země v metrech
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
