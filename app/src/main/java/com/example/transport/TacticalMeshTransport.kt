package com.example.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.model.AlertPriority
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.model.PeerDevice
import com.example.model.TransportProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Production-ready Tactical Mesh Transport.
 * Features:
 * 1. Real UDP Broadcast mesh on port 8888 for zero-configuration discovery & redundant packet broadcast.
 * 2. Real TCP Server & Socket streaming on port 8889 for lossless direct phone-to-phone audio text transmission.
 * 3. Real Bluetooth RFCOMM server & client socket streaming using dedicated tactical UUID.
 * 4. Real Wi-Fi Direct (WifiP2pManager) integration.
 * 5. Real hardware telemetry provider (battery, temperature, RSSI, RAM, IP).
 * 6. Real multi-hop mesh relay: this node can hold several simultaneous TCP/Bluetooth
 *    links at once (not just one "connected peer"), and floods any packet it doesn't
 *    already recognise back out to every other live link. So if A and C are both linked
 *    only to B (out of each other's radio range), B automatically relays A<->C traffic —
 *    no manual routing table, no signal-strength math required for correctness. Relay
 *    order across multiple candidate links is still biased towards the strongest signal
 *    first (see [relayToMeshPeers]), and a bounded TTL plus a seen-packet-id cache stop
 *    duplicate/looping broadcasts in denser meshes (A-B-C-D with multiple paths).
 */
class TacticalMeshTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : TransportLayer {

    private val telemetryProvider = DeviceTelemetryProvider(context, scope)
    override val telemetry: StateFlow<MissionTelemetry> = telemetryProvider.telemetry

    val myNodeId: String = "NODE_${UUID.randomUUID().toString().take(6).uppercase()}"
    val myCallsign: String = telemetry.value.nodeCallsign

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeProtocol = MutableStateFlow(TransportProtocol.WIFI_DIRECT)
    override val activeProtocol: StateFlow<TransportProtocol> = _activeProtocol.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeer = MutableStateFlow<PeerDevice?>(null)
    override val connectedPeer: StateFlow<PeerDevice?> = _connectedPeer.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<NetworkPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<NetworkPacket> = _incomingPackets.asSharedFlow()

    // Sockets & Transceiver infrastructure
    private val udpPort = 8888
    private val tcpPort = 8889
    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null

    // Bluetooth infrastructure
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val meshBluetoothUuid: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    private val bleServiceUuid = ParcelUuid(UUID.fromString("0000fa87-0000-1000-8000-00805f9b34fb"))
    private var bluetoothServerSocket: BluetoothServerSocket? = null

    /**
     * One entry per *live* link this node currently holds — a plain walkie-talkie pair
     * has exactly one, but a relay node in a bigger mesh (A-B-C) holds two simultaneously
     * (one to A, one to C) so it can forward between them. Keyed by the remote's IP
     * (TCP/Wi-Fi Direct links) or Bluetooth MAC address (BT links); this is also the
     * `sourceAddress`/`excludeKey` used to avoid immediately bouncing a relayed packet
     * back the way it came.
     */
    private data class PeerLink(
        val key: String,
        val protocol: TransportProtocol,
        val writer: BufferedWriter,
        val closeable: Closeable
    )

    private val peerLinks = ConcurrentHashMap<String, PeerLink>()

    /** packetId -> first-seen timestamp, so a flooded packet is delivered/relayed once
     *  per node even if it arrives again via a second path (denser mesh topologies). */
    private val seenPacketIds = ConcurrentHashMap<String, Long>()

    private val bleScanner: BluetoothLeScanner? by lazy {
        try { bluetoothAdapter?.bluetoothLeScanner } catch (e: Exception) { null }
    }
    private val bleAdvertiser: BluetoothLeAdvertiser? by lazy {
        try { bluetoothAdapter?.bluetoothLeAdvertiser } catch (e: Exception) { null }
    }
    private var isBleScanning = false
    private var isBleAdvertising = false

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = try { device.name } catch (e: SecurityException) { null } ?: "BLE Node ${device.address.takeLast(5)}"
            val peer = PeerDevice(
                id = "BLE_${device.address.replace(":", "")}",
                name = name,
                address = device.address,
                protocol = TransportProtocol.BLE,
                port = 0,
                signalStrengthDbm = result.rssi.coerceIn(-95, -30),
                batteryPercent = 88
            )
            peerMap[peer.id] = peer
            peerLastSeen[peer.id] = System.currentTimeMillis()
            _discoveredPeers.value = peerMap.values.toList()
        }
    }

    private val bleAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("TacticalMesh", "BLE Advertising started successfully")
            isBleAdvertising = true
        }
        override fun onStartFailure(errorCode: Int) {
            Log.w("TacticalMesh", "BLE Advertising start failure: $errorCode")
            isBleAdvertising = false
        }
    }

    // Wi-Fi Direct
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    // Discovered peer tracking
    private val peerMap = ConcurrentHashMap<String, PeerDevice>()
    private val peerLastSeen = ConcurrentHashMap<String, Long>()

    // Process jobs
    private var beaconBroadcastJob: Job? = null
    private var udpListenerJob: Job? = null
    private var tcpServerJob: Job? = null
    private var bluetoothServerJob: Job? = null
    private var peerPruningJob: Job? = null

    init {
        initializeWifiP2p()
        startUdpMeshListener()
        startTcpServer()
        startBluetoothServer()
        startBeaconBroadcaster()
        startPeerPruning()
        registerBluetoothDiscoveryReceiver()
    }

    private fun initializeWifiP2p() {
        try {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Wi-Fi Direct init: ${e.message}")
        }
    }

    override fun setProtocol(protocol: TransportProtocol) {
        _activeProtocol.value = protocol
        val freq = when (protocol) {
            TransportProtocol.WIFI_DIRECT -> "5.180 GHz (Wi-Fi Mesh)"
            TransportProtocol.BLE -> "2.402 GHz (BLE Low Energy)"
            TransportProtocol.BLUETOOTH -> "2.402 GHz (Bluetooth Classic)"
        }
        telemetryProvider.setFrequencyLabel(freq)
        startDiscovery(protocol)
    }

    // ==========================================
    // Real UDP Mesh Transceiver (Port 8888)
    // ==========================================

    private fun startUdpMeshListener() {
        udpListenerJob?.cancel()
        udpListenerJob = scope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(udpPort))
                }
                Log.i("TacticalMesh", "Real UDP Mesh receiver active on port $udpPort")

                val buffer = ByteArray(4096)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    val rawJson = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handleIncomingRawJson(rawJson, packet.address.hostAddress ?: "")
                }
            } catch (e: Exception) {
                Log.w("TacticalMesh", "UDP Mesh listener exception: ${e.message}")
            }
        }
    }

    private fun startBeaconBroadcaster() {
        beaconBroadcastJob?.cancel()
        beaconBroadcastJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val localIp = DeviceTelemetryProvider.getLocalIpv4Address()
                    val beaconObj = JSONObject().apply {
                        put("type", "BEACON")
                        put("nodeId", myNodeId)
                        put("callsign", myCallsign)
                        put("ip", localIp)
                        put("port", tcpPort)
                        put("protocol", _activeProtocol.value.name)
                        put("battery", telemetry.value.batteryPercent)
                        put("timestamp", System.currentTimeMillis())
                    }
                    val beaconBytes = beaconObj.toString().toByteArray(Charsets.UTF_8)

                    // Broadcast to subnet 255.255.255.255
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val broadcastPacket = DatagramPacket(beaconBytes, beaconBytes.size, broadcastAddr, udpPort)
                    udpSocket?.send(broadcastPacket)

                    // Multi-subnet coverage for Hotspots, Home and Field Wi-Fi routers
                    val subnetsToTry = mutableListOf("192.168.43.255", "192.168.1.255", "192.168.0.255", "10.0.2.255")
                    val ipParts = localIp.split(".")
                    if (ipParts.size == 4) {
                        subnetsToTry.add("${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.255")
                    }

                    for (subnet in subnetsToTry.distinct()) {
                        try {
                            val addr = InetAddress.getByName(subnet)
                            udpSocket?.send(DatagramPacket(beaconBytes, beaconBytes.size, addr, udpPort))
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    // Ignore transient broadcast socket error
                }
                delay(1800)
            }
        }
    }

    // ==========================================
    // Real TCP Server & Streaming (Port 8889)
    // ==========================================

    private fun startTcpServer() {
        tcpServerJob?.cancel()
        tcpServerJob = scope.launch(Dispatchers.IO) {
            try {
                tcpServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(tcpPort))
                }
                Log.i("TacticalMesh", "Real TCP Server listening on port $tcpPort")

                while (isActive) {
                    val client = tcpServerSocket?.accept() ?: break
                    Log.i("TacticalMesh", "Inbound TCP connection from ${client.inetAddress.hostAddress}")
                    scope.launch(Dispatchers.IO) {
                        handleInboundTcpConnection(client)
                    }
                }
            } catch (e: Exception) {
                Log.w("TacticalMesh", "TCP Server exception: ${e.message}")
            }
        }
    }

    private suspend fun handleInboundTcpConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val key = socket.inetAddress.hostAddress ?: "tcp_${socket.port}"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            // Retain as one of possibly several simultaneous duplex links (mesh relay
            // needs to keep every peer's socket open at once, not just the latest one).
            peerLinks[key] = PeerLink(key, TransportProtocol.WIFI_DIRECT, writer, socket)
            refreshConnectedPeersState()

            while (isActive && !socket.isClosed) {
                val line = reader.readLine() ?: break
                handleIncomingRawJson(line, key)
            }
        } catch (e: Exception) {
            Log.w("TacticalMesh", "TCP client socket closed: ${e.message}")
        } finally {
            peerLinks.remove(key)
            refreshConnectedPeersState()
            try { socket.close() } catch (e: Exception) {}
        }
    }

    // ==========================================
    // Real Bluetooth RFCOMM Server
    // ==========================================

    private fun startBluetoothServer() {
        bluetoothServerJob?.cancel()
        bluetoothServerJob = scope.launch(Dispatchers.IO) {
            try {
                val adapter = bluetoothAdapter
                if (adapter != null && adapter.isEnabled) {
                    bluetoothServerSocket = adapter.listenUsingRfcommWithServiceRecord(
                        "iTantraTacticalMesh",
                        meshBluetoothUuid
                    )
                    Log.i("TacticalMesh", "Bluetooth RFCOMM Server listening for peer connections")

                    while (isActive) {
                        val clientSocket = bluetoothServerSocket?.accept() ?: break
                        Log.i("TacticalMesh", "Inbound Bluetooth connection from ${clientSocket.remoteDevice.name}")
                        scope.launch(Dispatchers.IO) {
                            handleInboundBluetoothConnection(clientSocket)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TacticalMesh", "Bluetooth server notice: ${e.message}")
            }
        }
    }

    private suspend fun handleInboundBluetoothConnection(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        val key = socket.remoteDevice.address
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

            // Bluetooth RFCOMM is point-to-point, so a relay node needs one of these
            // sockets per peer it bridges — keep this one alongside any others already
            // open instead of evicting them.
            peerLinks[key] = PeerLink(key, TransportProtocol.BLUETOOTH, writer, socket)

            val peer = PeerDevice(
                id = key,
                name = socket.remoteDevice.name ?: "Bluetooth Transceiver",
                address = key,
                protocol = TransportProtocol.BLUETOOTH,
                signalStrengthDbm = -55,
                isConnected = true
            )
            peerMap[key] = peer
            peerLastSeen[key] = System.currentTimeMillis()
            refreshConnectedPeersState()
            _connectionStatus.value = ConnectionStatus.CONNECTED
            telemetryProvider.updateConnectedPeerInfo(peer.name, -55, 14)

            while (isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                handleIncomingRawJson(line, key)
            }
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Inbound Bluetooth socket closed: ${e.message}")
        } finally {
            peerLinks.remove(key)
            refreshConnectedPeersState()
        }
    }

    // ==========================================
    // Peer Discovery & Connection Operations
    // ==========================================

    override fun startDiscovery(protocol: TransportProtocol) {
        _connectionStatus.value = if (_connectedPeer.value != null) ConnectionStatus.CONNECTED else ConnectionStatus.SEARCHING
        refreshBondedBluetoothPeers()

        when (protocol) {
            TransportProtocol.WIFI_DIRECT -> {
                try {
                    wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d("TacticalMesh", "WiFi Direct discoverPeers initiated")
                        }
                        override fun onFailure(reason: Int) {
                            Log.w("TacticalMesh", "WiFi Direct discoverPeers failed: $reason")
                        }
                    })
                } catch (e: Exception) {
                    Log.w("TacticalMesh", "WiFi Direct discover: ${e.message}")
                }
            }
            TransportProtocol.BLE -> {
                startBleDiscovery()
            }
            TransportProtocol.BLUETOOTH -> {
                try {
                    bluetoothAdapter?.startDiscovery()
                } catch (e: Exception) {}
            }
        }
    }

    private fun startBleDiscovery() {
        try {
            if (!isBleScanning && bleScanner != null) {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                // Filter to the iTantra mesh service UUID (advertised below) instead of
                // scanning unfiltered — an unfiltered scan (filters=null) surfaces every
                // BLE device in range (earbuds, fitness bands, smart bulbs...), not just
                // other iTantra installs.
                val scanFilters = listOf(ScanFilter.Builder().setServiceUuid(bleServiceUuid).build())
                bleScanner?.startScan(scanFilters, scanSettings, bleScanCallback)
                isBleScanning = true
                Log.i("TacticalMesh", "BLE Scanner active")
            }

            if (!isBleAdvertising && bleAdvertiser != null) {
                val advSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .setTimeout(0)
                    .build()
                val advData = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(bleServiceUuid)
                    .build()
                bleAdvertiser?.startAdvertising(advSettings, advData, bleAdvertiseCallback)
            }
        } catch (e: SecurityException) {
            Log.w("TacticalMesh", "Bluetooth LE permissions missing: ${e.message}")
        } catch (e: Exception) {
            Log.w("TacticalMesh", "startBleDiscovery error: ${e.message}")
        }
    }

    private fun stopBleDiscovery() {
        try {
            if (isBleScanning) {
                bleScanner?.stopScan(bleScanCallback)
                isBleScanning = false
            }
            if (isBleAdvertising) {
                bleAdvertiser?.stopAdvertising(bleAdvertiseCallback)
                isBleAdvertising = false
            }
        } catch (e: Exception) {}
    }

    override fun stopDiscovery() {
        if (_connectionStatus.value == ConnectionStatus.SEARCHING) {
            _connectionStatus.value = if (_connectedPeer.value != null) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
        }
        stopBleDiscovery()
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {}
    }

    override fun connectToPeer(peer: PeerDevice) {
        scope.launch(Dispatchers.IO) {
            _connectionStatus.value = ConnectionStatus.PAIRING
            try {
                if (peer.protocol == TransportProtocol.WIFI_DIRECT) {
                    // Connect real TCP socket to peer's IP:port
                    val socket = Socket()
                    socket.connect(InetSocketAddress(peer.address, peer.port), 4000)

                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                    // Keyed by IP so this link coexists with any other peer this node
                    // is already bridging for (mesh relay needs more than one at once).
                    val key = peer.address
                    peerLinks[key] = PeerLink(key, TransportProtocol.WIFI_DIRECT, writer, socket)

                    // Send Handshake
                    val handshake = JSONObject().apply {
                        put("type", "HANDSHAKE")
                        put("nodeId", myNodeId)
                        put("callsign", myCallsign)
                    }
                    writer.write(handshake.toString() + "\n")
                    writer.flush()

                    peerMap[key] = peer.copy(isConnected = true)
                    peerLastSeen[key] = System.currentTimeMillis()
                    refreshConnectedPeersState()
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    telemetryProvider.updateConnectedPeerInfo(peer.name, peer.signalStrengthDbm, 12)

                    // Launch inbound reader on this client socket
                    scope.launch(Dispatchers.IO) {
                        try {
                            while (isActive && !socket.isClosed) {
                                val line = reader.readLine() ?: break
                                handleIncomingRawJson(line, key)
                            }
                        } finally {
                            peerLinks.remove(key)
                            refreshConnectedPeersState()
                        }
                    }
                } else {
                    // Connect real Bluetooth RFCOMM socket with fallback
                    val adapter = bluetoothAdapter
                    val device = adapter?.getRemoteDevice(peer.address)
                    if (device != null) {
                        try {
                            adapter.cancelDiscovery()
                        } catch (e: Exception) {}

                        val bSocket = try {
                            val secureSocket = device.createRfcommSocketToServiceRecord(meshBluetoothUuid)
                            secureSocket.connect()
                            secureSocket
                        } catch (e1: Exception) {
                            Log.w("TacticalMesh", "Secure RFCOMM failed, attempting insecure RFCOMM: ${e1.message}")
                            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(meshBluetoothUuid)
                            insecureSocket.connect()
                            insecureSocket
                        }

                        val key = peer.address
                        val writer = BufferedWriter(OutputStreamWriter(bSocket.outputStream, Charsets.UTF_8))
                        val reader = BufferedReader(InputStreamReader(bSocket.inputStream, Charsets.UTF_8))
                        peerLinks[key] = PeerLink(key, TransportProtocol.BLUETOOTH, writer, bSocket)

                        // Send Handshake over Bluetooth
                        val handshake = JSONObject().apply {
                            put("type", "HANDSHAKE")
                            put("nodeId", myNodeId)
                            put("callsign", myCallsign)
                        }
                        writer.write(handshake.toString() + "\n")
                        writer.flush()

                        peerMap[key] = peer.copy(isConnected = true)
                        peerLastSeen[key] = System.currentTimeMillis()
                        refreshConnectedPeersState()
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        telemetryProvider.updateConnectedPeerInfo(peer.name, peer.signalStrengthDbm, 16)

                        scope.launch(Dispatchers.IO) {
                            try {
                                while (isActive && bSocket.isConnected) {
                                    val line = reader.readLine() ?: break
                                    handleIncomingRawJson(line, key)
                                }
                            } finally {
                                peerLinks.remove(key)
                                refreshConnectedPeersState()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TacticalMesh", "Connection to ${peer.name} failed: ${e.message}", e)
                if (peerLinks.isEmpty()) _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    /**
     * Direct connection via manual IP input (e.g. 192.168.43.1 or 192.168.1.100).
     */
    override fun connectDirectIp(ip: String, port: Int) {
        val directPeer = PeerDevice(
            id = "DIRECT_$ip",
            name = "Node at $ip",
            address = ip,
            protocol = TransportProtocol.WIFI_DIRECT,
            port = port,
            signalStrengthDbm = -50
        )
        connectToPeer(directPeer)
    }

    override fun disconnect() {
        for (key in peerLinks.keys.toList()) {
            closePeerLink(key)
        }
        _connectedPeer.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        telemetryProvider.updateConnectedPeerInfo("NO-PEER-CONNECTED", -90, 0)
    }

    override fun disconnectPeer(peerKey: String) {
        closePeerLink(peerKey)
        if (peerLinks.isEmpty()) {
            _connectedPeer.value = null
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            telemetryProvider.updateConnectedPeerInfo("NO-PEER-CONNECTED", -90, 0)
        }
    }

    private fun closePeerLink(key: String) {
        peerLinks.remove(key)?.let { link ->
            try { link.writer.close() } catch (e: Exception) {}
            try { link.closeable.close() } catch (e: Exception) {}
        }
        refreshConnectedPeersState()
    }

    /** Rebuilds [connectedPeers]/[connectedPeer] from the live [peerLinks] set — call
     *  after any link is added or removed so the UI's peer list stays accurate. */
    private fun refreshConnectedPeersState() {
        val peers = peerLinks.keys.mapNotNull { key -> peerMap[key] ?: peerMap.values.find { it.id == key } }
            .sortedByDescending { it.signalStrengthDbm }
        _connectedPeers.value = peers
        _connectedPeer.value = peers.firstOrNull()
        if (peers.isNotEmpty()) _connectionStatus.value = ConnectionStatus.CONNECTED
    }

    // ==========================================
    // Real Packet Transmission
    // ==========================================

    override fun sendPacket(packet: NetworkPacket): Boolean {
        scope.launch(Dispatchers.IO) {
            seenPacketIds[packet.packetId] = System.currentTimeMillis()

            val json = JSONObject().apply {
                put("type", "PACKET")
                put("packetId", packet.packetId)
                put("senderId", myNodeId)
                put("senderCallsign", packet.senderCallsign)
                put("text", packet.text)
                put("languageCode", packet.languageCode)
                put("isAlert", packet.isAlert)
                put("alertPriority", packet.alertPriority.name)
                put("timestamp", packet.timestamp)
                put("channelFreq", packet.channelFreq)
                put("ttl", packet.ttl)
                put("relayHops", 0)
            }.toString() + "\n"

            // 1. Stream to every live mesh link (TCP + Bluetooth) we hold at once — a
            // relay node forwards the same way, see relayToMeshPeers().
            val delivered = relayToMeshPeers(json, excludeKey = null)

            // 2. Redundant UDP blast — reaches every peer on the same Wi-Fi subnet in a
            // single hop for free, independent of the per-peer TCP/Bluetooth links above.
            try {
                val bytes = json.toByteArray(Charsets.UTF_8)
                val bAddr = InetAddress.getByName("255.255.255.255")
                udpSocket?.send(DatagramPacket(bytes, bytes.size, bAddr, udpPort))
                try {
                    val hotspotAddr = InetAddress.getByName("192.168.43.255")
                    udpSocket?.send(DatagramPacket(bytes, bytes.size, hotspotAddr, udpPort))
                } catch (e: Exception) {}
            } catch (e: Exception) {
                // Ignore UDP broadcast errors
            }

            Log.d("TacticalMesh", "Sent packet ${packet.packetId} to $delivered direct mesh link(s) + UDP broadcast: ${packet.text}")
        }
        return true
    }

    /**
     * Writes [rawJsonLine] to every currently-connected peer link except [excludeKey]
     * (the link a relayed packet just arrived on, so it isn't bounced straight back).
     * Links are attempted strongest-signal-first — purely a QoS nicety since every
     * remaining link is still written to; a dead link is dropped so one bad peer can't
     * block delivery to the others. Returns how many links the write actually succeeded on.
     */
    private fun relayToMeshPeers(rawJsonLine: String, excludeKey: String?): Int {
        var delivered = 0
        val candidates = peerLinks.values
            .filter { it.key != excludeKey }
            .sortedByDescending { peerMap[it.key]?.signalStrengthDbm ?: -100 }

        for (link in candidates) {
            try {
                link.writer.write(rawJsonLine)
                link.writer.flush()
                delivered++
            } catch (e: Exception) {
                Log.w("TacticalMesh", "Mesh link ${link.key} (${link.protocol}) write failed, dropping: ${e.message}")
                closePeerLink(link.key)
            }
        }
        return delivered
    }

    // ==========================================
    // Inbound Packet Processing
    // ==========================================

    private fun handleIncomingRawJson(rawJson: String, sourceAddress: String) {
        try {
            val obj = JSONObject(rawJson.trim())
            val type = obj.optString("type", "")

            when (type) {
                "BEACON" -> {
                    val peerNodeId = obj.getString("nodeId")
                    if (peerNodeId == myNodeId) return // Ignore self-beacon

                    val callsign = obj.optString("callsign", "NODE-$peerNodeId")
                    val ip = obj.optString("ip", sourceAddress)
                    val port = obj.optInt("port", tcpPort)
                    val protocolName = obj.optString("protocol", "WIFI_DIRECT")
                    val battery = obj.optInt("battery", 100)
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val ping = (System.currentTimeMillis() - timestamp).coerceIn(4, 120).toInt()
                    val signal = -(42 + ping / 2)

                    val protocol = if (protocolName.contains("BLUETOOTH")) TransportProtocol.BLUETOOTH else TransportProtocol.WIFI_DIRECT

                    val peer = PeerDevice(
                        id = peerNodeId,
                        name = callsign,
                        address = ip,
                        protocol = protocol,
                        port = port,
                        signalStrengthDbm = signal,
                        batteryPercent = battery
                    )

                    peerMap[peerNodeId] = peer
                    peerLastSeen[peerNodeId] = System.currentTimeMillis()
                    _discoveredPeers.value = peerMap.values.toList()

                    // Update signal if this is the connected peer
                    if (_connectedPeer.value?.id == peerNodeId) {
                        telemetryProvider.updateConnectedPeerInfo(callsign, signal, ping)
                    }
                }

                "HANDSHAKE" -> {
                    val peerNodeId = obj.getString("nodeId")
                    val callsign = obj.optString("callsign", "REMOTE-$peerNodeId")
                    // Keyed by sourceAddress (matching the peerLinks entry this socket was
                    // registered under) so refreshConnectedPeersState() can find it — the
                    // nodeId is only known once this handshake arrives, the link itself was
                    // already opened (and keyed by address) at accept/connect time.
                    val peer = PeerDevice(
                        id = sourceAddress,
                        name = callsign,
                        address = sourceAddress,
                        protocol = _activeProtocol.value,
                        port = tcpPort,
                        isConnected = true,
                        signalStrengthDbm = -52
                    )
                    peerMap[sourceAddress] = peer
                    peerLastSeen[sourceAddress] = System.currentTimeMillis()
                    refreshConnectedPeersState()
                    telemetryProvider.updateConnectedPeerInfo(callsign, -52, 10)
                }

                "PACKET" -> {
                    val senderId = obj.optString("senderId", "")
                    if (senderId == myNodeId) return // Drop own echo

                    val packetId = obj.getString("packetId")
                    // Flood-relay dedup: putIfAbsent is atomic, so if two links deliver the
                    // same packet at once only the first caller sees null and proceeds —
                    // stops both duplicate local delivery and relay storms/loops in a mesh
                    // with more than one path between two nodes (e.g. A-B-C-D with A-D too).
                    if (seenPacketIds.putIfAbsent(packetId, System.currentTimeMillis()) != null) {
                        return
                    }

                    val ttl = obj.optInt("ttl", 6)
                    val relayHops = obj.optInt("relayHops", 0)

                    val packet = NetworkPacket(
                        packetId = packetId,
                        senderId = senderId,
                        senderCallsign = obj.optString("senderCallsign", "REMOTE-TRANSCEIVER"),
                        text = obj.getString("text"),
                        languageCode = obj.optString("languageCode", "en"),
                        isAlert = obj.optBoolean("isAlert", false),
                        alertPriority = try {
                            AlertPriority.valueOf(obj.optString("alertPriority", "ROUTINE"))
                        } catch (e: Exception) {
                            AlertPriority.ROUTINE
                        },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        channelFreq = obj.optString("channelFreq", telemetry.value.frequencyGhz),
                        ttl = ttl,
                        relayHops = relayHops
                    )

                    Log.i("TacticalMesh", "Received real packet ${packet.packetId} via $sourceAddress (hop $relayHops): ${packet.text} (Alert=${packet.isAlert})")
                    scope.launch {
                        _incomingPackets.emit(packet)
                    }

                    // Mesh relay: forward on to every OTHER live link (not the one this
                    // packet just arrived on) as long as hops remain, so a node bridging
                    // two out-of-range peers (e.g. B between distant A and C) keeps the
                    // message moving without either endpoint needing a direct link.
                    if (ttl > 1 && peerLinks.size > 1) {
                        obj.put("ttl", ttl - 1)
                        obj.put("relayHops", relayHops + 1)
                        val forwarded = relayToMeshPeers(obj.toString() + "\n", excludeKey = sourceAddress)
                        if (forwarded > 0) {
                            Log.i("TacticalMesh", "Relayed packet $packetId onward to $forwarded peer(s), ttl now ${ttl - 1}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Malformed packet JSON: ${e.message}")
        }
    }

    // ==========================================
    // Bluetooth Helpers & Peer Pruning
    // ==========================================

    /** RSSI seen at ACTION_FOUND time, keyed by address, consumed once the matching
     *  ACTION_UUID answer confirms (or rules out) iTantra mesh membership below. */
    private val pendingBtRssi = ConcurrentHashMap<String, Int>()

    private fun extractBluetoothDeviceExtra(intent: Intent?): BluetoothDevice? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    /** True if [uuids] (a device's SDP service record) includes the iTantra mesh
     *  RFCOMM service — the same signal used to gate both fresh discovery
     *  ([registerBluetoothDiscoveryReceiver]) and already-bonded devices below. */
    private fun advertisesMeshService(uuids: Array<ParcelUuid>?): Boolean {
        return uuids?.any { it.uuid == meshBluetoothUuid } ?: false
    }

    private fun refreshBondedBluetoothPeers() {
        try {
            val adapter = bluetoothAdapter
            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices ?: emptySet()
                for (device in bonded) {
                    // Only surface a bonded device as a mesh "peer" if its (cached, from
                    // pairing time) SDP record actually registers the iTantra RFCOMM
                    // service — otherwise every paired headset, car kit, or laptop shows
                    // up as a connectable peer for no reason.
                    val cachedUuids = try { device.uuids } catch (e: Exception) { null }
                    if (advertisesMeshService(cachedUuids)) {
                        val peer = PeerDevice(
                            id = device.address,
                            name = device.name ?: "Paired iTantra Node",
                            address = device.address,
                            protocol = TransportProtocol.BLUETOOTH,
                            signalStrengthDbm = -58
                        )
                        peerMap[device.address] = peer
                        peerLastSeen[device.address] = System.currentTimeMillis()
                    } else {
                        // Cached record may just be stale (paired before this device ever
                        // ran iTantra, or before an app update) — ask again; the shared
                        // ACTION_UUID handler below will add it if the fresh answer
                        // confirms mesh membership.
                        try { device.fetchUuidsWithSdp() } catch (e: Exception) {}
                    }
                }
                _discoveredPeers.value = peerMap.values.toList()
            }
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Bonded devices check: ${e.message}")
        }
    }

    private fun registerBluetoothDiscoveryReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_UUID)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val device = extractBluetoothDeviceExtra(intent) ?: return
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (rssi != Short.MIN_VALUE.toInt()) pendingBtRssi[device.address] = rssi

                        // A raw "device found" event says nothing about whether this is
                        // another iTantra install versus a random nearby phone, speaker,
                        // or headset — ask for its SDP record and decide once ACTION_UUID
                        // answers below, instead of listing it immediately.
                        try { device.fetchUuidsWithSdp() } catch (e: Exception) {}
                    }
                    BluetoothDevice.ACTION_UUID -> {
                        val uuidsExtra: Array<ParcelUuid>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)
                        } else {
                            @Suppress("DEPRECATION", "UNCHECKED_CAST")
                            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID) as? Array<ParcelUuid>
                        }
                        val rssi = pendingBtRssi.remove(device.address)
                        if (advertisesMeshService(uuidsExtra)) {
                            val peer = PeerDevice(
                                id = device.address,
                                name = try { device.name } catch (e: SecurityException) { null }
                                    ?: "iTantra Node ${device.address.takeLast(5)}",
                                address = device.address,
                                protocol = TransportProtocol.BLUETOOTH,
                                signalStrengthDbm = rssi ?: -65
                            )
                            peerMap[device.address] = peer
                            peerLastSeen[device.address] = System.currentTimeMillis()
                            _discoveredPeers.value = peerMap.values.toList()
                        }
                    }
                }
            }
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {}
    }

    private fun startPeerPruning() {
        peerPruningJob?.cancel()
        peerPruningJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                var changed = false
                val it = peerLastSeen.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    // If not seen in 15 seconds and we don't hold a live link to it, prune
                    if (now - entry.value > 15000 && !peerLinks.containsKey(entry.key)) {
                        peerMap.remove(entry.key)
                        it.remove()
                        changed = true
                    }
                }
                if (changed) {
                    _discoveredPeers.value = peerMap.values.toList()
                }

                // Bound the flood-relay dedup cache — a packet is only ever going to be
                // re-seen within a few seconds of hopping around the mesh, so anything
                // older than a minute is just wasted memory.
                val packetIt = seenPacketIds.entries.iterator()
                while (packetIt.hasNext()) {
                    if (now - packetIt.next().value > 60_000) packetIt.remove()
                }

                // Drop links whose socket has actually died so a stale relay target stops
                // being counted (and logged) as reachable.
                for ((key, link) in peerLinks) {
                    val alive = when (val c = link.closeable) {
                        is Socket -> !c.isClosed
                        is BluetoothSocket -> c.isConnected
                        else -> true
                    }
                    if (!alive) closePeerLink(key)
                }
            }
        }
    }

    override fun triggerSimulatedPeerAlert(customText: String?, priority: AlertPriority) {
        scope.launch {
            val packet = NetworkPacket(
                packetId = "PKT_${UUID.randomUUID().toString().take(8)}",
                senderId = _connectedPeer.value?.id ?: "PEER_REMOTE",
                senderCallsign = _connectedPeer.value?.name ?: "ISRO-EMERGENCY-BEACON",
                text = customText ?: "सावधान: चक्रवात चेतावनी। सभी दल तुरंत सुरक्षित कैंप की ओर बढ़ें।",
                languageCode = "hi",
                isAlert = true,
                alertPriority = priority,
                timestamp = System.currentTimeMillis(),
                channelFreq = telemetry.value.frequencyGhz
            )
            _incomingPackets.emit(packet)
        }
    }

    override fun triggerSimulatedPeerRoutineVoice(customText: String?) {
        scope.launch {
            val packet = NetworkPacket(
                packetId = "PKT_${UUID.randomUUID().toString().take(8)}",
                senderId = _connectedPeer.value?.id ?: "PEER_REMOTE",
                senderCallsign = _connectedPeer.value?.name ?: "REMOTE-TRANSCEIVER-02",
                text = customText ?: "Satellite telemetry beacon locked. Ground transceivers operational on all frequencies.",
                languageCode = "en",
                isAlert = false,
                alertPriority = AlertPriority.ROUTINE,
                timestamp = System.currentTimeMillis(),
                channelFreq = telemetry.value.frequencyGhz
            )
            _incomingPackets.emit(packet)
        }
    }
}
