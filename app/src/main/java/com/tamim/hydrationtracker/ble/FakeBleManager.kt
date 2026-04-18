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
    }

    override fun disconnect() {
        _connectedDevice.value = null
    }

    override fun triggerSip(amountMl: Int) {
        _sipEventsMl.tryEmit(amountMl.coerceAtLeast(1))
    }
}
