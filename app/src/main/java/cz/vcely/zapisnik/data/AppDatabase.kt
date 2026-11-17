package cz.vcely.zapisnik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Hive::class, Inspection::class, Stanoviste::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hiveDao(): HiveDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun stanovisteDao(): StanovisteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** 5 -> 6: přibyl sloupec s fotkou podložky; stávající data zůstanou. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inspections ADD COLUMN varroaPhotoPath TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vceli_zapisnik.db"
                )
                    // Appka zatím nemá psané migrace, takže při zvýšení verze
                    // schématu (naposledy: hlídání motýlice u stanoviště,
                    // verze 4 -> 5) se lokální databáze bezpečně (ale nevratně)
                    // přetvoří od nuly.
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
