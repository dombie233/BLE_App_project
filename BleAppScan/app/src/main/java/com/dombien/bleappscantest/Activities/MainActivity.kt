package com.dombien.bleappscantest.Activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dombien.bleappscantest.BLE.BleDevice
import com.dombien.bleappscantest.BLE.BlePermissionManager
import com.dombien.bleappscantest.BLE.BleScanManager
import com.dombien.bleappscantest.Activities.DeviceActivity
import com.dombien.bleappscantest.BLE.DevicesAdapter
import com.dombien.bleappscantest.R

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), BleScanManager.BleScanCallback {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var lastClickTime: Long = 0
    private val CLICK_DEBOUNCE_DELAY = 1000L

    private lateinit var permissionManager: BlePermissionManager
    private lateinit var scanManager: BleScanManager

    private lateinit var btnScan: Button
    private lateinit var adapter: DevicesAdapter

    private val devicesMap: MutableMap<String, BleDevice> = LinkedHashMap()

    private val deviceActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            resetInterface()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter


        permissionManager = BlePermissionManager(this)
        scanManager = BleScanManager(bluetoothAdapter, this)

        setupUI()
    }

    private fun setupUI() {
        btnScan = findViewById(R.id.btnScan)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.isNestedScrollingEnabled = false
        recycler.layoutManager = LinearLayoutManager(this)


        adapter = DevicesAdapter(ArrayList()) { device ->
            handleDeviceClick(device)
        }
        recycler.adapter = adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this,
                "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnScan.setOnClickListener {
            val currentTime = System.currentTimeMillis()


            if (currentTime - lastClickTime < CLICK_DEBOUNCE_DELAY) {

                return@setOnClickListener
            }
            lastClickTime = currentTime


            if (scanManager.isScanning) {
                scanManager.stopScan()
            } else {
                startBleScanProcess()
            }
        }
    }

    private fun startBleScanProcess() {
        if (!permissionManager.hasPermissions() || bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(this,
                "Permissions or BT missing", Toast.LENGTH_SHORT).show()
            return
        }

        //Reset list before new scan
        devicesMap.clear()
        adapter.updateList(ArrayList())

        scanManager.startScan()
    }

    private fun handleDeviceClick(device: BleDevice) {
        // --- TOAST 2: Check if the device is already connected ---
        val nativeDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        val connectionState = bluetoothManager?.
        getConnectionState(nativeDevice, BluetoothProfile.GATT)

        if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            Toast.makeText(this, "The phone connects" +
                    " to a device that is already connected." +
                    "", Toast.LENGTH_SHORT).show()
        } else {
            scanManager.stopScan()
            val intent = Intent(this, DeviceActivity::class.java)
            intent.putExtra("DEVICE_ADDRESS", device.address)
            intent.putExtra("DEVICE_NAME", device.name)
            deviceActivityLauncher.launch(intent)
        }
    }

    private fun resetInterface() {
        devicesMap.clear()
        adapter.updateList(ArrayList())
        scanManager.stopScan()
    }

    // --- Callbacks from BleScanManager ---

    override fun onDeviceFound(device: BleDevice) {
        if (!devicesMap.containsKey(device.address)) {
            devicesMap[device.address] = device
            adapter.updateList(ArrayList(devicesMap.values))
        }
    }

    override fun onScanStatusChanged(isScanning: Boolean) {
        runOnUiThread {
            btnScan.text = if (isScanning) "Stop scan" else "Start scan"
        }
    }

    override fun onScanFailed(errorCode: Int) {
        if (errorCode == 2) {
            runOnUiThread {
                Toast.makeText(this, "The system has blocked" +
                        " scanning. Please wait 30 seconds.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onScanFinishedAutomatic() {
        if (devicesMap.isEmpty()) {
            Toast.makeText(this, "The devices BLE no found" +
                    "", Toast.LENGTH_LONG).show()
        }
    }
}