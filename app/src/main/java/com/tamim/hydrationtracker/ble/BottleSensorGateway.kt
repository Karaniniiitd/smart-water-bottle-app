package com.tamim.hydrationtracker.ble

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

enum class SensorMode {
    SIMULATION,
    REAL_BOTTLE
}

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val batteryPercent: Int
)

data class BleDebugSnapshot(
    val lastRawPacketHex: String? = null,
    val lastRawPacketText: String? = null,
    val lastParsedSipMl: Int? = null,
    val connectionEvents: List<String> = emptyList(),
    val lastUpdatedEpochMs: Long? = null
)

interface BottleSensorGateway {
    val mode: SensorMode
    val devices: StateFlow<List<BleDevice>>
    val connectedDevice: StateFlow<BleDevice?>
    val sipEventsMl: SharedFlow<Int>
    val debugSnapshot: StateFlow<BleDebugSnapshot>

    suspend fun startScan()
    fun connect(device: BleDevice)
    fun disconnect()
    fun triggerSip(amountMl: Int = 120)
}
