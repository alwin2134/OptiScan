"""Request and response schemas for image analysis and health checks."""

from enum import Enum
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field


class TaskType(str, Enum):
    """Supported analysis tasks."""

    DESCRIBE_IMAGE = "describe_image"
    READ_TEXT = "read_text"
    GUIDED_TASK = "guided_task"


class StatusType(str, Enum):
    """API response status codes."""

    SUCCESS = "success"
    ERROR = "error"
    RECAPTURE = "recapture"


class HealthResponse(BaseModel):
    """Health check endpoint response schema."""

    model_config = ConfigDict(populate_by_name=True)

    status: str = Field(
        default="ok",
        description="Health status indicator",
        examples=["ok"],
    )
    service: str = Field(
        default="optiscan-backend",
        description="Service identifier",
        examples=["optiscan-backend"],
    )


class AnalysisResponse(BaseModel):
    """Standardized response schema for POST /analyze."""

    model_config = ConfigDict(populate_by_name=True)

    status: str = Field(
        ...,
        description="Outcome status of the analysis: 'success', 'error', or 'recapture'",
        examples=["success", "recapture", "error"],
    )
    task: str = Field(
        ...,
        description="The task that was executed: 'describe_image', 'read_text', or 'guided_task'",
        examples=["describe_image", "read_text", "guided_task"],
    )
    answer: Optional[str] = Field(
        default=None,
        description="The descriptive text, instruction, or OCR readout provided by the vision engine",
        examples=["A white bottle is placed on a table."],
    )
    should_recapture: bool = Field(
        default=False,
        description="True if image quality was insufficient and user should retake the photo",
        examples=[False, True],
    )
    reason: Optional[str] = Field(
        default=None,
        description="Explanatory reason when recapture is recommended or error occurred",
        examples=["Image is too blurry. Please hold steady and recapture."],
    )
    step_index: Optional[int] = Field(
        default=None,
        description="Current step index for sequential guided task mode",
        examples=[1],
    )
    is_task_complete: bool = Field(
        default=False,
        description="True if the guided task has reached its final completion step",
        examples=[False, True],
    )
    needs_clarification: bool = Field(
        default=False,
        description="True if the vision model requires user clarification on an ambiguous state",
        examples=[False, True],
    )
    needs_rescan: bool = Field(
        default=False,
        description="True if visual confirmation is required before proceeding to the next step",
        examples=[False, True],
    )


class SupportedLanguage(str, Enum):
    """Supported response languages."""

    ENGLISH = "en"
    HINDI = "hi"


class OcrAnalysisRequest(BaseModel):
    """Optional JSON payload schema for analyzing pre-extracted OCR text."""

    ocr_text: str = Field(
        ...,
        description="Extracted text string provided by on-device ML Kit OCR",
        min_length=1,
    )
    question: Optional[str] = Field(
        default=None,
        description="Optional question about the extracted text (e.g., 'What is the dosage?')",
    )
    language: Optional[str] = Field(
        default="en",
        description="Requested response language code ('en' or 'hi')",
        examples=["en", "hi"],
    )

