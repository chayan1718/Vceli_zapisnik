package cz.vcely.zapisnik.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.vcely.zapisnik.data.AppDatabase
import cz.vcely.zapisnik.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Jednou denně (na pozadí, přes WorkManager) zkontroluje u všech stanovišť se
 * zapnutým hlídáním motýlice, jestli v posledních [WINDOW_DAYS] dnech – nebo
 * od posledního síření, podle toho, co nastalo později – bylo alespoň
 * [THRESHOLD_DAYS] dní s maximální teplotou nad [THRESHOLD_TEMP] °C. Pokud
 * ano (a appka o tom ještě neinformovala od posledního síření), pošle SMS na
 * číslo nastavené v appce.
 *
 * Plánuje se v MainActivity jako unikátní periodická práce ("motylice-check").
 */
class MotyliceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "motylice-check"
        const val WINDOW_DAYS = 7
        const val THRESHOLD_TEMP = 10.0
        const val THRESHOLD_DAYS = 4
        val REPEAT_INTERVAL: Long = 1
        val REPEAT_UNIT: TimeUnit = TimeUnit.DAYS
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.get(applicationContext)
            val phone = UserPrefs.getAlertPhone(applicationContext)
            val monitored = db.stanovisteDao().getMonitored()
                .filter { it.latitude != null && it.longitude != null }

            for (s in monitored) {
                val temps = WeatherHelper.fetchDailyMaxTemps(s.latitude!!, s.longitude!!, WINDOW_DAYS)
                    ?: continue // appka je offline / Open-Meteo nedostupné -> zkusíme zas zítra

                val now = System.currentTimeMillis()
                val daysSinceSireni = s.lastSireniAt?.let {
                    ((now - it) / TimeUnit.DAYS.toMillis(1)).toInt() + 1
                }
                val windowDays = (daysSinceSireni ?: WINDOW_DAYS).coerceIn(1, WINDOW_DAYS)
                val recent = temps.takeLast(windowDays)
                val hotDays = recent.count { it.second > THRESHOLD_TEMP }

                if (hotDays >= THRESHOLD_DAYS && !s.alertAlreadySent) {
                    if (!phone.isNullOrBlank()) {
                        sendSms(
                            phone,
                            "Včelí zápisník: na stanovišti „${s.name}“ bylo $hotDays z posledních " +
                                "$windowDays dní nad ${THRESHOLD_TEMP.toInt()} °C – zvaž síření rámků proti motýlici."
                        )
                    }
                    db.stanovisteDao().update(s.copy(alertAlreadySent = true))
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendSms(phone: String, text: String) {
        val granted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return // appka SMS oprávnění nemá – uživatel ho zatím neudělil
        try {
            @Suppress("DEPRECATION")
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            // Delší zprávy SMS automaticky rozdělí appka/operátor do více částí.
            val parts = sms?.divideMessage(text)
            if (parts != null) {
                sms.sendMultipartTextMessage(phone, null, parts, null, null)
            }
        } catch (e: Exception) {
            // Odeslání se nepovedlo (např. telefon bez SIM) – zkusíme znovu příští den.
        }
    }
}
