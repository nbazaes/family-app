# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Núcleo familiar y hogar cotidiano: padres, hijos y parejas que necesitan coordinar las rutinas y logística del hogar (compras del supermercado, quehaceres domésticos, eventos escolares y citas médicas) sin fricción de cuentas ni suscripciones de pago.

## Product Purpose

Alternativa autoalojable, privada, ligera y libre a FamilyWall. Permite a las familias organizar su vida diaria de manera colaborativa, con sincronización en tiempo real y funcionamiento sin conexión garantizado.

## Positioning

Solución open source y self-hosted con arquitectura estricta Offline-First y zero-friction onboarding: conexión inmediata sin registros ni contraseñas (solo URL del servidor y nombre del familiar). Sincronización reactiva instantánea mediante SSE + outbox con resolución Last-Write-Wins, e integración universal de calendario vía feed dinámico RFC 5545 (`.ics`).

## Operating Context

- Uso rápido y frecuente sobre la marcha en smartphones (en el pasillo del supermercado con conectividad intermitente, en la cocina revisando tareas pendientes, o por la mañana consultando la agenda del día).
- Conectividad variable (WiFi del hogar, datos móviles o modo sin conexión).
- Múltiples miembros del hogar interactuando simultáneamente sobre los mismos datos.

## Capabilities and Constraints

- **Arquitectura:** Android nativo en Jetpack Compose + Material 3, diseñado con flexibilidad responsiva para tablets (Navigation Rail/Drawer en anchos expandidos vs Navigation Bar en compactos) y futura paridad Web.
- **Módulos Core:**
  1. *Listas de Compras Compartidas:* agrupación por categorías, tachado optimista inmediato y sincronización reactiva.
  2. *Tareas Familiares:* asignación por miembros (`assigned_to`), fechas de vencimiento y seguimiento pendientes/completadas.
  3. *Calendario Familiar:* agenda semanal/mensual, eventos de día completo o por horas, y suscripción iCal RFC 5545 (`.ics`).
  4. *Finanzas Familiares:* registro de gastos por categorías con fechas, gestión de diferentes cuentas con saldo inicial y cálculo de saldos individuales y balance general.
- **Sincronización:** Patrón Outbox con Room DB local (`sync_status = 'PENDING_MUTATION'`), WorkManager en segundo plano y canal SSE en vivo para difusión instantánea.
- **Cero Fricción:** Configuración con solo URL del servidor y nombre de usuario.

## Brand Commitments

- **Nombre:** FamilyApp.
- **Tono:** Cálido, confiable, claro y orientado al hogar; libre de tecnicismos para que niños y adultos mayores lo usen con naturalidad.

## Evidence on Hand

- Código fuente del cliente Android en Jetpack Compose (`/android/app/src/main/java/com/familyapp`).
- Backend FastAPI en producción local/Docker con endpoints REST, SQLite en modo WAL y SSE Hub (`/server/app`).
- Documentación y especificación arquitectónica en [README.md](file:///home/nicolas/Desarrollo/family-app/README.md).

## Product Principles

- **Cero fricción cotidiana:** Cada acción frecuente (tachar un producto, completar una tarea) debe ser instantánea y requerir el mínimo esfuerzo cognitivo y táctil.
- **Fiabilidad offline radical:** La aplicación nunca debe bloquearse ni mostrar pantallas en blanco por falta de red; los datos locales son la verdad inmediata del usuario.
- **Claridad intergeneracional:** Jerarquías tipográficas limpias, contrastes nítidos e íconos universales que cualquier miembro de la familia pueda entender.
- **Fidelidad nativa Material 3:** Respeto riguroso de las directrices de Android (objetivos táctiles de 48dp+, soporte de tema oscuro de primer nivel, back navigation predictivo y adaptación fluida a pantallas grandes).

## Accessibility & Inclusion

- Objetivos táctiles mínimos de 48×48 dp con espaciado de al menos 8 dp.
- Tipografía estricta en unidades `sp` para respetar el escalado de fuentes del sistema.
- Contraste de color compatible con WCAG AA en modos claro y oscuro.
