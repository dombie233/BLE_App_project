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
class DeviceActivity : AppCompatActivity() {

    // UI Views
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvDetailTemp: TextView
    private lateinit var tvDetailHumidity: TextView
    private lateinit var tvDetailRssi: TextView
    private lateinit var btnDisconnect: Button

    // Bluetooth
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    // State
    private val handler = Handler(Looper.getMainLooper())


    private var currentTempStr = "--.-- °C"
    private var currentHumStr = "-- %"
    private var currentBatStr = "-- %"
    private var currentRssiStr = "-- dBm"

    companion object {
        private const val TAG = "DeviceActivity"

        // --- UUIDs ---
        // Environmental Sensing Service
        private val ESS_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
        private val TEMP_CHAR_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        private val HUMIDITY_CHAR_UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")

        // Battery Service
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        // Client Config (Standard)
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // Odbiornik sygnału z powiadomienia (Bumerang/Disconnect)
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
        setContentView(R.layout.activity_device)

        // UI Binding
        tvDetailAddress = findViewById(R.id.tvDetailAddress)
        tvDetailTemp = findViewById(R.id.tvDetailTemp)
        tvDetailHumidity = findViewById(R.id.tvDetailHumidity)
        tvDetailRssi = findViewById(R.id.tvDetailRssi)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        // Init Bluetooth
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        // Get Intent Data
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        tvDetailAddress.text = deviceAddress ?: "Unknown Address"

        // Register Receiver
        val filter = IntentFilter(BleForegroundService.ACTION_DISCONNECT_REQUEST)
        ContextCompat.registerReceiver(this, disconnectReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Listeners
        btnDisconnect.setOnClickListener {
            performCleanUp()
            setResult(RESULT_OK)
            finish()
        }

        // Start Logic
        startBleService("Connecting...")
        if (deviceAddress != null) {
            connectToDevice(deviceAddress!!)
        } else {
            Toast.makeText(this, "No device address", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateUIAndService() {

        runOnUiThread {
            tvDetailTemp.text = currentTempStr
            tvDetailHumidity.text = currentHumStr

            tvDetailRssi.text = currentRssiStr
        }


        val notificationText = "Temperature: $currentTempStr \n Humidity: $currentHumStr \n Rssi: $currentRssiStr"
        startBleService(notificationText)
    }

    private fun startBleService(status: String) {
        val serviceIntent = Intent(this, BleForegroundService::class.java)
        serviceIntent.putExtra("temperature", status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopBleService() {
        val serviceIntent = Intent(this, BleForegroundService::class.java)
        stopService(serviceIntent)
    }

    private fun performCleanUp() {
        stopBleService()
        if (bluetoothGatt != null) {
            val deviceToForget = bluetoothGatt?.device
            try { bluetoothGatt?.disconnect() } catch (e:Exception){}
            try { bluetoothGatt?.close() } catch (e:Exception){}
            bluetoothGatt = null
            if (deviceToForget != null) {
                removeBond(deviceToForget)
            }
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

    private fun removeBond(device: BluetoothDevice) {
        try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                val method = device.javaClass.getMethod("removeBond")
                method.invoke(device)
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to remove bond", e) }
    }

    // --- GATT CALLBACKS ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                startBleService("Connected. Discovering services...")
                handler.postDelayed({ gatt.discoverServices() }, 1000)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                startBleService("Disconnected")
                runOnUiThread {
                    tvDetailTemp.text = "--"
                    tvDetailRssi.text = "Offline"
                }
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Odebrano wartość z odczytu (Read): ${characteristic.uuid}")
                handleCharacteristicUpdate(characteristic)
            } else {

                Log.e(TAG, "Błąd odczytu charakterystyki! Status: $status (GATT_INSUFFICIENT_AUTHENTICATION=5, GATT_FAILURE=257)")


                if (status == 137 || status == 5) {
                    Log.w(TAG, "Próba parowania...")
                    gatt.device.createBond()
                }
            }
        }
        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentRssiStr = "$rssi dBm"
                updateUIAndService()
            }
        }
    }

    // Decoding value support
    private fun handleCharacteristicUpdate(characteristic: BluetoothGattCharacteristic) {
        val uuid = characteristic.uuid

        when (uuid) {
            TEMP_CHAR_UUID -> {
                val valInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                val celsius = valInt / 100.0f
                currentTempStr = "%.2f °C".format(celsius)
            }
            HUMIDITY_CHAR_UUID -> {

                val valInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                val hum = valInt / 100.0f
                currentHumStr = "%.2f %%".format(hum)
            }
//            BATTERY_LEVEL_CHAR_UUID -> {
//                // Standard: uint8, 0-100%
//                val valInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
//                currentBatStr = "$valInt %"
//            }
        }
        updateUIAndService()
    }

    // --- Notification activation sequence  ---

    private fun enableNotificationsSequence(gatt: BluetoothGatt) {
        val delayStep = 1000L
        var currentDelay = 0L

        //Temperature (Notify)
        handler.postDelayed({
            Log.d(TAG, "Enabling Temperature...")
            enableNotification(gatt, ESS_SERVICE_UUID, TEMP_CHAR_UUID)
        }, currentDelay)

        currentDelay += delayStep

        // Humidity (Notify)
        handler.postDelayed({
            Log.d(TAG, "Enabling Humidity...")
            enableNotification(gatt, ESS_SERVICE_UUID, HUMIDITY_CHAR_UUID)
        }, currentDelay)

        currentDelay += delayStep



//        handler.postDelayed({
//            Log.d(TAG, "Reading Battery Initial Value...")
//            readCharacteristic(gatt, BATTERY_SERVICE_UUID, BATTERY_LEVEL_CHAR_UUID)
//        }, currentDelay)
//
//        currentDelay += delayStep
//
//
//        handler.postDelayed({
//            Log.d(TAG, "Enabling Battery Notifications...")
//            enableNotification(gatt, BATTERY_SERVICE_UUID, BATTERY_LEVEL_CHAR_UUID)
//        }, currentDelay)
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

    private fun readCharacteristic(gatt: BluetoothGatt, serviceUuid: UUID, charUuid: UUID) {
        val service = gatt.getService(serviceUuid)
        if (service == null) {
            Log.e(TAG, "BŁĄD: Nie znaleziono serwisu: $serviceUuid")
            return
        }
        val characteristic = service.getCharacteristic(charUuid)
        if (characteristic == null) {
            Log.e(TAG, "BŁĄD: Nie znaleziono charakterystyki: $charUuid")
            return
        }

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.e(TAG, "BŁĄD: Ta charakterystyka nie jest do odczytu (Not Readable)!")
            return
        }

        val success = gatt.readCharacteristic(characteristic)
        if (success) {
            Log.d(TAG, "Sukces: Wysłano zapytanie o odczyt dla $charUuid")
        } else {
            Log.e(TAG, "BŁĄD: Android odrzucił zapytanie readCharacteristic (Stack busy?)")
        }
    }
}