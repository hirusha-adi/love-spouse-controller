package dev.hirusha.lscontroller

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.*
import java.util.UUID

data class DevicePrefix(
    val name: String,
    val bytes: ByteArray,
    val deviceCount: Int
) {
    override fun equals(other: Any?) = other is DevicePrefix && name == other.name
    override fun hashCode() = name.hashCode()
}

object Protocol {
    const val COMPANY_ID = 0x00FF
    val SERVICE_UUID: ParcelUuid = ParcelUuid(
        UUID.fromString("0000ae8f-0000-1000-8000-00805f9b34fb")
    )

    val PREFIXES = listOf(
        DevicePrefix("wbMSE", byteArrayOf(0x77, 0x62, 0x4D, 0x53, 0x45), 3140),
        DevicePrefix("wb#C", byteArrayOf(0x77, 0x62, 0x16, 0x23, 0x43), 7),
        DevicePrefix("wb8a5E", byteArrayOf(0x77, 0x62, 0x38, 0xA5.toByte(), 0x45), 4),
        DevicePrefix("wb96e5", byteArrayOf(0x77, 0x62, 0x96.toByte(), 0xE5.toByte(), 0x33), 3),
        DevicePrefix("wbeb67", byteArrayOf(0x77, 0x62, 0xEB.toByte(), 0x67, 0xC5.toByte()), 1),
    )

    enum class Command(val label: String, val byte: Int) {
        STOP("Stop", 0x00),
        MODE1("Mode 1", 0x01),
        MODE2("Mode 2", 0x02),
        MODE3("Mode 3", 0x03),
        MODE4("Mode 4", 0x04),
        MODE5("Mode 5", 0x05),
        MODE6("Mode 6", 0x06),
        MODE7("Mode 7", 0x07),
        MODE8("Mode 8", 0x08),
        MODE9("Mode 9", 0x09),
        HEAT_ON("Heat On", 0x25),
        HEAT_OFF("Heat Off", 0x24),
        SUCK1("Suck 1", 0x51),
        SUCK2("Suck 2", 0x52),
        SUCK3("Suck 3", 0x53),
        SUCK4("Suck 4", 0x54),
        SUCK5("Suck 5", 0x55),
    }
}

class BleController(context: Context) {

    private val advertiser: BluetoothLeAdvertiser? = run {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.bluetoothLeAdvertiser
    }

    private var cycleJob: Job? = null
    private var currentCallback: AdvertiseCallback? = null

    val isSupported: Boolean get() = advertiser != null

    // Original app uses setConnectable(true) — this changes the PDU type
    // from ADV_NONCONN_IND to ADV_IND. Device firmware checks this.
    private val settingsBalanced = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setConnectable(true)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setTimeout(0)
        .build()

    private val settingsLowLatency = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setConnectable(true)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setTimeout(0)
        .build()

    private fun buildAdvData(prefix: ByteArray, commandByte: Int): AdvertiseData {
        val encoded = RfPayloadEncoder.encode(prefix, commandByte)
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(Protocol.COMPANY_ID, encoded)
            .addServiceUuid(Protocol.SERVICE_UUID)
            .build()
    }

    fun startSingle(
        prefix: ByteArray,
        commandByte: Int,
        onStatus: (String) -> Unit
    ) {
        stopAll()
        val data = buildAdvData(prefix, commandByte)
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings) {
                onStatus("Broadcasting")
            }
            override fun onStartFailure(error: Int) {
                onStatus("Failed (error $error)")
            }
        }
        currentCallback = cb
        advertiser?.startAdvertising(settingsBalanced, data, cb)
    }

    fun startCycle(
        commandByte: Int,
        dwellMs: Long = 300,
        onStatus: (String) -> Unit
    ) {
        stopAll()
        cycleJob = CoroutineScope(Dispatchers.Default).launch {
            var round = 1
            while (isActive) {
                for ((i, dp) in Protocol.PREFIXES.withIndex()) {
                    if (!isActive) break
                    withContext(Dispatchers.Main) {
                        onStatus("Round $round [${i + 1}/${Protocol.PREFIXES.size}] ${dp.name}")
                    }
                    val data = buildAdvData(dp.bytes, commandByte)
                    val latch = CompletableDeferred<Unit>()
                    val cb = object : AdvertiseCallback() {
                        override fun onStartSuccess(s: AdvertiseSettings) { latch.complete(Unit) }
                        override fun onStartFailure(e: Int) { latch.complete(Unit) }
                    }
                    withContext(Dispatchers.Main) {
                        currentCallback = cb
                        advertiser?.startAdvertising(settingsBalanced, data, cb)
                    }
                    latch.await()
                    delay(dwellMs)
                    withContext(Dispatchers.Main) {
                        advertiser?.stopAdvertising(cb)
                    }
                    currentCallback = null
                }
                round++
            }
        }
    }

    fun stopAll() {
        cycleJob?.cancel()
        cycleJob = null
        currentCallback?.let { advertiser?.stopAdvertising(it) }
        currentCallback = null
    }
}
