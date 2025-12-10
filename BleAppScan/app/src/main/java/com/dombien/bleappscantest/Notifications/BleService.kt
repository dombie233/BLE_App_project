package com.dombien.bleappscantest.Services

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.IBinder
import com.dombien.bleappscantest.Notifications.BleNotificationManager
import java.util.*

@SuppressLint("MissingPermission")
class BleService : Service() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Używamy Twojego managera
    private lateinit var notificationManager: BleNotificationManager

    companion object {
        // Stałe do komunikacji z Activity
        const val ACTION_GATT_CONNECTED = "com.dombien.ble.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.dombien.ble.ACTION_GATT_DISCONNECTED"
        const val ACTION_DATA_AVAILABLE = "com.dombien.ble.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.dombien.ble.EXTRA_DATA"

        // Komenda zatrzymania
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"

        private val ENVIRONMENTAL_SENSING_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
        private val TEMPERATURE_CHAR_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Inicjalizacja Twojego managera
        notificationManager = BleNotificationManager(this)
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obsługa zatrzymania serwisu
        if (intent?.action == ACTION_STOP_SERVICE) {
            closeConnection()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val deviceAddress = intent?.getStringExtra("DEVICE_ADDRESS")
        val deviceName = intent?.getStringExtra("DEVICE_NAME") ?: "Unknown Device"

        // 1. URUCHOMIENIE FOREGROUND (To sprawia, że notyfikacji nie da się usunąć)
        // Musisz dodać metodę .getNotification() do swojego BleNotificationManager (jak w poprzedniej odpowiedzi)
        // Jeśli jej nie dodałeś, użyj notificationManager.updateNotification(...), ale startForeground wymaga obiektu Notification.
        // Prowizoryczne rozwiązanie jeśli nie zmieniłeś Managera:
        notificationManager.updateNotification(deviceName, "Connecting...")
        // UWAGA: Aby to zadziałało w 100% poprawnie jako Foreground Service,
        // musisz w Managerze mieć funkcję zwracającą `Notification`.
        // Zakładam tutaj, że manager.updateNotification wywołuje notify(), ale dla serwisu potrzebujemy startForeground.

        // POPRAWNY SPOSÓB (wymaga zmiany w Managerze na zwracanie obiektu):
        // startForeground(1001, notificationManager.getNotification(deviceName, "Background service active"))
        val notification = notificationManager.getNotification(deviceName, "Connected")
        startForeground(1001, notification)
        if (deviceAddress != null) {
            connectToDevice(deviceAddress)
        }

        return START_STICKY
    }

    private fun connectToDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return

        // FIX: Dodanie TRANSPORT_LE jest kluczowe, aby wymusić tryb Low Energy.
        // Bez tego Android może nie wiedzieć, że ma zainicjować parowanie BLE.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_GATT_CONNECTED)
                notificationManager.updateNotification(gatt.device.name ?: "Device", "Connected. Waiting...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                notificationManager.updateNotification("Disconnected", "Waiting for reconnect...")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(ENVIRONMENTAL_SENSING_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(TEMPERATURE_CHAR_UUID)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == TEMPERATURE_CHAR_UUID) {
                val tempValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                val tempCelsius = tempValue / 100.0f
                val text = "%.2f °C".format(tempCelsius)

                // Aktualizacja notyfikacji przez Twój manager
                notificationManager.updateNotification(gatt.device.name ?: "Device", text)

                // Wysłanie danych do Activity
                broadcastUpdate(ACTION_DATA_AVAILABLE, text)
            }
        }
    }

    private fun broadcastUpdate(action: String, data: String? = null) {
        val intent = Intent(action)
        if (data != null) intent.putExtra(EXTRA_DATA, data)
        sendBroadcast(intent)
    }

    private fun closeConnection() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeConnection()
    }
}