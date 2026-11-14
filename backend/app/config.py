import os
from pydantic_settings import BaseSettings
from typing import Optional

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENV_PATH = os.path.join(BASE_DIR, ".env")


class Settings(BaseSettings):
    PROJECT_NAME: str = "SafePath AI Backend"
    API_V1_STR: str = "/api/v1"
    DEBUG: bool = True

    # Database: Defaults to SQLite for zero-config instant local setup
    DATABASE_URL: str = "sqlite:///./safepath.db"

    # JWT
    SECRET_KEY: str = "safepath_super_secret_local_dev_key_32bytes_min"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440

    # Firebase
    FIREBASE_CREDENTIALS_PATH: Optional[str] = None

    class Config:
        env_file = ENV_PATH if os.path.exists(ENV_PATH) else ".env"
        case_sensitive = True


settings = Settings()
