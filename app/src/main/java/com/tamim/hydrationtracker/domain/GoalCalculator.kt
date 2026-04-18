package com.tamim.hydrationtracker.domain

import com.tamim.hydrationtracker.data.model.ActivityLevel
import com.tamim.hydrationtracker.data.model.BiologicalSex
import com.tamim.hydrationtracker.data.model.ClimateType
import com.tamim.hydrationtracker.data.model.UserProfile
import kotlin.math.roundToInt

object GoalCalculator {

    fun calculateGoalMl(profile: UserProfile): Int {
        var goal = (profile.weightKg * 35f).roundToInt()

        goal += when (profile.biologicalSex) {
            BiologicalSex.MALE -> 200
            BiologicalSex.FEMALE, BiologicalSex.OTHER -> 0
        }

        goal += when (profile.activityLevel) {
            ActivityLevel.SEDENTARY -> 0
            ActivityLevel.LIGHT -> 200
            ActivityLevel.ACTIVE -> 400
            ActivityLevel.ATHLETE -> 700
        }

        goal += when (profile.climateType) {
            ClimateType.TEMPERATE -> 0
            ClimateType.HUMID -> 350
            ClimateType.HOT -> 500
            ClimateType.COLD -> 0
        }

        if (profile.age < 18) goal -= 150
        if (profile.age > 65) goal += 150

        if (profile.hasHealthCondition) {
            goal = (goal * 0.95f).roundToInt()
        }

        return goal.coerceIn(1200, 5500)
    }

    fun explanation(profile: UserProfile, goalMl: Int): String {
        return "Based on your ${profile.weightKg}kg body weight and ${profile.activityLevel.name.lowercase()} lifestyle, your goal is ${goalMl} ml/day."
    }
}
