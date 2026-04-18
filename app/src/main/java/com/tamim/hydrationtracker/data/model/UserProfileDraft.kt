package com.tamim.hydrationtracker.data.model

data class UserProfileDraft(
    val name: String,
    val ageText: String,
    val weightText: String,
    val heightText: String,
    val wakeHourText: String,
    val sleepHourText: String,
    val biologicalSex: BiologicalSex,
    val activityLevel: ActivityLevel,
    val climateType: ClimateType,
    val reminderStyle: ReminderStyle,
    val hasHealthCondition: Boolean
)
