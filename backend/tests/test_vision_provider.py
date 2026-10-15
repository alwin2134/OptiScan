"""Unit tests for VisionProvider implementations and factory."""

import pytest
from unittest.mock import AsyncMock, patch, MagicMock
import httpx

from app.core.config import Settings
from app.services.vision_provider import (
    GeminiVisionProvider,
    MockVisionProvider,
    VisionProviderError,
    get_vision_provider,
)


@pytest.mark.asyncio
async def test_mock_provider_describe_image_without_question():
    """Verify MockVisionProvider produces accessibility-focused description."""
    provider = MockVisionProvider()
    response = await provider.describe_image(image_bytes=b"dummy_image_data")

    assert response.status == "success"
    assert response.task == "describe_image"
    assert response.should_recapture is False
    assert "mock" in response.answer.lower()
    assert "mock" in response.reason.lower()
    assert "path" in response.answer.lower() or "hallway" in response.answer.lower()


@pytest.mark.asyncio
async def test_mock_provider_describe_image_with_question():
    """Verify MockVisionProvider answers specific question first."""
    provider = MockVisionProvider()
    response = await provider.describe_image(
        image_bytes=b"dummy_image_data",
        question="Is there a water bottle?",
    )

    assert response.status == "success"
    assert "Is there a water bottle?" in response.answer
    assert "direct answer" in response.answer.lower()


@pytest.mark.asyncio
async def test_mock_provider_ocr_with_mlkit_text():
    """Verify MockVisionProvider returns on-device ML Kit text."""
    provider = MockVisionProvider()
    response = await provider.process_ocr(
        image_bytes=b"",
        ocr_text="ASPIRIN 100MG",
        question="What is the medicine name?",
    )

    assert response.status == "success"
    assert "ASPIRIN 100MG" in response.answer
    assert "What is the medicine name?" in response.answer
    assert "Google ML Kit" in response.reason


@pytest.mark.asyncio
async def test_mock_provider_ocr_without_mlkit_text():
    """Verify MockVisionProvider declares fallback when client OCR text is absent."""
    provider = MockVisionProvider()
    response = await provider.process_ocr(image_bytes=b"dummy_image")

    assert response.status == "success"
    assert "mock ocr fallback" in response.answer.lower()
    assert "Google ML Kit on Android handles primary" in response.reason


@pytest.mark.asyncio
async def test_gemini_provider_empty_bytes_raises_error():
    """Verify GeminiVisionProvider raises error when empty image bytes are passed."""
    provider = GeminiVisionProvider(api_key="fake-test-key")
    with pytest.raises(VisionProviderError, match="cannot be empty"):
        await provider.describe_image(image_bytes=b"")


@pytest.mark.asyncio
async def test_gemini_provider_successful_request():
    """Verify GeminiVisionProvider parses valid response and sends headers & mime type."""
    provider = GeminiVisionProvider(api_key="valid-test-key", model_name="gemini-1.5-flash")

    mock_response_data = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {
                            "text": "Directly in front of you is a clear wooden dining table with a coffee mug to your right."
                        }
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }

    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value=mock_response_data),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        res = await provider.describe_image(
            image_bytes=b"fake-image-bytes",
            question="What is on the table?",
            mime_type="image/png",
        )

        assert res.status == "success"
        assert "dining table" in res.answer
        assert res.should_recapture is False

        # Verify call arguments
        call_kwargs = mock_post.call_args.kwargs
        assert call_kwargs["headers"]["x-goog-api-key"] == "valid-test-key"
        payload = call_kwargs["json"]
        assert "system_instruction" in payload
        assert payload["contents"][0]["parts"][1]["inline_data"]["mime_type"] == "image/png"


@pytest.mark.asyncio
async def test_gemini_provider_unauthorized_key():
    """Verify 401/403 status returns a clear credential error message."""
    provider = GeminiVisionProvider(api_key="bad-key")

    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=401,
        json=MagicMock(return_value={"error": {"message": "API key not valid."}}),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="Invalid or unauthorized Gemini API key"):
            await provider.describe_image(image_bytes=b"fake-bytes")


