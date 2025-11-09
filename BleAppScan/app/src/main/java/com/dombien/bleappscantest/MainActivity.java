package com.dombien.bleappscantest;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanRecord;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BleFinder";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int SCAN_PERIOD = 10000; // ms

    private static final String BACKEND_URL = "http://192.168.100.7:8080/data";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanning = false;

    private Button btnScan;
    private DevicesAdapter adapter;
    private Map<String, BleDevice> devicesMap = new LinkedHashMap<>();

    // Permissions list (runtime)
    private static final String[] PERMISSIONS_BEFORE_S = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private static final String[] PERMISSIONS_ANDROID12 = new String[] {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScan = findViewById(R.id.btnScan);
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DevicesAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnScan.setOnClickListener(v -> {
            if (!scanning) startBleScan();
            else stopBleScan();
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchBackendData();
                handler.postDelayed(this, 5000);
            }
        }, 1000);

        buildScanCallback();
    }

    private void buildScanCallback() {
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, @NonNull ScanResult result) {
                handleScanResult(result);
            }

            @Override
            public void onBatchScanResults(@NonNull List<ScanResult> results) {
                for (ScanResult r : results) handleScanResult(r);
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.w(TAG, "Scan failed: " + errorCode);
                Toast.makeText(MainActivity.this, "Scan failed: " + errorCode, Toast.LENGTH_SHORT).show();
                scanning = false;
                runOnUiThread(() -> btnScan.setText("Start scan"));
            }
        };
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            for (String p : PERMISSIONS_ANDROID12) {
                if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                    return false;
            }
            return true;
        } else {
            // older: need location
            for (String p : PERMISSIONS_BEFORE_S) {
                if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                    return false;
            }
            return true;
        }
    }

    private void requestAppropriatePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_ANDROID12, 1234);
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS_BEFORE_S, 1234);
        }
    }

    private void startBleScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
            return;
        }

        if (!hasPermissions()) {
            requestAppropriatePermissions();
            return;
        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            requestAppropriatePermissions();
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Toast.makeText(this, "BLE scanner not available", Toast.LENGTH_SHORT).show();
            return;
        }

        devicesMap.clear();
        adapter.updateList(new ArrayList<>());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            bleScanner.startScan(null, settings, scanCallback);
            scanning = true;
            btnScan.setText("Stop scan");
            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show();

            handler.postDelayed(() -> {
                if (scanning) stopBleScan();
            }, SCAN_PERIOD);

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for startScan()", e);
            Toast.makeText(this, "No Bluetooth scan permission", Toast.LENGTH_LONG).show();
        }
    }

    private void stopBleScan() {
        if (bleScanner != null && scanning) {
            try {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                                == PackageManager.PERMISSION_GRANTED) {
                    bleScanner.stopScan(scanCallback);
                }
            } catch (SecurityException e) {
                Log.w(TAG, "stopScan SecurityException", e);
            }
        }
        scanning = false;
        btnScan.setText("Start scan");
        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show();
    }

    private void fetchBackendData() {
        new Thread(() -> {
            try {
                URL url = new URL(BACKEND_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    // parsowanie JSON
                    JSONObject obj = new JSONObject(sb.toString());
                    SensorData sensorData = new SensorData();
                    sensorData.temperature = obj.optString("temperature", "--");
                    sensorData.humidity = obj.optString("humidity", "--");
                    sensorData.status = obj.optString("status", "OK");

                    runOnUiThread(() -> adapter.setSensorData(sensorData));
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error fetching backend data", e);
            }
        }).start();
    }

    private void handleScanResult(ScanResult result) {
        BluetoothDevice device = result.getDevice();

        String address = "unknown";
        String name = "unknown";
        int type = BluetoothDevice.DEVICE_TYPE_UNKNOWN;


        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED) {

                address = device.getAddress();
                name = device.getName();
                type = device.getType();

            } else {

                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission - skipping device details");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException reading device info", e);
        }

        int rssi = result.getRssi();

        ScanRecord record = result.getScanRecord();
        List<java.util.UUID> uuids = null;
        Integer txPower = null;
        if (record != null) {
            if (record.getServiceUuids() != null) {
                uuids = new ArrayList<>();
                for (android.os.ParcelUuid p : record.getServiceUuids()) {
                    uuids.add(p.getUuid());
                }
            }
            int t = record.getTxPowerLevel();
            if (t != Integer.MIN_VALUE) txPower = t;
        }

        BleDevice d = new BleDevice(name, address, rssi, type, uuids, txPower);
        devicesMap.put(address, d);

        runOnUiThread(() -> {
            List<BleDevice> list = new ArrayList<>(devicesMap.values());
            Collections.sort(list, (a, b) -> Integer.compare(b.rssi, a.rssi));
            adapter.updateList(list);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;

        if (allGranted) {
            startBleScan();
        } else {
            Toast.makeText(this, "Permissions required to scan BLE", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scanning) stopBleScan();
    }
}
