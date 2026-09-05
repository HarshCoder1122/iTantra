package com.example.transport

import com.example.model.AlertPriority
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.model.PeerDevice
import com.example.model.TransportProtocol
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class NetworkPacket(
    val packetId: String,
    val senderId: String,
    val senderCallsign: String,
    val text: String,
    val languageCode: String,
    val isAlert: Boolean = false,
    val alertPriority: AlertPriority = AlertPriority.ROUTINE,
    val timestamp: Long = System.currentTimeMillis(),
    val channelFreq: String = "5.180 GHz",
    // Mesh flood-relay controls: ttl bounds how many extra hops a packet may still
    // travel (decremented at every relaying node, dropped at 0), relayHops counts how
    // many hops it has already made (0 = heard directly from the sender) so the UI can
    // show "relayed via N hop(s)" instead of pretending every packet was a direct link.
    val ttl: Int = 6,
    val relayHops: Int = 0
)

interface TransportLayer {
    val connectionStatus: StateFlow<ConnectionStatus>
    val activeProtocol: StateFlow<TransportProtocol>
    val discoveredPeers: StateFlow<List<PeerDevice>>
    val connectedPeer: StateFlow<PeerDevice?>
    /** Every peer this node currently holds a live link to (TCP and/or Bluetooth) — a
     *  node with 2+ entries here is actively bridging/relaying traffic between them. */
    val connectedPeers: StateFlow<List<PeerDevice>>
    val incomingPackets: SharedFlow<NetworkPacket>
    val telemetry: StateFlow<MissionTelemetry>

    fun startDiscovery(protocol: TransportProtocol)
    fun stopDiscovery()
    fun connectToPeer(peer: PeerDevice)
    fun connectDirectIp(ip: String, port: Int = 8889)
    fun disconnect()
    /** Drops just this one mesh link, leaving any other connected peers untouched. */
    fun disconnectPeer(peerKey: String)
    fun sendPacket(packet: NetworkPacket): Boolean
    fun setProtocol(protocol: TransportProtocol)
    fun triggerSimulatedPeerAlert(customText: String? = null, priority: AlertPriority = AlertPriority.CRITICAL_DISTRESS)
    fun triggerSimulatedPeerRoutineVoice(customText: String? = null)
}
