package com.tamim.hydrationtracker.ble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware integration placeholder:
 * Keep this contract stable while bottle firmware and GATT characteristics are finalized.
 */
class RealBottleSensorGateway : BottleSensorGateway {

    override val mode: SensorMode = SensorMode.REAL_BOTTLE

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    override val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _sipEventsMl = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    override val sipEventsMl: SharedFlow<Int> = _sipEventsMl

    override suspend fun startScan() {
        // TODO: Implement BluetoothLeScanner + permission checks + filtering by bottle service UUID.
        _devices.value = emptyList()
    }

    override fun connect(device: BleDevice) {
        // TODO: Implement GATT connect and subscribe to sip measurement characteristic.
        _connectedDevice.value = device
    }

    override fun disconnect() {
        _connectedDevice.value = null
    }

    override fun triggerSip(amountMl: Int) {
        // Not used by real mode; kept for interface parity/testing hooks.
    }
}
