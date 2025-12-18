package com.dombien.bleappscantest.Activities

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dombien.bleappscantest.Notifications.BleForegroundService
import com.dombien.bleappscantest.R
import java.util.UUID

@SuppressLint("MissingPermission")
class DefaultDeviceActivity : AppCompatActivity() {

    // UI Views
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvDetailValue1: TextView
    private lateinit var tvDetailValue2: TextView
    private lateinit var tvDetailRssi: TextView
    private lateinit var btnDisconnect: Button

    // Bluetooth
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    // State
    private val handler = Handler(Looper.getMainLooper())

    private var currentVal1Str = "--"
    private var currentVal2Str = "--"
    private var currentRssiStr = "-- dBm"

    companion object {
        private const val TAG = "DefaultDeviceActivity"

        private val SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
        private val VALUE1_CHAR_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb") // Np. Temp
        private val VALUE2_CHAR_UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb") // Np. Hum

        // Client Config (Standard)
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val disconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BleForegroundService.ACTION_DISCONNECT_REQUEST) {
                performCleanUp()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_device)

        // UI Binding
        tvDetailAddress = findViewById(R.id.tvDetailAddress)
        tvDetailValue1 = findViewById(R.id.tvDetailValue1)
        tvDetailValue2 = findViewById(R.id.tvDetailValue2)
        tvDetailRssi = findViewById(R.id.tvDetailRssi)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        tvDetailAddress.text = deviceAddress ?: "Unknown Address"

        val filter = IntentFilter(BleForegroundService.ACTION_DISCONNECT_REQUEST)
        ContextCompat.registerReceiver(this, disconnectReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        btnDisconnect.setOnClickListener {
            performCleanUp()
            setResult(RESULT_OK)
            finish()
        }

        startBleService("Connecting...")
        if (deviceAddress != null) connectToDevice(deviceAddress!!)
        else finish()
    }

    private fun updateUIAndService() {
        runOnUiThread {
            tvDetailValue1.text = currentVal1Str
            tvDetailValue2.text = currentVal2Str
            tvDetailRssi.text = currentRssiStr
        }
        val notificationText = "V1: $currentVal1Str | V2: $currentVal2Str | RSSI: $currentRssiStr"
        startBleService(notificationText)
    }

    private fun startBleService(status: String) {
        val serviceIntent = Intent(this, BleForegroundService::class.java)
        serviceIntent.putExtra("temperature", status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    private fun stopBleService() {
        val serviceIntent = Intent(this, BleForegroundService::class.java)
        stopService(serviceIntent)
    }

    private fun performCleanUp() {
        stopBleService()
        if (bluetoothGatt != null) {
            try { bluetoothGatt?.disconnect() } catch (e:Exception){}
            try { bluetoothGatt?.close() } catch (e:Exception){}
            bluetoothGatt = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(disconnectReceiver)
        performCleanUp()
    }

    private fun connectToDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                startBleService("Connected. Discovering services...")
                handler.postDelayed({ gatt.discoverServices() }, 1000)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                startBleService("Disconnected")
                runOnUiThread { tvDetailValue1.text = "--"; tvDetailRssi.text = "Offline" }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotificationsSequence(gatt)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicUpdate(characteristic)
            gatt.readRemoteRssi()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleCharacteristicUpdate(characteristic)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentRssiStr = "$rssi dBm"
                updateUIAndService()
            }
        }
    }

    private fun handleCharacteristicUpdate(characteristic: BluetoothGattCharacteristic) {
        val uuid = characteristic.uuid
        when (uuid) {
            VALUE1_CHAR_UUID -> {
                val valInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                currentVal1Str = "${valInt / 100.0f}"
            }
            VALUE2_CHAR_UUID -> {
                val valInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                currentVal2Str = "${valInt / 100.0f}"
            }
        }
        updateUIAndService()
    }

    private fun enableNotificationsSequence(gatt: BluetoothGatt) {
        var currentDelay = 0L
        val delayStep = 1000L

        // Value 1
        handler.postDelayed({
            enableNotification(gatt, SERVICE_UUID, VALUE1_CHAR_UUID)
        }, currentDelay)
        currentDelay += delayStep

        // Value 2
        handler.postDelayed({
            enableNotification(gatt, SERVICE_UUID, VALUE2_CHAR_UUID)
        }, currentDelay)
    }

    private fun enableNotification(gatt: BluetoothGatt, serviceUuid: UUID, charUuid: UUID): Boolean {
        val service = gatt.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(charUuid) ?: return false
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            return true
        }
        return false
    }
}