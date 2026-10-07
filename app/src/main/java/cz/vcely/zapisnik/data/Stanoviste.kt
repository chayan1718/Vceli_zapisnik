package cz.vcely.zapisnik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stanoviště – místo, kde stojí jeden nebo více úlů. Identifikuje se názvem
 * (např. "Vidlákova Lhota", "Benešov Vršky") a GPS polohou, podle které ho
 * appka při dalších návštěvách automaticky rozpozná (tolerance cca 300 m).
 *
 * Souřadnice jsou nepovinné: pokud appka při zadávání názvu neměla k dispozici
 * žádnou polohu (vypnuté GPS, slabý signál), stanoviště se přesto uloží, ale
 * bez souřadnic – aby se omylem nepárovalo s (0, 0) a nekazilo rozpoznávání
 * jiných, skutečně změřených míst.
 *
 * [motyliceMonitoring]: hlídání rizika zavíječe voskového (motýlice) u
 * skladovaných rámků na tomto stanovišti – viz [cz.vcely.zapisnik.weather.MotyliceWorker].
 * Hlídání je možné jen pro stanoviště se známými souřadnicemi.
 * [lastSireniAt]: kdy bylo naposledy provedeno "síření" (rozšíření) rámků.
 * [alertAlreadySent]: appka už pro aktuální období (od posledního síření)
 * upozornění poslala, ať nebombarduje SMSkou každý den znovu.
 */
@Entity(tableName = "stanoviste")
data class Stanoviste(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Long = System.currentTimeMillis(),
    val motyliceMonitoring: Boolean = false,
    val lastSireniAt: Long? = null,
    val alertAlreadySent: Boolean = false,
)
