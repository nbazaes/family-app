from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List


class Settings(BaseSettings):
    PROJECT_NAME: str = "FamilyApp Backend"
    API_V1_STR: str = "/api"
    SECRET_KEY: str = "dev-secret-key-change-in-production-familyapp-2026"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days
    DATABASE_URL: str = "sqlite+aiosqlite:///./data/family.db"
    CORS_ORIGINS: List[str] = ["*"]
    DEFAULT_FAMILY_NAME: str = "Mi Familia"
    DEFAULT_FAMILY_CODE: str = "FAMILIA"

    model_config = SettingsConfigDict(env_file=".env", extra="allow")


settings = Settings()
