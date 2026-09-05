package com.example.model

import java.util.Locale

/**
 * 10 Official Regional & National Languages for iTantra Walkie-Talkie
 */
enum class SupportedLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val locale: Locale,
    val sampleAlertPhrase: String
) {
    HINDI(
        code = "hi",
        englishName = "Hindi",
        nativeName = "हिन्दी",
        locale = Locale("hi", "IN"),
        sampleAlertPhrase = "सावधान: चक्रवात चेतावनी। तुरंत सुरक्षित स्थान पर जाएं।"
    ),
    BENGALI(
        code = "bn",
        englishName = "Bengali",
        nativeName = "বাংলা",
        locale = Locale("bn", "IN"),
        sampleAlertPhrase = "সতর্কতা: জরুরি সংকেত জারি করা হয়েছে।"
    ),
    MARATHI(
        code = "mr",
        englishName = "Marathi",
        nativeName = "मराठी",
        locale = Locale("mr", "IN"),
        sampleAlertPhrase = "सतर्कता: आपत्कालीन मदत पथक रवाना झाले आहे."
    ),
    TELUGU(
        code = "te",
        englishName = "Telugu",
        nativeName = "తెలుగు",
        locale = Locale("te", "IN"),
        sampleAlertPhrase = "హెచ్చరిక: అత్యవసర సహాయ కేంద్రం అప్రమత్తమైంది."
    ),
    TAMIL(
        code = "ta",
        englishName = "Tamil",
        nativeName = "தமிழ்",
        locale = Locale("ta", "IN"),
        sampleAlertPhrase = "எச்சரிக்கை: பேரிடர் மீட்பு குழு விரைந்துள்ளது."
    ),
    GUJARATI(
        code = "gu",
        englishName = "Gujarati",
        nativeName = "ગુજરાતી",
        locale = Locale("gu", "IN"),
        sampleAlertPhrase = "ચેતવણી: તાકીદની સ્થળાંતર સૂચના જાહેર કરાઈ છે."
    ),
    KANNADA(
        code = "kn",
        englishName = "Kannada",
        nativeName = "ಕನ್ನಡ",
        locale = Locale("kn", "IN"),
        sampleAlertPhrase = "ಎಚ್ಚರಿಕೆ: ವಿಪತ್ತು ನಿರ್ವಹಣಾ ತಂಡ ಸನ್ನದ್ಧವಾಗಿದೆ."
    ),
    MALAYALAM(
        code = "ml",
        englishName = "Malayalam",
        nativeName = "മലയാളം",
        locale = Locale("ml", "IN"),
        sampleAlertPhrase = "ജാഗ്രത: അടിയന്തര ദുരന്ത നിവാരണ മുന്നറിയിപ്പ്."
    ),
    ODIA(
        code = "or",
        englishName = "Odia",
        nativeName = "ଓଡ଼ିଆ",
        locale = Locale("or", "IN"),
        sampleAlertPhrase = "ସତର୍କତା: ଉପକୂଳବର୍ତ୍ତୀ ଅଞ୍ଚଳ ଖାଲି କରିବାକୁ ନିର୍ଦ୍ଦେଶ।"
    ),
    ENGLISH(
        code = "en",
        englishName = "English",
        nativeName = "English",
        locale = Locale("en", "IN"),
        sampleAlertPhrase = "Priority Alert: Disaster Response Protocol Activated."
    );

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

enum class WalkieTalkieMode {
    PTT,        // Push-to-Talk: Hold to talk (STT), release to listen (TTS)
    CONTINUOUS  // Full Duplex / Hands-free two-way voice messenger
}

enum class RadioChannelState {
    STANDBY,       // Idle, listening for peer packets
    LISTENING,     // Mic active, recording user voice
    TRANSMITTING,  // Encapsulating and blasting packet over link
    RECEIVING      // Receiving stream from peer, synthesizing TTS
}

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    PAIRING,
    CONNECTED,
    LOST
}

enum class TransportProtocol {
    WIFI_DIRECT,
    BLUETOOTH,
    BLE
}

enum class AlertPriority {
    ROUTINE,
    URGENT,
    CRITICAL_DISTRESS
}

enum class VadStatus {
    SILENCE,
    SPEECH_DETECTED
}

data class PeerDevice(
    val id: String,
    val name: String,
    val address: String,
    val protocol: TransportProtocol,
    val signalStrengthDbm: Int = -55,
    val isConnected: Boolean = false,
    val isP2pHost: Boolean = false,
    val port: Int = 8889,
    val batteryPercent: Int = 100
)

data class MissionTelemetry(
    val nodeCallsign: String = "ISRO-SATCOM-01",
    val peerCallsign: String = "REMOTE-TRANSCV-02",
    val frequencyGhz: String = "5.180 GHz (Ch 36)",
    val signalDbm: Int = -58,
    val linkQualityPercent: Int = 94,
    val latencyMs: Int = 18,
    val batteryPercent: Int = 88,
    val powerDrawWatts: Float = 0.42f, // Low power budget design
    val isModelLoaded: Boolean = true,
    val batteryTemperatureC: Float = 30.5f,
    val isCharging: Boolean = false,
    val localIpAddress: String = "127.0.0.1",
    val deviceModel: String = "Android Terminal",
    val ramUsageMb: Float = 32.0f,
    val deviceRole: String = "TRANSCEIVER" // TRANSCEIVER, STT_ONLY, TTS_ONLY
)
