# AGENTS.md — FamilyApp

Monorepo with two independent halves, no shared build: `server/` (FastAPI + SQLite) and `android/` (Kotlin Compose). No root workspace, no lint/typecheck config.

## Server (`server/`)

- Run from `server/`: `python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt`
- Dev: `uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload` (docs at `/docs`, health at `/api/health`). Docker: `docker compose up -d` from `server/`.
- Tests: `pytest` or focused `pytest tests/test_sync.py -v`. Config is `pytest.ini` (`asyncio_mode = auto`, `pythonpath = .`) — no markers needed. Fixtures in `tests/conftest.py`: in-memory SQLite, `ASGITransport` client, `auth_headers` (registers a uuid email per test).
- SQLite WAL is set via PRAGMAs in `app/core/database.py` (`journal_mode=WAL`, `synchronous=NORMAL`, `foreign_keys=ON`). Schema is created in `lifespan` (`app/main.py`) via `Base.metadata.create_all` plus an ad-hoc `ALTER TABLE families ADD COLUMN access_code` — there are **no migrations/Alembic**. Add new columns the same way (nullable + tolerant `try/except`), don't introduce a migration framework.
- Auth has two paths: `POST /api/auth/connect` (zero-friction `member_name` + optional `family_code`, auto-creates family/user) is what Android uses; `/register` and `/login` are legacy. All routers mount under `/api` (`settings.API_V1_STR`).
- `GET /api/events/stream` (SSE) and `GET /api/calendar/feed.ics` accept Bearer header **or `?token=`** via `get_user_from_token_param` (`app/api/deps.py`) — required because EventSource/calendar subscribers can't set headers. `GET /api/calendar/export.ics` is Bearer-only.
- Sync is Last-Write-Wins on `updated_at` (client wins if `>=`), implemented per-domain in `app/services/sync_service.py` (`sync_items/sync_events/sync_accounts/sync_finance_transactions`). Deletes are **soft** (`deleted_at`, never hard-delete server-side — offline clients need the tombstone). Single `Item` model with `type` `SHOPPING|TASK`; filter via `?type=&include_completed=`.
- Realtime is an in-memory `SSEHub` (`app/core/sse.py`) partitioned by `family_id` with 15s `: ping` keepalive. In-memory = no multi-instance fan-out; every mutation must call `sse_hub.broadcast`.
- Timezones: always compare tz-aware; use `ensure_tz_aware()` from `sync_service` for calendar `start/end` filters and sync payloads.
- Config via `pydantic-settings` (`app/core/config.py`, `.env` file, see `.env.example`). `DATABASE_URL` default `sqlite+aiosqlite:///./data/family.db`; `*.db` is gitignored.

## Android (`android/`)

- Build from `android/`: `./gradlew assembleDebug` / `./gradlew assembleRelease`. Requires JDK 17 (CI uses `temurin` 17). Release is signed with the debug key, `minifyEnabled=false`, lint non-blocking — don't "fix" that, it's intentional for sideloaded APKs. Only CI is `.github/workflows/release.yml`: on `v*` tags builds release APK and attaches `FamilyApp-<tag>.apk`.
- Versions: `compileSdk/targetSdk 35`, `minSdk 26`, `applicationId com.familyapp` (`app/build.gradle.kts`). Deps in `gradle/libs.versions.toml` (Room 2.6.1 + KSP, WorkManager, Retrofit/Gson, `okhttp-sse`, Compose BOM).
- Offline-first outbox (never call the API directly from UI): write to Room first with `sync_status = PENDING_MUTATION`, then `SyncManager.scheduleSync()` (WorkManager `FamilyBatchSync`, `CONNECTED` constraint). `SyncManager` singleton also holds the SSE listener: incoming `*_created/*_updated` upsert with `SYNCED`, `*_deleted` call `deletePermanently`. Endpoints in `core/network/ApiService.kt`, per-module logic in `data/repository/` + `ui/<module>/` (`Screen` + `ViewModel`).
- Base URL + token live in `core/network/NetworkClient.kt` (user enters server URL + name at onboarding, e.g. `http://192.168.1.130:8000/` — trailing slash matters for Retrofit `api/...` relative paths).

## UI / Design (`DESIGN.md`, `PRODUCT.md`)

- Botanical Material 3 tokens: use semantic `MaterialTheme.colorScheme.*`, never hardcoded hex. Local `Commissioner` font (offline) in `sp`; 48×48dp touch targets; bottom sheets (`24dp` top corners) instead of center dialogs; no neon/neubrutalist styles. Light/dark is first-class with manual Auto/Light/Dark + 1-tap header toggle.
