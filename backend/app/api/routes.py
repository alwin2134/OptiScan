"""API route endpoints for OptiScan backend service."""

import logging
from typing import Optional
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import JSONResponse

from app.core.config import Settings, get_settings
from app.schemas.analysis import AnalysisResponse, HealthResponse, OcrAnalysisRequest, StatusType, TaskType
from app.services.image_validation import ImageValidationService, ValidationResult
from app.services.vision_provider import VisionProvider, VisionProviderError, get_vision_provider

logger = logging.getLogger("optiscan.api")
router = APIRouter()


def get_validation_service(
    settings: Settings = Depends(get_settings),
) -> ImageValidationService:
    """Dependency provider for image validation service."""
    return ImageValidationService(settings=settings)


def get_provider(
    settings: Settings = Depends(get_settings),
) -> VisionProvider:
    """Dependency provider for vision AI service."""
    return get_vision_provider(settings=settings)


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Health Check",
    description="Returns the operational status of the OptiScan backend service.",
)
async def health_check() -> HealthResponse:
    """Return health status and service identifier."""
    return HealthResponse(status="ok", service="optiscan-backend")


@router.post(
    "/analyze",
    response_model=AnalysisResponse,
    summary="Analyze Image",
    description="Processes an uploaded camera capture for scene description or text reading.",
)
async def analyze_image(
    image: UploadFile = File(
        ...,
        description="The image file captured from the camera (JPEG, PNG, WEBP).",
    ),
    task: str = Form(
        ...,
        description="Analysis task to perform: 'describe_image' or 'read_text'.",
    ),
    question: Optional[str] = Form(
        default=None,
        description="Optional question about the scene or text for interactive assistance.",
    ),
    ocr_text: Optional[str] = Form(
        default=None,
        description="Optional client-side text extracted on Android via Google ML Kit.",
    ),
    language: Optional[str] = Form(
        default="en",
        description="Optional requested response language code: 'en' (default) or 'hi'.",
    ),
    task_goal: Optional[str] = Form(
        default=None,
        description="Optional goal for guided task mode (e.g., 'make tea').",
    ),
    step_history: Optional[str] = Form(
        default=None,
        description="Optional history/context of completed steps for guided task mode.",
    ),
    settings: Settings = Depends(get_settings),
    validation_service: ImageValidationService = Depends(get_validation_service),
    vision_provider: VisionProvider = Depends(get_provider),
) -> AnalysisResponse:
    """Validate image quality and dispatch to the configured vision provider."""
    # 1. Validate task
    normalized_task = task.strip().lower()
    valid_tasks = {TaskType.DESCRIBE_IMAGE.value, TaskType.READ_TEXT.value, TaskType.GUIDED_TASK.value}
    if normalized_task not in valid_tasks:
        logger.warning("Unsupported task requested: '%s'", task)
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=task,
                answer=None,
                should_recapture=False,
                reason=f"Unsupported task '{task}'. Allowed tasks are 'describe_image', 'read_text', and 'guided_task'.",
            ).model_dump(),
        )

    # 2. Validate response language
    raw_lang = (language or "en").strip().lower()
    if raw_lang in ("en", "english"):
        target_language = "en"
    elif raw_lang in ("hi", "hindi"):
        target_language = "hi"
    else:
        logger.warning("Unsupported language requested: '%s'", language)
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=task,
                answer=None,
                should_recapture=False,
                reason=f"Unsupported language '{language}'. Supported languages are 'en' (English) and 'hi' (Hindi).",
            ).model_dump(),
        )

    # 2. Read image bytes in bounded chunks to prevent out-of-memory on oversized uploads
    try:
        image_bytes, is_oversized = await validation_service.read_bounded_bytes(
            image,
            max_bytes=settings.max_upload_size_bytes,
            chunk_size=settings.chunk_read_size_bytes,
        )
    except Exception as e:
        logger.error("Failed to read uploaded image bytes: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=normalized_task,
                answer=None,
                should_recapture=True,
                reason="Failed to read the uploaded image data.",
            ).model_dump(),
        )

    if is_oversized:
        max_mb = settings.max_upload_size_bytes / (1024 * 1024)
        logger.warning("Upload exceeds max size of %s MB", max_mb)
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=normalized_task,
                answer=None,
                should_recapture=True,
                reason=f"Image file exceeds maximum allowed size of {max_mb:.1f} MB.",
            ).model_dump(),
        )

    # 3. Validate image dimensions, format, and blur
    val_result: ValidationResult = validation_service.validate(image_bytes)

    if not val_result.is_valid:
        if val_result.format and val_result.width > 0:
            status_value = StatusType.RECAPTURE.value
            http_code = status.HTTP_200_OK
        else:
            status_value = StatusType.ERROR.value
            http_code = status.HTTP_400_BAD_REQUEST

        logger.info(
            "Image validation unsuccessful: status=%s, should_recapture=%s, reason=%s",
            status_value,
            val_result.should_recapture,
            val_result.reason,
        )
        return JSONResponse(
            status_code=http_code,
            content=AnalysisResponse(
                status=status_value,
                task=normalized_task,
                answer=None,
                should_recapture=val_result.should_recapture,
                reason=val_result.reason,
            ).model_dump(),
        )

    # 4. Dispatch to Vision Provider safely
    mime_type = val_result.mime_type or "image/jpeg"
    try:
        if normalized_task == TaskType.DESCRIBE_IMAGE.value:
            response = await vision_provider.describe_image(
                image_bytes,
                question=question,
                mime_type=mime_type,
                language=target_language,
            )
        elif normalized_task == TaskType.GUIDED_TASK.value:
            response = await vision_provider.guide_task(
                image_bytes,
                goal=task_goal or question or "assist user",
                step_history=step_history,
                user_response=question,
                mime_type=mime_type,
                language=target_language,
            )
        else:
            response = await vision_provider.process_ocr(
                image_bytes,
                ocr_text=ocr_text,
                question=question,
                mime_type=mime_type,
                language=target_language,
            )
        return response
    except VisionProviderError as e:
        logger.error("Vision provider failure: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=normalized_task,
                answer=None,
                should_recapture=False,
                reason="Vision AI provider failed or timed out. Please try again.",
            ).model_dump(),
        )
    except Exception as e:
        logger.exception("Unexpected error during analysis: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=normalized_task,
                answer=None,
                should_recapture=False,
                reason="An unexpected internal error occurred while analyzing the image.",
            ).model_dump(),
        )


@router.post(
    "/analyze/ocr-text",
    response_model=AnalysisResponse,
    summary="Process ML Kit Extracted Text",
    description="Processes text already extracted on Android by Google ML Kit, with optional question answering.",
)
async def process_ocr_text(
    payload: OcrAnalysisRequest,
    vision_provider: VisionProvider = Depends(get_provider),
) -> AnalysisResponse:
    """Process on-device OCR text without requiring an image upload."""
    target_lang = "hi" if (payload.language or "en").strip().lower() in ("hi", "hindi") else "en"
    return await vision_provider.process_ocr(
        image_bytes=b"",
        ocr_text=payload.ocr_text,
        question=payload.question,
        language=target_lang,
    )
