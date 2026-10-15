# OptiScan: Voice-First AI Visual Assistant for Blind and Low-Vision Users

OptiScan is an accessible, voice-first Android visual assistant engineered specifically for blind and low-vision users. Designed for high reliability, accessibility, and offline resilience, OptiScan enables users to capture real-time camera frames, instantly read printed text via on-device OCR, receive comprehensive spatial scene descriptions powered by Gemini 2.5 Flash, ask contextual follow-up questions by voice or text, and receive spoken audio guidance in English or Hindi (हिंदी).

---

## 1. Key Capabilities

- **On-Device Text Recognition:** Instant, private OCR using Google ML Kit. Immediately extracts and speaks printed signs, menus, documents, and packaging without requiring an internet connection.
- **Scene & Obstacle Understanding:** Multimodal visual reasoning powered by Gemini 2.5 Flash (via FastAPI backend). Delivers natural-language descriptions prioritizing spatial relationships, immediate walking paths, and physical obstacles.
- **Interactive Follow-Up Q&A:** Voice-driven conversational inquiry using Android `SpeechRecognizer` (with typed input fallback). Allows users to ask specific questions about the captured scene (e.g., *"Where is the doorway?"* or *"क्या रास्ता साफ है?"*).
- **Full Bilingual English & Hindi (हिंदी) Support:** Complete localization of UI labels, voice announcements, AI prompt orchestration, and text-to-speech. Built with strict medical safety rules: pharmaceutical names, active strengths, and dosage units remain uncorrupted.
- **Accessibility-First Interaction:**
  - High-contrast, WCAG AAA compliant color scheme (high-visibility yellow on obsidian black).
  - Oversized touch targets (minimum 64dp height).
  - Optional **Triple-Tap Accidental Touch Protection** mode for non-TalkBack users, featuring an automatic **Single-Tap Emergency Bypass** for urgent actions (Stop Speech, Retake, Retry, and Grant Permissions).
  - Full Android TalkBack screen reader support with explicit semantic headings and content descriptions.
- **Acoustic Feedback Loop:** Immediate voice readouts via Android `TextToSpeech`, tactile haptic confirmations, automatic speech stoppage upon forward navigation, and dedicated "Stop Speech" controls.
- **Smart Recapture Guidance:** Edge-variance blur and darkness heuristics detect covered lenses, motion blur, or severe underexposure before sending to cloud AI, giving the user calm, actionable spoken feedback to steady the camera.
- **Offline Mock Mode:** Full offline simulation mode providing deterministic visual assistance without cloud dependencies or paid API keys. All mock outputs are explicitly branded (`[Offline Mock]` / `[ऑफलाइन मॉक]`) so simulated data is never mistaken for real-time vision perception.

---

## 2. Technology Stack

- **Android Client:** Kotlin, Jetpack Compose, CameraX, Google ML Kit Text Recognition, Android `SpeechRecognizer`, Android `TextToSpeech`, Retrofit 2, OkHttp 3, Kotlin Coroutines & Flow.
- **Backend Service:** Python 3.11+, FastAPI, Uvicorn, Google Gemini 2.5 Flash API (`httpx`), Pillow, Pydantic v2, Pytest, Pytest-Asyncio.
- **Architecture:** Clean MVVM with unidirectional data flow, repository abstraction, on-device OCR priority with cloud fallback, and strict separation between UI, networking, and speech engines.

---

## 3. Repository Architecture

