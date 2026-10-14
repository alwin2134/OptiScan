"""API route endpoints for OptiScan backend service."""

import logging
from typing import Optional
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import JSONResponse

from app.core.config import Settings, get_settings
from app.schemas.analysis import AnalysisResponse, HealthResponse, StatusType, TaskType
from app.services.image_validation import ImageValidationService, ValidationResult
from app.services.vision_provider import VisionProvider, get_vision_provider

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
        description="Optional question about the scene for interactive assistance.",
    ),
    validation_service: ImageValidationService = Depends(get_validation_service),
    vision_provider: VisionProvider = Depends(get_provider),
) -> AnalysisResponse:
    """Validate image quality and dispatch to the vision provider."""
    # 1. Validate task
    normalized_task = task.strip().lower()
    valid_tasks = {TaskType.DESCRIBE_IMAGE.value, TaskType.READ_TEXT.value}
    if normalized_task not in valid_tasks:
        logger.warning("Unsupported task requested: '%s'", task)
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task=task,
                answer=None,
                should_recapture=False,
                reason=f"Unsupported task '{task}'. Allowed tasks are 'describe_image' and 'read_text'.",
            ).model_dump(),
        )

    # 2. Read image bytes
    try:
        image_bytes = await image.read()
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

    # 3. Validate image dimensions, format, and blur
    val_result: ValidationResult = validation_service.validate(image_bytes)

    if not val_result.is_valid:
        # If it's a decodable image that failed quality/blur checks, mark as recapture (HTTP 200)
        # Otherwise, if corrupt, unsupported, or empty payload, mark as error (HTTP 400)
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

    # 4. Dispatch to Vision Provider
    try:
        if normalized_task == TaskType.DESCRIBE_IMAGE.value:
            response = await vision_provider.describe_image(image_bytes, question=question)
        else:
            response = await vision_provider.read_text(image_bytes)
        return response
    except Exception as e:
        logger.exception("Unexpected error in vision provider: %s", str(e))
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
