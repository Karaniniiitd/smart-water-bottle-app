package com.tamim.hydrationtracker.domain

import com.tamim.hydrationtracker.data.model.ActivityLevel
import com.tamim.hydrationtracker.data.model.BiologicalSex
import com.tamim.hydrationtracker.data.model.ClimateType
import com.tamim.hydrationtracker.data.model.ReminderStyle
import com.tamim.hydrationtracker.data.model.UserProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    @Test
    fun `active male in hot climate gets elevated goal`() {
        val profile = UserProfile(
            name = "Tamim",
            age = 28,
            weightKg = 80,
            heightCm = 180,
            biologicalSex = BiologicalSex.MALE,
            activityLevel = ActivityLevel.ACTIVE,
            climateType = ClimateType.HOT,
            hasHealthCondition = false,
            wakeHour = 7,
            sleepHour = 23,
            reminderStyle = ReminderStyle.GAMIFIED
        )

        val goal = GoalCalculator.calculateGoalMl(profile)

        assertTrue(goal >= 3800)
    }

    @Test
    fun `goal remains inside safe clamp`() {
        val profile = UserProfile(
            name = "Senior",
            age = 72,
            weightKg = 140,
            heightCm = 178,
            biologicalSex = BiologicalSex.MALE,
            activityLevel = ActivityLevel.ATHLETE,
            climateType = ClimateType.HOT,
            hasHealthCondition = false,
            wakeHour = 5,
            sleepHour = 22,
            reminderStyle = ReminderStyle.AGGRESSIVE
        )

        val goal = GoalCalculator.calculateGoalMl(profile)
        assertTrue(goal <= 5500)
    }
}
