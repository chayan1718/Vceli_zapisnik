package cz.vcely.zapisnik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Úl (včelstvo). Identifikuje se buď číslem ("5"), nebo jménem/přezdívkou
 * ("Jana") – appka rozezná obojí i v diktovaných záznamech.
 */
@Entity(tableName = "hives")
data class Hive(
    @PrimaryKey val id: String,   // identifikátor úlu, např. "5" nebo "Jana"
)
