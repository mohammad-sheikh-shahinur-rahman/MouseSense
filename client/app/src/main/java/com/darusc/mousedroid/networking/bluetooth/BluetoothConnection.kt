package com.darusc.mousedroid.networking.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.Connection
import com.darusc.mousedroid.networking.toHIDReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@SuppressLint("MissingPermission") // For BLUETOOTH_CONNECT permission
class BluetoothConnection(
    context: Context,
    private val listener: Listener
) : Connection() {

    private var isClosing = false
    private var connectionEstablished = false

    private val bluetoothAdapterWrapper = BluetoothAdapterWrapper.getInstance()
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothAdapterWrapper?.adapter

    /**
     * The device that sends the reports
     */
    private var bluetoothHIDDevice: BluetoothHidDevice? = null

    /**
     * The device reports will be sent to
     */
    private var bluetoothHostDevice: BluetoothDevice? = null

    /**
     * SDP settings used for registering the app with the bluetooth HID device
     */
    private val sdp = BluetoothHidDeviceAppSdpSettings(
        "MouseSense", "Android HID", "MouseSense",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        HID_REPORT_DESC
    )

    /**
     * QOS settings used for registering the app with the bluetooth HID device
     */
    private val qos = BluetoothHidDeviceAppQosSettings(
        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
        800, 9, 0, 11250, -1
    )

    private val callback: BluetoothHidDevice.Callback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)

            val hostname = bluetoothHostDevice?.name ?: "Unknown"
            bluetoothHostDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null

            when (state) {
                BluetoothProfile.STATE_CONNECTING -> Log.d("MouseSense", "Connecting...")

                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("MouseSense", "Connected!")
                    connectionEstablished = true
                    listener.onConnected(Mode.BLUETOOTH, bluetoothHostDevice?.name ?: "Unknown device")

                    // Send a battery report after connecting
//                    CoroutineScope(Dispatchers.IO).launch {
//                        delay(3000)
//                        if (bluetoothHostDevice != null) {
//                            val level = BatteryMonitor.getBatteryLevel(context)
//                            send(InputEvent.BatteryEvent(level))
//                        }
//                    }
                }

                BluetoothProfile.STATE_DISCONNECTING -> Log.d("MouseSense", "Disconnecting...")

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (isClosing) {
                        cleanupProxy()
                    } else {
                        if (connectionEstablished) {
                            // Host turned off or went out of range after the connection
                            // was established. Instant disconnect
                            Log.d("MouseSense", "Active session lost. Disconnecting instantly.")
                            listener.onDisconnected(Mode.BLUETOOTH, hostname)
                            connectionEstablished = false
                        } else {
                            // Connection failed
                            Log.d("MouseSense", "Connection to $hostname failed")
                            listener.onConnectionFailed(Mode.BLUETOOTH)
                        }
                    }
                }
            }
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)

            if (registered) {
                // HID service ready
                Log.d("MouseSense", "App registered successfully.")
            } else {
                Log.e("MouseSense", "Failed to register app. Check permissions or device compatibility.")
            }
        }
    }

    /**
     * Non blocking queue of reports to be sent over the bluetooth connection
     */
    private val reportChannel = Channel<Array<HIDReport>>(Channel.UNLIMITED)
    private val sendReportJob = CoroutineScope(Dispatchers.IO).launch {
        for (reports in reportChannel) {
            for (report in reports) {
                bluetoothHIDDevice?.sendReport(bluetoothHostDevice, report.id, report.bytes)
                if (report is KeyboardReport) {
                    delay(15)
                }
            }
        }
    }

    init {
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHIDDevice = proxy as BluetoothHidDevice
                    // Unregister first to clear "ghost states" caused by
                    // incorrect closing
                    bluetoothHIDDevice?.unregisterApp()
                    bluetoothHIDDevice?.registerApp(
                        sdp,
                        null,
                        null,
                        Executors.newSingleThreadExecutor(),
                        callback
                    )
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                bluetoothHIDDevice = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    override fun send(event: InputEvent) {
        val reports = event.toHIDReport()
        reportChannel.trySend(reports)
    }

    override fun close() {
        if (isClosing) {
            return
        }
        isClosing = true

        Log.d("MouseSense", "Cleaning up Bluetooth connection...")

        sendReportJob.cancel()
        reportChannel.close()

        if (bluetoothHostDevice != null && bluetoothHIDDevice != null) {
            val disconnected = bluetoothHIDDevice?.disconnect(bluetoothHostDevice)
            if (disconnected == false) {
                cleanupProxy()
            }
        } else {
            cleanupProxy()
        }
    }

    /**
     * Connect to a bluetooth device
     */
    fun connect(hostMacAddress: String) {
        CoroutineScope(Dispatchers.Main).launch {
            // Wait up to 6 seconds for the Bluetooth HID service to initialize and register
            var attempts = 0
            while (bluetoothHIDDevice == null && attempts < 24 && !isClosing) {
                delay(250)
                attempts++
            }

            if (isClosing) return@launch

            if (bluetoothHIDDevice == null) {
                Log.e("MouseSense", "Bluetooth HID device not ready")
                listener.onConnectionFailed(Mode.BLUETOOTH)
                return@launch
            }

            if (bluetoothHostDevice == null) {
                val target = bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == hostMacAddress }
                if (target != null) {
                    val result = bluetoothHIDDevice?.connect(target)
                    if (result == false) {
                        Log.d("MouseSense", "HID connect returned false")
                        listener.onConnectionFailed(Mode.BLUETOOTH)
                    }
                } else {
                    Log.d("MouseSense", "Target device not found among bonded devices")
                    listener.onConnectionFailed(Mode.BLUETOOTH)
                }
            }
        }
    }

    /**
     * Unregisters the HID app and closes the HID_DEVICE profile proxy
     */
    private fun cleanupProxy() {
        try {
            bluetoothHIDDevice?.unregisterApp()
            if (bluetoothHIDDevice != null) {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHIDDevice)
            }
        } catch (_: Exception) {
        }
        bluetoothHIDDevice = null
    }
}