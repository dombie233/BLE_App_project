package com.dombien.bleappscantest

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class MainActivity : AppCompatActivity() {
    val TARGET_MAC_ADDRESS: String = "D5:09:31:54:37:C7"

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var isConnecting = false

    private lateinit var btnScan: Button
    private lateinit var tvHeartRate: TextView
    private lateinit var adapter: DevicesAdapter
    private val devicesMap: MutableMap<String, BleDevice> = LinkedHashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScan = findViewById(R.id.btnScan)
        tvHeartRate = findViewById(R.id.tvHeartRate)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = DevicesAdapter(ArrayList())
        recycler.adapter = adapter

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnScan.setOnClickListener {
            if (bluetoothGatt != null) {
                disconnect()
            } else if (scanning) {
                stopBleScan()
            } else {
                startBleScan()
            }
        }

        buildScanCallback()
    }

    private fun buildScanCallback() {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Scan failed with error code: $errorCode")
                scanning = false
                runOnUiThread { btnScan.text = "Start scan" }
            }
        }
    }

    private fun startBleScan() {
        if (!hasPermissions() || !isBluetoothEnabled) return

        devicesMap.clear()
        adapter.updateList(ArrayList())
        tvHeartRate.text = "Scanning...."

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) return

        val scanFilter = ScanFilter.Builder().setDeviceAddress(TARGET_MAC_ADDRESS).build()
        val filters = mutableListOf(scanFilter)
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return

        bleScanner?.startScan(filters, settings, scanCallback)
        scanning = true
        btnScan.text = "Stop scan"

        handler.postDelayed({
            if (scanning) {
                stopBleScan()
                runOnUiThread { tvHeartRate.text = "No device found." }
            }
        }, SCAN_PERIOD.toLong())
    }

    private fun stopBleScan() {
        handler.removeCallbacksAndMessages(null)
        if (bleScanner != null && scanning) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
            bleScanner?.stopScan(scanCallback)
        }
        scanning = false
        runOnUiThread { btnScan.text = "Start scan" }
    }

    private fun handleScanResult(result: ScanResult) {
        if (isConnecting || bluetoothGatt != null) return
        stopBleScan()
        connectToDevice(result.device)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        isConnecting = true
        tvHeartRate.text = "Connect...."

        val d = BleDevice(device.name, device.address, -1, -1, null, null)
        devicesMap[device.address] = d
        runOnUiThread { adapter.updateList(ArrayList(devicesMap.values)) }

        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT.toLong())
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            Log.w(TAG, "Connection attempt timed out.")
            disconnect()
        }
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.removeCallbacks(connectionTimeoutRunnable)
            isConnecting = false

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.")
                    runOnUiThread {
                        btnScan.text = "Disconnect"
                        tvHeartRate.text = "Connected!"
                    }
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.")
                    refreshDeviceCache(gatt)
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                    gatt.close()
                    bluetoothGatt = null
                    runOnUiThread {
                        tvHeartRate.text = "Disconnected"
                        btnScan.text = "Start scan"
                        adapter.updateList(ArrayList())
                    }
                }
            } else {
                Log.w(TAG, "GATT Error onConnectionStateChange: $status")
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableHeartRateNotifications(gatt)
            } else {
                Log.w(TAG, "Service discovery failed with status: $status")
                disconnect()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (HEART_RATE_MEASUREMENT_CHAR_UUID == characteristic.uuid) {
                val data = characteristic.value ?: return
                val flag = data[0].toInt()
                val format = if ((flag and 0x01) != 0) BluetoothGattCharacteristic.FORMAT_UINT16 else BluetoothGattCharacteristic.FORMAT_UINT8
                val heartRate = characteristic.getIntValue(format, 1)
                runOnUiThread { tvHeartRate.text = "Hearth Rate: $heartRate BPM" }
            }
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            val refresh = gatt.javaClass.getMethod("refresh")
            val success = refresh.invoke(gatt) as Boolean
            Log.i(TAG, "GATT Cache refreshed: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "An error occurred while refreshing GATT cache", e)
        }
        return false
    }

    private fun enableHeartRateNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(HEART_RATE_SERVICE_UUID)
        if (service == null) {
            disconnect()
            return
        }
        val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
        if (characteristic == null) {
            disconnect()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor?.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        descriptor?.let { gatt.writeDescriptor(it) }
        runOnUiThread { tvHeartRate.text = "Waiting....." }
    }

    private fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        isConnecting = false
        scanning = false
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        bluetoothGatt?.disconnect()
    }

    private val isBluetoothEnabled: Boolean
        get() {
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestAppropriatePermissions()
                    return false
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
                return false
            }
            return true
        }

    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PERMISSIONS_ANDROID12 else PERMISSIONS_BEFORE_S
        for (p in permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                requestAppropriatePermissions()
                return false
            }
        }
        return true
    }

    private fun requestAppropriatePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PERMISSIONS_ANDROID12 else PERMISSIONS_BEFORE_S
        ActivityCompat.requestPermissions(this, permissions, 1234)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1234) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    companion object {
        private const val TAG = "BleFinder"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val SCAN_PERIOD = 10000
        private const val CONNECTION_TIMEOUT = 15000

        private val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val PERMISSIONS_BEFORE_S: Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        private val PERMISSIONS_ANDROID12: Array<String> = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}