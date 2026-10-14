"""Tests for the /analyze endpoint including tasks, validation, and error states."""

import io
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

from app.main import app

client = TestClient(app)


def create_test_image(width: int = 200, height: int = 200, format: str = "JPEG") -> bytes:
    """Generate a synthetic in-memory image with sharp edges for testing."""
    image = Image.new("RGB", (width, height), color=(240, 240, 240))
    draw = ImageDraw.Draw(image)
    # High-contrast geometric shapes to satisfy edge sharpness check
    draw.rectangle([20, 20, 100, 100], fill=(20, 20, 20))
    draw.line([0, 0, width, height], fill=(200, 0, 0), width=4)
    draw.line([0, height, width, 0], fill=(0, 0, 200), width=4)
    draw.text((30, 40), "OPTISCAN", fill=(255, 255, 255))

    buf = io.BytesIO()
    image.save(buf, format=format)
    return buf.getvalue()


def create_blank_uniform_image(width: int = 150, height: int = 150) -> bytes:
    """Generate a uniform blank image (e.g. lens covered) to test recapture."""
    image = Image.new("RGB", (width, height), color=(0, 0, 0))
    buf = io.BytesIO()
    image.save(buf, format="JPEG")
    return buf.getvalue()


def test_analyze_describe_image_success():
    """Verify describe_image returns successful scene description."""
    img_bytes = create_test_image()
    files = {"image": ("test.jpg", img_bytes, "image/jpeg")}
    data = {"task": "describe_image"}

    response = client.post("/analyze", files=files, data=data)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "success"
    assert payload["task"] == "describe_image"
    assert payload["answer"] is not None
    assert len(payload["answer"]) > 0
    assert payload["should_recapture"] is False
    assert payload["reason"] is None


def test_analyze_describe_image_with_question():
    """Verify describe_image handles interactive questions."""
    img_bytes = create_test_image()
    files = {"image": ("test.jpg", img_bytes, "image/jpeg")}
    data = {
        "task": "describe_image",
        "question": "Is there a doorway ahead?",
    }

    response = client.post("/analyze", files=files, data=data)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "success"
    assert "doorway" in payload["answer"].lower() or "question" in payload["answer"].lower()


def test_analyze_read_text_success():
    """Verify read_text task returns OCR result payload."""
    img_bytes = create_test_image()
    files = {"image": ("test.jpg", img_bytes, "image/jpeg")}
    data = {"task": "read_text"}

    response = client.post("/analyze", files=files, data=data)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "success"
    assert payload["task"] == "read_text"
    assert payload["answer"] is not None
    assert payload["should_recapture"] is False


def test_analyze_unsupported_task():
    """Verify error status and 400 when an invalid task is requested."""
    img_bytes = create_test_image()
    files = {"image": ("test.jpg", img_bytes, "image/jpeg")}
    data = {"task": "invalid_task_name"}

    response = client.post("/analyze", files=files, data=data)
    assert response.status_code == 400

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["answer"] is None
    assert "Unsupported task" in payload["reason"]


def test_analyze_non_image_file():
    """Verify error status and recapture flag when uploaded file is not an image."""
    fake_data = b"Hello, this is just plain text, not a valid image."
    files = {"image": ("document.txt", fake_data, "text/plain")}
    data = {"task": "describe_image"}

    response = client.post("/analyze", files=files, data=data)
    assert response.status_code == 400

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["should_recapture"] is True
    assert "not a valid" in payload["reason"]


def test_analyze_empty_image_file():
    """Verify error status when uploaded image is empty."""
    files = {"image": ("empty.jpg", b"", "image/jpeg")}
    data = {"task": "describe_image"}

    response = client.post("/analyze", files=files, data=data)
    assert response.status_code == 400

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["should_recapture"] is True


def test_analyze_blank_image_prompts_recapture():
    """Verify that a uniform/covered lens image triggers a recapture recommendation."""
    blank_bytes = create_blank_uniform_image()
    files = {"image": ("blank.jpg", blank_bytes, "image/jpeg")}
    data = {"task": "describe_image"}

    response = client.post("/analyze", files=files, data=data)
    # The API returns 200 with recapture guidance for poor quality captures
    assert response.status_code in {200, 400}

    payload = response.json()
    assert payload["should_recapture"] is True
    assert payload["status"] == "recapture"
    assert "unobstructed" in payload["reason"].lower() or "dark" in payload["reason"].lower()
