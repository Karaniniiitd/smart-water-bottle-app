package com.tamim.hydrationtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tamim.hydrationtracker.ble.BottleSensorGateway
import com.tamim.hydrationtracker.ble.BleDevice
import com.tamim.hydrationtracker.ble.SensorMode
import com.tamim.hydrationtracker.data.model.DailyIntake
import com.tamim.hydrationtracker.data.model.EntrySource
import com.tamim.hydrationtracker.data.model.HydrationEntry
import com.tamim.hydrationtracker.data.model.UserProfile
import com.tamim.hydrationtracker.data.model.UserProfileDraft
import com.tamim.hydrationtracker.data.repo.HydrationRepository
import com.tamim.hydrationtracker.data.repo.SettingsSection
import com.tamim.hydrationtracker.data.repo.SettingsSectionState
import com.tamim.hydrationtracker.domain.GoalCalculator
import com.tamim.hydrationtracker.work.ReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CsvExportResult(
    val message: String,
    val filePath: String?,
    val preview: String
)

data class ExportFileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long
)

data class HydrationUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val goalMl: Int = 2500,
    val goalExplanation: String = "",
    val todayIntakeMl: Int = 0,
    val weeklyStats: List<DailyIntake> = emptyList(),
    val entries: List<HydrationEntry> = emptyList(),
    val unitIsMl: Boolean = true,
    val settingsProfileExpanded: Boolean = true,
    val settingsControlsExpanded: Boolean = true,
    val settingsExportExpanded: Boolean = true,
    val settingsRecentExpanded: Boolean = true,
    val profileDraft: UserProfileDraft? = null,
    val sensorMode: SensorMode = SensorMode.SIMULATION,
    val connectedDevice: BleDevice? = null,
    val scannedDevices: List<BleDevice> = emptyList()
) {
    val progress: Float
        get() = (todayIntakeMl.toFloat() / goalMl.toFloat()).coerceIn(0f, 1f)

    val streakDays: Int
        get() {
            var streak = 0
            val sorted = weeklyStats.reversed()
            for (day in sorted) {
                if (day.hitGoal) streak++ else break
            }
            return streak
        }

    val lastSipAgoMinutes: Long
        get() {
            val latest = entries.firstOrNull() ?: return -1
            return ((System.currentTimeMillis() - latest.timestampEpochMs) / 60_000L).coerceAtLeast(0)
        }
}

