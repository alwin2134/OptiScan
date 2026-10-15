# Open-Source Assistive Vision Audit & Research Evaluation

This document presents a structured engineering, accessibility, licensing, and security audit of prominent open-source and research assistive-vision systems. It evaluates their relevance to the **OptiScan** architecture (Android Jetpack Compose + CameraX + ML Kit OCR + FastAPI + Gemini 2.5 Flash Vision).

---

## 1. Project Evaluation & Comparative Matrix

| Project / Reference | License / Status | Tech Stack | Architectural Compatibility | Reusability Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **SightBuddy** | MIT (2026 Drophouse Ltd) | Kotlin, Jetpack Compose, CameraX, Android Keystore, on-device Whisper & small LLMs | **High**: Native modern Android, Compose, accessibility-first design | **Adapt**: Spoken state confirmation, accessible directional haptics, discrete voice controls |
| **NavAIgate** | MIT (2025 Niteesh L) | Kotlin, Jetpack Compose, CameraX, direct Gemini SDK | **Medium**: Modern Android stack, but violates server-side secret isolation | **Learn From / Reject Code**: Adopts multi-prompt separation, but rejects direct client Gemini SDK & embedded keys |
| **SeeAssist** | **Unlicensed** (No explicit license file in repository) | Java, Android Views, CameraX, TensorFlow Lite (YOLOv5m, HSV color) | **Low**: Legacy Java Views, unlicensed IP risk, 100+ MB model asset bloat | **Reject**: Severe licensing risk; on-device YOLO models increase APK size without high reliability |
| **CogniVision** | **Unlicensed** (No explicit license file) | Flutter (Dart), Firebase, Node.js / Express, MongoDB, Socket.IO | **Incompatible**: Flutter/Dart frontend and Node/MongoDB backend | **Reject**: Fundamentally incompatible technology stack; introduces unnecessary cloud and framework bloat |
| **AROMA** | Research Paper (arXiv:2507.10963) | Mixed-Initiative BLV Cooking Assistance Research | **Conceptual**: Grounded multi-modal guidance, non-visual cues, safety | **Learn From**: Foundational inspiration for Guided Task Mode (single instruction, uncertainty verification) |
| **Be My Eyes & Gemini Guided Vision** | Commercial / Reference Interaction Model | GPT-4 Vision / Gemini Live conversational vision | **Reference Paradigm**: Interactive, audio-first step-by-step guidance | **Learn From**: Structured general-to-specific descriptions, audio earcons, explicit user verification loops |

---

## 2. In-Depth Project Audits

