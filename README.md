# FamilyApp 🌿

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688.svg)](https://fastapi.tiangolo.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)

Alternativa moderna, autoalojable y ligera a FamilyWall construida con arquitectura estricta **Offline-First**, diseño cálido botánico y sincronización reactiva en tiempo real.

---

## 📥 Descarga e Instalación

Puedes descargar el archivo `.apk` listo para instalar directamente desde los **[Releases de GitHub](https://github.com/nbazaes/family-app/releases)**.

1. Descarga el archivo `FamilyApp-vX.X.X.apk` en tu teléfono Android.
2. Abre el archivo descargado para instalarlo (habilita la opción de "Instalar desde fuentes desconocidas" si tu navegador te lo solicita).
3. Abre la app e ingresa la URL de tu servidor local o Tailscale (ej: `http://192.168.1.130:8000/`) y tu nombre para sincronizar al instante.

---

## 🌿 Identidad Visual: «Herbario & Calidez Botánica»

FamilyApp cuenta con un sistema de diseño propio alejado de los estándares fríos corporativos:
- **Isotipo oficial «Casa con Hojas»:** Logotipo vectorial nativo que fusiona la calidez del hogar con brotes botánicos.
- **Icono Adaptativo:** Compatible con todos los launchers de Android y soporte para iconos temáticos monocromáticos de **Material You** (Android 13+).
- **Modo Claro / Oscuro manual:** Conmutador rápido de un toque en la cabecera y selector de 3 opciones (Auto / Claro ☀️ / Oscuro 🌙) persistente.
- **Tipografía Commissioner:** Tipografía neogrotesca humanista integrada localmente para máxima legibilidad y funcionamiento 100% offline.

---

## 🌟 Módulos Incluidos

1. **Listas de Compras Compartidas:** Agrupación por categorías, tachado optimista y sincronización instantánea entre familiares.
2. **Tareas Familiares:** Asignación por miembros (`assigned_to`), filtros rápidos, fechas de vencimiento y seguimiento de pendientes vs completadas.
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
- **Jetpack Compose + Material 3:** Interfaz moderna y declarativa con tokens botánicos.
- **Patrón Outbox Offline-First:** Las mutaciones impactan Room Database de inmediato con `sync_status = 'PENDING_MUTATION'`.
- **WorkManager:** `SyncWorker` con restricción de conectividad que procesa los lotes en segundo plano (`batch-sync`).
- **SSE Listener Reactivo:** Conexión continua en primer plano para recibir cambios de otros familiares en tiempo real.

---

## 🚀 Despliegue del Backend

### Con Docker Compose (Recomendado)

```bash
cd server
docker compose up -d
```
El servidor quedará disponible en `http://localhost:8000`.
- Documentación interactiva Swagger: `http://localhost:8000/docs`
- Verificación de estado: `http://localhost:8000/api/health`

### En Entorno Local Python

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 📱 Compilación del Cliente Android

```bash
cd android

# Compilar versión de depuración (Debug)
./gradlew assembleDebug

# Compilar versión de producción firmada (Release)
./gradlew assembleRelease
```
El archivo APK firmado se generará en:
`android/app/build/outputs/apk/release/app-release.apk`

---

## 📄 Licencia

Este proyecto está bajo la Licencia **MIT**. Consulta el archivo [LICENSE](LICENSE) para más detalles.
