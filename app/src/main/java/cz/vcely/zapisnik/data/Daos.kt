package cz.vcely.zapisnik.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HiveDao {
    @Query("SELECT * FROM hives ORDER BY id")
    fun observeAll(): Flow<List<Hive>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hive: Hive)

    // Záloha a obnova (nemění schéma databáze).
    @Query("SELECT * FROM hives ORDER BY id")
    suspend fun getAll(): List<Hive>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(hives: List<Hive>)
}

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Inspection>>

    @Query("UPDATE inspections SET stanovisteName = :newName WHERE TRIM(stanovisteName) = TRIM(:oldName) COLLATE NOCASE")
    suspend fun renameStanoviste(oldName: String, newName: String)

    @Query("SELECT * FROM inspections WHERE hiveId = :hive ORDER BY timestamp DESC")
    fun observeForHive(hive: String): Flow<List<Inspection>>

    @Insert
    suspend fun insert(inspection: Inspection): Long

    @Update
    suspend fun update(inspection: Inspection)

    @Delete
    suspend fun delete(inspection: Inspection)

    @Query("SELECT * FROM inspections ORDER BY hiveId, timestamp")
    suspend fun getAllForExport(): List<Inspection>

    // Obnova ze zálohy: záznamy se stejným id se přepíšou, ostatní zůstanou.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(inspections: List<Inspection>)
}

@Dao
interface StanovisteDao {
    @Query("SELECT * FROM stanoviste ORDER BY name")
    fun observeAll(): Flow<List<Stanoviste>>

    @Query("UPDATE stanoviste SET name = :newName WHERE TRIM(name) = TRIM(:oldName) COLLATE NOCASE")
    suspend fun renameByName(oldName: String, newName: String)

    @Query("SELECT * FROM stanoviste WHERE motyliceMonitoring = 1")
    suspend fun getMonitored(): List<Stanoviste>

    @Insert
    suspend fun insert(stanoviste: Stanoviste): Long

    @Update
    suspend fun update(stanoviste: Stanoviste)

    // Záloha a obnova.
    @Query("SELECT * FROM stanoviste ORDER BY id")
    suspend fun getAll(): List<Stanoviste>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stanoviste: List<Stanoviste>)
}
