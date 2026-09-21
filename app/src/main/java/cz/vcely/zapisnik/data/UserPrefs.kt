package cz.vcely.zapisnik.data

import android.content.Context
import androidx.core.content.edit

/**
 * Jednoduché trvalé uložení jména/přezdívky uživatele (SharedPreferences).
 * Používá se pro dotaz "Jak se jmenujete?" při prvním spuštění appky.
 */
object UserPrefs {
    private const val PREFS = "vceli_zapisnik_prefs"
    private const val KEY_NAME = "user_name"

    fun getName(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
    }

    fun setName(context: Context, name: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit { putString(KEY_NAME, name.trim()) }
    }
}
