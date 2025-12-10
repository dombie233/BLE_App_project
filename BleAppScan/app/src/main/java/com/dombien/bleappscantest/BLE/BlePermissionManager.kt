package com.dombien.bleappscantest.BLE

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

class BlePermissionManager(private val activity: Activity) {

    fun hasPermissions(): Boolean {
        val permissions = getPermissionsArray()
        for (p in permissions) {
            if (ActivityCompat.checkSelfPermission(activity,
                    p) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions()
                return false
            }
        }
        return true
    }

    fun requestPermissions() {
        ActivityCompat.requestPermissions(activity, getPermissionsArray(),
            1234)
    }

    private fun getPermissionsArray(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSIONS_ANDROID13
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_ANDROID12
        } else {
            PERMISSIONS_BEFORE_S
        }
    }

    companion object {
        private val PERMISSIONS_BEFORE_S = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        private val PERMISSIONS_ANDROID12 = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        private val PERMISSIONS_ANDROID13 = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}