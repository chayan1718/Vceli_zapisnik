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
    @Query("SELECT * FROM hives ORDER BY number")
    fun observeAll(): Flow<List<Hive>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hive: Hive)
}

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE hiveNumber = :hive ORDER BY timestamp DESC")
    fun observeForHive(hive: Int): Flow<List<Inspection>>

    @Insert
    suspend fun insert(inspection: Inspection): Long

    @Update
    suspend fun update(inspection: Inspection)

    @Delete
    suspend fun delete(inspection: Inspection)

    @Query("SELECT * FROM inspections ORDER BY hiveNumber, timestamp")
    suspend fun getAllForExport(): List<Inspection>
}
