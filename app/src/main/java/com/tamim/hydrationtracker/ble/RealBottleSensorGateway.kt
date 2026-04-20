package com.tamim.hydrationtracker.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.text.Charsets.UTF_8

/**
 * Real BLE integration for ESP32-based bottle firmware.
 *
 * Expected firmware shape (can be changed by editing companion constants):
 * - Service UUID: 0000FFB0-0000-1000-8000-00805F9B34FB
 * - Notify characteristic UUID: 0000FFB1-0000-1000-8000-00805F9B34FB
 *
 * Payload formats accepted from ESP notifications:
 * - Binary u16 little-endian (e.g. 0x78 0x00 => 120ml)
 * - Plain text integer ("120")
 * - Key-value text ("sip_ml:120", "amount=120")
 * - JSON-like text containing a positive integer (e.g. {"sip_ml":120})
 */
class RealBottleSensorGateway(
    private val context: Context
) : BottleSensorGateway {

    override val mode: SensorMode = SensorMode.REAL_BOTTLE

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    override val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _sipEventsMl = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    override val sipEventsMl: SharedFlow<Int> = _sipEventsMl

    private val _debugSnapshot = MutableStateFlow(BleDebugSnapshot())
    override val debugSnapshot: StateFlow<BleDebugSnapshot> = _debugSnapshot.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val knownScanResults = linkedMapOf<String, BleDevice>()
    private var activeGatt: BluetoothGatt? = null

    /**
     * When true, upsertDevice uses relaxed filtering (accepts any device with
     * a non-null name) because the strict UUID-filtered scan found nothing.
     * This handles the very common case where the ESP32 BLE stack does not
     * include the service UUID in its advertisement payload AND Android cannot
     * resolve the device name through scanRecord.deviceName (returns null on
     * many Android 12+ devices without BLUETOOTH_CONNECT granted at scan time).
     */
    private var fallbackScanActive = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            upsertDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::upsertDevice)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                addDebugEvent("Connected: ${gatt.device.address}")
                safeRun { gatt.discoverServices() }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                addDebugEvent("Disconnected: ${gatt.device.address}")
                _connectedDevice.value = null
                safeRun { gatt.close() }
                if (activeGatt == gatt) {
                    activeGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            addDebugEvent("Services discovered (${gatt.services.size})")

            subscribeToSipCharacteristic(gatt)
            readBatteryLevel(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != SIP_MEASUREMENT_CHARACTERISTIC_UUID) return
            val value = characteristic.value ?: return
            val parsed = parseSipMlFromPayload(value)
            updateRawPacketDebug(value, parsed)
            parsed
                ?.takeIf { it > 0 }
                ?.let {
                    _sipEventsMl.tryEmit(it)
                    addDebugEvent("Sip parsed: ${it}ml")
                }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                val battery = value.firstOrNull()?.toInt()?.and(0xFF) ?: return
                _connectedDevice.value = _connectedDevice.value?.copy(batteryPercent = battery)
                addDebugEvent("Battery update: ${battery}%")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        if (!hasBlePermissions()) {
            addDebugEvent("Scan blocked: missing permissions")
            _devices.value = emptyList()
            return
        }

        val bleScanner = scanner ?: run {
            addDebugEvent("Scan failed: BLE scanner unavailable")
            _devices.value = emptyList()
            return
        }

        addDebugEvent("Scan started")
        knownScanResults.clear()
        _devices.value = emptyList()
        fallbackScanActive = false

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val strictFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BOTTLE_SERVICE_UUID))
                .build()
        )

        // Pass 1: strict UUID scan (fast when advertisement includes service UUID)
        safeRun { bleScanner.startScan(strictFilters, settings, scanCallback) }
        delay(SCAN_WINDOW_MS)
        safeRun { bleScanner.stopScan(scanCallback) }

        // Pass 2: name-based filter scan (catches ESP32 devices that don't expose
        // the service UUID in advertisement packets but do broadcast their name)
        if (knownScanResults.isEmpty()) {
            addDebugEvent("UUID-filter scan empty, trying name-filter scan")
            val nameFilters = KNOWN_DEVICE_NAMES.map { name ->
                ScanFilter.Builder()
                    .setDeviceName(name)
                    .build()
            }
            safeRun { bleScanner.startScan(nameFilters, settings, scanCallback) }
            delay(NAME_SCAN_WINDOW_MS)
            safeRun { bleScanner.stopScan(scanCallback) }
        }

        // Pass 3 fallback: fully unfiltered scan — accept any named BLE device and
        // let the user pick.  This handles the case where Android can't read the
        // device name in the scan record (common on Android 12+).
        if (knownScanResults.isEmpty()) {
            addDebugEvent("Name-filter scan empty, retrying unfiltered scan")
            fallbackScanActive = true
            safeRun { bleScanner.startScan(null, settings, scanCallback) }
            delay(FALLBACK_SCAN_WINDOW_MS)
            safeRun { bleScanner.stopScan(scanCallback) }
            fallbackScanActive = false
        }

        addDebugEvent("Scan finished: ${knownScanResults.size} device(s)")

        if (_devices.value.isEmpty() && knownScanResults.isNotEmpty()) {
            _devices.value = knownScanResults.values.toList()
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BleDevice) {
        if (!hasBlePermissions()) return
        val adapter = bluetoothAdapter ?: return

        safeRun { scanner?.stopScan(scanCallback) }
        safeRun {
            activeGatt?.disconnect()
            activeGatt?.close()
        }

        val remote = runCatching { adapter.getRemoteDevice(device.address) }.getOrNull() ?: return
        _connectedDevice.value = device
        addDebugEvent("Connecting: ${device.name} (${device.address})")
        activeGatt = safeRun {
            remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        addDebugEvent("Manual disconnect requested")
        safeRun { scanner?.stopScan(scanCallback) }
        safeRun { activeGatt?.disconnect() }
        safeRun { activeGatt?.close() }
        activeGatt = null
        _connectedDevice.value = null
    }

    override fun triggerSip(amountMl: Int) {
        // Not used by real mode; kept for interface parity/testing hooks.
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToSipCharacteristic(gatt: BluetoothGatt) {
        val service = gatt.getService(BOTTLE_SERVICE_UUID)
            ?: gatt.services.firstOrNull { svc ->
                svc.characteristics.any { ch ->
                    ch.uuid == SIP_MEASUREMENT_CHARACTERISTIC_UUID || ch.canNotify()
                }
            }
            ?: return

        val characteristic = service.getCharacteristic(SIP_MEASUREMENT_CHARACTERISTIC_UUID)
            ?: service.characteristics.firstOrNull { it.canNotify() }
            ?: return

        safeRun { gatt.setCharacteristicNotification(characteristic, true) }
        val cccd = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        safeRun { gatt.writeDescriptor(cccd) }
    }

    @SuppressLint("MissingPermission")
    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val batteryService = gatt.getService(BATTERY_SERVICE_UUID) ?: return
        val batteryChar = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID) ?: return
        safeRun { gatt.readCharacteristic(batteryChar) }
    }

    private fun upsertDevice(result: ScanResult) {
        val address = result.device.address ?: return
        val scanRecord = result.scanRecord
        val advertisedServiceMatch = scanRecord
            ?.serviceUuids
            ?.any { it.uuid == BOTTLE_SERVICE_UUID }
            ?: false

        val resolvedName = scanRecord?.deviceName ?: safeDeviceName(result.device)
        val nameMatch = resolvedName?.let(::isEspBottleName) ?: false

        // During normal (filtered) scanning: require UUID or name match.
        // During fallback (unfiltered) scanning: accept ALL devices.
        if (!fallbackScanActive) {
            if (!advertisedServiceMatch && !nameMatch) return
        }

        val displayName = resolvedName ?: if (fallbackScanActive) "Unknown-${address.takeLast(5).replace(":", "")}" else "SmartBottle-${address.takeLast(5).replace(":", "")}" 

        knownScanResults[address] = BleDevice(
            name = displayName,
            address = address,
            rssi = result.rssi,
            batteryPercent = _connectedDevice.value?.takeIf { it.address == address }?.batteryPercent ?: -1
        )
        _devices.value = knownScanResults.values.toList()
        addDebugEvent("Scan hit: $displayName (${result.rssi} dBm)")
    }

    private fun addDebugEvent(event: String) {
        val current = _debugSnapshot.value
        _debugSnapshot.value = current.copy(
            connectionEvents = (current.connectionEvents + event).takeLast(20),
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    private fun updateRawPacketDebug(payload: ByteArray, parsedSipMl: Int?) {
        val hex = payload.joinToString(" ") { "%02X".format(it) }
        val printable = payload.toString(UTF_8).trim().ifBlank { null }
        val current = _debugSnapshot.value
        _debugSnapshot.value = current.copy(
            lastRawPacketHex = hex,
            lastRawPacketText = printable,
            lastParsedSipMl = parsedSipMl,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return null
        }
        return runCatching { device.name }.getOrNull()
    }

    private fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun BluetoothGattCharacteristic.canNotify(): Boolean {
        return properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
            properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
    }

    private fun isEspBottleName(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("esp") ||
            normalized.contains("smartbottle") ||
            normalized.contains("smart bottle") ||
            normalized.contains("bottle") ||
            normalized.contains("hydr") ||
            normalized.contains("aqua") ||
            normalized.contains("smart")
    }

    private fun <T> safeRun(block: () -> T): T? = runCatching(block).getOrNull()

    companion object {
        private const val SCAN_WINDOW_MS = 6_000L
        private const val NAME_SCAN_WINDOW_MS = 5_000L
        private const val FALLBACK_SCAN_WINDOW_MS = 8_000L

        /** Device names the ESP32 firmware might advertise as. */
        private val KNOWN_DEVICE_NAMES = listOf("SmartBottle", "ESP32", "HydrationTracker")

        // Default custom service/characteristic expected from ESP firmware.
        private val BOTTLE_SERVICE_UUID: UUID = UUID.fromString("0000FFB0-0000-1000-8000-00805F9B34FB")
        private val SIP_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFB1-0000-1000-8000-00805F9B34FB")

        // Standard Battery Service.
        private val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        private val BATTERY_LEVEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

        // Client Characteristic Configuration Descriptor.
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        internal fun parseSipMlFromPayload(payload: ByteArray): Int? {
            if (payload.isEmpty()) return null

            // 16-bit little-endian unsigned integer format.
            if (payload.size == 2) {
                val binaryMl = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                if (binaryMl in 1..5_000) return binaryMl
            }

            val text = payload.toString(UTF_8).trim()
            if (text.isEmpty()) return null

            text.toIntOrNull()?.let { if (it > 0) return it }

            val keyValueRegex = Regex("""(?i)(sip(_ml)?|amount|ml)\s*[:=]\s*(\d{1,5})""")
            keyValueRegex.find(text)?.groupValues?.getOrNull(3)?.toIntOrNull()?.let {
                if (it > 0) return it
            }

            Regex("""\d{1,5}""").find(text)?.value?.toIntOrNull()?.let {
                if (it > 0) return it
            }

            return null
        }
    }
}
