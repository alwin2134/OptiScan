"""Application configuration loaded from environment variables."""

from functools import lru_cache
from typing import List, Optional
from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Central configuration for OptiScan backend service."""

    model_config = SettingsConfigDict(
        env_file=(".env", "backend/.env"),
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # General app settings
    app_name: str = "OptiScan Backend"
    app_version: str = "0.1.0"
    environment: str = "development"
    debug: bool = False

    # Server binding
    host: str = "0.0.0.0"
    port: int = 8000

    # CORS: configured for local development and Android emulator/device clients
    cors_origins: List[str] = [
        "http://localhost:8000",
        "http://127.0.0.1:8000",
        "http://10.0.2.2:8000",
        "http://localhost:3000",
    ]

    # Image upload constraints (8 MB limit as specified in requirements)
    max_upload_size_bytes: int = 8 * 1024 * 1024  # 8 MB limit
    chunk_read_size_bytes: int = 64 * 1024        # 64 KB chunk size for streaming upload reads
    allowed_image_formats: List[str] = ["JPEG", "PNG", "WEBP", "MPO"]
    min_image_dimension: int = 64
    max_image_dimension: int = 8192
    max_image_pixels: int = 25_000_000  # Pillow decompression bomb protection ceiling (~25 MP)
    blur_threshold: float = 8.0  # Sharpness edge-variance threshold for recapture (calibrated for mobile)


    # AI Vision Provider Configuration
    # Options: "mock", "gemini", "openai"
    vision_provider: str = "mock"
    vision_api_key: Optional[str] = None
    vision_model_name: str = "gemini-3.5-flash"
    vision_request_timeout_seconds: float = 30.0

    @field_validator("vision_provider")
    @classmethod
    def validate_provider(cls, v: str) -> str:
        allowed = {"mock", "gemini", "openai"}
        val = v.lower().strip()
        if val not in allowed:
            raise ValueError(f"Unsupported vision_provider '{v}'. Allowed providers: {', '.join(sorted(allowed))}")
        return val


@lru_cache()
def get_settings() -> Settings:
    """Return cached application settings instance."""
    return Settings()