```text
OptiScan/
├── README.md
├── .gitignore
├── .env.example
├── docs/
│   ├── architecture.md          # Complete architecture diagrams and accessibility design
│   ├── api-contract.md          # REST API specification, request/response models
│   ├── team-workflow.md         # Milestone schedule and subsystem boundaries
│   └── vision-ai.md             # Vision prompt engineering and medical safety rules
├── android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml # Camera, Audio, Internet permissions
│           │   ├── java/com/optiscan/app/
│           │   │   ├── MainActivity.kt
│           │   │   ├── camera/
│           │   │   │   └── CameraManager.kt          # CameraX lifecycle & frame capture
│           │   │   ├── data/
│           │   │   │   ├── api/ApiClient.kt, OptiScanApi.kt
│           │   │   │   ├── mock/MockData.kt          # Offline mock datasets (EN & HI)
│           │   │   │   ├── models/AnalysisModels.kt  # Request/response contracts
│           │   │   │   ├── preferences/PreferencesManager.kt # Language & triple-tap settings
│           │   │   │   └── repository/ScanRepository.kt      # Cloud & mock dispatch
│           │   │   ├── navigation/
│           │   │   │   ├── NavigationModel.kt        # Screen enum definitions
│           │   │   │   └── ScreenState.kt            # Sealed UI state representations
│           │   │   ├── ocr/
│           │   │   │   └── OcrService.kt             # Google ML Kit Text Recognition
│           │   │   ├── speech/
│           │   │   │   ├── SpeechInputManager.kt     # Android SpeechRecognizer voice input
│           │   │   │   └── TtsManager.kt             # TextToSpeech engine with hi-IN support
│           │   │   ├── ui/
│           │   │   │   ├── components/               # AccessibleButton, OptiScanButton, ResultCard,
│           │   │   │   │                             # AccessibleTapGesture, SpeechControls
│           │   │   │   ├── localization/AppStrings.kt# Bilingual English/Hindi string catalogue
│           │   │   │   ├── screens/                  # HomeScreen, CameraScreen, ImagePreviewScreen,
│           │   │   │   │                             # ProcessingScreen, ReadTextResultScreen,
│           │   │   │   │                             # DescriptionResultScreen, AskQuestionScreen,
│           │   │   │   │                             # SettingsScreen, WelcomeScreen, MainViewModel
│           │   │   │   └── theme/                    # Color, Theme, Type (WCAG AAA palette)
│           │   │   └── utils/
│           │   │       └── NetworkConfig.kt          # Emulator (10.0.2.2) & device URL resolution
│           │   └── res/values/strings.xml, colors.xml
│           └── test/java/com/optiscan/app/           # Unit & integration tests
│               ├── MainViewModelIntegrationTest.kt
│               ├── PreferencesAndSpeechTest.kt
│               └── ModelContractTest.kt
├── backend/
│   ├── requirements.txt
│   ├── requirements-dev.txt
│   ├── pytest.ini
│   ├── app/
│   │   ├── main.py              # FastAPI app root, CORS, and logging
│   │   ├── api/routes.py        # /health and /analyze endpoints
│   │   ├── core/config.py       # Pydantic Settings & environment config
│   │   ├── schemas/analysis.py  # Request/response schemas
│   │   └── services/
│   │       ├── image_validation.py # Sharpness, blur, size, format validator
│   │       └── vision_provider.py  # Gemini 2.5 Flash & Mock vision providers
│   └── tests/
│       ├── test_analyze.py
│       ├── test_health.py
│       ├── test_image_validation.py
│       └── test_vision_provider.py
└── .github/workflows/
    └── backend-tests.yml
```

---

## 4. Setup & Running Instructions

### 4.1. Backend Service (FastAPI)

