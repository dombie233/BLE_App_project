package com.dombien.bleappscantest

import java.util.UUID

class BleDevice(
    var name: String?,
    var address: String?,
    var rssi: Int,
    var type: Int,
    var serviceUuids: MutableList<UUID?>?,
    var txPower: Int?
) 