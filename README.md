# FamilyApp (MVP Open Source & Self-Hosted)

Alternativa autoalojable y ligera a FamilyWall construida con arquitectura estricta **Offline-First** y sincronización reactiva en tiempo real.

---

## 🌟 Módulos Incluidos

1. **Listas de Compras Compartidas:** Agrupación por categorías, tachado optimista y sincronización instantánea entre familiares.
2. **Tareas Familiares:** Asignación por miembros (`assigned_to`), fechas de vencimiento y seguimiento de pendientes vs completadas.
3. **Calendario Familiar Sincronizado:** Agenda semanal/mensual, eventos de día completo o por horas, y **feed dinámico RFC 5545 (`.ics`)** para suscribirse desde Google Calendar, Apple Calendar o Thunderbird.

---

## 🏗️ Arquitectura y Tecnologías

### Backend (`/server`)
- **FastAPI (Python):** Framework asíncrono de alto rendimiento.
- **SQLite con modo WAL:** `PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;` vía SQLAlchemy async (`aiosqlite`) para máxima concurrencia en lecturas y escrituras sin bloqueos.
- **Sync Engine (Last-Write-Wins):** Resolución de conflictos basada en marcas de tiempo UTC `updated_at`.
- **SSE Hub en Memoria:** Canal Server-Sent Events (`/api/events/stream`) particionado por familia para difusión reactiva instantánea.
- **iCalendar RFC 5545:** Endpoint `/api/calendar/feed.ics?token=...` compatible con clientes estándar de calendario.
- **Docker:** Empaquetado en un solo `Dockerfile` y `docker-compose.yml`.

### Frontend Android (`/android`)
- **Jetpack Compose + Material 3:** Interfaz moderna y declarativa.
- **Patrón Outbox Offline-First:** Las inserciones/ediciones impactan Room Database de inmediato con `sync_status = 'PENDING_MUTATION'`.
- **WorkManager:** `SyncWorker` con restricción de red (`NetworkType.CONNECTED`) que procesa los lotes en segundo plano (`batch-sync`).
- **SSE Listener Reactivo:** Corutina en primer plano que escucha eventos y actualiza la base de datos local en tiempo cero.

---

## 🚀 Despliegue del Backend

### Opción 1: Con Docker Compose (Recomendado)

```bash
cd server
docker compose up -d
```
El servidor quedará disponible en `http://localhost:8000`.
- Documentación interactiva Swagger: `http://localhost:8000/docs`
- Verificación de estado: `http://localhost:8000/api/health`

### Opción 2: En Entorno Local Python

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Ejecutar Pruebas Automatizadas del Backend

```bash
cd server
pytest -v
```

---

## 📱 Compilación del Cliente Android

Abre la carpeta `android/` en **Android Studio** (Koala / Ladybug o superior) o usa la línea de comandos:

```bash
cd android
./gradlew assembleDebug
```

> **Nota:** Para conectar el emulador de Android al backend local en tu máquina, usa la IP `http://10.0.2.2:8000/`. Si pruebas en un dispositivo físico conectado a la misma red WiFi, configura la IP local de tu ordenador (ej: `http://192.168.1.100:8000/`) tocando el icono de configuración ⚙️ en la barra superior de la app.
> 
> **Conexión sin cuenta (Cero Fricción):** No es necesario crear usuarios ni contraseñas. Cada miembro de la familia solo ingresa la URL del servidor y su nombre (ej: "Papá", "Nicolás") y presiona **"Conectar y Sincronizar"** para comenzar a sincronizar al instante.
