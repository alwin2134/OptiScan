# OptiScan Architecture & System Design

OptiScan is an AI-powered visual assistant built for blind and low-vision individuals. It pairs an accessible, high-contrast Android frontend with an extensible FastAPI backend to deliver real-time visual scene descriptions, printed text reading, and instant acoustic feedback.

---

## 1. High-Level Architecture Diagram

```mermaid
graph TD
    subgraph AndroidClient["Android Client (Kotlin + Jetpack Compose)"]
        UI["Accessible Compose UI<br/>(Large Targets, High Contrast)"]
        CamX["CameraX Module<br/>(Capture & Preview)"]
        TTS["Text-to-Speech Manager<br/>(Voice Feedback)"]
        MLKit["Google ML Kit<br/>(On-Device OCR)"]
        Repo["Scan Repository<br/>(Mock / Live Toggle)"]
        RetrofitClient["Retrofit & OkHttp<br/>(REST Client)"]
    end

    subgraph BackendService["Backend Service (FastAPI + Python 3.11+)"]
        API["FastAPI Routes<br/>(/health, /analyze)"]
        Val["Image Validation Service<br/>(Pillow / Sharpness Heuristic)"]
        Provider["Vision Provider Interface<br/>(Mock / Gemini / OpenAI)"]
    end

    subgraph ExternalServices["External AI Services (Optional)"]
        CloudVision["Google Gemini / OpenAI Vision API"]
    end

    CamX -->|Raw Photo| Repo
    Repo -->|Local Bitmap| MLKit
    MLKit -->|Extracted Text| TTS
    MLKit -->|Extracted Text| UI

    Repo -->|Multipart JPEG| RetrofitClient
    RetrofitClient -->|HTTP POST /analyze| API
    API --> Val
    Val -->|Valid Image| Provider
    Val -->|Blurry / Dark| API
    Provider -.->|Optional Live API| CloudVision
    Provider -->|Analysis JSON| API
    API -->|HTTP 200 AnalysisResponse| RetrofitClient
    RetrofitClient -->|AnalysisResponse| Repo
    Repo --> UI
    Repo --> TTS
```

---

## 2. Core Workflows & Sequence Diagrams

### 2.1. Workflow 1: On-Device Text Reading (ML Kit OCR)

To minimize latency and support offline operation, printed text recognition runs directly on the device using Google ML Kit.

```mermaid
sequenceDiagram
    autonumber
    actor User as Blind / Low-Vision User
    participant UI as Compose UI
    participant Cam as CameraX
    participant MLKit as ML Kit OCR Service
    participant TTS as TTS Manager

    User->>UI: Taps "Read Text" (Large Action Button)
    UI->>TTS: Spoken announcement ("Capturing photo for text reading")
    UI->>Cam: capturePhoto()
    Cam-->>UI: Returns JPEG ByteArray
    UI->>MLKit: recognizeText(Bitmap)
    alt Text Detected
        MLKit-->>UI: "CAUTION: AUTOMATIC DOOR - KEEP CLEAR"
        UI->>TTS: speak("CAUTION: AUTOMATIC DOOR - KEEP CLEAR")
        UI->>UI: Render ResultCard with large typography
    else No Text Detected
        MLKit-->>UI: "No printed text detected in the image."
        UI->>TTS: speak("No printed text detected.")
    end
```

### 2.2. Workflow 2: Surroundings Description & Scene Understanding

Scene description is dispatched to the backend service. The backend validates image quality and queries the active vision provider.

```mermaid
sequenceDiagram
    autonumber
    actor User as Blind / Low-Vision User
    participant UI as Compose UI
    participant Cam as CameraX
    participant Repo as ScanRepository
    participant Backend as FastAPI (/analyze)
    participant Val as ImageValidationService
    participant Vision as VisionProvider
    participant TTS as TTS Manager

    User->>UI: Taps "Describe Scene"
    UI->>TTS: Spoken announcement ("Analyzing scene, please wait")
    UI->>Cam: capturePhoto()
    Cam-->>UI: Returns JPEG ByteArray
    UI->>Repo: analyzeImage(bytes, task="describe_image")
    Repo->>Backend: POST /analyze (multipart: image, task)
    Backend->>Val: validate(image_bytes)

    alt Image Blurry / Lens Covered
        Val-->>Backend: is_valid=False, should_recapture=True, reason="Image is too blurry"
        Backend-->>Repo: 200 OK (status="recapture", should_recapture=true)
        Repo-->>UI: AnalysisResponse(shouldRecapture=true)
        UI->>TTS: speak("Recapture recommended. Image is too blurry. Please hold steady.")
        UI->>UI: Render prominent "Action Required: Retake Photo" banner
    else Image Quality Valid
        Val-->>Backend: is_valid=True
        Backend->>Vision: describe_image(image_bytes)
        Vision-->>Backend: AnalysisResponse(status="success", answer="A person is standing near a doorway...")
        Backend-->>Repo: 200 OK (status="success")
        Repo-->>UI: AnalysisResponse(answer=...)
        UI->>TTS: speak("A person is standing near a doorway...")
        UI->>UI: Render high-contrast ResultCard with "Repeat Spoken Output"
    end
```

---

## 3. Modular Architecture & Extension Points

### 3.1. Vision Provider Strategy (`VisionProvider`)
The backend decouples API routing from AI inference via an abstract strategy pattern:

- **`MockVisionProvider` (Default):** Enabled out-of-the-box for local testing, CI pipelines, and demos without requiring paid API keys or cloud credentials.
- **`ExternalVisionProviderStub`:** Ready-to-implement pluggable provider for Google Gemini (`gemini-1.5-flash`), OpenAI (`gpt-4o`), or Anthropic Claude.
- **Switching Mechanism:** Driven purely by `.env` variables (`VISION_PROVIDER=gemini` and `VISION_API_KEY=...`). No route modifications required.

### 3.2. Dual-Engine Text Recognition
- **Primary:** On-device Android ML Kit Text Recognition handles OCR with low latency, privacy preservation, and zero data cost.
- **Secondary / Fallback:** Backend `/analyze` accepts `task=read_text` as a secondary fallback if on-device model files are missing or cloud confirmation is needed.

### 3.3. Offline Mock Backend Mode
The Android `ScanRepositoryImpl` contains an internal mock toggle (`setMockMode(true)`). When the mobile device is not connected to the local development server, it can still demonstrate full capture, UI transitions, and speech flows.

---

## 4. Accessibility Architecture (A11y)

OptiScan adheres strictly to accessibility design principles for visual impairments:

1. **Large Touch Targets:** Action buttons have a minimum touch target height of 64dp (exceeding standard 48dp WCAG guidelines).
2. **High-Contrast Palette:** Pure dark background (`#121212`) paired with high-visibility primary yellow (`#FFD600`), cyan (`#00E5FF`), and crisp white text (`#FFFFFF`).
3. **Multi-Sensory Feedback:** Every visual transition (capturing, processing, completion, error) triggers corresponding spoken audio feedback via Android `TextToSpeech`.
4. **Independent Status Indicators:** Errors and warnings are distinguished by explicit textual headers and iconography, never relying on color alone.
5. **Acoustic Recapture Guidance:** When blur or occlusion is detected, the app clearly explains *why* the image was rejected and tells the user how to correct it (e.g. "Hold steady and ensure lens is unobstructed").
