package cz.vcely.zapisnik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Úl (včelstvo). Identifikuje se číslem, které se používá i v diktovaných záznamech. */
@Entity(tableName = "hives")
data class Hive(
    @PrimaryKey val number: Int,   // číslo úlu, např. 5
    val name: String? = null,      // volitelný název / stanoviště
)
