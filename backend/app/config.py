import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional, List

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENV_PATH = os.path.join(BASE_DIR, ".env")


class Settings(BaseSettings):
    PROJECT_NAME: str = "SafePath AI Backend"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True

    # CORS
    ALLOWED_ORIGINS: str = "*"

    # Database: Defaults to SQLite for zero-config instant local setup
    DATABASE_URL: str = "sqlite:///./safepath.db"

    # JWT
    SECRET_KEY: str = "safepath_super_secret_local_dev_key_32bytes_min"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440

    # Firebase
    FIREBASE_CREDENTIALS_PATH: Optional[str] = None

    # ML Model
    ML_MODEL_PATH: Optional[str] = os.path.join(BASE_DIR, "models", "area_risk_model.joblib")

    model_config = SettingsConfigDict(
        env_file=ENV_PATH if os.path.exists(ENV_PATH) else ".env",
        extra="ignore",
        case_sensitive=True,
    )

    @property
    def cors_origins(self) -> List[str]:
        if not self.ALLOWED_ORIGINS or self.ALLOWED_ORIGINS == "*":
            return ["*"]
        return [origin.strip() for origin in self.ALLOWED_ORIGINS.split(",") if origin.strip()]


settings = Settings()
