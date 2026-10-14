"""Application configuration loaded from environment variables."""

from functools import lru_cache
from typing import List, Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Central configuration for OptiScan backend service."""

    model_config = SettingsConfigDict(
        env_file=".env",
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

    # CORS
    cors_origins: List[str] = ["*"]

    # Image upload constraints
    max_upload_size_bytes: int = 10 * 1024 * 1024  # 10 MB limit
    allowed_image_formats: List[str] = ["JPEG", "PNG", "WEBP", "MPO"]
    min_image_dimension: int = 64
    blur_threshold: float = 12.0  # Sharpness variance threshold for recapture

    # AI Vision Provider
    # Supported: "mock", "gemini", "openai"
    vision_provider: str = "mock"
    vision_api_key: Optional[str] = None
    vision_model_name: Optional[str] = None


@lru_cache()
def get_settings() -> Settings:
    """Return cached application settings instance."""
    return Settings()
