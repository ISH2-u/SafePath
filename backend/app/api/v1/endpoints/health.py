from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from app.database.session import get_db

router = APIRouter()


@router.get("/health", summary="Service health check")
def health():
    return {"status": "healthy", "service": "SafePath AI Backend", "version": "1.0.0"}


@router.get("/health/db", summary="Database connectivity check")
def db_health(db: Session = Depends(get_db)):
    try:
        db.execute(text("SELECT 1"))
        return {"status": "connected", "database": "PostgreSQL operational"}
    except Exception as exc:
        return {"status": "degraded", "error": str(exc)}

