package com.dombien.bleappscantest

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

@SuppressLint("MissingPermission")
class DeviceActivity : AppCompatActivity() {

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvDetailTemp: TextView
    private lateinit var btnDisconnect: Button

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        // 1. Pobieranie danych z Intentu
        deviceAddress = intent.getStringExtra(EXTRA_ADDRESS)
        deviceName = intent.getStringExtra(EXTRA_NAME)

        // 2. Inicjalizacja widoków
        tvDetailName = findViewById(R.id.tvDetailName)
        tvDetailAddress = findViewById(R.id.tvDetailAddress)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvDetailTemp = findViewById(R.id.tvDetailTemp)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        tvDetailName.text = deviceName ?: "Unknown"
        tvDetailAddress.text = deviceAddress


        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter


        if (deviceAddress != null) {
            connectToDevice(deviceAddress!!)
        } else {
            finish()
        }


        btnDisconnect.setOnClickListener {
            disconnectAndClose()
            finish()
        }
    }

    private fun connectToDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
            return
        }

        tvConnectionStatus.text = "Connecting..."


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { tvConnectionStatus.text = "Discovering Services..." }
                // Po połączeniu, szukamy serwisów
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    tvConnectionStatus.text = "Disconnected"
                    tvDetailTemp.text = "--.-- °C"
                    // Opcjonalnie zamknij aktywność po rozłączeniu:
                    // finish()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { tvConnectionStatus.text = "Connected" }
                enableTemperatureNotifications(gatt)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == TEMPERATURE_CHAR_UUID) {
                // Odczyt formatu SINT16 (zgodnie z Twoim starym kodem)
                val temperatureValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                val temperatureCelsius = temperatureValue / 100.0f

                runOnUiThread {
                    tvDetailTemp.text = "%.2f °C".format(temperatureCelsius)
                }
            }
        }

        // W niektórych wersjach Androida (API 33+) callback jest inny:
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // Jeśli używasz bardzo nowego API, możesz potrzebować tej metody zamiast powyższej
            super.onCharacteristicChanged(gatt, characteristic, value)
            // Logika dekodowania byłaby podobna
        }
    }

    private fun enableTemperatureNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(ENVIRONMENTAL_SENSING_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(TEMPERATURE_CHAR_UUID)

        if (characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor?.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            gatt.writeDescriptor(descriptor)
        } else {
            runOnUiThread {
                tvConnectionStatus.text = "Temp Service Not Found"
            }
        }
    }

    private fun disconnectAndClose() {
        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
    }

    override fun onDestroy() {
        disconnectAndClose()
        super.onDestroy()
    }

    // Stałe potrzebne tylko tutaj
    companion object {
        const val EXTRA_ADDRESS = "device_address"
        const val EXTRA_NAME = "device_name"
        private const val TAG = "DeviceActivity"

        // UUID zgodne z Twoim kodem
        private val ENVIRONMENTAL_SENSING_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
        private val TEMPERATURE_CHAR_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}