package com.dombien.bleappscantest.Activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dombien.bleappscantest.BLE.BleDevice
import com.dombien.bleappscantest.BLE.BlePermissionManager
import com.dombien.bleappscantest.BLE.BleScanManager
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

    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private val devicesMap: MutableMap<String, BleDevice> = LinkedHashMap()

    private val lastSeenMap: MutableMap<String, Long> = HashMap()

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val DEVICE_TIMEOUT_MS = 5000L
    private val CLEANUP_INTERVAL_MS = 1000L

    private val deviceActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            resetInterface()
        }
    }


    private val cleanupRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val iterator = devicesMap.entries.iterator()
            var listModified = false

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val address = entry.key
                val lastSeenTime = lastSeenMap[address] ?: 0L

                // Jeśli urządzenie milczy dłużej niż 5 sekund -> usuń z listy
                if (currentTime - lastSeenTime > DEVICE_TIMEOUT_MS) {
                    iterator.remove()
                    lastSeenMap.remove(address)
                    listModified = true
                }
            }

            if (listModified) {
                adapter.updateList(ArrayList(devicesMap.values))
            }


            if (scanManager.isScanning) {
                cleanupHandler.postDelayed(this, CLEANUP_INTERVAL_MS)
            }
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
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
        swipeRefreshLayout.setOnRefreshListener {

            resetInterface()
            startBleScanProcess()
            swipeRefreshLayout.isRefreshing = false
        }

        adapter = DevicesAdapter(ArrayList()) { device ->
            handleDeviceClick(device)
        }
        recycler.adapter = adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
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
                stopBleScanProcess()
            } else {
                startBleScanProcess()
            }
        }
    }

    private fun startBleScanProcess() {
        if (!permissionManager.hasPermissions() || bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(this, "Permissions or BT missing", Toast.LENGTH_SHORT).show()
            return
        }


        cleanupHandler.removeCallbacks(cleanupRunnable)
        cleanupHandler.post(cleanupRunnable)

        scanManager.startScan()
    }

    private fun stopBleScanProcess() {
        scanManager.stopScan()
        cleanupHandler.removeCallbacks(cleanupRunnable)
    }

    private fun handleDeviceClick(device: BleDevice) {
        val nativeDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        val connectionState = bluetoothManager?.
        getConnectionState(nativeDevice, BluetoothProfile.GATT)

        if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            Toast.makeText(this, "Device already connected elsewhere.", Toast.LENGTH_SHORT).show()
        } else {
            // We stop scanning before moving on
            stopBleScanProcess()

            val intent = Intent(this, DeviceActivity::class.java)
            intent.putExtra("DEVICE_ADDRESS", device.address)
            intent.putExtra("DEVICE_NAME", device.name)
            deviceActivityLauncher.launch(intent)
        }
    }

    private fun resetInterface() {
        //  here we clear everything
        stopBleScanProcess()
        devicesMap.clear()
        lastSeenMap.clear()
        adapter.updateList(ArrayList())
    }

    // --- Callbacks from BleScanManager ---

    override fun onDeviceFound(device: BleDevice) {
        // Update last seen time
        lastSeenMap[device.address] = System.currentTimeMillis()

        if (!devicesMap.containsKey(device.address)) {
            devicesMap[device.address] = device
            adapter.updateList(ArrayList(devicesMap.values))
        }

    }

    override fun onScanStatusChanged(isScanning: Boolean) {
        runOnUiThread {
            btnScan.text = if (isScanning) "Stop scan" else "Start scan"
            if (!isScanning) {

                cleanupHandler.removeCallbacks(cleanupRunnable)
            }
        }
    }

    override fun onScanFailed(errorCode: Int) {
        cleanupHandler.removeCallbacks(cleanupRunnable)
        if (errorCode == 2) {
            runOnUiThread {
                Toast.makeText(this, "System blocked scanning. Wait 30s.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onScanFinishedAutomatic() {
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScanProcess()
    }
}