@pytest.mark.asyncio
async def test_gemini_provider_rate_limit_exceeded():
    """Verify 429 status returns clear rate limit message."""
    provider = GeminiVisionProvider(api_key="valid-key")

    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=429,
        json=MagicMock(return_value={"error": {"message": "Resource exhausted."}}),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="rate limit or quota exceeded"):
            await provider.describe_image(image_bytes=b"fake-bytes")


@pytest.mark.asyncio
async def test_gemini_provider_model_not_found():
    """Verify 404 status returns model not found message."""
    provider = GeminiVisionProvider(api_key="valid-key", model_name="nonexistent-model")

    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=404,
        json=MagicMock(return_value={"error": {"message": "Model not found."}}),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="was not found"):
            await provider.describe_image(image_bytes=b"fake-bytes")


@pytest.mark.asyncio
async def test_gemini_provider_timeout():
    """Verify network timeouts raise sanitized VisionProviderError."""
    provider = GeminiVisionProvider(api_key="valid-key")

    mock_post = AsyncMock(side_effect=httpx.TimeoutException("Request timed out"))

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="timed out"):
            await provider.describe_image(image_bytes=b"fake-bytes")


@pytest.mark.asyncio
async def test_gemini_provider_safety_blocked():
    """Verify safety blocks raise descriptive error."""
    provider = GeminiVisionProvider(api_key="valid-key")

    mock_response_data = {
        "promptFeedback": {"blockReason": "SAFETY"}
    }
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value=mock_response_data),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="blocked by safety policy"):
            await provider.describe_image(image_bytes=b"fake-bytes")


def test_get_vision_provider_factory_mock():
    """Verify default settings creates MockVisionProvider."""
    settings = Settings(vision_provider="mock")
    provider = get_vision_provider(settings=settings)
    assert isinstance(provider, MockVisionProvider)


def test_get_vision_provider_factory_gemini_missing_key():
    """Verify factory raises ValueError when gemini configured without key."""
    settings = Settings(vision_provider="gemini", vision_api_key=None)
    with pytest.raises(ValueError, match="VISION_PROVIDER is set to 'gemini' but VISION_API_KEY is missing"):
        get_vision_provider(settings=settings)


def test_get_vision_provider_factory_gemini_valid_key():
    """Verify factory creates GeminiVisionProvider when configured with key."""
    settings = Settings(vision_provider="gemini", vision_api_key="real-gemini-key")
    provider = get_vision_provider(settings=settings)
    assert isinstance(provider, GeminiVisionProvider)
    assert provider.api_key == "real-gemini-key"


def test_accessibility_system_prompt_includes_critical_safeguards():
    """Verify system instruction contains mandatory blind accessibility & safety rules."""
    from app.services.vision_provider import SYSTEM_INSTRUCTION_ACCESSIBILITY

    prompt = SYSTEM_INSTRUCTION_ACCESSIBILITY.lower()
    # 1. Direct answer requirement
    assert "direct answer" in prompt
    # 2. Spatial awareness
    assert "spatial" in prompt
    assert "relative to the user" in prompt
    # 3. Path safety disclaimer & hazard detection
    assert "never guarantee" in prompt or "cannot guarantee" in prompt
    assert "hazard" in prompt
    # 4. Medicine & dosage safety
    assert "medicine" in prompt or "medication" in prompt
    assert "dosage" in prompt
    # 5. Honest uncertainty without hallucination
    assert "uncertainty" in prompt
    assert "never invent" in prompt or "guessing" in prompt
    # 6. Concise for TTS
    assert "text-to-speech" in prompt or "tts" in prompt
    # 7. Elevation / stairs caution
    assert "elevation" in prompt or "stairs" in prompt or "curbs" in prompt


