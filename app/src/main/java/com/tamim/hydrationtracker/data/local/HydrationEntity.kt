package com.tamim.hydrationtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hydration_entries")
data class HydrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMl: Int,
    val timestampEpochMs: Long,
    val source: String
)