class HydrationViewModel(
    app: Application,
    private val repository: HydrationRepository,
    private val sensorGateway: BottleSensorGateway
) : AndroidViewModel(app) {

    private val goalFlow = MutableStateFlow(2500)

    init {
        viewModelScope.launch {
            sensorGateway.sipEventsMl.collect { sipMl ->
                repository.addHydration(sipMl, EntrySource.BLE)
            }
        }
        viewModelScope.launch {
            repository.profileFlow.collect { profile ->
                profile?.let {
                    goalFlow.value = GoalCalculator.calculateGoalMl(it)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<HydrationUiState> = combine(
        repository.profileFlow,
        repository.todayTotalFlow(),
        repository.allEntriesFlow(),
        repository.unitIsMlFlow,
        repository.settingsSectionStateFlow,
        repository.profileDraftFlow,
        sensorGateway.devices,
        sensorGateway.connectedDevice,
        goalFlow
    ) { args: Array<Any?> ->
        val profile = args[0] as UserProfile?
        val todayMl = args[1] as Int
        val entries = args[2] as List<HydrationEntry>
        val unitIsMl = args[3] as Boolean
        val sectionState = args[4] as SettingsSectionState
        val profileDraft = args[5] as UserProfileDraft?
        val scannedDevices = args[6] as List<BleDevice>
        val connected = args[7] as BleDevice?
        val currentGoal = args[8] as Int

        val computedGoal = profile?.let { GoalCalculator.calculateGoalMl(it) } ?: currentGoal
        val weeklyStats = computeWeekly(entries, computedGoal)

        HydrationUiState(
            loading = false,
            profile = profile,
            goalMl = computedGoal,
            goalExplanation = profile?.let { GoalCalculator.explanation(it, computedGoal) } ?: "Set up your profile to personalize your goal.",
            todayIntakeMl = todayMl,
            weeklyStats = weeklyStats,
            entries = entries,
            unitIsMl = unitIsMl,
            settingsProfileExpanded = sectionState.profileExpanded,
            settingsControlsExpanded = sectionState.controlsExpanded,
            settingsExportExpanded = sectionState.exportExpanded,
            settingsRecentExpanded = sectionState.recentExpanded,
            profileDraft = profileDraft,
            sensorMode = sensorGateway.mode,
            connectedDevice = connected,
            scannedDevices = scannedDevices
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HydrationUiState()
    )

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            goalFlow.value = GoalCalculator.calculateGoalMl(profile)
            ReminderWorker.schedule(getApplication())
        }
    }

    fun logManualSip(amountMl: Int) {
        viewModelScope.launch {
            repository.addHydration(amountMl, EntrySource.MANUAL)
        }
    }

    fun logBleSip(amountMl: Int = 120) {
        sensorGateway.triggerSip(amountMl)
    }

    fun startScan() {
        viewModelScope.launch {
            sensorGateway.startScan()
        }
    }

    fun connectToBottle(device: BleDevice) {
        sensorGateway.connect(device)
    }

    fun disconnectBottle() {
        sensorGateway.disconnect()
    }

    fun toggleUnit() {
        viewModelScope.launch {
            repository.setUnitIsMl(!uiState.value.unitIsMl)
        }
    }

    fun setSettingsProfileExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsSectionExpanded(SettingsSection.PROFILE, expanded)
        }
    }

    fun setSettingsControlsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsSectionExpanded(SettingsSection.CONTROLS, expanded)
        }
    }

    fun setSettingsExportExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsSectionExpanded(SettingsSection.EXPORT, expanded)
        }
    }

    fun setSettingsRecentExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsSectionExpanded(SettingsSection.RECENT, expanded)
        }
    }

    fun saveProfileDraft(draft: UserProfileDraft) {
        viewModelScope.launch {
            repository.saveProfileDraft(draft)
        }
    }

    fun clearProfileDraft() {
        viewModelScope.launch {
            repository.clearProfileDraft()
        }
    }

    fun exportCsv(): CsvExportResult {
        val lines = buildList {
            add("timestamp,amount_ml,source")
            uiState.value.entries.forEach { entry ->
                add("${entry.timestampEpochMs},${entry.amountMl},${entry.source}")
            }
        }
        val csvText = lines.joinToString("\n")
        val preview = csvText.lines().take(8).joinToString("\n")

        return runCatching {
            val root = getApplication<Application>().getExternalFilesDir(null)
                ?: error("External files directory not available")
            val exportDir = File(root, "exports").apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val file = File(exportDir, "hydration_export_$stamp.csv")
            file.writeText(csvText)

            CsvExportResult(
                message = "Saved to:\n${file.absolutePath}",
                filePath = file.absolutePath,
                preview = preview
            )
        }.getOrElse { error ->
            CsvExportResult(
                message = "Export failed: ${error.message ?: "Unknown error"}",
                filePath = null,
                preview = preview
            )
        }
    }

    fun latestExportFilePath(): String? {
        val exportDir = getExportDirectoryOrNull() ?: return null
        return exportDir
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    fun listExportFiles(): List<ExportFileItem> {
        val exportDir = getExportDirectoryOrNull() ?: return emptyList()
        return exportDir
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                ExportFileItem(
                    name = it.name,
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                    lastModifiedEpochMs = it.lastModified()
                )
            }
            .orEmpty()
    }

    fun deleteExportFile(path: String): Boolean {
        return runCatching {
            val exportDir = getExportDirectoryOrNull() ?: return false
            val target = File(path)
            if (!target.exists() || !target.isFile) return false
            if (target.parentFile?.absolutePath != exportDir.absolutePath) return false
            target.delete()
        }.getOrDefault(false)
    }

    fun exportDirectoryPath(): String? = getExportDirectoryOrNull()?.absolutePath

    private fun getExportDirectoryOrNull(): File? {
        val root = getApplication<Application>().getExternalFilesDir(null) ?: return null
        val dir = File(root, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun computeWeekly(entries: List<HydrationEntry>, goalMl: Int): List<DailyIntake> {
        val byDay = entries.groupBy {
            Instant.ofEpochMilli(it.timestampEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return (0L..6L).map { offset ->
            val day = LocalDate.now().minusDays(6 - offset)
            val total = byDay[day]?.sumOf { it.amountMl } ?: 0
            DailyIntake(
                dayLabel = day.dayOfWeek.name.take(3),
                totalMl = total,
                hitGoal = total >= goalMl
            )
        }
    }

    companion object {
        fun factory(
            app: Application,
            repository: HydrationRepository,
            sensorGateway: BottleSensorGateway
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HydrationViewModel::class.java)) {
                        return HydrationViewModel(app, repository, sensorGateway) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
