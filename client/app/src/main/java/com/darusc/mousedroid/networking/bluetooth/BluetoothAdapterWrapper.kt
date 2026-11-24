package com.darusc.mousedroid.networking.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

/**
 * Wrapper class around BluetoothAdapter to provide unified access
 * to the Android BluetoothAdapter
 */
class BluetoothAdapterWrapper private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: BluetoothAdapterWrapper? = null

        fun getInstance(): BluetoothAdapterWrapper? {
            synchronized(this) {
                return instance
            }
        }

        fun initialize(context: Context) {
            synchronized(this) {
                if (instance == null) {
                    instance = BluetoothAdapterWrapper(context.applicationContext)
                }
            }
        }
    }

    private val _adapter: BluetoothAdapter?

    val adapter: BluetoothAdapter?
        get() = _adapter

    val isEnabled: Boolean
        @SuppressLint("MissingPermission")
        get() = try {
            _adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }

    val pairedDevices: Set<BluetoothDevice>
        @SuppressLint("MissingPermission")
        get() = try {
            _adapter?.bondedDevices ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        return try {
            if (_adapter?.isDiscovering == true) {
                _adapter.cancelDiscovery()
            }
            _adapter?.startDiscovery() == true
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery(): Boolean {
        return try {
            _adapter?.cancelDiscovery() == true
        } catch (_: Exception) {
            false
        }
    }

    init {
        _adapter = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
                bluetoothManager?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
        } catch (_: Exception) {
            null
        }
    }
}
