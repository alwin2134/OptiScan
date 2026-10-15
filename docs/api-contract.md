# OptiScan API Contract & REST Specification

This document details the backend REST API specifications for the OptiScan service (`FastAPI`). It acts as the contract agreement between Android client developers and backend engineers.

---

## 1. General Conventions

- **Base URL (Local Development):** `http://localhost:8000/`
- **Base URL (Android Emulator):** `http://10.0.2.2:8000/`
- **Content-Type (Requests):** `multipart/form-data` for file uploads, standard query/params for GET.
- **Content-Type (Responses):** `application/json; charset=utf-8`
- **Field Consistency:** Response fields (`status`, `task`, `answer`, `should_recapture`, `reason`) remain present across both success and failure payloads to avoid null-pointer errors on clients.

---

## 2. Endpoints

### 2.1. GET `/health`

Inspects service availability and verifies basic network reachability.

#### Request
- **Method:** `GET`
- **Path:** `/health`
- **Headers:** `Accept: application/json`

#### Response (`200 OK`)
```json
{
  "status": "ok",
  "service": "optiscan-backend"
}
```

#### Field Specifications
| Field | Type | Description |
|---|---|---|
| `status` | string | Always `"ok"` when operational. |
| `service` | string | Identifier of the backend service (`"optiscan-backend"`). |

---

### 2.2. POST `/analyze`

Accepts an image captured from the camera, runs quality validation (size, format, blur), and dispatches to the active vision provider.

#### Request
- **Method:** `POST`
- **Path:** `/analyze`
- **Content-Type:** `multipart/form-data`

#### Form Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `image` | binary file | **Yes** | Captured image file. Supported formats: JPEG, PNG, WEBP. Maximum size: 8 MB. |
| `task` | string | **Yes** | Operation to perform. Allowed values: `describe_image`, `read_text`. |
| `question` | string | No | Optional natural-language question about the scene or text (e.g. "Is the path clear?"). |
| `ocr_text` | string | No | Optional pre-extracted text from client-side Google ML Kit OCR on Android. |
| `language` | string | No | Optional target response language: `"en"` (default) or `"hi"` (Hindi in Devanagari script). |

#### Responses

##### A. Successful Scene Description (`200 OK`)
```json
{
  "status": "success",
  "task": "describe_image",
  "answer": "A person is standing near an open doorway in a well-lit room. There is a desk with a computer monitor to your right.",
  "should_recapture": false,
  "reason": null
}
```

##### B. Successful Text Reading Fallback (`200 OK`)
```json
{
  "status": "success",
  "task": "read_text",
  "answer": "ROOM 302 - ACCESSIBLE ENTRANCE. PLEASE RING BELL FOR ASSISTANCE.",
  "should_recapture": false,
  "reason": null
}
```

##### C. Recapture Recommendation (Blur / Dark / Occlusion) (`200 OK`)
When the uploaded image format is valid but visual quality is insufficient for AI perception (e.g. motion blur, covered camera lens, extreme underexposure), the backend returns a `recapture` recommendation with actionable guidance.

```json
{
  "status": "recapture",
  "task": "describe_image",
  "answer": null,
  "should_recapture": true,
  "reason": "Image is too blurry. Please hold steady and recapture."
}
```

##### D. Bad Request / Validation Failure (`400 Bad Request`)
Returned when mandatory parameters are missing, an unsupported task name is provided, or the file payload is corrupt / empty / not an image.

```json
{
  "status": "error",
  "task": "unknown_task",
  "answer": null,
  "should_recapture": false,
  "reason": "Unsupported task 'unknown_task'. Allowed tasks are 'describe_image' and 'read_text'."
}
```

##### E. Internal Server Error (`500 Internal Server Error`)
```json
{
  "status": "error",
  "task": "describe_image",
  "answer": null,
  "should_recapture": false,
  "reason": "An unexpected internal error occurred while analyzing the image."
}
```

---

## 3. Client-Backend Contract Mapping

This table defines the 1-to-1 correspondence between backend Python models and Android Kotlin models:

| Backend Field (`AnalysisResponse`) | Android Field (`AnalysisResponse`) | Kotlin Type | Description |
|---|---|---|---|
| `status` | `status` | `String` | `"success"`, `"error"`, `"recapture"` |
| `task` | `task` | `String` | `"describe_image"`, `"read_text"` |
| `answer` | `answer` | `String?` | Spoken readout or scene description |
| `should_recapture` | `shouldRecapture` | `Boolean` | Flag triggering client retake prompt |
| `reason` | `reason` | `String?` | Guidance message explaining recapture/error |

---

## 4. Curl Verification Commands

### Test Health Check
```bash
curl -X GET http://localhost:8000/health
```

### Test Scene Description with Image Upload
```bash
curl -X POST http://localhost:8000/analyze \
  -F "image=@sample.jpg;type=image/jpeg" \
  -F "task=describe_image" \
  -F "question=What is in front of me?"
```

### Test OCR Fallback
```bash
curl -X POST http://localhost:8000/analyze \
  -F "image=@sample.jpg;type=image/jpeg" \
  -F "task=read_text"
```
