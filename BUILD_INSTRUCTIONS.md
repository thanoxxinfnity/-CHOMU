# CHOMU — Build Instructions

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Flutter SDK | ≥ 3.22 | https://flutter.dev/docs/get-started/install |
| Dart SDK | ≥ 3.2 | bundled with Flutter |
| Android Studio | Hedgehog+ | https://developer.android.com/studio |
| Android SDK | API 34 | via Android Studio SDK Manager |
| NDK | 25.1.8937393 | via Android Studio SDK Manager |
| Java | 17 | bundled with Android Studio |

---

## 1 — Clone & Setup

```bash
git clone https://github.com/thanoxxinfnity/-CHOMU.git
cd -CHOMU
flutter pub get
```

---

## 2 — Configure API Keys

Open **Settings** in-app after first launch, or pre-fill `android/local.properties`:

```
# android/local.properties  (never commit this file)
flutter.sdk=/path/to/flutter
```

All API keys (OpenAI, Gemini, ElevenLabs) are stored on-device via Hive —
entered through the in-app Settings screen.

---

## 3 — Run on Device / Emulator

```bash
# List connected devices
flutter devices

# Run debug build
flutter run --debug

# Run on specific device
flutter run -d <device-id>
```

---

## 4 — Build Release APK

### 4a. Create a signing keystore (one-time)

```bash
keytool -genkey -v \
  -keystore android/app/chomu-release.jks \
  -alias chomu \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

### 4b. Create `android/key.properties`

```properties
storePassword=<your-store-password>
keyPassword=<your-key-password>
keyAlias=chomu
storeFile=chomu-release.jks
```

### 4c. Add signing config to `android/app/build.gradle`

Add this block inside `android { ... }` before `buildTypes`:

```groovy
def keyProps = new Properties()
def keyPropsFile = rootProject.file('key.properties')
if (keyPropsFile.exists()) {
    keyPropsFile.withReader('UTF-8') { keyProps.load(it) }
}

signingConfigs {
    release {
        keyAlias keyProps['keyAlias']
        keyPassword keyProps['keyPassword']
        storeFile keyProps['storeFile'] ? file(keyProps['storeFile']) : null
        storePassword keyProps['storePassword']
    }
}
```

Update `buildTypes.release.signingConfig` to `signingConfigs.release`.

### 4d. Build the APK

```bash
# Fat APK (all ABIs, ~30-50 MB)
flutter build apk --release

# Smaller split APKs per ABI
flutter build apk --release --split-per-abi
```

Output location: `build/app/outputs/flutter-apk/`

### 4e. Build App Bundle (for Play Store)

```bash
flutter build appbundle --release
```

Output: `build/app/outputs/bundle/release/app-release.aab`

---

## 5 — 3D Model Setup

### Default model
The bundled `assets/models/default_companion.glb` is loaded automatically.

### Custom VRM / GLB model
1. Open the app → tap **Settings** (⚙️ top right)
2. Under **Companion**, tap **Browse**
3. Select any `.vrm`, `.glb`, or `.fbx` file from your device storage
4. The 3D viewer hot-reloads the new model instantly

### VRM blendshape binding
The viewer auto-binds standard VRM 0.x / 1.0 expression names:
- Emotions: `happy`, `sad`, `surprised`, `angry`, `relaxed`, `neutral`
- Visemes: `aa`, `ih`, `oh`, `ou`, `E`, `pp`, `FF`, `kk`, `SS`, `nn`, `RR`, `dd`, `th`, `ch`
- System: `blink`, `blinkLeft`, `blinkRight`

For GLB/FBX models, the same names are mapped to morph target indices.

---

## 6 — LLM Endpoint Configuration

### OpenAI / OpenAI-compatible (default)
- Endpoint: `https://api.openai.com/v1`
- Model: `gpt-4o-mini` (or any chat model)
- API Key: your `sk-...` key

### Google Gemini
- Endpoint: `https://generativelanguage.googleapis.com/v1beta`
- Model: `gemini-1.5-flash` (or `gemini-1.5-pro`)
- API Key: your Google AI Studio key

### Local LLMs (Ollama / LM Studio)
- Endpoint: `http://10.0.2.2:11434/v1` (Android emulator → host)
  or your machine's LAN IP for a real device
- Model: `llama3`, `mistral`, etc.
- API Key: leave blank

---

## 7 — TTS Options

| Engine | Setup |
|--------|-------|
| Device TTS (free) | No setup — uses Android's built-in TTS engine |
| ElevenLabs | Enter API key + Voice ID in Settings → Text-to-Speech |

---

## 8 — Architecture Overview

```
lib/
├── main.dart               # Entry point
├── app.dart                # MultiProvider root + MaterialApp
├── core/
│   ├── constants.dart      # App-wide constants, default prompts
│   └── theme.dart          # Glassmorphic dark theme
├── models/                 # Hive-persistent data models
│   ├── message.dart        # Chat message
│   ├── session.dart        # Conversation session
│   ├── memory_fact.dart    # Long-term memory fact
│   └── ai_response.dart    # Parsed JSON AI response
├── services/
│   ├── database_service.dart   # Hive CRUD (sessions, memory, settings)
│   ├── llm_service.dart        # OpenAI / Gemini API client
│   ├── tts_service.dart        # Flutter TTS + ElevenLabs
│   ├── audio_service.dart      # Recording + playback + amplitude
│   └── viseme_service.dart     # Amplitude → mouth blendshape mapper
├── providers/              # ChangeNotifier state management
│   ├── companion_provider.dart # 3D state, idle animations, JS bridge
│   ├── chat_provider.dart      # Message flow, STT/TTS orchestration
│   └── settings_provider.dart  # Persisted settings
├── widgets/
│   ├── companion_viewer.dart   # InAppWebView + JS bridge wiring
│   ├── chat_bar.dart           # Glassmorphic input + mic + send
│   ├── top_action_bar.dart     # Status chip + settings/memory buttons
│   └── memory_drawer.dart      # Session list drawer
└── screens/
    ├── home_screen.dart        # Fullscreen 3D canvas + overlays
    ├── settings_screen.dart    # API keys, model picker, TTS config
    └── memory_screen.dart      # Long-term memory CRUD

assets/
├── html/viewer.html        # Three.js + @pixiv/three-vrm WebGL scene
└── models/
    └── default_companion.glb   # Bundled default 3D character
```

---

## 9 — Troubleshooting

| Problem | Fix |
|---------|-----|
| Model not loading | Check internet for CDN libs on first launch; or bundle Three.js locally |
| No audio | Grant microphone permission in Android Settings |
| LLM not responding | Verify endpoint URL ends without trailing slash; check API key |
| File picker crash | Grant `READ_MEDIA_*` permissions in Android 13+ |
| WebView blank | Enable hardware acceleration in `AndroidManifest.xml` (already set) |

---

## 10 — Offline / Production Hardening

To make the 3D viewer fully offline, download and bundle these JS files
into `assets/html/libs/`:

```bash
# From project root
mkdir -p assets/html/libs
curl -o assets/html/libs/three.min.js \
  https://cdn.jsdelivr.net/npm/three@0.158.0/build/three.min.js
curl -o assets/html/libs/GLTFLoader.js \
  https://cdn.jsdelivr.net/npm/three@0.158.0/examples/js/loaders/GLTFLoader.js
curl -o assets/html/libs/three-vrm.min.js \
  https://cdn.jsdelivr.net/npm/@pixiv/three-vrm@2.1.3/lib/three-vrm.min.js
```

Then update `assets/html/viewer.html` script src tags to use
`libs/three.min.js` etc.
