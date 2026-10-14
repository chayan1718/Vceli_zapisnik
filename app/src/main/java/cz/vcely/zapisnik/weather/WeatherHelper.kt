package cz.vcely.zapisnik.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Stahuje denní maximální teploty pro dané souřadnice z Open-Meteo
 * (https://open-meteo.com) – zdarma, bez API klíče. Používá se k hlídání
 * rizika zavíječe voskového (motýlice) u skladovaných rámků, viz
 * [MotyliceWorker].
 */
object WeatherHelper {

    /**
     * Vrátí denní maximální teploty za posledních [pastDays] dní (včetně
     * dneška) pro dané GPS souřadnice, jako seznam dvojic (datum, °C) –
     * nebo null při chybě (appka je offline, Open-Meteo nedostupné apod.).
     */
    fun fetchDailyMaxTemps(latitude: Double, longitude: Double, pastDays: Int = 7): List<Pair<String, Double>>? {
        return try {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f" +
                    "&daily=temperature_2m_max&past_days=%d&forecast_days=1&timezone=auto",
                latitude, longitude, pastDays
            )
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val daily = root.optJSONObject("daily") ?: return null
            val dates = daily.optJSONArray("time") ?: return null
            val temps = daily.optJSONArray("temperature_2m_max") ?: return null
            val result = mutableListOf<Pair<String, Double>>()
            for (i in 0 until dates.length()) {
                if (temps.isNull(i)) continue
                result.add(dates.getString(i) to temps.getDouble(i))
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
