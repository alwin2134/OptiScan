"""Vision provider abstraction and implementations for scene description and text reading."""

import logging
from abc import ABC, abstractmethod
from typing import Optional

from app.core.config import Settings, get_settings
from app.schemas.analysis import AnalysisResponse, StatusType, TaskType

logger = logging.getLogger("optiscan.vision_provider")


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
    async def read_text(
        self,
        image_bytes: bytes,
    ) -> AnalysisResponse:
        """Extract printed text from the image (note: Android ML Kit is primary client-side OCR)."""
        pass


class MockVisionProvider(VisionProvider):
    """Mock vision provider for development, unit testing, and offline hackathon demos."""

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
                f"I see an indoor office environment. Regarding your question '{question.strip()}': "
                "The walking path directly ahead is unobstructed, with a wooden table to your right."
            )
        else:
            answer = (
                "A person is standing near an open doorway in a well-lit room. "
                "There is a desk with a computer monitor to your right and a clear path straight ahead."
            )

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.DESCRIBE_IMAGE.value,
            answer=answer,
            should_recapture=False,
            reason=None,
        )

    async def read_text(
        self,
        image_bytes: bytes,
    ) -> AnalysisResponse:
        logger.info(
            "MockVisionProvider read_text fallback called (payload size: %d bytes)",
            len(image_bytes),
        )
        # Android ML Kit handles client-side on-device OCR for low latency and privacy;
        # backend provides this endpoint as fallback or secondary validation.
        answer = "ROOM 302 - ACCESSIBLE ENTRANCE. PLEASE RING BELL FOR ASSISTANCE."

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.READ_TEXT.value,
            answer=answer,
            should_recapture=False,
            reason=None,
        )


class ExternalVisionProviderStub(VisionProvider):
    """Extension template for connecting third-party vision APIs (e.g. Gemini, OpenAI, Claude).

    Activated when VISION_PROVIDER is configured and VISION_API_KEY is present in environment.
    """

    def __init__(self, api_key: str, model_name: Optional[str] = None):
        self.api_key = api_key
        self.model_name = model_name or "gemini-1.5-flash"
        logger.info("ExternalVisionProviderStub initialized with model %s", self.model_name)

    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
    ) -> AnalysisResponse:
        # Template for future live API integration:
        # Format payload, call external vision endpoint via httpx, return AnalysisResponse.
        logger.info("External vision provider integration stub called with image size %d", len(image_bytes))
        prompt = question or "Describe this scene concisely for a visually impaired user, noting obstacles and orientation."
        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.DESCRIBE_IMAGE.value,
            answer=f"[Live Vision Stub: {self.model_name}] A clear view of the area answering prompt: {prompt}",
            should_recapture=False,
            reason=None,
        )

    async def read_text(
        self,
        image_bytes: bytes,
    ) -> AnalysisResponse:
        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.READ_TEXT.value,
            answer="[Live Vision Stub] On-device Android ML Kit is recommended for OCR.",
            should_recapture=False,
            reason=None,
        )


def get_vision_provider(settings: Optional[Settings] = None) -> VisionProvider:
    """Factory creating the appropriate VisionProvider based on environment configuration."""
    resolved_settings = settings or get_settings()
    provider_type = resolved_settings.vision_provider.lower().strip()

    if provider_type in {"gemini", "openai", "external"} and resolved_settings.vision_api_key:
        logger.info("Instantiating external vision provider (%s)", provider_type)
        return ExternalVisionProviderStub(
            api_key=resolved_settings.vision_api_key,
            model_name=resolved_settings.vision_model_name,
        )

    logger.info("Using MockVisionProvider for image analysis")
    return MockVisionProvider(settings=resolved_settings)
