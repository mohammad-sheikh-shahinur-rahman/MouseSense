package com.darusc.mousedroid.networking

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class NetworkDiscovery(private val context: Context) {

    private val TAG = "MouseSenseDiscovery"
    private val DISCOVERY_PORT = 6969
    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    interface DiscoveryListener {
        fun onServerFound(name: String, ipAddress: String, port: Int)
    }

    private var listener: DiscoveryListener? = null

    fun setListener(listener: DiscoveryListener?) {
        this.listener = listener
    }

    fun startDiscovery() {
        stopDiscovery()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        try {
            multicastLock = wifiManager?.createMulticastLock("MouseSenseDiscovery")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }

        discoveryJob = CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 1000
                }

                // Send discovery broadcasts periodically
                val senderJob = launch {
                    while (isActive) {
                        try {
                            sendDiscoveryBroadcast(socket)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending broadcast: ${e.message}")
                        }
                        delay(2000)
                    }
                }

                // Listen for responses / beacons
                val buffer = ByteArray(512)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length).trim()
                        val senderIp = packet.address.hostAddress ?: ""

                        if (text.startsWith("MOUSESENSE_SERVER|") || text.startsWith("MOUSESENSE_BEACON|")) {
                            val parts = text.split("|")
                            val name = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "PC ($senderIp)"
                            val port = if (parts.size > 2) parts[2].toIntOrNull() ?: DISCOVERY_PORT else DISCOVERY_PORT

                            withContext(Dispatchers.Main) {
                                listener?.onServerFound(name, senderIp, port)
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Expected on timeout, continue loop
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error receiving packet: ${e.message}")
                        }
                    }
                }

                senderJob.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Discovery socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun sendDiscoveryBroadcast(socket: DatagramSocket) {
        val payload = "MOUSESENSE_DISCOVER".toByteArray()

        // 1. Send to global broadcast 255.255.255.255
        try {
            val globalBroadcast = InetAddress.getByName("255.255.255.255")
            socket.send(DatagramPacket(payload, payload.size, globalBroadcast, DISCOVERY_PORT))
        } catch (_: Exception) {}

        // 2. Send to subnet broadcast address
        try {
            val subnetBroadcast = getSubnetBroadcastAddress()
            if (subnetBroadcast != null) {
                socket.send(DatagramPacket(payload, payload.size, subnetBroadcast, DISCOVERY_PORT))
            }
        } catch (_: Exception) {}
    }

    private fun getSubnetBroadcastAddress(): InetAddress? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val dhcp: DhcpInfo = wifiManager.dhcpInfo ?: return null

        val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
        val quads = ByteArray(4)
        for (k in 0..3) {
            quads[k] = ((broadcast shr k * 8) and 0xFF).toByte()
        }
        return try {
            InetAddress.getByAddress(quads)
        } catch (_: Exception) {
            null
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
