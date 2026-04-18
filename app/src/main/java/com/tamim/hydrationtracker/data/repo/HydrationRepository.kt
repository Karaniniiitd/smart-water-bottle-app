package com.tamim.hydrationtracker.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamim.hydrationtracker.data.local.HydrationDao
import com.tamim.hydrationtracker.data.local.HydrationEntity
import com.tamim.hydrationtracker.data.model.ActivityLevel
import com.tamim.hydrationtracker.data.model.BiologicalSex
import com.tamim.hydrationtracker.data.model.ClimateType
import com.tamim.hydrationtracker.data.model.DailyIntake
import com.tamim.hydrationtracker.data.model.EntrySource
import com.tamim.hydrationtracker.data.model.HydrationEntry
import com.tamim.hydrationtracker.data.model.ReminderStyle
import com.tamim.hydrationtracker.data.model.UserProfile
import com.tamim.hydrationtracker.data.model.UserProfileDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "hydration_settings")

enum class SettingsSection {
    PROFILE,
    CONTROLS,
    EXPORT,
    RECENT
}

data class SettingsSectionState(
    val profileExpanded: Boolean = true,
    val controlsExpanded: Boolean = true,
    val exportExpanded: Boolean = true,
    val recentExpanded: Boolean = true
)

class HydrationRepository(
    private val context: Context,
    private val hydrationDao: HydrationDao
) {

    private object Keys {
        val name = stringPreferencesKey("name")
        val age = intPreferencesKey("age")
        val weight = intPreferencesKey("weight")
        val height = intPreferencesKey("height")
        val sex = stringPreferencesKey("sex")
        val activity = stringPreferencesKey("activity")
        val climate = stringPreferencesKey("climate")
        val healthCondition = booleanPreferencesKey("health_condition")
        val wakeHour = intPreferencesKey("wake_hour")
        val sleepHour = intPreferencesKey("sleep_hour")
        val reminderStyle = stringPreferencesKey("reminder_style")
        val unitIsMl = booleanPreferencesKey("unit_is_ml")
        val profileExpanded = booleanPreferencesKey("settings_profile_expanded")
        val controlsExpanded = booleanPreferencesKey("settings_controls_expanded")
        val exportExpanded = booleanPreferencesKey("settings_export_expanded")
        val recentExpanded = booleanPreferencesKey("settings_recent_expanded")

        val profileDraftActive = booleanPreferencesKey("profile_draft_active")
        val profileDraftName = stringPreferencesKey("profile_draft_name")
        val profileDraftAgeText = stringPreferencesKey("profile_draft_age_text")
        val profileDraftWeightText = stringPreferencesKey("profile_draft_weight_text")
        val profileDraftHeightText = stringPreferencesKey("profile_draft_height_text")
        val profileDraftWakeHourText = stringPreferencesKey("profile_draft_wake_hour_text")
        val profileDraftSleepHourText = stringPreferencesKey("profile_draft_sleep_hour_text")
        val profileDraftSex = stringPreferencesKey("profile_draft_sex")
        val profileDraftActivity = stringPreferencesKey("profile_draft_activity")
        val profileDraftClimate = stringPreferencesKey("profile_draft_climate")
        val profileDraftReminderStyle = stringPreferencesKey("profile_draft_reminder_style")
        val profileDraftHealthCondition = booleanPreferencesKey("profile_draft_health_condition")
    }

    val profileFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        prefs.toProfileOrNull()
    }

    val unitIsMlFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.unitIsMl] ?: true }

    val settingsSectionStateFlow: Flow<SettingsSectionState> = context.dataStore.data.map { prefs ->
        SettingsSectionState(
            profileExpanded = prefs[Keys.profileExpanded] ?: true,
            controlsExpanded = prefs[Keys.controlsExpanded] ?: true,
            exportExpanded = prefs[Keys.exportExpanded] ?: true,
            recentExpanded = prefs[Keys.recentExpanded] ?: true
        )
    }

    val profileDraftFlow: Flow<UserProfileDraft?> = context.dataStore.data.map { prefs ->
        val active = prefs[Keys.profileDraftActive] ?: false
        if (!active) return@map null

        UserProfileDraft(
            name = prefs[Keys.profileDraftName].orEmpty(),
            ageText = prefs[Keys.profileDraftAgeText].orEmpty(),
            weightText = prefs[Keys.profileDraftWeightText].orEmpty(),
            heightText = prefs[Keys.profileDraftHeightText].orEmpty(),
            wakeHourText = prefs[Keys.profileDraftWakeHourText].orEmpty(),
            sleepHourText = prefs[Keys.profileDraftSleepHourText].orEmpty(),
            biologicalSex = enumValueOfOrDefault(prefs[Keys.profileDraftSex], BiologicalSex.OTHER),
            activityLevel = enumValueOfOrDefault(prefs[Keys.profileDraftActivity], ActivityLevel.LIGHT),
            climateType = enumValueOfOrDefault(prefs[Keys.profileDraftClimate], ClimateType.TEMPERATE),
            reminderStyle = enumValueOfOrDefault(prefs[Keys.profileDraftReminderStyle], ReminderStyle.GENTLE),
            hasHealthCondition = prefs[Keys.profileDraftHealthCondition] ?: false
        )
    }

    fun allEntriesFlow(): Flow<List<HydrationEntry>> =
        hydrationDao.observeAllEntries().map { list -> list.map { it.toModel() } }

    fun todayTotalFlow(): Flow<Int> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return hydrationDao.observeEntriesSince(start).map { list -> list.sumOf { it.amountMl } }
    }

    fun weeklyStatsFlow(goalMl: Int): Flow<List<DailyIntake>> {
        val start = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return hydrationDao.observeEntriesSince(start).map { entities ->
            val grouped = entities.groupBy {
                Instant.ofEpochMilli(it.timestampEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            (0L..6L).map { offset ->
                val day = LocalDate.now().minusDays(6 - offset)
                val total = grouped[day]?.sumOf { it.amountMl } ?: 0
                DailyIntake(
                    dayLabel = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    totalMl = total,
                    hitGoal = total >= goalMl
                )
            }
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.name] = profile.name
            prefs[Keys.age] = profile.age
            prefs[Keys.weight] = profile.weightKg
            prefs[Keys.height] = profile.heightCm
            prefs[Keys.sex] = profile.biologicalSex.name
            prefs[Keys.activity] = profile.activityLevel.name
            prefs[Keys.climate] = profile.climateType.name
            prefs[Keys.healthCondition] = profile.hasHealthCondition
            prefs[Keys.wakeHour] = profile.wakeHour
            prefs[Keys.sleepHour] = profile.sleepHour
            prefs[Keys.reminderStyle] = profile.reminderStyle.name
        }
    }

    suspend fun setUnitIsMl(isMl: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.unitIsMl] = isMl
        }
    }

    suspend fun setSettingsSectionExpanded(section: SettingsSection, expanded: Boolean) {
        context.dataStore.edit { prefs ->
            when (section) {
                SettingsSection.PROFILE -> prefs[Keys.profileExpanded] = expanded
                SettingsSection.CONTROLS -> prefs[Keys.controlsExpanded] = expanded
                SettingsSection.EXPORT -> prefs[Keys.exportExpanded] = expanded
                SettingsSection.RECENT -> prefs[Keys.recentExpanded] = expanded
            }
        }
    }

    suspend fun saveProfileDraft(draft: UserProfileDraft) {
        context.dataStore.edit { prefs ->
            prefs[Keys.profileDraftActive] = true
            prefs[Keys.profileDraftName] = draft.name
            prefs[Keys.profileDraftAgeText] = draft.ageText
            prefs[Keys.profileDraftWeightText] = draft.weightText
            prefs[Keys.profileDraftHeightText] = draft.heightText
            prefs[Keys.profileDraftWakeHourText] = draft.wakeHourText
            prefs[Keys.profileDraftSleepHourText] = draft.sleepHourText
            prefs[Keys.profileDraftSex] = draft.biologicalSex.name
            prefs[Keys.profileDraftActivity] = draft.activityLevel.name
            prefs[Keys.profileDraftClimate] = draft.climateType.name
            prefs[Keys.profileDraftReminderStyle] = draft.reminderStyle.name
            prefs[Keys.profileDraftHealthCondition] = draft.hasHealthCondition
        }
    }

    suspend fun clearProfileDraft() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.profileDraftActive)
            prefs.remove(Keys.profileDraftName)
            prefs.remove(Keys.profileDraftAgeText)
            prefs.remove(Keys.profileDraftWeightText)
            prefs.remove(Keys.profileDraftHeightText)
            prefs.remove(Keys.profileDraftWakeHourText)
            prefs.remove(Keys.profileDraftSleepHourText)
            prefs.remove(Keys.profileDraftSex)
            prefs.remove(Keys.profileDraftActivity)
            prefs.remove(Keys.profileDraftClimate)
            prefs.remove(Keys.profileDraftReminderStyle)
            prefs.remove(Keys.profileDraftHealthCondition)
        }
    }

    suspend fun addHydration(amountMl: Int, source: EntrySource) {
        hydrationDao.insert(
            HydrationEntity(
                amountMl = amountMl,
                timestampEpochMs = System.currentTimeMillis(),
                source = source.name
            )
        )
    }

    private fun Preferences.toProfileOrNull(): UserProfile? {
        val name = this[Keys.name] ?: return null
        return UserProfile(
            name = name,
            age = this[Keys.age] ?: 25,
            weightKg = this[Keys.weight] ?: 70,
            heightCm = this[Keys.height] ?: 170,
            biologicalSex = enumValueOfOrDefault(this[Keys.sex], BiologicalSex.OTHER),
            activityLevel = enumValueOfOrDefault(this[Keys.activity], ActivityLevel.LIGHT),
            climateType = enumValueOfOrDefault(this[Keys.climate], ClimateType.TEMPERATE),
            hasHealthCondition = this[Keys.healthCondition] ?: false,
            wakeHour = this[Keys.wakeHour] ?: 7,
            sleepHour = this[Keys.sleepHour] ?: 23,
            reminderStyle = enumValueOfOrDefault(this[Keys.reminderStyle], ReminderStyle.GENTLE)
        )
    }

    private fun HydrationEntity.toModel(): HydrationEntry = HydrationEntry(
        id = id,
        amountMl = amountMl,
        timestampEpochMs = timestampEpochMs,
        source = enumValueOfOrDefault(source, EntrySource.MANUAL)
    )

    private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String?, default: T): T {
        return runCatching { enumValueOf<T>(value ?: "") }.getOrDefault(default)
    }
}
