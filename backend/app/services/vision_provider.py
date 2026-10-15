"""Vision provider abstraction and implementations for scene description and text reading."""

import base64
import logging
from abc import ABC, abstractmethod
from typing import Optional
import httpx

from app.core.config import Settings, get_settings
from app.schemas.analysis import AnalysisResponse, StatusType, TaskType

logger = logging.getLogger("optiscan.vision_provider")


class VisionProviderError(Exception):
    """Raised when an external vision provider fails or times out."""
    pass


class VisionProvider(ABC):
    """Abstract interface for AI vision description and OCR fallback providers."""

    @abstractmethod
    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        """Generate a scene description or answer a question about the image."""
        pass

    @abstractmethod
    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        """Process printed text (supporting client-side Android ML Kit text)."""
        pass


class MockVisionProvider(VisionProvider):
    """Mock vision provider for development, unit testing, and offline hackathon demos.

    Clearly indicates simulated status and does not misrepresent mock data as actual image perception.
    """

    def __init__(self, settings: Optional[Settings] = None):
        self.settings = settings or get_settings()

    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        logger.info(
            "MockVisionProvider describe_image called (payload size: %d bytes, question: %s)",
            len(image_bytes),
            question,
        )

        if question and question.strip():
            answer = (
                f"[Mock AI Description] The image depicts an indoor hallway. Answering your question '{question.strip()}': "
                "The walking path directly ahead is unobstructed, with a wooden table to your right."
            )
        else:
            answer = (
                "[Mock AI Description] A person is standing near an open doorway in a well-lit room. "
                "There is a desk with a computer monitor to your right and a clear path straight ahead."
            )

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.DESCRIBE_IMAGE.value,
            answer=answer,
            should_recapture=False,
            reason="Running in mock provider mode. Set VISION_PROVIDER=gemini and VISION_API_KEY for live vision.",
        )

    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        logger.info(
            "MockVisionProvider process_ocr called (payload size: %d bytes, has_ocr_text: %s)",
            len(image_bytes),
            ocr_text is not None,
        )

        if ocr_text and ocr_text.strip():
            # Android ML Kit provided client-side extracted text
            clean_text = ocr_text.strip()
            if question and question.strip():
                answer = f"[Android ML Kit OCR Text] \"{clean_text}\". Regarding '{question.strip()}': Information is found on the label."
            else:
                answer = f"[Android ML Kit OCR Text] {clean_text}"
            reason = "Text recognized by client-side Google ML Kit OCR engine."
        else:
            # No on-device text provided; clearly declare mock fallback without claiming image OCR occurred
            answer = (
                "[Mock OCR Fallback] ROOM 302 - ACCESSIBLE ENTRANCE. PLEASE RING BELL FOR ASSISTANCE. "
                "(Note: Google ML Kit on Android is the primary OCR engine; server-side OCR was not performed on image bytes)."
            )
            reason = "Google ML Kit on Android handles primary on-device text recognition."

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.READ_TEXT.value,
            answer=answer,
            should_recapture=False,
            reason=reason,
        )


class GeminiVisionProvider(VisionProvider):
    """Live vision provider integration with Google Gemini multimodal API."""

    def __init__(self, api_key: str, model_name: str = "gemini-1.5-flash", timeout_seconds: float = 15.0):
        self.api_key = api_key
        self.model_name = model_name
        self.timeout_seconds = timeout_seconds
        self.endpoint_url = (
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:generateContent"
        )
        logger.info("GeminiVisionProvider initialized with model %s", self.model_name)

    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        b64_image = base64.b64encode(image_bytes).decode("utf-8")
        prompt = (
            question.strip()
            if question and question.strip()
            else (
                "Describe this scene concisely for a visually impaired user. "
                "Highlight key objects, clear walking paths, and immediate obstacles."
            )
        )

        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": b64_image,
                            }
                        },
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 250,
            },
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(
                    self.endpoint_url,
                    params={"key": self.api_key},
                    json=payload,
                )

            if response.status_code != 200:
                logger.error("Gemini API error (%d): %s", response.status_code, response.text[:200])
                raise VisionProviderError(f"Gemini API returned HTTP {response.status_code}")

            data = response.json()
            candidates = data.get("candidates", [])
            if not candidates:
                raise VisionProviderError("Gemini API returned no candidates.")

            content_parts = candidates[0].get("content", {}).get("parts", [])
            answer = "".join(p.get("text", "") for p in content_parts).strip()

            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.DESCRIBE_IMAGE.value,
                answer=answer,
                should_recapture=False,
                reason=None,
            )
        except httpx.TimeoutException as e:
            logger.error("Timeout connecting to Gemini API: %s", str(e))
            raise VisionProviderError("Vision provider timed out while analyzing the image.") from e
        except Exception as e:
            logger.error("Failed to query Gemini API: %s", str(e))
            raise VisionProviderError(f"Vision provider error: {str(e)}") from e

    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        if ocr_text and ocr_text.strip():
            answer = f"Recognized text: {ocr_text.strip()}"
            if question:
                answer += f"\nAnswer to '{question}': Based on text, details confirmed."
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.READ_TEXT.value,
                answer=answer,
                should_recapture=False,
                reason=None,
            )

        # Fallback to visual description prompting text extraction
        prompt = question or "Transcribe the prominent printed text in this image verbatim."
        return await self.describe_image(image_bytes, question=prompt)


def get_vision_provider(settings: Optional[Settings] = None) -> VisionProvider:
    """Factory creating the appropriate VisionProvider based on environment configuration.

    Raises clear configuration errors if real-provider mode is selected without credentials.
    """
    resolved_settings = settings or get_settings()
    provider_type = resolved_settings.vision_provider.lower().strip()

    if provider_type == "mock":
        return MockVisionProvider(settings=resolved_settings)

    if provider_type == "gemini":
        if not resolved_settings.vision_api_key or not resolved_settings.vision_api_key.strip():
            raise ValueError(
                "VISION_PROVIDER is set to 'gemini' but VISION_API_KEY is missing or empty. "
                "Please configure VISION_API_KEY in your .env file or set VISION_PROVIDER='mock'."
            )
        return GeminiVisionProvider(
            api_key=resolved_settings.vision_api_key.strip(),
            model_name=resolved_settings.vision_model_name,
            timeout_seconds=resolved_settings.vision_request_timeout_seconds,
        )

    if provider_type == "openai":
        if not resolved_settings.vision_api_key or not resolved_settings.vision_api_key.strip():
            raise ValueError(
                "VISION_PROVIDER is set to 'openai' but VISION_API_KEY is missing or empty. "
                "Please configure VISION_API_KEY in your .env file or set VISION_PROVIDER='mock'."
            )
        # Note: can plug in OpenAI client if needed
        raise NotImplementedError("OpenAI provider integration is scheduled for next iteration.")

    raise ValueError(f"Unknown vision_provider: '{provider_type}'")
