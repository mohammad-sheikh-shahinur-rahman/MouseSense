package com.darusc.mousedroid.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darusc.mousedroid.getDeviceDetails
import com.darusc.mousedroid.networking.Connection
import com.darusc.mousedroid.networking.ConnectionManager
import com.darusc.mousedroid.networking.NetworkDiscovery
import com.darusc.mousedroid.networking.bluetooth.BluetoothAdapterWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceListViewModel(
    application: Application,
    val mode: Connection.Mode
) : AndroidViewModel(application) {

    private val TAG = "MouseSenseDeviceList"

    data class State(
        val devices: List<Pair<String, String>> = emptyList(),
        val status: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val connectionManager = ConnectionManager.getInstance()
    private val appPreferences: SharedPreferences =
        application.getSharedPreferences("devices", Context.MODE_PRIVATE)
    private val settingsPreferences: SharedPreferences =
        application.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)

    private var networkDiscovery: NetworkDiscovery? = null
    private var hasAutoConnected = false

    // Distinct list of devices: Address -> Name
    private val deviceMap = LinkedHashMap<String, String>()
    private var pendingBondAddress: String? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    if (device != null && !device.address.isNullOrBlank()) {
                        val name = device.name ?: "Bluetooth Device"
                        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                        val displayName = if (isBonded) name else "$name [Nearby]"
                        if (!deviceMap.containsKey(device.address)) {
                            deviceMap[device.address] = displayName
                            updateDevicesState("Found nearby device: $name")
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (device != null && bondState == BluetoothDevice.BOND_BONDED) {
                        val name = device.name ?: "Bluetooth Device"
                        deviceMap[device.address] = name
                        updateDevicesState("Paired with $name")

                        if (pendingBondAddress == device.address) {
                            pendingBondAddress = null
                            connectBluetooth(device.address, name)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (mode == Connection.Mode.BLUETOOTH) {
                        updateStatus(if (deviceMap.isEmpty()) "No devices found. Tap Pair on PC." else "Bluetooth discovery finished.")
                    }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    class Factory(
        private val application: Application,
        private val mode: Connection.Mode
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeviceListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DeviceListViewModel(application, mode) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    init {
        initializeList()
    }

    fun initializeList() {
        deviceMap.clear()
        hasAutoConnected = false

        if (mode == Connection.Mode.WIFI) {
            startWifiDiscovery()
        } else {
            startBluetoothDiscovery()
        }
    }

    private fun startWifiDiscovery() {
        // 1. Load saved Wi-Fi devices
        for ((name, address) in appPreferences.all) {
            if (address is String) {
                deviceMap[address] = name
            }
        }
        updateDevicesState("Scanning for MouseSense servers on Wi-Fi...")

        // 2. Start UDP network discovery
        val context = getApplication<Application>().applicationContext
        networkDiscovery = NetworkDiscovery(context).apply {
            setListener(object : NetworkDiscovery.DiscoveryListener {
                override fun onServerFound(name: String, ipAddress: String, port: Int) {
                    val displayName = "$name (Wi-Fi Auto-Detected)"
                    val isNew = !deviceMap.containsKey(ipAddress)
                    deviceMap[ipAddress] = displayName
                    updateDevicesState("Found server: $name ($ipAddress)")

                    // Auto-connect logic
                    checkWifiAutoConnect(name, ipAddress, isNew)
                }
            })
            startDiscovery()
        }

        // Subnet fast scan / probe can also be triggered
        viewModelScope.launch {
            delay(1500)
            if (deviceMap.isEmpty()) {
                updateStatus("Scanning local network for MouseSense PC...")
            }
        }
    }

    private fun checkWifiAutoConnect(serverName: String, ipAddress: String, isNew: Boolean) {
        val autoConnectEnabled = settingsPreferences.getBoolean("auto_connect", true)
        val lastIp = settingsPreferences.getString("last_wifi_ip", null)

        if (autoConnectEnabled && !hasAutoConnected) {
            if (lastIp != null && lastIp == ipAddress) {
                hasAutoConnected = true
                viewModelScope.launch {
                    updateStatus("Auto-connecting to $serverName...")
                    delay(300)
                    connectWifi(ipAddress, serverName)
                }
            } else if (lastIp == null && isNew && deviceMap.size == 1) {
                // First server discovered and no prior saved server
                hasAutoConnected = true
                viewModelScope.launch {
                    updateStatus("Auto-connecting to $serverName...")
                    delay(600)
                    connectWifi(ipAddress, serverName)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        val btAdapter = BluetoothAdapterWrapper.getInstance()

        // 1. Load paired devices
        val paired = btAdapter?.pairedDevices ?: emptySet()
        for (device in paired) {
            val name = device.name ?: "Paired Device"
            deviceMap[device.address] = name
        }

        updateDevicesState(if (paired.isEmpty()) "Scanning for Bluetooth devices..." else "Searching for nearby devices...")

        // 2. Check Bluetooth auto-connect
        val autoConnectEnabled = settingsPreferences.getBoolean("auto_connect", true)
        val lastBtAddress = settingsPreferences.getString("last_bt_address", null)
        val lastBtName = settingsPreferences.getString("last_bt_name", "PC") ?: "PC"

        if (autoConnectEnabled && !hasAutoConnected && lastBtAddress != null) {
            if (paired.any { it.address == lastBtAddress }) {
                hasAutoConnected = true
                viewModelScope.launch {
                    updateStatus("Auto-connecting to $lastBtName...")
                    delay(500)
                    connectBluetooth(lastBtAddress, lastBtName)
                }
                return
            }
        }

        // 3. Register receiver and start Bluetooth discovery for unpaired PCs
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            getApplication<Application>().registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true

            btAdapter?.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Bluetooth discovery: ${e.message}")
        }
    }

    fun addManualDevice(name: String, address: String) {
        appPreferences.edit { putString(name, address) }
        deviceMap[address] = name
        updateDevicesState("Added $name")
    }

    fun removeManualDevice(name: String) {
        appPreferences.edit { remove(name) }
        val toRemove = deviceMap.entries.firstOrNull { it.value == name }?.key
        if (toRemove != null) {
            deviceMap.remove(toRemove)
        }
        updateDevicesState("Removed $name")
    }

    @SuppressLint("MissingPermission")
    fun onDeviceClick(context: Context, name: String, address: String) {
        if (mode == Connection.Mode.WIFI) {
            connectWifi(address, name)
        } else {
            val btAdapter = BluetoothAdapterWrapper.getInstance()
            val device = btAdapter?.adapter?.getRemoteDevice(address)
            if (device != null && device.bondState == BluetoothDevice.BOND_NONE) {
                // Device not bonded yet, initiate bonding
                pendingBondAddress = address
                updateStatus("Pairing with $name...")
                device.createBond()
            } else {
                connectBluetooth(address, name)
            }
        }
    }

    private fun connectWifi(address: String, name: String) {
        settingsPreferences.edit {
            putString("last_wifi_ip", address)
            putString("last_wifi_name", name)
        }
        val context = getApplication<Application>().applicationContext
        val details = getDeviceDetails(context, Connection.Mode.WIFI)
        connectionManager.connectWIFI(address, 6969, details)
    }

    private fun connectBluetooth(address: String, name: String) {
        settingsPreferences.edit {
            putString("last_bt_address", address)
            putString("last_bt_name", name)
        }
        connectionManager.connectBluetooth(address)
    }

    private fun updateDevicesState(statusMessage: String? = null) {
        val list = deviceMap.map { (address, name) -> Pair(name, address) }
        _state.value = State(
            devices = list,
            status = statusMessage ?: _state.value.status
        )
    }

    private fun updateStatus(statusMessage: String) {
        _state.value = _state.value.copy(status = statusMessage)
    }

    fun refresh() {
        initializeList()
    }

    override fun onCleared() {
        super.onCleared()
        networkDiscovery?.stopDiscovery()
        networkDiscovery = null

        if (isReceiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }

        BluetoothAdapterWrapper.getInstance()?.cancelDiscovery()
    }
}
