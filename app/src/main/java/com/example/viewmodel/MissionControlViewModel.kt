package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AlertAudioManager
import com.example.data.ITantraDatabase
import com.example.data.VoiceMessageEntity
import com.example.data.VoiceMessageRepository
import com.example.model.AlertPriority
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.model.PeerDevice
import com.example.model.RadioChannelState
import com.example.model.SupportedLanguage
import com.example.model.TransportProtocol
import com.example.model.VadStatus
import com.example.stt.IndicSttEngine
import com.example.stt.SttEngine
import com.example.stt.SttModelInfo
import com.example.transport.NetworkPacket
import com.example.transport.TacticalMeshTransport
import com.example.transport.TransportLayer
import com.example.translation.BundledOfflineTranslator
import com.example.tts.IndicTtsEngine
import com.example.tts.TtsEngine
import com.example.tts.TtsModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MissionUiState(
    val selectedLanguage: SupportedLanguage = SupportedLanguage.HINDI,
    val isPttActive: Boolean = true, // true = Push-to-Talk (Walkie-Talkie); false = Phone Mode (Continuous VAD)
    val deviceRole: String = "TRANSCEIVER", // "TRANSCEIVER", "STT_ONLY", "TTS_ONLY"
    val channelState: RadioChannelState = RadioChannelState.STANDBY,
    val currentTranscript: String = "",
    val activeIncomingCaption: String? = null,
    val activeIncomingIsAlert: Boolean = false,
    val forceMaxVolumeAlerts: Boolean = true,
    val isLowPowerListeningEnabled: Boolean = true,
    val showArmDistressDialog: Boolean = false,
    val directIpInput: String = "",
    val themeMode: String = "system" // "system", "light", "dark"
)

class MissionControlViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Audio & Infrastructure Engines
    val alertAudioManager = AlertAudioManager(context)
    // Shared across both engines: each would otherwise construct its own
    // BundledModelManager and independently SHA-256-verify every bundled model on
    // startup — with 9 languages that's ~2GB hashed twice in parallel instead of once.
    private val sharedModelManager = com.example.model.BundledModelManager(context)
    val sttEngine: SttEngine = IndicSttEngine(context, viewModelScope, sharedModelManager)
    val ttsEngine: TtsEngine = IndicTtsEngine(context, alertAudioManager, viewModelScope, sharedModelManager)
    val transportLayer: TransportLayer = TacticalMeshTransport(context, viewModelScope)

    // Room Persistence
    private val database = ITantraDatabase.getInstance(context)
    val repository = VoiceMessageRepository(database.voiceMessageDao())

    val messageLogs: StateFlow<List<VoiceMessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertCount: StateFlow<Int> = repository.alertCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // UI State
    private val _uiState = MutableStateFlow(MissionUiState())
    val uiState: StateFlow<MissionUiState> = _uiState.asStateFlow()

    // Engine State flows exposed to UI
    val isListening: StateFlow<Boolean> = sttEngine.isListening
    val vadStatus: StateFlow<VadStatus> = sttEngine.vadStatus
    val speechProbability: StateFlow<Float> = sttEngine.speechProbability
    val audioLevel: StateFlow<Float> = sttEngine.audioLevel
    val partialTranscript: StateFlow<String> = sttEngine.partialTranscript
    val sttModelInfo: StateFlow<SttModelInfo> = sttEngine.modelInfo

    val isTtsSpeaking: StateFlow<Boolean> = ttsEngine.isSpeaking
    val ttsPlayingText: StateFlow<String?> = ttsEngine.currentlyPlayingText
    val ttsPlayingCaption: StateFlow<String?> = ttsEngine.currentlyPlayingText
    val ttsModelInfo: StateFlow<TtsModelInfo> = ttsEngine.modelInfo

    val bundledModelManager = sharedModelManager
    val languagePacks: StateFlow<Map<String, com.example.model.LanguagePack>> = bundledModelManager.languagePacks
    val isManifestLoaded: StateFlow<Boolean> = bundledModelManager.isManifestLoaded
    val verifiedAssets: StateFlow<Map<String, com.example.model.VerifiedAsset>> = bundledModelManager.verifiedAssets

    /** Forces a real reload of a language's on-device models and reports actual measured load time. */
    fun runModelBenchmark(languageCode: String) {
        viewModelScope.launch {
            (sttEngine as? IndicSttEngine)?.reloadAndBenchmark(SupportedLanguage.fromCode(languageCode))
        }
    }

    val connectionStatus: StateFlow<ConnectionStatus> = transportLayer.connectionStatus
    val activeProtocol: StateFlow<TransportProtocol> = transportLayer.activeProtocol
    val discoveredPeers: StateFlow<List<PeerDevice>> = transportLayer.discoveredPeers
    val connectedPeer: StateFlow<PeerDevice?> = transportLayer.connectedPeer
    val connectedPeers: StateFlow<List<PeerDevice>> = transportLayer.connectedPeers
    val telemetry: StateFlow<MissionTelemetry> = transportLayer.telemetry

    init {
        // Collect partial live transcript from STT Engine in real-time
        viewModelScope.launch {
            sttEngine.partialTranscript.collect { partial ->
                if (partial.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(currentTranscript = partial)
                }
            }
        }

        // Collect finalized voice utterances from STT Engine
        viewModelScope.launch {
            sttEngine.finalizedUtterances.collect { utterance ->
                if (utterance.text.isNotBlank()) {
                    transmitUtterance(
                        text = utterance.text,
                        language = utterance.language,
                        isAlert = false,
                        priority = AlertPriority.ROUTINE
                    )
                }
                // In continuous phone mode, resume listening automatically
                if (!_uiState.value.isPttActive && _uiState.value.deviceRole != "TTS_ONLY") {
                    sttEngine.startListening(_uiState.value.selectedLanguage)
                    _uiState.value = _uiState.value.copy(channelState = RadioChannelState.LISTENING)
                }
            }
        }

        // Collect incoming tactical mesh packets from remote device
        viewModelScope.launch {
            transportLayer.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    // ==========================================
    // Role & Mode Configuration
    // ==========================================

    fun setDeviceRole(role: String) {
        _uiState.value = _uiState.value.copy(deviceRole = role)
        if (role == "TTS_ONLY") {
            sttEngine.stopListening()
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.STANDBY)
        } else if (!_uiState.value.isPttActive) {
            // In Phone mode and not TTS only, start listening
            sttEngine.startListening(_uiState.value.selectedLanguage)
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.LISTENING)
        }
    }

    fun setDirectIpInput(ip: String) {
        _uiState.value = _uiState.value.copy(directIpInput = ip)
    }

    fun connectDirectIp(ip: String, port: Int = 8889) {
        if (ip.isNotBlank()) {
            transportLayer.connectDirectIp(ip.trim(), port)
        }
    }

    // ==========================================
    // Push-To-Talk & Voice Capture Management
    // ==========================================

    fun onPttPressed() {
        if (_uiState.value.deviceRole == "TTS_ONLY") return
        if (_uiState.value.channelState == RadioChannelState.RECEIVING) return

        _uiState.value = _uiState.value.copy(
            channelState = RadioChannelState.TRANSMITTING,
            currentTranscript = "Listening..."
        )
        sttEngine.startListening(_uiState.value.selectedLanguage)
    }

    fun onPttReleased() {
        if (_uiState.value.deviceRole == "TTS_ONLY") return
        if (_uiState.value.channelState == RadioChannelState.TRANSMITTING) {
            sttEngine.stopListening()
            _uiState.value = _uiState.value.copy(
                channelState = RadioChannelState.STANDBY,
                currentTranscript = if (_uiState.value.currentTranscript == "Listening...") "" else _uiState.value.currentTranscript
            )
        }
    }

    fun togglePttMode(isPtt: Boolean) {
        _uiState.value = _uiState.value.copy(isPttActive = isPtt)
        if (!isPtt && _uiState.value.deviceRole != "TTS_ONLY") {
            // Continuous Phone Mode: VAD listens continuously and segments sentences automatically
            sttEngine.startListening(_uiState.value.selectedLanguage)
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.LISTENING)
        } else {
            // Push-to-Talk Mode
            sttEngine.stopListening()
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.STANDBY)
        }
    }

    fun setSelectedLanguage(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
        sttEngine.setLanguage(lang)
    }

    fun setForceMaxVolumeAlerts(force: Boolean) {
        _uiState.value = _uiState.value.copy(forceMaxVolumeAlerts = force)
    }

    fun setLowPowerListeningEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isLowPowerListeningEnabled = enabled)
    }

    fun setThemeMode(mode: String) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    // ==========================================
    // Transmission & Reception
    // ==========================================

    private fun transmitUtterance(
        text: String,
        language: SupportedLanguage,
        isAlert: Boolean,
        priority: AlertPriority
    ) {
        viewModelScope.launch {
            val packet = NetworkPacket(
                packetId = "PKT_${UUID.randomUUID().toString().take(8)}",
                senderId = "MY_NODE_ALPHA",
                senderCallsign = telemetry.value.nodeCallsign,
                text = text,
                languageCode = language.code,
                isAlert = isAlert,
                alertPriority = priority,
                timestamp = System.currentTimeMillis(),
                channelFreq = telemetry.value.frequencyGhz
            )

            // Stream packet over real sockets (TCP / Bluetooth / UDP)
            transportLayer.sendPacket(packet)

            // Persist into Room mission log
            repository.logMessage(
                VoiceMessageEntity(
                    messageUid = packet.packetId,
                    text = packet.text,
                    senderCallsign = packet.senderCallsign,
                    isLocal = true,
                    languageCode = packet.languageCode,
                    timestamp = packet.timestamp,
                    isAlert = packet.isAlert,
                    alertPriority = packet.alertPriority.name,
                    audioDurationSec = 2.4f,
                    hasPlayed = true
                )
            )

            _uiState.value = _uiState.value.copy(
                currentTranscript = text,
                channelState = if (!_uiState.value.isPttActive) RadioChannelState.LISTENING else RadioChannelState.STANDBY
            )
        }
    }

    private fun handleIncomingPacket(packet: NetworkPacket) {
        viewModelScope.launch {
            val incomingLang = SupportedLanguage.fromCode(packet.languageCode)
            val receiverSelectedLang = _uiState.value.selectedLanguage

            // Translate into receiver device's active selected language
            val translation = BundledOfflineTranslator.translate(
                text = packet.text,
                source = incomingLang,
                target = receiverSelectedLang
            )
            val speechText = translation.translatedText
            val speechLang = receiverSelectedLang

            val captionDisplay = if (translation.isTranslated && incomingLang != receiverSelectedLang) {
                "${translation.translatedText} (${incomingLang.nativeName} ➔ ${receiverSelectedLang.nativeName})"
            } else {
                packet.text
            }

            _uiState.value = _uiState.value.copy(
                channelState = RadioChannelState.RECEIVING,
                activeIncomingCaption = captionDisplay,
                activeIncomingIsAlert = packet.isAlert
            )

            // Save to Room DB: store readable text under local receiver language
            val storedText = if (translation.isTranslated && incomingLang != receiverSelectedLang) {
                "${translation.translatedText} [मूळ: ${packet.text}]"
            } else {
                packet.text
            }

            val rowId = repository.logMessage(
                VoiceMessageEntity(
                    messageUid = packet.packetId,
                    text = storedText,
                    senderCallsign = packet.senderCallsign,
                    isLocal = false,
                    languageCode = receiverSelectedLang.code,
                    timestamp = packet.timestamp,
                    isAlert = packet.isAlert,
                    alertPriority = packet.alertPriority.name,
                    audioDurationSec = 3.2f,
                    hasPlayed = false
                )
            )

            // If device is in STT_ONLY mode, do not play aloud over speaker
            if (_uiState.value.deviceRole == "STT_ONLY") {
                repository.markAsPlayed(rowId)
                _uiState.value = _uiState.value.copy(
                    channelState = if (!_uiState.value.isPttActive) RadioChannelState.LISTENING else RadioChannelState.STANDBY,
                    activeIncomingCaption = captionDisplay,
                    activeIncomingIsAlert = packet.isAlert
                )
                return@launch
            }

            // Play voice note / alert speech through phone speaker in receiver's SELECTED language!
            ttsEngine.speak(
                text = speechText,
                language = speechLang,
                isAlert = packet.isAlert,
                onDone = {
                    viewModelScope.launch {
                        repository.markAsPlayed(rowId)
                        _uiState.value = _uiState.value.copy(
                            channelState = if (!_uiState.value.isPttActive && _uiState.value.deviceRole != "TTS_ONLY") RadioChannelState.LISTENING else RadioChannelState.STANDBY,
                            activeIncomingCaption = null,
                            activeIncomingIsAlert = false
                        )
                    }
                }
            )
        }
    }

    // ==========================================
    // Priority Distress Alert Broadcast
    // ==========================================

    fun broadcastDistressAlert(
        customMessage: String?,
        priority: AlertPriority = AlertPriority.CRITICAL_DISTRESS
    ) {
        val lang = _uiState.value.selectedLanguage
        val alertText = customMessage?.ifBlank { null } ?: lang.sampleAlertPhrase

        transmitUtterance(
            text = alertText,
            language = lang,
            isAlert = true,
            priority = priority
        )

        // Haptic feedback & local notification
        alertAudioManager.triggerSosHaptics()
    }

    fun playVoiceMessage(message: VoiceMessageEntity) {
        val currentLang = _uiState.value.selectedLanguage
        val messageLang = SupportedLanguage.fromCode(message.languageCode)
        val cleanText = message.text.substringBefore(" [मूळ:")
        val translation = BundledOfflineTranslator.translate(
            text = cleanText,
            source = messageLang,
            target = currentLang
        )
        ttsEngine.speak(
            text = translation.translatedText,
            language = currentLang,
            isAlert = message.isAlert
        )
    }

    fun testTtsAudio(customText: String? = null) {
        val lang = _uiState.value.selectedLanguage
        val speech = customText ?: when (lang) {
            SupportedLanguage.HINDI -> "आई-तंत्र सिस्टम सक्रिय है। ऑडियो ट्रांसमिशन चालू है।"
            SupportedLanguage.ENGLISH -> "iTantra system active. Speech synthesizer operational."
            SupportedLanguage.BENGALI -> "আই-তন্ত্র সিস্টেম সক্রিয় রয়েছে। অডিও ট্রান্সমিশন চলছে।"
            SupportedLanguage.TELUGU -> "ఐ-తంత్ర వ్యవస్థ చురుకుగా ఉంది. ఆడియో ప్రసారం పనిచేస్తోంది."
            SupportedLanguage.TAMIL -> "ஐ-தந்திர அமைப்பு செயல்பாட்டில் உள்ளது. ஆடியோ இயங்குகிறது."
            SupportedLanguage.MARATHI -> "आय-तंत्र प्रणाली सक्रिय आहे. ऑडिओ प्रक्षेपण सुरू आहे."
            SupportedLanguage.GUJARATI -> "આઈ-તંત્ર સિસ્ટમ સક્રિય છે. ઑડિયો ટ્રાન્સમિશન ચાલુ છે."
            SupportedLanguage.KANNADA -> "ಐ-ತಂತ್ರ ವ್ಯವಸ್ಥೆಯು ಸಕ್ರಿಯವಾಗಿದೆ. ಆಡಿಯೊ ಪ್ರಸಾರ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತಿದೆ."
            SupportedLanguage.MALAYALAM -> "ഐ-തന്ത്ര സിസ്റ്റം സജീവമാണ്. ഓഡിയോ സംപ്രേക്ഷണം പ്രവർത്തിക്കുന്നു."
            SupportedLanguage.ODIA -> "ଆଇ-ତନ୍ତ୍ର ସିଷ୍ଟମ୍ ସକ୍ରିୟ ଅଛି। ଅଡିଓ ପ୍ରସାରଣ ଚାଲୁଅଛି।"
        }
        ttsEngine.speak(
            text = speech,
            language = lang,
            isAlert = false
        )
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearMissionLogs()
        }
    }

    // ==========================================
    // Pairing & Connection Controls
    // ==========================================

    fun scanForPeers(protocol: TransportProtocol) {
        transportLayer.startDiscovery(protocol)
    }

    fun connectToPeer(peer: PeerDevice) {
        transportLayer.connectToPeer(peer)
    }

    fun connectBluetooth(deviceAddress: String, deviceName: String = "Tactical BT Node") {
        val peer = PeerDevice(
            id = deviceAddress,
            name = deviceName,
            address = deviceAddress,
            protocol = TransportProtocol.BLUETOOTH,
            signalStrengthDbm = -55
        )
        transportLayer.connectToPeer(peer)
    }

    fun disconnectPeer() {
        transportLayer.disconnect()
    }

    fun switchProtocol(protocol: TransportProtocol) {
        transportLayer.setProtocol(protocol)
    }

    // Interactive Demo / Testing functions
    fun simulatePeerDistress() {
        transportLayer.triggerSimulatedPeerAlert(
            customText = "अलर्ट: इसरो मिशन नियंत्रण - तटीय क्षेत्रों में संचार टावर प्रभावित। उपग्रह बैकअप चालू किया गया।",
            priority = AlertPriority.CRITICAL_DISTRESS
        )
    }

    fun simulatePeerRoutineVoice() {
        transportLayer.triggerSimulatedPeerRoutineVoice(
            customText = "Ground station Alpha confirming telemetry signal 95% lock. All frequencies open."
        )
    }

    override fun onCleared() {
        super.onCleared()
        sttEngine.stopListening()
        ttsEngine.shutdown()
        transportLayer.disconnect()
        alertAudioManager.releaseAlertAudioFocus()
        alertAudioManager.stopHaptics()
    }
}
