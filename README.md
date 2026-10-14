# OptiScan: AI Visual Assistant for Blind and Low-Vision Users

OptiScan is an accessible, Android-first assistive technology application designed for individuals with blindness or visual impairments. Built for rapid hackathon iteration, OptiScan enables users to capture real-time photos, instantly read printed text through on-device OCR, receive comprehensive AI-generated descriptions of their surroundings, and receive spoken audio guidance when an image is blurred or obscured.

---

## 1. Key Features

- **On-Device Text Recognition:** Instant, private OCR using Google ML Kit. Reads signs, documents, and labels without requiring network connectivity.
- **Scene & Obstacle Understanding:** Dispatches captures to an extensible FastAPI backend vision provider for natural-language descriptions of walking paths and obstacles.
- **Acoustic Feedback Loop:** Immediate voice readouts via Android `TextToSpeech` with tactile touch feedback and repetition controls.
- **Smart Recapture Prompt:** Real-time blur and occlusion heuristics detect out-of-focus or covered camera captures, guiding the user to steady the camera.
- **Accessible-First Interface:** High-contrast palette (WCAG AAA compliant), oversized touch targets (>=64dp height), explicit semantic labels, and zero distracting animations.
- **Offline Mock Support:** Full development and demo capability without external paid API keys or cloud dependencies.

---

## 2. Technology Stack

- **Android Client:** Kotlin, Jetpack Compose, CameraX, Google ML Kit Text Recognition, Android TextToSpeech, Retrofit 2, OkHttp 3, Kotlin Coroutines.
- **Backend Service:** Python 3.11+, FastAPI, Uvicorn, Pydantic v2, Pillow, Pytest, Pytest-Asyncio.
- **Architecture:** Decoupled client-server architecture with an abstract vision provider strategy and on-device OCR priority.

---

## 3. Repository Structure

```text
OptiScan/
├── README.md
├── .gitignore
├── .env.example
├── docs/
│   ├── architecture.md          # Architectural diagrams, sequence flows, and accessibility design
│   ├── api-contract.md          # Complete REST API specification and model mappings
│   └── team-workflow.md         # 4-person parallel development boundaries and milestone schedule
├── android/
│   ├── settings.gradle.kts      # Repository and project configuration
│   ├── build.gradle.kts         # Root Android build configuration
│   ├── gradle.properties        # JVM and AndroidX optimization flags
│   └── app/
│       ├── build.gradle.kts     # Compose, CameraX, ML Kit, and Retrofit dependencies
│       ├── proguard-rules.pro   # Serialization and model obfuscation rules
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml # Camera and internet permissions
│           │   ├── java/com/optiscan/app/
│           │   │   ├── MainActivity.kt
│           │   │   ├── navigation/ScreenState.kt
│           │   │   ├── ui/
│           │   │   │   ├── screens/HomeScreen.kt, HomeScreenViewModel.kt
│           │   │   │   ├── components/AccessibleButton.kt, ResultCard.kt, CameraPreviewView.kt
│           │   │   │   └── theme/Color.kt, Theme.kt, Type.kt
│           │   │   ├── camera/CameraManager.kt
│           │   │   ├── ocr/OcrService.kt
│           │   │   ├── speech/TtsManager.kt
│           │   │   ├── data/
│           │   │   │   ├── api/OptiScanApi.kt, ApiClient.kt
│           │   │   │   ├── models/AnalysisModels.kt
│           │   │   │   └── repository/ScanRepository.kt
│           │   │   └── utils/NetworkConfig.kt
│           │   └── res/values/strings.xml, colors.xml
│           └── test/java/com/optiscan/app/ModelContractTest.kt
├── backend/
│   ├── requirements.txt         # Production dependencies
│   ├── requirements-dev.txt     # Development and testing dependencies
│   ├── pytest.ini               # Test configuration and pythonpath
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py              # FastAPI application root and CORS middleware
│   │   ├── api/routes.py        # /health and /analyze route handlers
│   │   ├── schemas/analysis.py  # Pydantic request and response contracts
│   │   ├── services/
│   │   │   ├── image_validation.py # Sharpness, format, size, and blur validation
│   │   │   └── vision_provider.py  # Abstract VisionProvider and MockVisionProvider
│   │   └── core/config.py       # Pydantic Settings and environment management
│   └── tests/
│       ├── test_health.py       # Health check tests
│       └── test_analyze.py      # Multipart upload, task dispatch, and blur validation tests
└── .github/
    └── workflows/
        └── backend-tests.yml    # CI test matrix across Python versions
```

---

## 4. Setup & Running Instructions

### 4.1. Backend Service

#### Prerequisites
- Python 3.11 or newer (Python 3.11, 3.12, 3.13, or 3.14)
- Git