@pytest.mark.asyncio
async def test_gemini_provider_empty_candidates_error():
    """Verify empty candidate list raises VisionProviderError without inventing data."""
    provider = GeminiVisionProvider(api_key="valid-key")
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value={"candidates": []}),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="returned no analysis candidates"):
            await provider.describe_image(image_bytes=b"fake-bytes")


@pytest.mark.asyncio
async def test_gemini_provider_empty_text_parts_error():
    """Verify candidates with empty parts raises VisionProviderError."""
    provider = GeminiVisionProvider(api_key="valid-key")
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value={"candidates": [{"content": {"parts": [{"text": "   "}]}}]}),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        with pytest.raises(VisionProviderError, match="returned an empty description"):
            await provider.describe_image(image_bytes=b"fake-bytes")


@pytest.mark.asyncio
async def test_mock_provider_hindi_describe_image():
    """Verify MockVisionProvider produces Hindi scene description in Devanagari script."""
    provider = MockVisionProvider()
    response = await provider.describe_image(image_bytes=b"dummy", language="hi")
    assert response.status == "success"
    assert any("\u0900" <= c <= "\u097F" for c in response.answer)
    assert "गलियारा" in response.answer or "रास्ता" in response.answer


@pytest.mark.asyncio
async def test_mock_provider_hindi_ocr_preserves_medicine_and_numbers():
    """Verify MockVisionProvider Hindi OCR preserves verbatim medicine names and numbers."""
    provider = MockVisionProvider()
    response = await provider.process_ocr(
        image_bytes=b"",
        ocr_text="Amoxicillin 500mg Capsule",
        question="दवा क्या है?",
        language="hi",
    )
    assert response.status == "success"
    assert "Amoxicillin 500mg" in response.answer
    assert any("\u0900" <= c <= "\u097F" for c in response.answer)


@pytest.mark.asyncio
async def test_gemini_provider_hindi_payload_construction():
    """Verify GeminiVisionProvider embeds Hindi instruction and Devanagari prompt."""
    provider = GeminiVisionProvider(api_key="valid-key")
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value={
            "candidates": [{
                "content": {"parts": [{"text": "आपके सामने एक खुला दरवाज़ा है।"}]},
                "finishReason": "STOP",
            }]
        }),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        response = await provider.describe_image(image_bytes=b"jpeg-bytes", language="hi")
        assert response.status == "success"
        assert response.answer == "आपके सामने एक खुला दरवाज़ा है।"

        call_args = mock_post.call_args
        payload = call_args.kwargs["json"]
        # System instruction must include Hindi & medicine safety
        sys_text = payload["system_instruction"]["parts"][0]["text"]
        assert "MULTILINGUAL & HINDI SUPPORT" in sys_text
        assert "CRITICAL MEDICATION SAFETY" in sys_text
        # User prompt must request Hindi
        user_prompt = payload["contents"][0]["parts"][0]["text"]
        assert "Hindi" in user_prompt or "हिंदी" in user_prompt


@pytest.mark.asyncio
async def test_gemini_provider_ocr_with_question_and_image():
    """Verify GeminiVisionProvider queries multimodal Gemini when question and image are provided."""
    provider = GeminiVisionProvider(api_key="valid-key")
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value={
            "candidates": [{
                "content": {"parts": [{"text": "The dosage is 500mg twice daily with meals."}]},
                "finishReason": "STOP",
            }]
        }),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        response = await provider.process_ocr(
            image_bytes=b"dummy-image-bytes",
            ocr_text="AMOXICILLIN 500MG CAPSULE. TAKE 1 CAPSULE TWICE DAILY.",
            question="What is the dosage?",
            language="en",
        )
        assert response.status == "success"
        assert response.task == "read_text"
        assert "500mg twice daily" in response.answer
        assert "Gemini vision reasoning" in response.reason

        call_args = mock_post.call_args
        payload = call_args.kwargs["json"]
        parts = payload["contents"][0]["parts"]
        assert len(parts) == 2  # Text prompt + inline image data
        assert "User Question: 'What is the dosage?'" in parts[0]["text"]
        assert "AMOXICILLIN 500MG" in parts[0]["text"]


