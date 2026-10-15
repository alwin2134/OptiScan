"""Image validation service: checks dimensions, file size, formats, and blur/clarity."""

import io
import logging
from dataclasses import dataclass
from typing import Optional
from fastapi import UploadFile
from PIL import Image, ImageFilter, ImageStat

from app.core.config import Settings, get_settings

logger = logging.getLogger("optiscan.image_validation")


@dataclass
class ValidationResult:
    """Outcome of an image validation check."""

    is_valid: bool
    should_recapture: bool
    reason: Optional[str] = None
    format: Optional[str] = None
    width: int = 0
    height: int = 0


class ImageValidationService:
    """Validates image uploads against size, format, dimension, and blur criteria."""

    def __init__(self, settings: Optional[Settings] = None):
        self.settings = settings or get_settings()

    @staticmethod
    async def read_bounded_bytes(
        upload_file: UploadFile,
        max_bytes: int,
        chunk_size: int = 64 * 1024,
    ) -> tuple[bytes, bool]:
        """Read UploadFile in bounded chunks to prevent out-of-memory denial-of-service.

        Returns (data, is_oversized). If the stream exceeds max_bytes, reading halts
        immediately without buffering the remaining content.
        """
        buffer = bytearray()
        while True:
            chunk = await upload_file.read(chunk_size)
            if not chunk:
                break
            buffer.extend(chunk)
            if len(buffer) > max_bytes:
                # Halt immediately; avoid loading remainder of oversized file
                return bytes(buffer), True

        return bytes(buffer), False

    def validate(self, image_bytes: bytes) -> ValidationResult:
        """Inspect image bytes for size, validity, resolution, and blur."""
        # 1. Byte length check
        if not image_bytes or len(image_bytes) == 0:
            logger.warning("Empty image payload received")
            return ValidationResult(
                is_valid=False,
                should_recapture=True,
                reason="Uploaded image file is empty.",
            )

        if len(image_bytes) > self.settings.max_upload_size_bytes:
            max_mb = self.settings.max_upload_size_bytes / (1024 * 1024)
            logger.warning("Image payload exceeds maximum allowed size (%s bytes)", len(image_bytes))
            return ValidationResult(
                is_valid=False,
                should_recapture=True,
                reason=f"Image file exceeds maximum allowed size of {max_mb:.1f} MB.",
            )

        # 2. Decode image with Pillow
        try:
            image = Image.open(io.BytesIO(image_bytes))
            image.verify()  # Verify file header and integrity
            # Reopen because verify() closes or alters some image internal pointers
            image = Image.open(io.BytesIO(image_bytes))
        except Exception as e:
            logger.warning("Failed to decode image: %s", str(e))
            return ValidationResult(
                is_valid=False,
                should_recapture=True,
                reason="The uploaded file is not a valid or readable image.",
            )

        # 3. Format check
        image_format = (image.format or "").upper()
        if image_format not in self.settings.allowed_image_formats:
            logger.warning("Unsupported image format: %s", image_format)
            allowed = ", ".join(self.settings.allowed_image_formats)
            return ValidationResult(
                is_valid=False,
                should_recapture=False,
                reason=f"Unsupported image format '{image_format}'. Allowed formats: {allowed}.",
                format=image_format,
            )

        width, height = image.size

        # 4. Dimension check
        if width < self.settings.min_image_dimension or height < self.settings.min_image_dimension:
            logger.info("Image dimensions too small: %dx%d", width, height)
            return ValidationResult(
                is_valid=False,
                should_recapture=True,
                reason=f"Image resolution ({width}x{height}) is too low for reliable visual recognition.",
                format=image_format,
                width=width,
                height=height,
            )

        # 5. Blur and uniformity check
        try:
            # Convert to grayscale
            gray = image.convert("L")
            stat = ImageStat.Stat(gray)

            # Uniformity check (e.g. completely black or white frame from covered lens)
            if stat.stddev[0] < 2.0:
                logger.info("Image appears completely uniform or covered (stddev=%.2f)", stat.stddev[0])
                return ValidationResult(
                    is_valid=False,
                    should_recapture=True,
                    reason="The image is completely dark or uniform. Ensure the camera lens is unobstructed.",
                    format=image_format,
                    width=width,
                    height=height,
                )

            # Edge detection sharpness heuristic
            edges = gray.filter(ImageFilter.FIND_EDGES)
            edge_stat = ImageStat.Stat(edges)
            edge_variance = edge_stat.var[0]

            logger.debug("Image edge variance: %.2f (threshold: %.2f)", edge_variance, self.settings.blur_threshold)
            if edge_variance < self.settings.blur_threshold:
                logger.info("Image failed blur check (variance=%.2f < threshold=%.2f)", edge_variance, self.settings.blur_threshold)
                return ValidationResult(
                    is_valid=False,
                    should_recapture=True,
                    reason="Image is too blurry. Please hold steady and recapture.",
                    format=image_format,
                    width=width,
                    height=height,
                )
        except Exception as e:
            logger.error("Error during image quality analysis: %s", str(e))
            # If sharpness check fails unexpectedly, do not block the request if basic image was valid
            pass

        return ValidationResult(
            is_valid=True,
            should_recapture=False,
            format=image_format,
            width=width,
            height=height,
        )
