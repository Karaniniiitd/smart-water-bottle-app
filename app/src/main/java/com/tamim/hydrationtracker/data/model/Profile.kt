package com.tamim.hydrationtracker.data.model

enum class BiologicalSex {
    MALE,
    FEMALE,
    OTHER
}

enum class ActivityLevel {
    SEDENTARY,
    LIGHT,
    ACTIVE,
    ATHLETE
}

enum class ClimateType {
    TEMPERATE,
    HUMID,
    HOT,
    COLD
}

enum class ReminderStyle {
    GENTLE,
    AGGRESSIVE,
    GAMIFIED
}

data class UserProfile(
    val name: String,
    val age: Int,
    val weightKg: Int,
    val heightCm: Int,
    val biologicalSex: BiologicalSex,
    val activityLevel: ActivityLevel,
    val climateType: ClimateType,
    val hasHealthCondition: Boolean,
    val wakeHour: Int,
    val sleepHour: Int,
    val reminderStyle: ReminderStyle
)

enum class EntrySource {
    MANUAL,
    BLE
}

data class HydrationEntry(
    val id: Long,
    val amountMl: Int,
    val timestampEpochMs: Long,
    val source: EntrySource
)

data class DailyIntake(
    val dayLabel: String,
    val totalMl: Int,
    val hitGoal: Boolean
)