### A. SightBuddy
* **Repository**: [https://github.com/DrophouseLtd/SightBuddy](https://github.com/DrophouseLtd/SightBuddy)
* **License**: MIT License (Copyright 2026 Drophouse Ltd). Fully compatible with commercial and open-source use with standard attribution.
* **Relevant Implementation**:
  - Voice-guided directional audio and haptic feedback loops.
  - Granular TalkBack accessibility labelling across every surface.
  - Local settings persistence and Keystore-backed credential encryption.
  - Spoken playback rates (1.0x to 3.0x) and prompt audio state synchronization.
* **Useful Ideas & Reusable Components**:
  - Accessibility-first feedback patterns: announcing state transitions cleanly while giving users single-tap physical overrides.
  - Spoken confirmation on critical actions without blocking TalkBack or screen reader focus.
* **Compatibility with OptiScan**:
  - **High**: Shares Kotlin and Jetpack Compose foundations. 
* **Risks & Trade-offs**:
  - SightBuddy downloads large (2.6 GB) on-device models for offline LLM execution, which is unviable for lightweight hackathon and resource-constrained devices.
* **Explicit Decision**: **ADAPT**. Extract accessibility patterns (haptics, prompt speech cancellation, and voice command arbitration) while retaining OptiScan's server-brokered backend architecture.

---

### B. NavAIgate
* **Repository**: [https://github.com/NiteeshL/NavAIgate](https://github.com/NiteeshL/NavAIgate)
* **License**: MIT License (Copyright 2025 Niteesh L). Compatible with attribution.
* **Relevant Implementation**:
  - `GeminiAPI.kt`, `GeminiAPI 1.kt`, `GeminiAPI2.kt`: Separate prompt engineering modules for navigation, Q&A, and OCR.
* **Useful Ideas**:
  - Structured prompt separation: keeping system instructions for visual navigation distinct from text-reading or question-answering.
* **Compatibility with OptiScan**:
  - **Medium**: Uses Kotlin and CameraX, but directly invokes the Google AI client SDK from the Android device.
* **Security & Architectural Risks**:
  - **Critical Security Violation**: Embedding Gemini API keys inside the Android client APK exposes credentials to decompilation, quota abuse, and billing hijacking.
  - Direct client-to-Gemini streaming lacks centralized request bounded validation, rate limiting, and sanitization.
* **Explicit Decision**: **LEARN FROM / REJECT CODE**. Learn from its distinct prompt specialization, but strictly reject client-side Gemini calls. All AI requests must remain brokered through OptiScan's secure FastAPI backend.

---

### C. SeeAssist
* **Repository**: [https://github.com/DhruvByteCode/SeeAssist](https://github.com/DhruvByteCode/SeeAssist)
* **License**: **Unlicensed** (No license file present). Under default copyright law, all rights are reserved by the author.
* **Relevant Implementation**:
  - `ObjectDetection.java`, `Yolov5TFLiteDetector.java`, `ColorDetection.java`.
  - On-device TFLite models (`yolov5m-fp16.tflite`, `currency-fp16.tflite`).
* **Useful Ideas**:
  - Audio earcons (ding sound effects) to signal object presence.
* **Compatibility & Risks**:
  - **Low Compatibility**: Legacy Java Android Views (`activity_*.xml`) rather than Jetpack Compose.
  - **APK Bloat**: Bundling multiple TFLite weights (`yolov5m-fp16.tflite`) inflates the APK by over 100 MB.
  - **Legal/IP Risk**: Absence of an open-source license strictly prohibits copying or adapting code.
* **Explicit Decision**: **REJECT**. Do not copy or import code. Use lightweight system audio and vibration primitives instead of heavy TFLite models.

---

### D. CogniVision
* **Repository**: [https://github.com/Anudeep2005/CogniVision](https://github.com/Anudeep2005/CogniVision)
* **License**: **Unlicensed** (No license file present).
* **Relevant Implementation**:
  - Flutter / Dart mobile app (`lib/`) with Node.js / Express backend.
  - WebSocket (Socket.IO) audio streaming with Gemini 2.0 Live and YOLOv8n.
* **Compatibility & Risks**:
  - **Total Framework Mismatch**: Flutter/Dart and Node.js/MongoDB have zero compatibility with OptiScan's Kotlin Android app and Python FastAPI backend.
  - High operational complexity: requires maintaining MongoDB, Express, Socket.IO rooms, and Flutter toolchains.
* **Explicit Decision**: **REJECT**. Introducing Flutter or Node.js components would compromise OptiScan's clean architecture.

---

### E. AROMA (Mixed-Initiative Assistance for Non-Visual Cooking)
* **Reference**: Research Paper [arXiv:2507.10963](https://arxiv.org/abs/2507.10963)
* **Key Insights**:
  - **Mixed-Initiative Guidance**: The AI should not overwhelm the user with continuous monologue. Instead, it must guide step-by-step, providing **one actionable instruction at a time**.
  - **Physical State Verification**: Blind users rely on tactile and auditory feedback. The AI must explicitly verify the workspace (e.g., verifying whether a cup is empty or water is boiling) before issuing irreversible or hazardous instructions.
  - **Safety Conservatism**: Extreme caution around boiling water, hot appliances, open flames, and sharp utensils. The AI must never hallucinate that a stove is off or an item is cold.
* **Explicit Decision**: **LEARN FROM**. Serves as the primary behavioral specification for OptiScan's **Guided Task Mode**.

---

### F. Be My Eyes & Gemini Guided Vision
* **Reference**: [Be My Eyes](https://www.bemyeyes.com/) and [Gemini Guided Vision](https://blog.google/innovation-and-ai/products/gemini-app/guided-vision-gemini-live/)
* **Key Interaction Principles**:
  - **Step-by-step procedural assistance**: Breaking down multi-step tasks into clear, discrete sub-actions.
  - **State Clarity**: User commands: `"Next"`, `"Repeat"`, `"Scan again"`, `"Stop"`.
  - **Honest uncertainty**: Explicitly stating when visual details are ambiguous or outside camera view, prompting a rescan instead of guessing.
* **Explicit Decision**: **LEARN FROM**. Adopt the conversational task control paradigm.

---

## 3. Recommended Architectural Upgrades

Ranked by user impact and implementation feasibility:

1. **Guided Task Mode MVP (Highest Value)**:
   - Provide a voice-first, step-by-step assistance mode (e.g., "Help me make tea", "Help me organize medicines").
   - Discrete, testable session state machine (`IDLE`, `OBSERVING`, `AWAITING_CLARIFICATION`, `INSTRUCTING`, `AWAITING_USER_ACTION`, `COMPLETED`, `STOPPED`).
   - One actionable instruction at a time with verbal commands: **Next**, **Repeat**, **Scan again**, **Stop**.
   - Strict safety guards: conservative assertions regarding hot liquids, sharp utensils, and appliance dials.

2. **Visual Uncertainty & Grounded Verification**:
   - When key task prerequisites cannot be verified visually from the captured frame, prompt the user for clarification or a rescan before advancing.

3. **Offline Mock & Safety Guarantees**:
   - Provide realistic, fully declared mock guidance for Guided Task Mode when offline or running without Gemini credentials, guaranteeing zero mock fabrication in production.

4. **Bilingual Localization (English & Hindi)**:
   - Complete localized strings for all task prompts, button controls, voice command recognitions, and accessibility announcements.

---

## 4. Reusability and Attribution Summary

- **SightBuddy**: MIT License attribution maintained for accessibility interaction ideas (directional haptic feedback and TTS concurrency management).
- **AROMA & Be My Eyes**: Academic and product reference citations for procedural task guidance.
- **NavAIgate, SeeAssist, CogniVision**: Analyzed for competitive comparison; no code imported due to licensing restrictions, security hazards, or framework incompatibilities.
