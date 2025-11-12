package com.dombien.bleappscantest;

import java.util.List;
import java.util.UUID;

public class BleDevice {
    public String name;
    public String address;
    public int rssi;
    public int type; // BluetoothDevice.getType()
    public List<UUID> serviceUuids;
    public Integer txPower; // nullable

    public BleDevice(String name, String address, int rssi, int type, List<UUID> serviceUuids, Integer txPower) {
        this.name = name;
        this.address = address;
        this.rssi = rssi;
        this.type = type;
        this.serviceUuids = serviceUuids;
        this.txPower = txPower;
    }
}