# OptiScan Vision AI Integration & Image Processing Pipeline

This document details the architecture, configuration, prompt engineering, and image processing pipeline of the OptiScan backend vision subsystem.

---

## 1. Subsystem Architecture

OptiScan decouples API routing from vision model vendors using a pluggable provider abstraction pattern (`VisionProvider`).

```
[Android Client / CameraX]
         │ (HTTP Multipart POST /analyze)
         ▼
[ImageValidationService]
   ├── Bounded stream reading (8 MB ceiling)
   ├── Pillow decoding & decompression bomb safeguard (MAX_IMAGE_PIXELS = 25M)
   ├── Dimension boundaries (64px <= w, h <= 8192px)
   ├── Format verification (JPEG, PNG, WEBP, MPO) -> MIME type resolution
   ├── Lens obstruction / darkness check (stddev < 2.0)
   └── Edge variance blur detection (threshold = 8.0 with boundary crop)
         │
         ├── [Invalid/Blurry/Dark] ──> Returns JSON { status: "recapture" | "error", should_recapture: true }
         ▼ [Valid Image]
[VisionProvider Factory]
         │
         ├── Mode: "mock"   ──> [MockVisionProvider] (Offline demos & automated testing)
         └── Mode: "gemini" ──> [GeminiVisionProvider] (Google Gemini REST Multimodal API)
```

---

## 2. Vision Provider Interface

Defined in `backend/app/services/vision_provider.py`:

```python
class VisionProvider(ABC):
    @abstractmethod
    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
    ) -> AnalysisResponse:
        """Generate an accessibility-first scene description or answer a question."""
        ...

    @abstractmethod
    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
    ) -> AnalysisResponse:
        """Process printed text (supporting on-device Google ML Kit OCR text)."""
        ...
```

### Available Implementations

### A. `MockVisionProvider`
- **Purpose**: Enables rapid frontend development, local testing, and offline hackathon demos without network dependency or API keys.
- **Tone & Accessibility**: Simulates high-quality accessibility descriptions (spatial directions, obstacles, walking paths).
- **Transparency**: Clearly indicates `[Mock ...]` in responses and `reason` fields so mock data is never misrepresented as genuine camera perception.

### B. `GeminiVisionProvider`
- **Model**: Default `gemini-2.5-flash` (configurable via `VISION_MODEL_NAME`).
- **Endpoint**: `https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent`.
- **Authentication**: Header-based authentication using `x-goog-api-key: <api_key>`. Passing the key in headers rather than query parameters prevents credential leakage in HTTP access logs, proxy logs, and error traces.
- **Latency & Streaming Optimization**: Configured with `"thinkingConfig": {"thinkingBudget": 0}` and `"maxOutputTokens": 1024`. This bypasses reasoning token overhead and provides ultra-fast (~2–3s) inference suitable for Text-to-Speech (TTS).
- **MIME Support**: Automatically propagates detected MIME type (`image/jpeg`, `image/png`, `image/webp`) into `inline_data.mime_type`.
- **System Instructions**: Transmits `SYSTEM_INSTRUCTION_ACCESSIBILITY` as a top-level `system_instruction` in the Gemini API payload.

---

## 3. Accessibility-First Prompt Engineering & Safety Guidelines

General AI vision prompts often produce flowery visual descriptions (colors, aesthetics, background scenery) that do not help blind or low-vision users safely interact with their environment.

OptiScan enforces strict accessibility and safety guidelines embedded in `SYSTEM_INSTRUCTION_ACCESSIBILITY`:

```text
1. DIRECT ANSWERS: If the user asked a question, answer it immediately in the first sentence.
2. SPATIAL AWARENESS: Describe locations relative to the user's perspective
   ('directly in front of you', 'to your left', 'to your right', 'at waist height', 'on the floor').
3. HAZARDS & PATHWAYS: Prioritize immediate obstacles, trip hazards, changes in elevation (steps/curbs),
   doorways, and visible walking path status. Do not claim absence of visible hazards proves no hazard exists;
   never guarantee a path or route is safe to navigate from a single 2D image.
4. HIGH-CONSEQUENCE CONTENT & MEDICINE: Transcribe text, medication packaging, and expiry dates accurately and verbatim.
   Never guess or invent dosage instructions, medical uses, or health decisions.
5. HONEST UNCERTAINTY: Explicitly state uncertainty when an object, text, distance, or detail is blurry,
   partially obscured, or unidentifiable. Never invent text, numbers, brands, or distances.
6. OBJECTIVE & CONCISE: Distinguish what is directly visible from what is inferred. Avoid unnecessary flowery
   descriptions of colors or aesthetic appearance when the user asks a practical question. Keep responses to 2-4 sentences for TTS.
7. CAUTION WITH ELEVATIONS & TRAFFIC: Express caution regarding curbs, drop-offs, stairs, and traffic, as depth and motion cannot be reliably judged from a single static photograph.
```

---

## 4. Image Validation Pipeline

Mobile users who are blind or low-vision cannot visually inspect whether an image is in-focus, centered, or unobstructed. The backend validation pipeline provides rapid automated feedback before invoking expensive AI models.

