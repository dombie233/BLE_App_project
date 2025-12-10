package com.dombien.bleappscantest.Activities

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
import com.dombien.bleappscantest.R
import java.util.UUID

@SuppressLint("MissingPermission")
class DeviceActivity : AppCompatActivity() {

    private lateinit var tvDetailAddress: TextView
    private lateinit var tvDetailTemp: TextView
    private lateinit var btnDisconnect: Button

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var deviceAddress: String? = null

    private var deviceName: String = "Unknown Device"

    private val handler = Handler(Looper.getMainLooper())
    private val NO_DATA_VALUE = "--.-- °C"


    private val CHANNEL_ID = "ble_temp_channel"
    private val NOTIFICATION_ID = 1001
    private lateinit var notificationManager: NotificationManagerCompat

    companion object {
        private const val TAG = "DeviceActivity"
        private val ENVIRONMENTAL_SENSING_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
        private val TEMPERATURE_CHAR_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        // Inicjalizacja menedżera powiadomień
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        tvDetailAddress = findViewById(R.id.tvDetailAddress)
        tvDetailTemp = findViewById(R.id.tvDetailTemp)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        // Pobieramy nazwę, jeśli jest null to wstawiamy domyślną
        deviceName = intent.getStringExtra("DEVICE_NAME") ?: "Unknown Device"

        tvDetailAddress.text = deviceAddress ?: "Unknown Address"
        tvDetailTemp.text = NO_DATA_VALUE

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        btnDisconnect.setOnClickListener {
            performCleanUp()
            setResult(RESULT_OK)
            finish()
        }

        if (deviceAddress != null) {
            connectToDevice(deviceAddress!!)
        } else {
            Toast.makeText(this, "No device address provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- LOGIKA POWIADOMIEŃ ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Temperature Monitor"
            val descriptionText = "Shows real-time temperature from BLE device"
            val importance = NotificationManager.IMPORTANCE_LOW // Low, aby nie wydawało dźwięku przy każdej zmianie
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(temperatureText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(this, DeviceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ble_notification)
            .setContentTitle(deviceName)
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



    private fun performCleanUp() {
        cancelNotification()

        if (bluetoothGatt != null) {
            val deviceToForget = bluetoothGatt?.device
            refreshDeviceCache(bluetoothGatt!!)
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            if (deviceToForget != null) {
                removeBond(deviceToForget)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        performCleanUp()
    }

    private fun connectToDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            finish()
            return
        }

        tvDetailTemp.text = NO_DATA_VALUE


        updateNotification("Connecting...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        }
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove bond", e)
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            return localMethod.invoke(gatt) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "An exception occurred while refreshing device", e)
        }
        return false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { tvDetailTemp.text = NO_DATA_VALUE }
                // Aktualizacja powiadomienia o statusie
                updateNotification("Connected. Waiting for data...")

                handler.postDelayed({
                    if (bluetoothGatt != null) {
                        gatt.discoverServices()
                    }
                }, 600)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread { tvDetailTemp.text = NO_DATA_VALUE }
                // Usuwamy powiadomienie po utracie połączenia
                cancelNotification()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableTemperatureNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == TEMPERATURE_CHAR_UUID) {
                val temperatureValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                val temperatureCelsius = temperatureValue / 100.0f
                val formattedTemp = "%.2f °C".format(temperatureCelsius)

                // 1. Aktualizacja powiadomienia w czasie rzeczywistym
                updateNotification(formattedTemp)

                // 2. Aktualizacja UI
                runOnUiThread {
                    tvDetailTemp.text = formattedTemp
                }
            }
        }
    }

    private fun enableTemperatureNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(ENVIRONMENTAL_SENSING_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(TEMPERATURE_CHAR_UUID)

        if (characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                runOnUiThread { tvDetailTemp.text = NO_DATA_VALUE }
            }
        }
    }
}