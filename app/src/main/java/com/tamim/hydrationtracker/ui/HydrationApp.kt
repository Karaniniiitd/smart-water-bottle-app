package com.tamim.hydrationtracker.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tamim.hydrationtracker.data.model.ActivityLevel
import com.tamim.hydrationtracker.data.model.BiologicalSex
import com.tamim.hydrationtracker.data.model.ClimateType
import com.tamim.hydrationtracker.data.model.ReminderStyle
import com.tamim.hydrationtracker.data.model.UserProfile
import com.tamim.hydrationtracker.data.model.UserProfileDraft
import com.tamim.hydrationtracker.ble.SensorMode
import com.tamim.hydrationtracker.ui.theme.AmberWarning
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class Route(val route: String, val label: String) {
    data object Onboarding : Route("onboarding", "Onboarding")
    data object Pairing : Route("pairing", "Bottle")
    data object Dashboard : Route("dashboard", "Home")
    data object History : Route("history", "History")
    data object Insights : Route("insights", "Insights")
    data object Settings : Route("settings", "Settings")
}

private val bottomRoutes = listOf(
    Route.Dashboard,
    Route.History,
    Route.Insights,
    Route.Pairing,
    Route.Settings
)

@Composable
fun HydrationApp(viewModel: HydrationViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val start = if (uiState.profile == null) Route.Onboarding.route else Route.Dashboard.route

    Scaffold(
        bottomBar = {
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = currentBackStackEntry?.destination
            if (currentDestination?.route != Route.Onboarding.route) {
                NavigationBar {
                    bottomRoutes.forEach { route ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == route.route } == true,
                            onClick = {
                                navController.navigate(route.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (route) {
                                        Route.Dashboard -> Icons.Default.Home
                                        Route.History -> Icons.AutoMirrored.Filled.ShowChart
                                        Route.Insights -> Icons.Default.Insights
                                        Route.Pairing -> Icons.Default.Bluetooth
                                        Route.Settings -> Icons.Default.Settings
                                        Route.Onboarding -> Icons.Default.Home
                                    },
                                    contentDescription = route.label
                                )
                            },
                            label = { Text(route.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding)
        ) {
            composable(Route.Onboarding.route) {
                OnboardingScreen(
                    onSaveProfile = {
                        viewModel.saveProfile(it)
                        navController.navigate(Route.Pairing.route) {
                            popUpTo(Route.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Route.Pairing.route) {
                PairingScreen(
                    state = uiState,
                    onScan = viewModel::startScan,
                    onConnect = viewModel::connectToBottle,
                    onDisconnect = viewModel::disconnectBottle,
                    onContinue = { navController.navigate(Route.Dashboard.route) }
                )
            }
            composable(Route.Dashboard.route) {
                DashboardScreen(
                    state = uiState,
                    onManualAdd = viewModel::logManualSip,
                    onBleAdd = { viewModel.logBleSip() }
                )
            }
            composable(Route.History.route) {
                HistoryScreen(state = uiState)
            }
            composable(Route.Insights.route) {
                InsightsScreen(state = uiState)
            }
            composable(Route.Settings.route) {
                SettingsScreen(
                    state = uiState,
                    onSaveProfile = viewModel::saveProfile,
                    onSaveProfileDraft = viewModel::saveProfileDraft,
                    onClearProfileDraft = viewModel::clearProfileDraft,
                    onSetProfileSectionExpanded = viewModel::setSettingsProfileExpanded,
                    onSetControlsSectionExpanded = viewModel::setSettingsControlsExpanded,
                    onSetExportSectionExpanded = viewModel::setSettingsExportExpanded,
                    onSetRecentSectionExpanded = viewModel::setSettingsRecentExpanded,
                    onToggleUnit = viewModel::toggleUnit,
                    onExportCsv = viewModel::exportCsv,
                    onGetLatestExportPath = viewModel::latestExportFilePath,
                    onGetExportsDirectoryPath = viewModel::exportDirectoryPath,
                    onListExportFiles = viewModel::listExportFiles,
                    onDeleteExportFile = viewModel::deleteExportFile
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(
    onSaveProfile: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("25") }
    var weight by remember { mutableStateOf("70") }
    var height by remember { mutableStateOf("170") }
    var wakeHour by remember { mutableStateOf("7") }
    var sleepHour by remember { mutableStateOf("23") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var heightError by remember { mutableStateOf<String?>(null) }
    var wakeError by remember { mutableStateOf<String?>(null) }
    var sleepError by remember { mutableStateOf<String?>(null) }

    var sex by remember { mutableStateOf(BiologicalSex.OTHER) }
    var activity by remember { mutableStateOf(ActivityLevel.LIGHT) }
    var climate by remember { mutableStateOf(ClimateType.TEMPERATE) }
    var reminderStyle by remember { mutableStateOf(ReminderStyle.GENTLE) }
    var hasCondition by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Bio profile setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text("We use this to calculate your personalized daily hydration goal.")
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = age,
                onValueChange = {
                    age = it
                    ageError = null
                },
                label = { Text("Age") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = ageError != null,
                supportingText = { ageError?.let { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = weight,
                onValueChange = {
                    weight = it
                    weightError = null
                },
                label = { Text("Weight (kg)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = weightError != null,
                supportingText = { weightError?.let { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = height,
                onValueChange = {
                    height = it
                    heightError = null
                },
                label = { Text("Height (cm)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = heightError != null,
                supportingText = { heightError?.let { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = wakeHour,
                onValueChange = {
                    wakeHour = it
                    wakeError = null
                },
                label = { Text("Wake hour (0-23)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = wakeError != null,
                supportingText = { wakeError?.let { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = sleepHour,
                onValueChange = {
                    sleepHour = it
                    sleepError = null
                },
                label = { Text("Sleep hour (0-23)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = sleepError != null,
                supportingText = { sleepError?.let { Text(it) } }
            )
        }

        item {
            ChoiceChips(
                label = "Biological sex",
                options = BiologicalSex.entries,
                selected = sex,
                onSelect = { sex = it }
            )
        }
        item {
            ChoiceChips(
                label = "Activity",
                options = ActivityLevel.entries,
                selected = activity,
                onSelect = { activity = it }
            )
        }
        item {
            ChoiceChips(
                label = "Climate",
                options = ClimateType.entries,
                selected = climate,
                onSelect = { climate = it }
            )
        }
        item {
            ChoiceChips(
                label = "Reminder style",
                options = ReminderStyle.entries,
                selected = reminderStyle,
                onSelect = { reminderStyle = it }
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Health condition")
                Switch(checked = hasCondition, onCheckedChange = { hasCondition = it })
            }
        }

        item {
            Button(
                onClick = {
                    val parsedAge = age.toIntOrNull()
                    val parsedWeight = weight.toIntOrNull()
                    val parsedHeight = height.toIntOrNull()
                    val parsedWake = wakeHour.toIntOrNull()
                    val parsedSleep = sleepHour.toIntOrNull()

                    nameError = if (name.isBlank()) "Please enter your name." else null
                    ageError = when {
                        parsedAge == null -> "Enter a valid number."
                        parsedAge !in 8..100 -> "Age should be between 8 and 100."
                        else -> null
                    }
                    weightError = when {
                        parsedWeight == null -> "Enter a valid number."
                        parsedWeight !in 20..300 -> "Weight should be between 20 and 300 kg."
                        else -> null
                    }
                    heightError = when {
                        parsedHeight == null -> "Enter a valid number."
                        parsedHeight !in 90..250 -> "Height should be between 90 and 250 cm."
                        else -> null
                    }
                    wakeError = when {
                        parsedWake == null -> "Enter a valid hour."
                        parsedWake !in 0..23 -> "Wake hour must be 0 to 23."
                        else -> null
                    }
                    sleepError = when {
                        parsedSleep == null -> "Enter a valid hour."
                        parsedSleep !in 0..23 -> "Sleep hour must be 0 to 23."
                        else -> null
                    }

                    val hasErrors = listOf(
                        nameError,
                        ageError,
                        weightError,
                        heightError,
                        wakeError,
                        sleepError
                    ).any { it != null }

                    if (hasErrors) {
                        Toast.makeText(context, "Please fix the highlighted fields.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    onSaveProfile(
                        UserProfile(
                            name = name.trim(),
                            age = parsedAge!!,
                            weightKg = parsedWeight!!,
                            heightCm = parsedHeight!!,
                            biologicalSex = sex,
                            activityLevel = activity,
                            climateType = climate,
                            hasHealthCondition = hasCondition,
                            wakeHour = parsedWake!!,
                            sleepHour = parsedSleep!!,
                            reminderStyle = reminderStyle
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save profile & continue")
            }
        }
    }
}

@Composable
private fun PairingScreen(
    state: HydrationUiState,
    onScan: () -> Unit,
    onConnect: (com.tamim.hydrationtracker.ble.BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Require all permissions to be granted EXCEPT notifications (which are optional for scanning)
        val scanPermissionsGranted = results.entries.all { (permission, isGranted) ->
            if (permission == Manifest.permission.POST_NOTIFICATIONS) true else isGranted
        }
        
        if (scanPermissionsGranted) {
            onScan()
        } else {
            Toast.makeText(context, "Bluetooth and Location permissions are required for scanning.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestBluetoothPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        bluetoothPermissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Bottle pairing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Scan and connect to your BLE bottle for live sip sync.")
        Text(
            text = if (state.sensorMode == SensorMode.SIMULATION) {
                "Mode: Simulation (recommended while bottle firmware is in progress)."
            } else {
                "Mode: Real bottle hardware"
            },
            style = MaterialTheme.typography.bodySmall
        )

    Button(onClick = { requestBluetoothPermissionsAndScan() }) { Text("Scan devices") }

        state.connectedDevice?.let {
            Text("Connected: ${it.name} • Battery ${it.batteryPercent}%")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onContinue) { Text("Continue to dashboard") }
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.scannedDevices) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text("RSSI ${device.rssi} dBm")
                        }
                        Button(onClick = { onConnect(device) }) { Text("Pair") }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ESP debug", fontWeight = FontWeight.SemiBold)
                Text(
                    "Last parsed sip: ${state.bleDebug.lastParsedSipMl?.let { "$it ml" } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Raw (text): ${state.bleDebug.lastRawPacketText ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Raw (hex): ${state.bleDebug.lastRawPacketHex ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text("Connection timeline", fontWeight = FontWeight.Medium)
                if (state.bleDebug.connectionEvents.isEmpty()) {
                    Text("No events yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    state.bleDebug.connectionEvents.takeLast(8).reversed().forEach { event ->
                        Text("• $event", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    state: HydrationUiState,
    onManualAdd: (Int) -> Unit,
    onBleAdd: () -> Unit
) {
    val statusColor = when {
        state.progress >= 0.8f -> MaterialTheme.colorScheme.primary
        state.progress >= 0.5f -> AmberWarning
        else -> Color.Red
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hydration dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Streak: ${state.streakDays} days")

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.todayIntakeMl} / ${state.goalMl} ml", fontWeight = FontWeight.Bold)
                Text("${(state.progress * 100).toInt()}%")
            }
        }

        val lastSip = if (state.lastSipAgoMinutes < 0) "No sips yet" else "Last sip: ${state.lastSipAgoMinutes} min ago"
        Text(lastSip)
        Text(state.goalExplanation, textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onManualAdd(100) }) { Text("+100 ml") }
            Button(onClick = { onManualAdd(250) }) { Text("+250 ml") }
            Button(onClick = onBleAdd) { Text("BLE sip") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(12.dp),
                text = when {
                    state.progress >= 1f -> "Amazing — goal hit! Keep steady through the evening."
                    state.progress >= 0.6f -> "On track. Keep sipping every 30–45 minutes."
                    else -> "You’re behind. Try a full glass now and one more in 45 minutes."
                }
            )
        }
    }
}

@Composable
private fun HistoryScreen(state: HydrationUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("7-day history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Average: ${state.weeklyStats.map { it.totalMl }.average().toInt()} ml/day")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            state.weeklyStats.forEach { day ->
                val ratio = (day.totalMl.toFloat() / state.goalMl.toFloat()).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height((ratio * 140).dp)
                            .background(if (day.hitGoal) MaterialTheme.colorScheme.primary else AmberWarning)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(day.dayLabel)
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(state: HydrationUiState) {
    val bestDay = state.weeklyStats.maxByOrNull { it.totalMl }
    val worstDay = state.weeklyStats.minByOrNull { it.totalMl }
    val avg = state.weeklyStats.map { it.totalMl }.average().toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        InsightCard("Pattern", "You’re usually lower in afternoon hours. Schedule extra reminder around 2–4 PM.")
        InsightCard("Best day", "${bestDay?.dayLabel ?: "-"} with ${bestDay?.totalMl ?: 0} ml")
        InsightCard("Lowest day", "${worstDay?.dayLabel ?: "-"} with ${worstDay?.totalMl ?: 0} ml")
        InsightCard("Average intake", "$avg ml/day")

        val suggestedGoal = if (avg > state.goalMl * 1.1f) (state.goalMl * 1.05f).toInt() else state.goalMl
        Text("Suggested adjusted goal: $suggestedGoal ml/day")
    }
}

@Composable
private fun SettingsScreen(
    state: HydrationUiState,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveProfileDraft: (UserProfileDraft) -> Unit,
    onClearProfileDraft: () -> Unit,
    onSetProfileSectionExpanded: (Boolean) -> Unit,
    onSetControlsSectionExpanded: (Boolean) -> Unit,
    onSetExportSectionExpanded: (Boolean) -> Unit,
    onSetRecentSectionExpanded: (Boolean) -> Unit,
    onToggleUnit: () -> Unit,
    onExportCsv: () -> CsvExportResult,
    onGetLatestExportPath: () -> String?,
    onGetExportsDirectoryPath: () -> String?,
    onListExportFiles: () -> List<ExportFileItem>,
    onDeleteExportFile: (String) -> Boolean
) {
    val context = LocalContext.current
    var showCsv by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<CsvExportResult?>(null) }
    var exportFiles by remember { mutableStateOf(onListExportFiles()) }
    var showEditProfile by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "Notification permission granted." else "Notification permission denied.",
            Toast.LENGTH_SHORT
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            fun refreshExports() {
                exportFiles = onListExportFiles()
            }

            fun formatTime(epochMs: Long): String {
                return runCatching {
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(epochMs))
                }.getOrDefault("Unknown time")
            }

            fun formatSize(bytes: Long): String {
                return when {
                    bytes < 1024 -> "$bytes B"
                    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                    else -> "${bytes / (1024 * 1024)} MB"
                }
            }

            fun shareExport(path: String) {
                val file = File(path)
                if (!file.exists()) {
                    Toast.makeText(context, "Export file not found.", Toast.LENGTH_SHORT).show()
                    return
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Share hydration export"))
                }.onFailure {
                    Toast.makeText(context, "No compatible app found for sharing.", Toast.LENGTH_SHORT).show()
                }
            }

            fun openExportsFolder(path: String) {
                val folder = File(path)
                if (!folder.exists()) {
                    Toast.makeText(context, "Export folder does not exist yet.", Toast.LENGTH_SHORT).show()
                    return
                }

                val folderUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
                val openFolderIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val launched = runCatching {
                    context.startActivity(openFolderIntent)
                }.isSuccess

                if (!launched) {
                    val latest = onGetLatestExportPath()
                    if (latest != null) {
                        shareExport(latest)
                        Toast.makeText(context, "Folder open not supported here; opened share sheet for latest export.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "No export file available yet.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            item {
                Text("Profile & settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Make your hydration app truly yours.")
            }

            item {
                CollapsibleSettingsSection(
                    title = "User profile",
                    expanded = state.settingsProfileExpanded,
                    onToggle = { onSetProfileSectionExpanded(!state.settingsProfileExpanded) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                val initial = state.profile?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(initial, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Hydration identity", fontWeight = FontWeight.SemiBold)
                                    Text(state.profile?.name ?: "Not configured yet", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            FilledTonalButton(onClick = {
                                if (state.profile == null) {
                                    Toast.makeText(context, "Set up your profile first from onboarding.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showEditProfile = true
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit")
                            }
                        }

                        if (state.profile == null) {
                            Text("No profile saved yet.")
                        } else {
                            val p = state.profile
                            ProfileDetailRow("Name", p.name)
                            ProfileDetailRow("Age", "${p.age} years")
                            ProfileDetailRow("Weight / Height", "${p.weightKg} kg • ${p.heightCm} cm")
                            ProfileDetailRow("Lifestyle", "${p.activityLevel.name.lowercase().replaceFirstChar { it.uppercase() }} • ${p.climateType.name.lowercase().replaceFirstChar { it.uppercase() }}")
                            ProfileDetailRow("Schedule", "Wake ${p.wakeHour}:00 • Sleep ${p.sleepHour}:00")
                            ProfileDetailRow("Reminder", p.reminderStyle.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            item {
                CollapsibleSettingsSection(
                    title = "App controls",
                    expanded = state.settingsControlsExpanded,
                    onToggle = { onSetControlsSectionExpanded(!state.settingsControlsExpanded) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unit: ${if (state.unitIsMl) "ml" else "fl oz"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onToggleUnit) { Text("Toggle unit") }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Notifications")
                                }
                            }
                        }
                        Text("Connected bottle: ${state.connectedDevice?.name ?: "None"}")
                        Text("Sensor mode: ${if (state.sensorMode == SensorMode.SIMULATION) "Simulation" else "Real bottle"}")
                        Text("Version 1.0")
                    }
                }
            }

            item {
                CollapsibleSettingsSection(
                    title = "Data export",
                    expanded = state.settingsExportExpanded,
                    onToggle = { onSetExportSectionExpanded(!state.settingsExportExpanded) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                exportResult = onExportCsv()
                                refreshExports()
                                showCsv = true
                            }) {
                                Text("Export CSV")
                            }
                            OutlinedButton(onClick = {
                                val latest = exportResult?.filePath ?: exportFiles.firstOrNull()?.path ?: onGetLatestExportPath()
                                if (latest == null) {
                                    Toast.makeText(context, "No export found. Export CSV first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareExport(latest)
                                }
                            }) {
                                Text("Share latest")
                            }
                        }
                        OutlinedButton(onClick = {
                            val directoryPath = onGetExportsDirectoryPath()
                            if (directoryPath == null) {
                                Toast.makeText(context, "Exports folder not available.", Toast.LENGTH_SHORT).show()
                            } else {
                                openExportsFolder(directoryPath)
                            }
                        }) {
                            Text("Open exports folder")
                        }
                    }
                }
            }

            item {
                CollapsibleSettingsSection(
                    title = "Recent exports",
                    expanded = state.settingsRecentExpanded,
                    onToggle = { onSetRecentSectionExpanded(!state.settingsRecentExpanded) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        if (exportFiles.isEmpty()) {
                            Text("No exports yet.")
                        } else {
                            exportFiles.take(4).forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.name, fontWeight = FontWeight.Medium)
                                    Text("${formatSize(item.sizeBytes)} • ${formatTime(item.lastModifiedEpochMs)}")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { shareExport(item.path) }) { Text("Share") }
                                        TextButton(onClick = {
                                            val deleted = onDeleteExportFile(item.path)
                                            if (deleted) {
                                                Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                                                refreshExports()
                                            } else {
                                                Toast.makeText(context, "Could not delete file.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }

                            if (exportFiles.size > 4) {
                                Text("+${exportFiles.size - 4} more files in exports folder")
                            }
                        }
                    }
                }
            }
        }

        if (showCsv) {
            AlertDialog(
                onDismissRequest = { showCsv = false },
                confirmButton = { TextButton(onClick = { showCsv = false }) { Text("Close") } },
                title = { Text("CSV export preview") },
                text = {
                    Text(
                        text = buildString {
                            append(exportResult?.message ?: "No export status")
                            append("\n\nPreview:\n")
                            append(exportResult?.preview?.take(700).orEmpty().ifBlank { "No data yet" })
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }

        if (showEditProfile && state.profile != null) {
            ProfileEditorDialog(
                initialProfile = state.profile,
                initialDraft = state.profileDraft,
                onDraftChange = onSaveProfileDraft,
                onDismiss = { showEditProfile = false },
                onSave = {
                    onSaveProfile(it)
                    onClearProfileDraft()
                    showEditProfile = false
                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val headerAlpha by animateFloatAsState(targetValue = if (expanded) 1f else 0.92f, label = "headerAlpha")

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = headerAlpha)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title"
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileEditorDialog(
    initialProfile: UserProfile,
    initialDraft: UserProfileDraft?,
    onDraftChange: (UserProfileDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.name ?: initialProfile.name) }
    var age by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.ageText ?: initialProfile.age.toString()) }
    var weight by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.weightText ?: initialProfile.weightKg.toString()) }
    var height by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.heightText ?: initialProfile.heightCm.toString()) }
    var wakeHour by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.wakeHourText ?: initialProfile.wakeHour.toString()) }
    var sleepHour by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.sleepHourText ?: initialProfile.sleepHour.toString()) }
    var sex by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.biologicalSex ?: initialProfile.biologicalSex) }
    var activity by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.activityLevel ?: initialProfile.activityLevel) }
    var climate by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.climateType ?: initialProfile.climateType) }
    var reminderStyle by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.reminderStyle ?: initialProfile.reminderStyle) }
    var hasCondition by remember(initialProfile, initialDraft) { mutableStateOf(initialDraft?.hasHealthCondition ?: initialProfile.hasHealthCondition) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var heightError by remember { mutableStateOf<String?>(null) }
    var wakeError by remember { mutableStateOf<String?>(null) }
    var sleepError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val parsedAge = age.toIntOrNull()
                val parsedWeight = weight.toIntOrNull()
                val parsedHeight = height.toIntOrNull()
                val parsedWake = wakeHour.toIntOrNull()
                val parsedSleep = sleepHour.toIntOrNull()

                nameError = if (name.isBlank()) "Name is required" else null
                ageError = when {
                    parsedAge == null -> "Enter a valid number"
                    parsedAge !in 8..100 -> "Range: 8 - 100"
                    else -> null
                }
                weightError = when {
                    parsedWeight == null -> "Enter a valid number"
                    parsedWeight !in 20..300 -> "Range: 20 - 300 kg"
                    else -> null
                }
                heightError = when {
                    parsedHeight == null -> "Enter a valid number"
                    parsedHeight !in 90..250 -> "Range: 90 - 250 cm"
                    else -> null
                }
                wakeError = when {
                    parsedWake == null -> "Enter a valid hour"
                    parsedWake !in 0..23 -> "Range: 0 - 23"
                    else -> null
                }
                sleepError = when {
                    parsedSleep == null -> "Enter a valid hour"
                    parsedSleep !in 0..23 -> "Range: 0 - 23"
                    else -> null
                }

                if (listOf(nameError, ageError, weightError, heightError, wakeError, sleepError).any { it != null }) {
                    return@Button
                }

                onSave(
                    UserProfile(
                        name = name.trim(),
                        age = parsedAge!!,
                        weightKg = parsedWeight!!,
                        heightCm = parsedHeight!!,
                        biologicalSex = sex,
                        activityLevel = activity,
                        climateType = climate,
                        hasHealthCondition = hasCondition,
                        wakeHour = parsedWake!!,
                        sleepHour = parsedSleep!!,
                        reminderStyle = reminderStyle
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit profile") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = nameError != null,
                        supportingText = { nameError?.let { Text(it) } }
                    )
                }
                item {
                    OutlinedTextField(
                        value = age,
                        onValueChange = {
                            age = it
                            ageError = null
                        },
                        label = { Text("Age") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ageError != null,
                        supportingText = { ageError?.let { Text(it) } }
                    )
                }
                item {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = {
                            weight = it
                            weightError = null
                        },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = weightError != null,
                        supportingText = { weightError?.let { Text(it) } }
                    )
                }
                item {
                    OutlinedTextField(
                        value = height,
                        onValueChange = {
                            height = it
                            heightError = null
                        },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = heightError != null,
                        supportingText = { heightError?.let { Text(it) } }
                    )
                }
                item {
                    OutlinedTextField(
                        value = wakeHour,
                        onValueChange = {
                            wakeHour = it
                            wakeError = null
                        },
                        label = { Text("Wake hour") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = wakeError != null,
                        supportingText = { wakeError?.let { Text(it) } }
                    )
                }
                item {
                    OutlinedTextField(
                        value = sleepHour,
                        onValueChange = {
                            sleepHour = it
                            sleepError = null
                        },
                        label = { Text("Sleep hour") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = sleepError != null,
                        supportingText = { sleepError?.let { Text(it) } }
                    )
                }

                item {
                    ChoiceChips(
                        label = "Biological sex",
                        options = BiologicalSex.entries,
                        selected = sex,
                        segmented = true,
                        onSelect = { sex = it }
                    )
                }
                item {
                    ChoiceChips(
                        label = "Activity",
                        options = ActivityLevel.entries,
                        selected = activity,
                        segmented = true,
                        onSelect = { activity = it }
                    )
                }
                item {
                    ChoiceChips(
                        label = "Climate",
                        options = ClimateType.entries,
                        selected = climate,
                        segmented = true,
                        onSelect = { climate = it }
                    )
                }
                item {
                    ChoiceChips(
                        label = "Reminder style",
                        options = ReminderStyle.entries,
                        selected = reminderStyle,
                        segmented = true,
                        onSelect = { reminderStyle = it }
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Health condition")
                        Switch(checked = hasCondition, onCheckedChange = { hasCondition = it })
                    }
                }
            }
        }
    )

    LaunchedEffect(name, age, weight, height, wakeHour, sleepHour, sex, activity, climate, reminderStyle, hasCondition) {
        onDraftChange(
            UserProfileDraft(
                name = name,
                ageText = age,
                weightText = weight,
                heightText = height,
                wakeHourText = wakeHour,
                sleepHourText = sleepHour,
                biologicalSex = sex,
                activityLevel = activity,
                climateType = climate,
                reminderStyle = reminderStyle,
                hasHealthCondition = hasCondition
            )
        )
    }
}

@Composable
private fun InsightCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body)
        }
    }
}

@Composable
private fun <T : Enum<T>> ChoiceChips(
    label: String,
    options: List<T>,
    selected: T,
    segmented: Boolean = false,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        if (segmented) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option == selected
                    val shape = when (index) {
                        0 -> RoundedCornerShape(14.dp)
                        options.lastIndex -> RoundedCornerShape(14.dp)
                        else -> RoundedCornerShape(14.dp)
                    }
                    val chipScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.02f else 1f,
                        label = "chipScale"
                    )
                    val borderWidth by animateDpAsState(
                        targetValue = if (isSelected) 1.5.dp else 1.dp,
                        label = "chipBorderWidth"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        label = "chipBorderColor"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        label = "chipContainerColor"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "chipContentColor"
                    )
                    val optionLabel = when (option) {
                        is BiologicalSex -> when (option) {
                            BiologicalSex.MALE -> "Male"
                            BiologicalSex.FEMALE -> "Female"
                            BiologicalSex.OTHER -> "Other"
                        }
                        is ActivityLevel -> when (option) {
                            ActivityLevel.SEDENTARY -> "Sedentary"
                            ActivityLevel.LIGHT -> "Light"
                            ActivityLevel.ACTIVE -> "Active"
                            ActivityLevel.ATHLETE -> "Athlete"
                        }
                        is ClimateType -> when (option) {
                            ClimateType.TEMPERATE -> "Temperate"
                            ClimateType.HUMID -> "Humid"
                            ClimateType.HOT -> "Hot"
                            ClimateType.COLD -> "Cold"
                        }
                        is ReminderStyle -> when (option) {
                            ReminderStyle.GENTLE -> "Gentle"
                            ReminderStyle.AGGRESSIVE -> "Aggressive"
                            ReminderStyle.GAMIFIED -> "Gamified"
                        }
                        else -> option.name
                            .replace('_', ' ')
                            .lowercase()
                            .replaceFirstChar { it.uppercase() }
                    }

                    FilledTonalButton(
                        onClick = { onSelect(option) },
                        shape = shape,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = chipScale
                                scaleY = chipScale
                            }
                            .border(
                                width = borderWidth,
                                color = borderColor,
                                shape = shape
                            )
                            .defaultMinSize(minHeight = 40.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        )
                    ) {
                        Text(
                            text = optionLabel,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    AssistChip(
                        onClick = { onSelect(option) },
                        label = { Text(option.name.replace('_', ' ')) },
                        leadingIcon = if (option == selected) {
                            { Text("✓") }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}
