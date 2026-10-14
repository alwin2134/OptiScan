# OptiScan 4-Person Team Workflow & Parallelization Guide

This document outlines team ownership, code boundaries, git branching, and a time-boxed 4–6 hour roadmap for building the OptiScan hackathon demo.

---

## 1. Team Roles & File Ownership Boundaries

To eliminate merge conflicts and enable simultaneous parallel development, each engineer owns explicit files and directories.

| Role | Engineer | Core Responsibilities | Owned Files & Directories |
|---|---|---|---|
| **Frontend 1** | *Camera & Capture Lead* | CameraX lifecycle, permission handling, camera capture callback, preview rendering, and retake logic. | `android/.../camera/`<br/>`android/.../ocr/`<br/>`android/.../ui/components/CameraPreviewView.kt`<br/>`android/.../AndroidManifest.xml` (Permissions) |
| **Frontend 2** | *UI & Networking Lead* | Jetpack Compose screens, accessible typography, high-contrast buttons, Text-to-Speech manager, and Retrofit integration. | `android/.../ui/screens/`<br/>`android/.../ui/components/AccessibleButton.kt`<br/>`android/.../ui/components/ResultCard.kt`<br/>`android/.../ui/theme/`<br/>`android/.../speech/`<br/>`android/.../data/` |
| **Backend 1** | *API & Core Lead* | FastAPI application routing, Pydantic schemas, contract stability, endpoint integration tests, and GitHub Actions CI. | `backend/app/main.py`<br/>`backend/app/api/`<br/>`backend/app/schemas/`<br/>`backend/app/core/config.py`<br/>`backend/tests/test_health.py`<br/>`.github/workflows/` |
| **Backend 2** | *Vision & Validation Lead* | Image decoding, resolution checks, blur heuristic, vision provider interface, mock responses, and live provider stubs. | `backend/app/services/image_validation.py`<br/>`backend/app/services/vision_provider.py`<br/>`backend/tests/test_analyze.py`<br/>`backend/pytest.ini` |

---

## 2. Shared Interface Agreements

To work in parallel without blocking each other:

1. **Frontend 1 & Frontend 2:** Agree on `CameraManager.capturePhoto(context): Result<ByteArray>`. Frontend 2 can build and test the entire UI using mock byte arrays or sample drawables until the camera is wired up.
2. **Frontend 2 & Backend 1:** Agree on `docs/api-contract.md`. Frontend 2 uses `ScanRepositoryImpl(useMockBackend = true)` to test full UI and TTS loops without waiting for backend deployment.
3. **Backend 1 & Backend 2:** Agree on `ImageValidationService.validate(image_bytes)` and `VisionProvider.describe_image(image_bytes)`. Backend 1 connects endpoints using the defined signatures while Backend 2 refines blur detection and AI provider integration.

---

## 3. Git Branching & Merging Strategy

- **`main` / `master`:** Stable demo releases. Protected branch.
- **`develop`:** Integration branch where team members merge verified pull requests.
- **Feature Branches:**
  - `feat/camera-capture` (Frontend 1)
  - `feat/accessible-ui-tts` (Frontend 2)
  - `feat/api-routes-ci` (Backend 1)
  - `feat/blur-vision-provider` (Backend 2)

### Integration Rules
- Never push directly to `main` or `develop`.
- Run backend tests locally before pushing (`pytest`).
- Merge `develop` into your feature branch periodically to catch integration discrepancies early.

---

## 4. 4–6 Hour Hackathon Timeline

```text
Hour 0:00 - 0:45: Project Scaffolding & Setup (DONE)
├── Repository layout, Gradle files, FastAPI boilerplate, schemas, and tests established.
└── Every team member clones repo, creates local virtualenv and opens Android Studio.

Hour 0:45 - 2:00: Parallel Feature Implementation
├── Frontend 1: Implement CameraX viewfinder & capture callback in CameraManager.
├── Frontend 2: Complete HomeScreen composables, ResultCard, and TTS welcome message.
├── Backend 1: Solidify routes, CORS, multipart payload handling, and health tests.
└── Backend 2: Implement Pillow sharpness/blur heuristics and mock provider responses.

Hour 2:00 - 3:15: First Internal Integration
├── Connect Frontend 1 camera capture to Frontend 2 HomeScreenViewModel.
├── Verify Android on-device OCR using ML Kit with sample printed signs.
├── Connect Frontend 2 Retrofit client to Backend 1 FastAPI running on localhost:8000 via 10.0.2.2.
└── Verify end-to-end "Describe Scene" loop returning mock backend description.

Hour 3:15 - 4:30: Polish, Edge Cases & Recapture Loop
├── Test blurry photo scenario: Ensure backend returns should_recapture=true and app triggers TTS prompt.
├── Add optional question input / voice prompt trigger if time permits.
├── Test TalkBack screen reader accessibility and verify large touch targets.
└── Run full regression suite (`pytest` and Android model contract tests).

Hour 4:30 - 5:30: Rehearsal & Live Demo Prep
├── Lock down master branch.
├── Verify Android APK build.
├── Rehearse 3-minute pitch:
│   1. Problem: Navigation and reading hurdles for visually impaired individuals.
│   2. Demo 1: Instant on-device OCR reading a room sign aloud.
│   3. Demo 2: Scene understanding describing a doorway and walking path.
│   4. Demo 3: Recapture guidance when an image is blurred or dark.
```
