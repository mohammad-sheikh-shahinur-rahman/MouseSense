package com.darusc.mousedroid

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.darusc.mousedroid.networking.Connection.Mode

fun getDeviceDetails(context: Context, connectionMode: Mode): String {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL

    var deviceName: String? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val bm = context.getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
                deviceName = bm?.adapter?.name
            }
        } else {
            deviceName = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }
    } catch (_: Exception) {
    }

    if (deviceName.isNullOrBlank()) {
        try {
            deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (_: Exception) {
        }
    }

    if (deviceName.isNullOrBlank()) {
        deviceName = model
    }

    return "$manufacturer/$deviceName/$model/${connectionMode.ordinal}"
}
