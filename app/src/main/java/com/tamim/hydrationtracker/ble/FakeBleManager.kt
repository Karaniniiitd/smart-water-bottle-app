package com.tamim.hydrationtracker.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBleManager : BottleSensorGateway {

    override val mode: SensorMode = SensorMode.SIMULATION

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    override val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _sipEventsMl = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    override val sipEventsMl: SharedFlow<Int> = _sipEventsMl

    private val _debugSnapshot = MutableStateFlow(BleDebugSnapshot())
    override val debugSnapshot: StateFlow<BleDebugSnapshot> = _debugSnapshot.asStateFlow()

    override suspend fun startScan() {
        delay(700)
        _devices.value = listOf(
            BleDevice("AquaSense Bottle", "00:11:22:AA:BC:01", -48, 82),
            BleDevice("HydroMate", "00:11:22:AA:BC:02", -60, 67),
            BleDevice("SipSync Lite", "00:11:22:AA:BC:03", -72, 54)
        )
    }

    override fun connect(device: BleDevice) {
        _connectedDevice.value = device
        _debugSnapshot.value = _debugSnapshot.value.copy(
            connectionEvents = (_debugSnapshot.value.connectionEvents + "Connected (sim): ${device.name}").takeLast(20),
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    override fun disconnect() {
        val deviceName = _connectedDevice.value?.name ?: "Unknown"
        _connectedDevice.value = null
        _debugSnapshot.value = _debugSnapshot.value.copy(
            connectionEvents = (_debugSnapshot.value.connectionEvents + "Disconnected (sim): $deviceName").takeLast(20),
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    override fun triggerSip(amountMl: Int) {
        val sip = amountMl.coerceAtLeast(1)
        _sipEventsMl.tryEmit(sip)
        _debugSnapshot.value = _debugSnapshot.value.copy(
            lastRawPacketText = "simulated:$sip",
            lastRawPacketHex = null,
            lastParsedSipMl = sip,
            connectionEvents = (_debugSnapshot.value.connectionEvents + "Sip event (sim): ${sip}ml").takeLast(20),
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }
}
