package cz.vcely.zapisnik.data

import android.content.Context
import androidx.core.content.edit

/**
 * Jednoduché trvalé uložení jména/přezdívky uživatele a dalších nastavení
 * (SharedPreferences). Jméno se používá pro dotaz "Jak se jmenujete?" při
 * prvním spuštění appky; telefonní číslo pro SMS upozornění na hlídání
 * motýlice (viz [cz.vcely.zapisnik.weather.MotyliceWorker]).
 */
object UserPrefs {
    private const val PREFS = "vceli_zapisnik_prefs"
    private const val KEY_NAME = "user_name"
    private const val KEY_ALERT_PHONE = "alert_phone"

    fun getName(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
    }

    fun setName(context: Context, name: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit { putString(KEY_NAME, name.trim()) }
    }

    /** Telefonní číslo, na které appka posílá SMS upozornění na síření rámků. */
    fun getAlertPhone(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getString(KEY_ALERT_PHONE, null)?.takeIf { it.isNotBlank() }
    }

    fun setAlertPhone(context: Context, phone: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit { putString(KEY_ALERT_PHONE, phone.trim()) }
    }
}
