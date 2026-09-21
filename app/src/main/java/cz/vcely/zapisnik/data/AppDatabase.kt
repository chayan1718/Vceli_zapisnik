package cz.vcely.zapisnik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Hive::class, Inspection::class, Stanoviste::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hiveDao(): HiveDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun stanovisteDao(): StanovisteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vceli_zapisnik.db"
                )
                    // Appka zatím nemá psané migrace, takže při zvýšení verze
                    // schématu (naposledy: souřadnice stanoviště jsou nepovinné,
                    // verze 3 -> 4) se lokální databáze bezpečně (ale nevratně)
                    // přetvoří od nuly.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
