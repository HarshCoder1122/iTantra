<div align="center">

# iTantra

### Offline, mesh-networked voice walkie-talkie for disaster response — in 10 Indian languages.

No internet. No cell towers. No cloud. Speak in your language, the person on the other end hears it in theirs — relayed phone-to-phone over Wi-Fi Direct, Bluetooth and BLE.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![ONNX Runtime](https://img.shields.io/badge/Inference-ONNX%20Runtime-005CED?style=for-the-badge&logo=onnx&logoColor=white)](https://onnxruntime.ai/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[![Stars](https://img.shields.io/github/stars/HarshCoder1122/iTantra?style=flat-square&color=7F52FF)](https://github.com/HarshCoder1122/iTantra/stargazers)
[![Last commit](https://img.shields.io/github/last-commit/HarshCoder1122/iTantra?style=flat-square&color=blue)](https://github.com/HarshCoder1122/iTantra/commits/main)
[![Repo size](https://img.shields.io/github/repo-size/HarshCoder1122/iTantra?style=flat-square&color=orange)](https://github.com/HarshCoder1122/iTantra)
[![Issues](https://img.shields.io/github/issues/HarshCoder1122/iTantra?style=flat-square&color=red)](https://github.com/HarshCoder1122/iTantra/issues)

<br/>

<table>
<tr>
<td align="center"><b>10</b><br/><sub>Indian languages</sub></td>
<td align="center"><b>3</b><br/><sub>Mesh transports</sub></td>
<td align="center"><b>0</b><br/><sub>Network calls for speech</sub></td>
<td align="center"><b>∞</b><br/><sub>Relay hops (TTL-bounded)</sub></td>
</tr>
</table>

</div>

<br/>

## Table of contents

- [Why](#why)
- [Features](#features)
- [Screenshots](#screenshots)
- [How it works](#how-it-works)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Supported languages](#supported-languages)
- [Getting started](#getting-started)
- [Project structure](#project-structure)
- [Models & credits](#models--credits)
- [Contributing](#contributing)
- [License](#license)

## Why

When a cyclone knocks out cell towers and the internet along a coastline, rescue teams and villagers still need to talk to each other — and not everyone in the affected region speaks the same language. iTantra turns any two (or more) Android phones into a walkie-talkie network that:

- needs **zero infrastructure** — no SIM, no Wi-Fi router, no data connection,
- **speaks and listens in the local language** on each end, translating in between,
- keeps working as a **multi-hop mesh** so a message can travel A → B → C even when A and C are out of each other's range,
- and can blast a **priority distress alert** to every connected node at once.

Built for the Smart India Hackathon around a disaster-management / space-comms (ISRO-styled) brief.

## Features

| | |
|---|---|
| **Offline speech pipeline** | On-device STT ([AI4Bharat IndicConformer](https://github.com/AI4Bharat/NeMo), INT8) with [Silero VAD](https://github.com/snakers4/silero-vad) for real-time sentence boundaries, and on-device TTS ([AI4Bharat Indic-TTS](https://github.com/AI4Bharat/Indic-TTS) FastPitch + HiFi-GAN) — zero network calls, ever. |
| **Streaming synthesis** | Long messages are chunked sentence-by-sentence and played back as each chunk finishes synthesizing, instead of waiting for the whole utterance to render before any sound plays. |
| **Cross-language relay** | The sender speaks their language; the receiver hears it in *theirs* — translated automatically in between. |
| **True mesh transport** | UDP broadcast discovery, TCP over Wi-Fi Direct, Bluetooth RFCOMM, and BLE scan/advertise, all live at once. |
| **Multi-hop relay** | A node holds every live link simultaneously and flood-relays unseen packets onward (TTL-bounded, dedup-cached) — two out-of-range phones can still talk through a third. |
| **Priority distress broadcast** | One tap sends a `CRITICAL_DISTRESS` packet to the whole mesh with a siren tone, forced max volume, and a haptic pattern. |
| **Push-to-talk or hands-free** | Classic PTT, or continuous VAD-segmented "phone mode" conversation. |
| **Per-device role** | Transceiver / STT-only / TTS-only — for mixed hearing- and speaking-impaired deployments. |
| **Mission voice log** | Every message sent and received is archived locally (Room) with language, timestamp, and alert priority. |
| **Live tactical telemetry** | Battery, signal strength, link quality, RSSI, local IP, RAM — always on screen. |

## Screenshots

<div align="center">
<table>
<tr>
<td align="center" width="20%"><img src="docs/screenshots/walkie.png" width="200"/><br/><b>Walkie-Talkie</b><br/><sub>Push-to-talk & voice-activated modes</sub></td>
<td align="center" width="20%"><img src="docs/screenshots/mesh-link.png" width="200"/><br/><b>Mesh Link</b><br/><sub>Peer discovery over Wi-Fi/BT/BLE</sub></td>
<td align="center" width="20%"><img src="docs/screenshots/messages.png" width="200"/><br/><b>Messages</b><br/><sub>Archived voice transcripts</sub></td>
<td align="center" width="20%"><img src="docs/screenshots/sos.png" width="200"/><br/><b>Emergency SOS</b><br/><sub>Priority distress broadcast</sub></td>
<td align="center" width="20%"><img src="docs/screenshots/settings.png" width="200"/><br/><b>Settings</b><br/><sub>On-device model status</sub></td>
</tr>
</table>
</div>

## How it works

```mermaid
sequenceDiagram
    participant U as Speaker (Node A)
    participant VAD as Silero VAD
    participant STT as IndicConformer STT
    participant MESH as Mesh Transport
    participant B as Relay (Node B)
    participant TR as Offline Translator
    participant TTS as FastPitch + HiFi-GAN TTS
    participant L as Listener (Node C)

    U->>VAD: raw microphone audio
    VAD->>STT: end-of-sentence segment
    STT->>MESH: transcribed text + language
    MESH->>B: UDP beacon / TCP / Bluetooth packet
    B-->>B: not seen before, TTL > 1 -> relay onward
    B->>MESH: forwarded packet
    MESH->>TR: text (sender language)
    TR->>TTS: text (receiver's selected language)
    TTS->>L: synthesized speech, chunk-by-chunk
```

## Architecture

```mermaid
flowchart LR
    subgraph NodeA["Node A — speaker"]
        MicA[Mic] --> VAD_A[Silero VAD]
        VAD_A --> STT_A[IndicConformer STT]
        STT_A --> XportA[TacticalMeshTransport]
    end

    subgraph NodeB["Node B — relay"]
        XportB[TacticalMeshTransport]
    end

    subgraph NodeC["Node C — listener"]
        XportC[TacticalMeshTransport]
        XportC --> Translate[Offline Translator]
        Translate --> TTS_C[FastPitch + HiFi-GAN TTS]
        TTS_C --> SpkC[Speaker]
    end

    XportA <-->|UDP:8888 beacon + broadcast| XportB
    XportA <-->|TCP:8889 / Bluetooth RFCOMM| XportB
    XportB <-->|flood relay, TTL-bounded| XportC
```

A is out of Bluetooth/Wi-Fi Direct range of C, but both are linked to B — B holds both links simultaneously and relays A's packet on to C without either endpoint configuring anything.

## Tech stack

<table>
<tr><td><b>Language</b></td><td>

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)

</td></tr>
<tr><td><b>UI</b></td><td>

![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Material3](https://img.shields.io/badge/Material%203-757575?logo=materialdesign&logoColor=white)

</td></tr>
<tr><td><b>Architecture</b></td><td>

![MVVM](https://img.shields.io/badge/MVVM-StateFlow-blue)
![Coroutines](https://img.shields.io/badge/Kotlin-Coroutines%2FFlow-7F52FF?logo=kotlin&logoColor=white)

</td></tr>
<tr><td><b>Persistence</b></td><td>

![Room](https://img.shields.io/badge/Room-Database-4285F4?logo=sqlite&logoColor=white)

</td></tr>
<tr><td><b>Speech-to-Text</b></td><td>

![sherpa-onnx](https://img.shields.io/badge/sherpa--onnx-IndicConformer%20(INT8)-orange)

</td></tr>
<tr><td><b>Voice Activity Detection</b></td><td>

![Silero VAD](https://img.shields.io/badge/Silero-VAD-lightgrey)

</td></tr>
<tr><td><b>Text-to-Speech</b></td><td>

![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-FastPitch%20%2B%20HiFi--GAN-005CED?logo=onnx&logoColor=white)

</td></tr>
<tr><td><b>Mesh networking</b></td><td>

![WiFi Direct](https://img.shields.io/badge/WiFi%20Direct-TCP%2FUDP-3DDC84?logo=android&logoColor=white)
![Bluetooth](https://img.shields.io/badge/Bluetooth-RFCOMM-0082FC?logo=bluetooth&logoColor=white)
![BLE](https://img.shields.io/badge/BLE-Scan%2FAdvertise-0082FC?logo=bluetooth&logoColor=white)

</td></tr>
<tr><td><b>Min / target SDK</b></td><td>

![SDK](https://img.shields.io/badge/API-24%20→%2035-3DDC84?logo=android&logoColor=white)

</td></tr>
</table>

## Supported languages

| Language | Native | STT | TTS |
|---|---|:---:|:---:|
| Hindi | हिन्दी | ✅ | ✅ |
| Bengali | বাংলা | ✅ | ✅ |
| Marathi | मराठी | ✅ | ✅ |
| Telugu | తెలుగు | ✅ | ✅ |
| Tamil | தமிழ் | ✅ | ✅ |
| Gujarati | ગુજરાતી | ✅ | ✅ |
| Kannada | ಕನ್ನಡ | ✅ | ✅ |
| Malayalam | മലയാളം | ✅ | ✅ |
| Odia | ଓଡ଼ିଆ | ✅ | ✅ |
| English | English | — | ✅ |

New languages are added purely by dropping converted model files into `assets/models/<code>/` and a manifest entry — see `tools/model_conversion/`.

## Getting started

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (Ladybug or newer), JDK 17.

```bash
git clone https://github.com/HarshCoder1122/iTantra.git
cd iTantra
```

### Model weights

The on-device STT/TTS model weights (~2.3GB across 10 languages) are **not** committed to this repo — several individual files exceed GitHub's 100MB limit. The manifest (`app/src/main/assets/models/model_manifest.json`), tokenizer vocabularies, and TTS frontend configs *are* included, describing exactly which upstream checkpoints each language pack expects and where they came from.

To run the app with real speech, regenerate the ONNX weights with the conversion scripts under `tools/model_conversion/` (`convert_stt.py`, `export_tts.py`) against the source checkpoints listed per-language in `model_manifest.json`, and drop the output into the matching `assets/models/<code>/stt/` and `assets/models/<code>/tts/` folders. Without them, a language simply reports itself as unavailable instead of faking a result — the app runs fine, it just has nothing to say yet.

### Build & run

1. Open the project root in Android Studio and let it sync.
2. Run on two physical devices (or a device + emulator) on the same Wi-Fi network / with Bluetooth enabled, to actually exercise the mesh.
3. Grant microphone, location, and nearby-devices/Bluetooth permissions when prompted — they're required for `RECORD_AUDIO`, Wi-Fi Direct discovery, and BLE/Bluetooth scanning respectively.

## Project structure

```
app/src/main/java/com/example/
├── stt/            # IndicConformer + Silero VAD speech-to-text engine
├── tts/            # FastPitch + HiFi-GAN text-to-speech engine
├── transport/      # UDP/TCP/Bluetooth/BLE/Wi-Fi Direct mesh transport
├── translation/    # Offline sender-language → receiver-language translation
├── model/          # Bundled model manifest loading & verification
├── data/           # Room database (mission voice log)
├── audio/          # Alert siren, haptics, audio focus management
├── viewmodel/      # MissionControlViewModel — app state & orchestration
└── ui/             # Compose screens & components
tools/model_conversion/  # Scripts to (re)export STT/TTS checkpoints to ONNX
```

## Models & credits

| Component | Source | License |
|---|---|---|
| STT acoustic model | [AI4Bharat IndicConformer](https://huggingface.co/ai4bharat) (via [trysem/indicconformer-120m-onnx](https://huggingface.co/trysem)) | CC-BY-4.0 |
| VAD | [Silero VAD](https://github.com/snakers4/silero-vad) | MIT |
| STT/VAD runtime | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (k2-fsa) | Apache-2.0 |
| TTS acoustic + vocoder | [AI4Bharat Indic-TTS](https://github.com/AI4Bharat/Indic-TTS) (FastPitch + HiFi-GAN) | MIT |
| TTS/general ONNX runtime | [ONNX Runtime Mobile](https://onnxruntime.ai/) (Microsoft) | MIT |

## Contributing

Issues and PRs are welcome — additional language packs and mesh-transport hardening are good starting points.

## License

App source code is [MIT licensed](LICENSE). Bundled third-party models and libraries retain their own upstream licenses (see table above).

<div align="center">
<br/>

[![Star History Chart](https://api.star-history.com/svg?repos=HarshCoder1122/iTantra&type=Date)](https://star-history.com/#HarshCoder1122/iTantra&Date)

<sub>If iTantra is useful to you, consider starring the repo.</sub>

</div>
