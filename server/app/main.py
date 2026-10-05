from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.database import engine, Base
from app.api import auth, families, items, calendar, events


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Ensure database schema is created on startup
    from sqlalchemy import text
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        try:
            await conn.execute(text("ALTER TABLE families ADD COLUMN access_code VARCHAR(50);"))
        except Exception:
            pass
    yield
    # Shutdown resources
    await engine.dispose()


app = FastAPI(
    title=settings.PROJECT_NAME,
    version="1.0.0",
    description="Backend asíncrono para FamilyApp con SQLite WAL, SSE en vivo y exportación iCalendar RFC 5545",
    lifespan=lifespan,
)

# CORS middleware for mobile and web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers under /api
app.include_router(auth.router, prefix=settings.API_V1_STR)
app.include_router(families.router, prefix=settings.API_V1_STR)
app.include_router(items.router, prefix=settings.API_V1_STR)
app.include_router(calendar.router, prefix=settings.API_V1_STR)
app.include_router(events.router, prefix=settings.API_V1_STR)


@app.get("/")
async def root():
    return {
        "app": settings.PROJECT_NAME,
        "status": "online",
        "docs": "/docs",
        "version": "1.0.0",
    }


@app.get("/api/health")
async def health():
    return {"status": "ok"}