@pytest.mark.asyncio
async def test_gemini_provider_ocr_with_question_text_only():
    """Verify GeminiVisionProvider queries Gemini with text-only prompt when image is omitted."""
    provider = GeminiVisionProvider(api_key="valid-key")
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value={
            "candidates": [{
                "content": {"parts": [{"text": "खुराक दिन में दो बार 1 कैप्सूल भोजन के साथ है।"}]},
                "finishReason": "STOP",
            }]
        }),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        response = await provider.process_ocr(
            image_bytes=b"",
            ocr_text="PARACETAMOL 650MG",
            question="खुराक क्या है?",
            language="hi",
        )
        assert response.status == "success"
        assert response.task == "read_text"
        assert "खुराक" in response.answer
        assert "Gemini reasoning using OCR context" in response.reason

        call_args = mock_post.call_args
        payload = call_args.kwargs["json"]
        parts = payload["contents"][0]["parts"]
        assert len(parts) == 1  # Text only, no image bytes
        assert "PARACETAMOL 650MG" in parts[0]["text"]


@pytest.mark.asyncio
async def test_mock_provider_guide_task_progression():
    """Verify MockVisionProvider steps sequentially through guided tea preparation."""
    provider = MockVisionProvider()

    # Initial scan: observation + water confirmation request
    step0 = await provider.guide_task(image_bytes=b"dummy", goal="make tea", language="en")
    assert step0.status == "success"
    assert step0.task == "guided_task"
    assert step0.step_index == 0
    assert step0.needs_clarification is True
    assert "cup and a kettle" in step0.answer

    # Step 1
    step1 = await provider.guide_task(
        image_bytes=b"dummy",
        goal="make tea",
        step_history="",
        user_response="Yes, the kettle has water.",
        language="en",
    )
    assert step1.step_index == 1
    assert "Step 1" in step1.answer
    assert "plugged in" in step1.answer

    # Step 3 with safety warning
    step3 = await provider.guide_task(
        image_bytes=b"dummy",
        goal="make tea",
        step_history="step 1 completed, step 2 completed",
        user_response="tea bag is in cup",
        language="en",
    )
    assert step3.step_index == 3
    assert "Step 3" in step3.answer
    assert "hot water" in step3.answer or "hot liquids" in step3.answer

    # Stop command
    stop_resp = await provider.guide_task(
        image_bytes=b"dummy",
        goal="make tea",
        user_response="stop",
        language="en",
    )
    assert stop_resp.is_task_complete is True
    assert "stopped" in stop_resp.answer.lower()


@pytest.mark.asyncio
async def test_gemini_provider_guide_task():
    """Verify GeminiVisionProvider constructs AROMA/Be My Eyes safety prompt and parses response."""
    provider = GeminiVisionProvider(api_key="valid-key")
    mock_post = AsyncMock()
    mock_post.return_value = MagicMock(
        status_code=200,
        json=MagicMock(return_value={
            "candidates": [{
                "content": {"parts": [{"text": "I see a cup and a kettle on the counter. Is the kettle filled with water?"}]},
                "finishReason": "STOP",
            }]
        }),
    )

    with patch("httpx.AsyncClient.post", mock_post):
        response = await provider.guide_task(
            image_bytes=b"jpeg-bytes",
            goal="Help me make tea",
            language="en",
        )
        assert response.status == "success"
        assert response.task == "guided_task"
        assert response.needs_clarification is True
        assert "cup and a kettle" in response.answer

        call_args = mock_post.call_args
        payload = call_args.kwargs["json"]
        parts = payload["contents"][0]["parts"]
        assert len(parts) == 2  # Prompt instructions + inline image data
        assert "AROMA & Be My Eyes Safety Standard" in parts[0]["text"]
        assert "EXACTLY ONE next actionable, concise instruction" in parts[0]["text"]



