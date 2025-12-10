package com.dombien.bleappscantest.BLE

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log

class BleScanManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val callback: BleScanCallback
) {

    interface BleScanCallback {
        fun onDeviceFound(device: BleDevice)
        fun onScanStatusChanged(isScanning: Boolean)
        fun onScanFailed(errorCode: Int)
        fun onScanFinishedAutomatic()
    }

    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    var isScanning = false
        private set

    private val FILTER_ENABLED = true
    private val TARGET_DEVICES = listOf(
        "C7:95:DA:5F:44:8A" // Valkyrie
    )


    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || isScanning) return

        bleScanner = bluetoothAdapter.bluetoothLeScanner


        scanCallback = buildScanCallback()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(ArrayList(), settings, scanCallback)
            isScanning = true
            callback.onScanStatusChanged(true)

            // Timer 10 seconds
            handler.postDelayed({
                if (isScanning) {
                    stopScan()
                    callback.onScanFinishedAutomatic()
                }
            }, 10000)
        } catch (e: SecurityException) {
            Log.e("BleScanManager", "No permissions to scan :( !", e)
            callback.onScanFailed(-1)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning && bleScanner != null && scanCallback != null) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e("BleScanManager", "No permissions :( ", e)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isScanning = false
        handler.removeCallbacksAndMessages(null)
        callback.onScanStatusChanged(false)
    }


    @SuppressLint("MissingPermission")
    private fun buildScanCallback() = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device

            val address = device.address
            val name = device.name ?: "Unknown"

            if (FILTER_ENABLED) {
                if (!TARGET_DEVICES.contains(address)) {
                    return
                }
            }

            callback.onDeviceFound(BleDevice(name, address))
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w("BLE", "Scan failed: $errorCode")
            isScanning = false
            callback.onScanStatusChanged(false)
            if (errorCode == 2)
                Log.e("BLE", "The system has blocked scanning (too frequent). Please wait a moment.")


            callback.onScanFailed(errorCode)
        }
    }
}