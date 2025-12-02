package com.dombien.bleappscantest

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.content.Context
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {
    val TARGET_MAC_ADDRESS: String = "C7:95:DA:5F:44:8A"

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothManager: BluetoothManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var isConnecting = false

    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var adapter: DevicesAdapter
    private val devicesMap: MutableMap<String, BleDevice> = LinkedHashMap()

    private lateinit var notificationManager: NotificationManagerCompat
    private val CHANNEL_ID = "ble_temp_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        btnScan = findViewById(R.id.btnScan)
        tvStatus = findViewById(R.id.tvStatus)
        tvTemperature = findViewById(R.id.tvTemperature)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = DevicesAdapter(ArrayList())
        recycler.adapter = adapter

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (hasPermissions()) {
            unpairDeviceOnStart()
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


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BLE Scan App test notification"
            val descriptionText = ""
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(temperatureText: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Temperature")
            .setContentText(temperatureText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
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
        tvStatus.text = "Looking for device..."
        tvTemperature.text = ""

        val connectedDevices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)
        if (connectedDevices != null) {
            for (device in connectedDevices) {
                if (device.address == TARGET_MAC_ADDRESS) {
                    Log.i(TAG, "Device found in system connected list! Connecting directly.")
                    connectToDevice(device)
                    return
                }
            }
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) return

        tvStatus.text = "Scanning..."

        val filters: List<ScanFilter> = ArrayList()
        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        }

        val settings = settingsBuilder.build()

        bleScanner?.startScan(filters, settings, scanCallback)
        scanning = true
        btnScan.text = "Stop scan"

        handler.postDelayed({
            if (scanning) {
                stopBleScan()
                runOnUiThread {
                    tvStatus.text = "Device not found"
                    Toast.makeText(
                        this,
                        "This device is not found or is connected to another device.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, SCAN_PERIOD.toLong())
    }

    private fun stopBleScan() {
        handler.removeCallbacksAndMessages(null)
        if (bleScanner != null && scanning) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Scan stop error", e)
            }
        }
        scanning = false
        runOnUiThread {
            btnScan.text = "Start scan"
            tvStatus.text = "Scan stopped"
        }
    }

    private fun handleScanResult(result: ScanResult) {
        if (result.device.address != TARGET_MAC_ADDRESS) {
            return
        }

        if (isConnecting || bluetoothGatt != null) return
        stopBleScan()
        Log.i(TAG, "Device found via Scan: ${result.device.address}")
        connectToDevice(result.device)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothGatt != null) {
            bluetoothGatt?.close()
            bluetoothGatt = null
        }

        isConnecting = true
        tvStatus.text = "Connecting..."

        val d = BleDevice(device.name, device.address)
        devicesMap[device.address] = d
        runOnUiThread { adapter.updateList(ArrayList(devicesMap.values)) }

        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT.toLong())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(applicationContext, false, gattCallback)
        }
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

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connected to GATT server.")
                runOnUiThread {
                    btnScan.text = "Disconnect"
                    tvStatus.text = "Connected!"
                    btnScan.isEnabled = true
                    updateNotification("Connected. Waiting for data...")
                }

                handler.postDelayed({
                    if (bluetoothGatt != null) gatt.discoverServices()
                }, 500)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected")
                gatt.close()
                if (bluetoothGatt == gatt) {
                    bluetoothGatt = null
                }
                runOnUiThread { finishDisconnection() }

            } else {
                Log.w(TAG, "Gatt Error: $status")
                gatt.close()
                if (bluetoothGatt == gatt) bluetoothGatt = null

                runOnUiThread {
                    finishDisconnection()
                    tvStatus.text = "Error: $status"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableTemperatureNotifications(gatt)
            } else {
                Log.w(TAG, "Service discovery failed with status: $status")
                disconnect()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor written successfully.")
                runOnUiThread { tvStatus.text = "Receiving Data..." }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == TEMPERATURE_CHAR_UUID) {
                val temperatureValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                val temperatureCelsius = temperatureValue / 100.0f
                val formattedTemp = "%.2f °C".format(temperatureCelsius)

                updateNotification("Current: $formattedTemp")

                runOnUiThread {
                    tvTemperature.text = formattedTemp
                    tvStatus.text = "Connected"
                }
            }
        }
    }

    private fun enableTemperatureNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(ENVIRONMENTAL_SENSING_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(TEMPERATURE_CHAR_UUID)
        if (characteristic == null) {
            Log.e(TAG, "Temperature characteristic not found.")
            disconnect()
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor?.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        descriptor?.let { gatt.writeDescriptor(it) }
    }

    private fun disconnect() {
        handler.removeCallbacks(connectionTimeoutRunnable)

        cancelNotification()

        if (bluetoothGatt != null) {
            tvStatus.text = "Disconnecting & Forgetting..."
            btnScan.isEnabled = false

            val deviceToForget = bluetoothGatt?.device
            refreshDeviceCache(bluetoothGatt!!)

            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            if (deviceToForget != null) {
                removeBond(deviceToForget)
            }

            finishDisconnection()
        } else {
            finishDisconnection()
        }
    }

    private fun finishDisconnection() {
        scanning = false
        isConnecting = false

        cancelNotification()

        tvStatus.text = "Disconnected"
        tvTemperature.text = ""
        btnScan.text = "Start scan"
        btnScan.isEnabled = true
        adapter.updateList(ArrayList())
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSIONS_ANDROID13
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_ANDROID12
        } else {
            PERMISSIONS_BEFORE_S
        }

        for (p in permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                requestAppropriatePermissions()
                return false
            }
        }
        return true
    }

    private fun requestAppropriatePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSIONS_ANDROID13
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_ANDROID12
        } else {
            PERMISSIONS_BEFORE_S
        }
        ActivityCompat.requestPermissions(this, permissions, 1234)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        cancelNotification()

        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            val result = localMethod.invoke(gatt) as Boolean
            return result
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    private fun unpairDeviceOnStart() {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(TARGET_MAC_ADDRESS)
            if (device != null && device.bondState == BluetoothDevice.BOND_BONDED) {
                removeBond(device)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        private const val TAG = "ValkyrieScanner"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val SCAN_PERIOD = 4000
        private const val CONNECTION_TIMEOUT = 3000

        private val ENVIRONMENTAL_SENSING_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
        private val TEMPERATURE_CHAR_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val PERMISSIONS_BEFORE_S: Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        private val PERMISSIONS_ANDROID12: Array<String> = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )


        @SuppressLint("InlinedApi")
        private val PERMISSIONS_ANDROID13: Array<String> = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}