### Key Safeguards

| Check | Mechanism | Behavior on Failure |
|---|---|---|
| **Memory Denial of Service** | `read_bounded_bytes(chunk_size=64KB, max_bytes=8MB)` | Reading halts immediately if stream exceeds 8 MB without buffering remaining data. |
| **Decompression Bomb Protection** | `Image.MAX_IMAGE_PIXELS = 25_000_000` + Dimension cap (`8192px`) | Rejects malicious or memory-exhausting image bombs safely with `should_recapture: true`. |
| **Format & MIME Resolution** | Pillow header verification | Accepts `JPEG`, `PNG`, `WEBP`, `MPO`. Returns 400 for unsupported formats (e.g. BMP/GIF/TIFF). |
| **Covered Lens / Darkness** | Grayscale standard deviation (`stat.stddev[0] < 2.0`) | Detects covered camera or solid dark/light frames. Prompts user: *"The image is completely dark or uniform. Ensure the camera lens is unobstructed."* |
| **Blur Detection** | `FIND_EDGES` filter + 2px border crop (`edge_variance < 8.0`) | Boundary pixels are cropped before computing edge variance to eliminate Pillow 0-padding convolution artifacts. Sharp variance is typically >1000, severe blur is <8.0. |

---

## 5. Error Handling & Upstream API Safety

The backend guards against upstream AI failures and ensures zero credential exposure:

| Upstream Status | Internal Interpretation | User-Facing Response |
|---|---|---|
| **401 / 403** | Invalid or unauthorized Google API key | Logged on server; returns HTTP 502 with sanitized message *"Vision AI provider failed or timed out."* |
| **404** | Invalid model identifier | Caught safely with descriptive log for backend maintainer. |
| **429** | Gemini rate limit / quota exhaustion | Identifies quota limit reached, preventing crashes. |
| **Timeout (>25s)** | `httpx.TimeoutException` | Returns clean HTTP 502 without exposing stack traces. |
| **Safety Block** | `promptFeedback.blockReason` or `finishReason: "SAFETY"` | Returns clear explanation that content safety filter was triggered. |

---

## 6. How to Run the Backend Locally

1. Create and activate a Python virtual environment:
   ```bash
   cd backend
   python -m venv .venv
   .\.venv\Scripts\activate   # Windows PowerShell: .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   ```
2. Configure environment variables in `backend/.env`:
   ```bash
   cp .env.example .env
   ```
3. Run the development server with Uvicorn:
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```
4. Verify backend health:
   ```bash
   curl http://localhost:8000/health
   # Returns: {"status":"ok","service":"optiscan-backend"}
   ```

---

## 7. How to Configure Live Inference with Gemini

1. Obtain an API key from [Google AI Studio](https://aistudio.google.com/).
2. Copy `.env.example` to `backend/.env`:
   ```bash
   cp backend/.env.example backend/.env
   ```
3. Set provider, model, and key in `backend/.env`:
   ```ini
   VISION_PROVIDER=gemini
   VISION_API_KEY=your_actual_api_key_here
   VISION_MODEL_NAME=gemini-2.5-flash
   VISION_REQUEST_TIMEOUT_SECONDS=25.0
   ```
4. Restart the backend service:
   ```bash
   uvicorn app.main:app --reload
   ```

*Note: If no API key is configured or `VISION_PROVIDER=mock`, OptiScan runs in mock mode.*

---

## 8. Android Client & Emulator Connectivity

- **Android Emulator**:
  - The standard Android emulator maps the host computer's `localhost` (`127.0.0.1`) to `10.0.2.2`.
  - Android base URL: `http://10.0.2.2:8000/`.
- **Physical Device via USB**:
  - Run ADB reverse port forwarding on your development computer:
    ```bash
    adb reverse tcp:8000 tcp:8000
    ```
  - This forwards `http://127.0.0.1:8000/` on the physical device directly to port 8000 on your computer.
- **Physical Device via Local Wi-Fi**:
  - Update `NetworkConfig.activeBaseUrl` to your development machine's local IP (e.g. `http://192.168.1.50:8000/`).
  - Ensure the backend binds to `0.0.0.0:8000` (default) and your local firewall allows inbound connections on port 8000.

---

## 9. Known Accessibility and Quality Limitations

1. **Heuristic Blur Limitations**: Edge variance detects severe motion blur and out-of-focus captures, but low-texture surfaces (e.g., plain painted drywall or clear sky) have low natural edge variance. OptiScan couples edge variance with uniformity checks (`stddev < 2.0`) to minimize false positives, but cannot replace full semantic understanding of focus.
2. **2D Single-Image Navigation Limitations**: A single 2D camera photograph lacks stereoscopic depth and cannot detect velocity or trajectory of moving objects. As enforced in the prompt, OptiScan must never be used as a replacement for a white cane, guide dog, or primary orientation and mobility aid.
3. **Medication & Dosage Safety**: While Gemini 2.5 is proficient at reading label text, optical glare, curved bottles, or poor lighting can obscure crucial numbers or warnings. The system prompt instructs the model to transcribe text verbatim and explicitly forbid medical dosage recommendations.
