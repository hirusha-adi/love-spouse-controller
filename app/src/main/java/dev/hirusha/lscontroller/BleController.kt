// SPDX-FileCopyrightText: 2026 Hirusha Adikari
// SPDX-License-Identifier: MIT

package dev.hirusha.lscontroller

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
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

    enum class Command(val byte: Int) {
        STOP(0x00),
        MODE1(0x01), MODE2(0x02), MODE3(0x03),
        MODE4(0x04), MODE5(0x05), MODE6(0x06),
        MODE7(0x07), MODE8(0x08), MODE9(0x09),
        HEAT_ON(0x25), HEAT_OFF(0x24),
        SUCK1(0x51), SUCK2(0x52), SUCK3(0x53), SUCK4(0x54), SUCK5(0x55);

        fun label(context: Context): String = when (this) {
            STOP -> context.getString(R.string.command_stop)
            HEAT_ON -> context.getString(R.string.command_heat_on)
            HEAT_OFF -> context.getString(R.string.command_heat_off)
            MODE1, MODE2, MODE3, MODE4, MODE5, MODE6, MODE7, MODE8, MODE9 ->
                context.getString(R.string.command_mode, byte)
            SUCK1, SUCK2, SUCK3, SUCK4, SUCK5 ->
                context.getString(R.string.command_suction, byte - 0x50)
        }
    }
}

data class BroadcastStatus(val text: String, val failed: Boolean = false)

class BleController(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(BluetoothManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cycleJob: Job? = null
    private var currentAdvertiser: BluetoothLeAdvertiser? = null
    private var currentCallback: AdvertiseCallback? = null

    val hasPermission: Boolean
        get() = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED

    fun unavailableReason(): String? {
        if (!hasPermission) return context.getString(R.string.permission_required)
        return try {
            val adapter = manager?.adapter
            when {
                adapter == null || !adapter.isMultipleAdvertisementSupported ->
                    context.getString(R.string.ble_unsupported)
                adapter.bluetoothLeAdvertiser == null ->
                    context.getString(R.string.bluetooth_unavailable)
                else -> null
            }
        } catch (_: SecurityException) {
            context.getString(R.string.permission_required)
        }
    }

    // Device firmware expects ADV_IND rather than ADV_NONCONN_IND.
    private val settings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setConnectable(true)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setTimeout(0)
        .build()

    private fun buildAdvData(prefix: ByteArray, commandByte: Int): AdvertiseData =
        AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(Protocol.COMPANY_ID, RfPayloadEncoder.encode(prefix, commandByte))
            .addServiceUuid(Protocol.SERVICE_UUID)
            .build()

    private fun advertiser(onStatus: (BroadcastStatus) -> Unit): BluetoothLeAdvertiser? {
        val reason = unavailableReason()
        if (reason != null) {
            onStatus(BroadcastStatus(reason, failed = true))
            return null
        }
        return try {
            manager?.adapter?.bluetoothLeAdvertiser.also {
                if (it == null) onStatus(BroadcastStatus(
                    context.getString(R.string.bluetooth_unavailable), failed = true
                ))
            }
        } catch (_: SecurityException) {
            onStatus(BroadcastStatus(context.getString(R.string.permission_required), failed = true))
            null
        }
    }

    private fun startAdvertising(
        advertiser: BluetoothLeAdvertiser,
        data: AdvertiseData,
        callback: AdvertiseCallback,
        onStatus: (BroadcastStatus) -> Unit
    ): Boolean {
        currentAdvertiser = advertiser
        currentCallback = callback
        return try {
            advertiser.startAdvertising(settings, data, callback)
            true
        } catch (_: SecurityException) {
            stopAll()
            onStatus(BroadcastStatus(context.getString(R.string.permission_required), failed = true))
            false
        } catch (_: IllegalStateException) {
            stopAll()
            onStatus(BroadcastStatus(context.getString(R.string.bluetooth_unavailable), failed = true))
            false
        }
    }

    fun startSingle(
        prefix: ByteArray,
        commandByte: Int,
        onStatus: (BroadcastStatus) -> Unit
    ) {
        stopAll()
        val advertiser = advertiser(onStatus) ?: return
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (currentCallback === this) {
                    onStatus(BroadcastStatus(context.getString(R.string.broadcasting)))
                }
            }

            override fun onStartFailure(errorCode: Int) {
                if (currentCallback === this) {
                    stopAll()
                    onStatus(BroadcastStatus(
                        context.getString(R.string.broadcast_failed, errorCode), failed = true
                    ))
                }
            }
        }
        startAdvertising(advertiser, buildAdvData(prefix, commandByte), callback, onStatus)
    }

    fun startCycle(
        commandByte: Int,
        dwellMs: Long = 300,
        onStatus: (BroadcastStatus) -> Unit
    ) {
        stopAll()
        val advertiser = advertiser(onStatus) ?: return
        cycleJob = scope.launch {
            var round = 1
            while (isActive) {
                for ((index, prefix) in Protocol.PREFIXES.withIndex()) {
                    ensureActive()
                    val result = CompletableDeferred<Int>()
                    val callback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                            result.complete(0)
                        }
                        override fun onStartFailure(errorCode: Int) {
                            result.complete(errorCode)
                        }
                    }
                    if (!startAdvertising(
                        advertiser, buildAdvData(prefix.bytes, commandByte), callback, onStatus
                    )) return@launch
                    val errorCode = withTimeoutOrNull(5_000) { result.await() }
                    if (errorCode != 0) {
                        stopAll()
                        val message = if (errorCode == null) {
                            context.getString(R.string.broadcast_timeout)
                        } else {
                            context.getString(R.string.broadcast_failed, errorCode)
                        }
                        onStatus(BroadcastStatus(message, failed = true))
                        return@launch
                    }
                    onStatus(BroadcastStatus(context.getString(
                        R.string.broadcast_round, round, index + 1, Protocol.PREFIXES.size, prefix.name
                    )))
                    delay(dwellMs)
                    stopCurrentAdvertisement()
                }
                round++
            }
        }
    }

    private fun stopCurrentAdvertisement() {
        val callback = currentCallback
        val advertiser = currentAdvertiser
        currentCallback = null
        currentAdvertiser = null
        if (callback != null && advertiser != null) {
            try {
                advertiser.stopAdvertising(callback)
            } catch (_: SecurityException) {
                // Permission can be revoked while an advertisement is active.
            } catch (_: IllegalStateException) {
                // Bluetooth can be disabled while an advertisement is active.
            }
        }
    }

    fun stopAll() {
        cycleJob?.cancel()
        cycleJob = null
        stopCurrentAdvertisement()
    }

    fun close() {
        stopAll()
        scope.cancel()
    }
}
