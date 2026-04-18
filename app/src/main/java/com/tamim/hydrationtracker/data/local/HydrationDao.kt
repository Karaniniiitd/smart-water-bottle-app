package com.tamim.hydrationtracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HydrationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HydrationEntity)

    @Query("SELECT * FROM hydration_entries ORDER BY timestampEpochMs DESC")
    fun observeAllEntries(): Flow<List<HydrationEntity>>

    @Query("SELECT * FROM hydration_entries WHERE timestampEpochMs >= :fromEpochMs ORDER BY timestampEpochMs DESC")
    fun observeEntriesSince(fromEpochMs: Long): Flow<List<HydrationEntity>>
}