#### Prerequisites
- Python 3.11 or newer (Python 3.11, 3.12, 3.13, or 3.14).
- A Google Gemini API key from [Google AI Studio](https://aistudio.google.com/) (optional; defaults to offline mock provider).

#### Windows (PowerShell)
```powershell
# 1. Navigate to backend directory
cd backend

# 2. Create and activate virtual environment
python -m venv .venv
.\.venv\Scripts\Activate.ps1

# 3. Install dependencies
pip install -r requirements-dev.txt

# 4. Configure environment
Copy-Item ..\.env.example .env
# Edit .env: set VISION_PROVIDER=gemini and VISION_API_KEY=your_key_here

# 5. Run tests to verify
pytest -v

# 6. Start the FastAPI development server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### Linux / macOS (Bash)
```bash
# 1. Navigate to backend directory
cd backend

# 2. Create and activate virtual environment
python3 -m venv .venv
source .venv/bin/activate

# 3. Install dependencies
pip install -r requirements-dev.txt

# 4. Configure environment
cp ../.env.example .env

# 5. Run tests
pytest -v

# 6. Start the development server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

### 4.2. Android Client Networking

The Android application needs to communicate with the FastAPI backend running on your development workstation. Choose the option matching your demo environment:

#### Option A: Android Studio Emulator (Easiest)
- The Android Emulator routes `10.0.2.2` directly to `localhost` on the host workstation.
- **OptiScan automatically defaults to `http://10.0.2.2:8000/`** when running inside an emulator.
- No manual network configuration is required.

#### Option B: Physical Android Device via USB Cable (Recommended for Live Demos)
- Connect your Android phone to your computer via USB with **USB Debugging** enabled.
- Run the following ADB reverse command in PowerShell or terminal:
  ```powershell
  adb reverse tcp:8000 tcp:8000
  ```
- This forwards port 8000 on the physical phone directly to port 8000 on your host computer.
- **OptiScan automatically detects physical hardware and connects to `http://127.0.0.1:8000/`**, making connection seamless over USB.

#### Option C: Physical Android Device via Local Wi-Fi
- Ensure both the Android phone and development machine are connected to the same Wi-Fi network.
- Determine your computer's local LAN IP (e.g. `192.168.1.15`).
- In `android/app/src/main/java/com/optiscan/app/utils/NetworkConfig.kt`, set:
  ```kotlin
  var activeBaseUrl: String = "http://192.168.1.15:8000/"
  ```

---

### 4.3. Environment Variables Reference

Configure these in `backend/.env` (never commit real credentials to Git):

| Variable | Default | Purpose |
|---|---|---|
| `VISION_PROVIDER` | `mock` | Active provider: `"gemini"` (live AI) or `"mock"` (offline simulation). |
| `VISION_API_KEY` | *(None)* | Google Gemini API key obtained from Google AI Studio. |
| `VISION_MODEL_NAME` | `gemini-2.5-flash` | Gemini model tag used for multimodal perception. |
| `VISION_REQUEST_TIMEOUT_SECONDS` | `25.0` | Upstream HTTP timeout for cloud inference. |
| `BLUR_THRESHOLD` | `8.0` | Sharpness edge-variance floor below which recapture is advised. |
| `MAX_UPLOAD_SIZE_BYTES` | `8388608` | Maximum image upload ceiling (8 MB). |

---

### 4.4. Hindi Voice (TTS) & Speech Recognition Setup

OptiScan supports full Hindi audio reading and voice Q&A out of the box:

1. **Verify Android Text-to-Speech Engine:**
   - On your Android device, open **Settings** → **System** → **Languages & input** (or **Accessibility**) → **Text-to-speech output**.
   - Ensure the preferred engine is set to **Speech Services by Google**.
   - Tap the gear icon ⚙ next to Google TTS → **Install voice data** → Select **Hindi (India)** and verify it is downloaded.
2. **Speech Recognition (Voice Input):**
   - Ensure the Google app has permission to use the microphone.
   - When the user presses the microphone button on the Q&A screen, OptiScan invokes `RecognizerIntent` with `Locale("hi", "IN")` for Hindi or `Locale.ENGLISH` for English.
   - If voice input is unavailable or fails, a typed text input field is provided as a seamless accessible fallback.

---

## 5. Live Hackathon Demonstration Script (60–90 Seconds)

Follow this step-by-step walkthrough for an impactful live demonstration:

### Step 1: Accessible Home & Audio Greeting (15 seconds)
1. Launch the app. Listen as the app automatically announces: *"OptiScan ready. Select Read Text or Describe Surroundings."*
2. Highlight the WCAG AAA high-contrast palette and oversized buttons (occupying half the screen each) designed for low-vision touch interaction.
3. Show the **Settings** screen: toggle **Triple-Tap Protection** on/off to demonstrate how non-TalkBack blind users are protected from accidental pocket touches.

### Step 2: Instant On-Device Text Reading (20 seconds)
1. From the Home screen, tap **Read Text**.
2. Point the camera at a printed document, medicine box, or conference badge.
3. Tap **Take Photo** (or double-tap if triple-tap mode is active).
4. Point out the instant response: **Google ML Kit OCR processes the image completely on-device** and reads the text aloud immediately via TTS without network latency.
5. Tap **Stop Reading** to showcase instant audio interrupt.

### Step 3: Spatial Scene Understanding via Gemini 2.5 Flash (25 seconds)
1. Navigate back to Home and select **Describe Surroundings**.
2. Point the camera around the room (showing chairs, tables, or a doorway).
3. Tap **Take Photo**.
4. The image is validated on the backend and analyzed by Gemini 2.5 Flash.
5. The app announces a direct, spatial description (e.g., *"Directly in front of you is a clear pathway. To your right at waist height is a table with a laptop. A doorway is visible 10 feet ahead."*).

### Step 4: Follow-up Voice Question & Answer (15 seconds)
1. On the description result screen, tap **Ask Follow-up**.
2. Tap the microphone button and ask: *"Where is the chair?"*
3. The app transcribes the question, queries Gemini with the existing image context, and speaks the targeted answer.

### Step 5: Full Hindi Localization & Medical Safety (15 seconds)
1. Navigate to **Settings** and select **हिंदी (Hindi)**.
2. The UI instantly updates to Hindi and announces: *"हिंदी चुनी गई"* (Hindi selected).
3. Return to camera, capture a scene or medicine packaging.
4. Listen to the fluent Hindi narration in Devanagari script.
5. Emphasize that **medicine brand names and dosages (e.g. 'Paracetamol 500mg') are never distorted or incorrectly translated**, ensuring critical patient safety.

---

## 6. Failure Recovery & Defensive Design

OptiScan is designed to never leave a blind user stuck in a silent or unresponsive state:

- **Upstream Network Failure:** If the backend is unreachable or cloud API times out, the app immediately announces: *"Could not connect to analysis service. Please check your network connection and try again."* A prominent **Retry** button (with single-tap bypass) allows instant re-attempt.
- **Accidental Camera Occlusion / Blur:** If the camera lens is covered by a finger or the shot is severely blurred, the backend detects low edge-variance and speaks: *"Image is too blurry. Please hold steady and recapture."*
- **Camera Permission Denial:** If camera permission is withheld, an accessible recovery card appears with a large **Grant Camera Permission** button, eliminating navigation dead-ends.
- **Speech Collision Prevention:** Navigating to a new screen or tapping any new capture immediately silences the active TTS stream, preventing overlapping audio feeds.
- **Emergency Action Bypass:** In Triple-Tap mode, high-urgency buttons (**Stop Speech**, **Retake Photo**, **Retry**, and **Grant Permission**) execute immediately on a single tap with haptic confirmation.

---

## 7. Automated Test Coverage & Verification

Every component is validated with automated unit and regression suites:

```text
Backend pytest Suite:
============================== 55 passed in 2.14s ==============================
- Health check endpoints
- Multipart image upload validation (JPEG, PNG, WEBP, MPO)
- Sharpness, blur, underexposure, and decompression bomb detection
- Gemini Vision provider payload construction & error sanitization
- Hindi language parameter routing and Devanagari prompting
- Medical safety guidelines enforcement in system prompt
- Explicit [Offline Mock] branding in mock mode

Android Gradle Test Suite:
============================== 26 passed in 6.78s ==============================
- Model JSON serialization/deserialization contracts
- Preferences persistence (Language, Triple-Tap)
- Mock vision repository dispatch & explicit mock branding
- MainViewModel state transitions & error handling
- Navigation speech stoppage on screen transitions
- No silent mock fabrication on cloud network outage
- Bilingual AppStrings coverage (English & Hindi)

Android Build:
- assembleDebug: SUCCESSFUL (Produces installable debug APK)
```

---

## 8. Implementation Status

| Component | Status | Verified Functionality |
|---|---|---|
| **FastAPI Backend Core** | **Working** | CORS, streaming chunk reads, config loading, and logging. |
| **Backend `/health` & `/analyze`** | **Working** | Tested across 55 pytest test cases. |
| **Blur & Sharpness Heuristics** | **Working** | Edge-variance detection for mobile camera frames. |
| **Google Gemini 2.5 Flash Provider** | **Working** | Multimodal image description, follow-up Q&A, and error handling. |
| **On-Device ML Kit OCR** | **Working** | Offline text extraction with cloud fallback. |
| **Bilingual Localization (EN & HI)** | **Working** | Full UI, TTS, SpeechRecognizer, and Gemini prompting in Hindi. |
| **Medical Term Safety** | **Working** | Verification that medicine brand names and dosages are not altered. |
| **Triple-Tap Accidental Protection** | **Working** | 3-tap mode for low-vision users with 1-tap emergency bypass. |
| **Acoustic Loop & TTS Management** | **Working** | Speech queueing, stop controls, and screen transition stoppage. |
| **Offline Mock Resilience** | **Working** | Fully functional offline demonstration mode with explicit mock labeling. |