#### Windows Setup (PowerShell)
```powershell
# 1. Navigate to the backend directory
cd backend

# 2. Create and activate a Python virtual environment
python -m venv .venv
.\.venv\Scripts\Activate.ps1

# 3. Install dependencies
pip install -r requirements-dev.txt

# 4. Configure environment (optional, defaults to mock provider)
Copy-Item ..\.env.example .env

# 5. Run automated tests
pytest -v

# 6. Start the development server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### Linux / macOS Setup (Bash)
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

# 6. Start backend server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

### 4.2. Android Frontend

#### Prerequisites
- **Android Studio:** Hedgehog (2023.1.1) or newer recommended (e.g. Iguana / Jellyfish / Koala).
- **Android SDK:** Compile SDK 34, Min SDK 26 (Android 8.0 Oreo or higher).
- **Java Development Kit:** JDK 17 or JDK 21.

#### Emulator Networking
When running the Android emulator, localhost on your computer cannot be accessed at `127.0.0.1` from inside the emulator. The Android emulator provides a virtual router alias:
- **`http://10.0.2.2:8000/`** connects directly to `localhost:8000` on your development host.
- The app defaults to `http://10.0.2.2:8000/` automatically.
- If using a physical Android device over Wi-Fi, update `activeBaseUrl` in `NetworkConfig.kt` to your host computer's local network IP (e.g., `http://192.168.1.50:8000/`).

#### Opening and Running the Project
1. Open **Android Studio**.
2. Select **Open** and choose the `OptiScan/android` directory.
3. Allow Gradle to synchronize dependencies.
4. Select an Android Emulator (API 30+) or a physical device with USB debugging.
5. Click **Run 'app'** (`Shift + F10`).
6. Grant camera permission on launch when prompted.

---

## 5. Verification & Test Execution

### Backend Test Results
The backend test suite verifies:
- `/health` status endpoint
- Successful scene description via `describe_image`
- Interactive question answering
- Text recognition task dispatch
- Rejection of invalid tasks (`400 Bad Request`)
- Detection and error handling for non-image files and empty payloads
- Automatic recapture recommendation for uniform or blurry photos

```text
============================= test session starts =============================
platform win32 -- Python 3.14.0, pytest-8.4.2, pluggy-1.6.0
rootdir: E:\OptiScan\backend
configfile: pytest.ini
testpaths: tests
collected 8 items

tests/test_analyze.py::test_analyze_describe_image_success PASSED        [ 12%]
tests/test_analyze.py::test_analyze_describe_image_with_question PASSED  [ 25%]
tests/test_analyze.py::test_analyze_read_text_success PASSED             [ 37%]
tests/test_analyze.py::test_analyze_unsupported_task PASSED              [ 50%]
tests/test_analyze.py::test_analyze_non_image_file PASSED                [ 62%]
tests/test_analyze.py::test_analyze_empty_image_file PASSED              [ 75%]
tests/test_analyze.py::test_analyze_blank_image_prompts_recapture PASSED [ 87%]
tests/test_health.py::test_health_check_returns_ok PASSED                [100%]

======================== 8 passed in 0.61s ====================================
```

---

## 6. Implementation Status: Working vs. Scaffolding

| Component | Status | Details |
|---|---|---|
| **FastAPI Backend Core** | **Working** | Fully functional application with CORS, configuration, logging, and error handling. |
| **Backend `/health` Endpoint** | **Working** | Tested and verified with pytest. |
| **Backend `/analyze` Endpoint** | **Working** | Tested and verified multipart upload, validation, and task routing. |
| **Image Validation & Blur Heuristic** | **Working** | Pillow-based decoding, dimension check, and variance-of-edges sharpness detection. |
| **Mock Vision Provider** | **Working** | Returns deterministic, descriptive responses and handles optional scene questions. |
| **External Vision Provider** | **Scaffolding** | Integration stub ready to connect live Gemini/OpenAI API keys via environment variables. |
| **Android Compose UI & Theme** | **Working Scaffolding** | Complete accessible Compose layout, large buttons, ResultCard, and dynamic states. |
| **Android TextToSpeech** | **Working Scaffolding** | Complete wrapper with queue handling, locale fallback, and repetition trigger. |
| **CameraX Capture & Preview** | **Working Scaffolding** | Complete lifecycle-aware CameraManager and PreviewView composable. |
| **Google ML Kit OCR** | **Working Scaffolding** | On-device text recognition client with coroutine suspension wrapper. |
| **Android Retrofit & Repository** | **Working** | Retrofit client, OkHttp logging, AnalysisModels, and mock repository fallback. |

---

## 7. Next 3 Highest-Priority Tasks for Hackathon Demo

1. **Physical Device / Emulator Camera Verification:** Open the `android/` project in Android Studio, execute Gradle build, and run on a camera-enabled emulator or physical device to verify live viewfinder surface rendering.
2. **End-to-End Live Capture Integration:** Verify that tapping "Describe Scene" takes an actual photo frame from CameraX, posts it to the running backend at `10.0.2.2:8000/analyze`, and plays the resulting audio description through TTS.
3. **Connect Live Vision API (Optional Stretch):** Add a free-tier Google Gemini or OpenAI API key into `.env` and complete `ExternalVisionProviderStub` to compare live AI scene descriptions against mock responses.
