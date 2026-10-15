"""Main entry point for OptiScan FastAPI backend."""

import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import router
from app.core.config import get_settings
from app.schemas.analysis import AnalysisResponse, StatusType
from app.services.vision_provider import get_vision_provider

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("optiscan.main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application startup and shutdown events."""
    settings = get_settings()
    logger.info("Starting %s v%s in %s mode", settings.app_name, settings.app_version, settings.environment)
    logger.info("Vision provider configured: %s", settings.vision_provider)

    # Validate provider configuration on startup
    try:
        get_vision_provider(settings)
    except Exception as e:
        logger.critical("Provider startup validation failed: %s", str(e))
        raise e

    yield
    logger.info("Shutting down %s", settings.app_name)


def create_app() -> FastAPI:
    """Build and configure the FastAPI application instance."""
    settings = get_settings()
    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description="OptiScan AI Visual Assistant Backend Service",
        lifespan=lifespan,
    )

    # Configure CORS for local development and Android emulator access
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["*"],
    )

    # Global unhandled exception handler to protect against leaking internal details
    @app.exception_handler(Exception)
    async def global_exception_handler(request: Request, exc: Exception):
        logger.exception("Unhandled server error processing %s: %s", request.url.path, str(exc))
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=AnalysisResponse(
                status=StatusType.ERROR.value,
                task="unknown",
                answer=None,
                should_recapture=False,
                reason="An internal server error occurred. Please try again later.",
            ).model_dump(),
        )

    # Register API routes
    app.include_router(router)

    return app


app = create_app()

if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )
