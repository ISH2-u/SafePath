import os
import sys
from importlib.abc import MetaPathFinder

# ── Windows Defender Application Control / SmartAppControl Bypass ───────────────
class PurePyFinder(MetaPathFinder):
    def find_spec(self, fullname, path, target=None):
        if fullname.startswith("sqlalchemy.engine._") and fullname.endswith("_cy"):
            for p in (path or []):
                name = fullname.split(".")[-1]
                py_file = os.path.join(p, name + ".py")
                if os.path.exists(py_file):
                    from importlib.util import spec_from_file_location
                    return spec_from_file_location(fullname, py_file)
        return None

sys.meta_path.insert(0, PurePyFinder())

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.api.v1.api import api_router
from app.database.base import Base
from app.database.session import engine
from app.models import models  # Ensures models are registered on Base metadata

app = FastAPI(
    title=settings.PROJECT_NAME,
    description="Production-oriented Personal Safety Backend API",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

@app.on_event("startup")
def on_startup():
    """Auto-creates database tables if they do not exist."""
    try:
        Base.metadata.create_all(bind=engine)
    except Exception as e:
        import logging
        logging.getLogger("uvicorn.error").warning(f"Database table check: {e}")

# ─── CORS ────────────────────────────────────────────────────────────────────
# In production, replace "*" with your Android app's exact domain / scheme
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── Routers ─────────────────────────────────────────────────────────────────
app.include_router(api_router, prefix=settings.API_V1_STR)


@app.get("/health", tags=["Health"], summary="Service health check")
def health_check():
    """Health-check endpoint confirming that the backend is running.
    Does not expose sensitive credentials, keys, or database passwords."""
    return {
        "status": "healthy",
        "service": "SafePath AI Backend",
        "version": "1.0.0"
    }


@app.get("/", tags=["Root"])
def root():
    return {
        "message": "SafePath AI Backend is running",
        "docs": "/docs",
        "health": "/health",
        "api_v1": settings.API_V1_STR,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)

