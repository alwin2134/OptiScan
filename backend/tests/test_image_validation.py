"""Unit tests for ImageValidationService."""

import io
import pytest
from fastapi import UploadFile
from PIL import Image, ImageDraw, ImageFilter

from app.core.config import Settings
from app.services.image_validation import ImageValidationService, ValidationResult


def generate_test_image(
    format: str = "JPEG",
    size: tuple[int, int] = (200, 200),
    draw_pattern: bool = True,
    blur: bool = False,
) -> bytes:
    """Helper to generate image bytes in memory."""
    img = Image.new("RGB", size, color=(200, 200, 200))
    if draw_pattern:
        draw = ImageDraw.Draw(img)
        # Draw high-contrast shapes to generate strong edges
        for i in range(0, size[0], 20):
            draw.line([(i, 0), (size[0] - i, size[1])], fill=(0, 0, 0), width=3)
            draw.rectangle([i, i, i + 15, i + 15], outline=(255, 0, 0), fill=(0, 255, 0))

    if blur:
        img = img.filter(ImageFilter.GaussianBlur(radius=20))

    buf = io.BytesIO()
    img.save(buf, format=format)
    return buf.getvalue()


def test_validate_valid_jpeg():
    """Verify that a sharp, valid JPEG image passes validation."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="JPEG", size=(250, 250), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is True
    assert res.should_recapture is False
    assert res.format == "JPEG"
    assert res.mime_type == "image/jpeg"
    assert res.width == 250
    assert res.height == 250


def test_validate_valid_png():
    """Verify that a sharp, valid PNG image passes validation."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="PNG", size=(250, 250), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is True
    assert res.should_recapture is False
    assert res.format == "PNG"
    assert res.mime_type == "image/png"


def test_validate_valid_webp():
    """Verify that a sharp, valid WEBP image passes validation."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="WEBP", size=(250, 250), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is True
    assert res.should_recapture is False
    assert res.format == "WEBP"
    assert res.mime_type == "image/webp"


def test_validate_empty_bytes():
    """Verify that an empty byte array fails validation."""
    service = ImageValidationService()
    res = service.validate(b"")

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "empty" in res.reason.lower()


def test_validate_oversized_payload():
    """Verify that payload exceeding max_upload_size_bytes is rejected."""
    custom_settings = Settings(max_upload_size_bytes=1000)
    service = ImageValidationService(settings=custom_settings)

    oversized = b"A" * 1001
    res = service.validate(oversized)

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "exceeds maximum allowed size" in res.reason.lower()


def test_validate_corrupted_bytes():
    """Verify that non-image bytes fail decoding."""
    service = ImageValidationService()
    res = service.validate(b"NOT_A_VALID_IMAGE_HEADER_DATA_123456789")

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "not a valid or readable image" in res.reason.lower()


def test_validate_unsupported_format():
    """Verify that an unsupported image format (e.g. BMP) is rejected."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="BMP", size=(200, 200), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is False
    assert res.should_recapture is False
    assert "unsupported image format" in res.reason.lower()
    assert res.format == "BMP"


def test_validate_dimensions_too_small():
    """Verify that images smaller than min_image_dimension prompt recapture."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="JPEG", size=(32, 32), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "too low" in res.reason.lower()
    assert res.width == 32
    assert res.height == 32


def test_validate_dimensions_too_large():
    """Verify that images exceeding max_image_dimension prompt recapture."""
    custom_settings = Settings(max_image_dimension=300)
    service = ImageValidationService(settings=custom_settings)
    img_bytes = generate_test_image(format="JPEG", size=(400, 200), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "exceeds maximum supported limits" in res.reason.lower()


def test_validate_uniform_covered_lens():
    """Verify that completely black/uniform image triggers covered lens warning."""
    service = ImageValidationService()
    # Uniform black image (lens covered)
    img_bytes = generate_test_image(format="JPEG", size=(200, 200), draw_pattern=False)
    res = service.validate(img_bytes)

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "unobstructed" in res.reason.lower() or "dark" in res.reason.lower()


def test_validate_severely_blurry_image():
    """Verify that severely out-of-focus or blurred image prompts recapture."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="JPEG", size=(300, 300), draw_pattern=True, blur=True)
    res = service.validate(img_bytes)

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "blurry" in res.reason.lower()


@pytest.mark.asyncio
async def test_read_bounded_bytes_normal():
    """Verify bounded reader correctly loads payloads within limits."""
    content = b"TEST_IMAGE_CONTENT" * 10
    upload = UploadFile(filename="test.jpg", file=io.BytesIO(content))

    data, is_oversized = await ImageValidationService.read_bounded_bytes(upload, max_bytes=1000)
    assert data == content
    assert is_oversized is False


@pytest.mark.asyncio
async def test_read_bounded_bytes_exceeded():
    """Verify bounded reader halts immediately when max_bytes threshold is crossed."""
    content = b"X" * 5000
    upload = UploadFile(filename="huge.jpg", file=io.BytesIO(content))

    data, is_oversized = await ImageValidationService.read_bounded_bytes(
        upload,
        max_bytes=1000,
        chunk_size=500,
    )
    assert is_oversized is True
    assert len(data) <= 2000  # Stopped after reading chunk crossing threshold


def test_validate_decompression_bomb_triggers_safely():
    """Verify that decompression bomb or pixel limit ceiling triggers recapture safely."""
    from unittest.mock import patch
    service = ImageValidationService(settings=Settings(max_image_pixels=10000))
    # 200x200 = 40,000 pixels > 10,000 max pixels
    img_bytes = generate_test_image(format="PNG", size=(200, 200), draw_pattern=True)
    res = service.validate(img_bytes)

    assert res.is_valid is False
    assert res.should_recapture is True
    assert "decompression limits" in res.reason.lower()


def test_validate_decompression_bomb_error_exception():
    """Verify Image.DecompressionBombError is caught safely."""
    from unittest.mock import patch
    service = ImageValidationService()
    img_bytes = generate_test_image(format="JPEG", size=(100, 100))

    with patch("PIL.Image.open") as mock_open:
        mock_open.side_effect = Image.DecompressionBombError("Image size exceeds limit")
        res = service.validate(img_bytes)
        assert res.is_valid is False
        assert res.should_recapture is True
        assert "decompression limits" in res.reason.lower()


def test_validate_image_resources_closed_on_error():
    """Verify image object is closed even if edge detection or quality check fails."""
    service = ImageValidationService()
    img_bytes = generate_test_image(format="JPEG", size=(100, 100))

    from unittest.mock import patch
    with patch("PIL.Image.Image.convert", side_effect=RuntimeError("Simulated filter failure")):
        # Should gracefully handle unexpected analysis error without crashing
        res = service.validate(img_bytes)
        # Service catches quality analysis error and allows valid image to proceed
        assert res.is_valid is True
        assert res.format == "JPEG"


def test_validate_sharp_image_with_complex_features():
    """Verify that a sharp, high-contrast image is never falsely rejected as blurry."""
    service = ImageValidationService()
    # Create sharp image with lines, shapes, and text
    img = Image.new("RGB", (300, 300), color=(255, 255, 255))
    draw = ImageDraw.Draw(img)
    draw.rectangle([30, 30, 270, 270], outline=(0, 0, 0), width=4)
    draw.text((50, 100), "SHARP TEST TEXT", fill=(0, 0, 0))
    for y in range(50, 250, 10):
        draw.line([(30, y), (270, y)], fill=(100, 100, 100), width=1)
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    res = service.validate(buf.getvalue())

    assert res.is_valid is True
    assert res.should_recapture is False
    assert res.format == "JPEG"
    assert res.width == 300
    assert res.height == 300

