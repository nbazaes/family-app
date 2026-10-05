---
name: FamilyApp
description: Herbario y Calidez Botánica - Organic, warm and calm Material 3 design system for everyday household logistics.
colors:
  primary: "#1E523A"
  primary-container: "#CEECC8"
  primary-dark: "#86D5A9"
  secondary: "#9E4726"
  secondary-container: "#FFDBD1"
  secondary-dark: "#FFB59E"
  tertiary: "#7A5900"
  tertiary-container: "#FFDE9F"
  tertiary-dark: "#F2C04D"
  neutral-bg: "#FBF9F4"
  neutral-bg-dark: "#111412"
  surface: "#FFFFFF"
  surface-dark: "#151916"
  surface-variant: "#E2E4DC"
  surface-variant-dark: "#222824"
  outline: "#747972"
  outline-dark: "#8E928B"
  error: "#BA1A1A"
  error-container: "#FFDAD6"
  sync-pending: "#E65100"
  sync-synced: "#2E7D32"
  member-sage: "#388E3C"
  member-terracotta: "#D84315"
  member-river: "#1976D2"
  member-amber: "#F57F17"
typography:
  headline:
    fontFamily: "Commissioner, sans-serif"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: "32sp"
  title:
    fontFamily: "Commissioner, sans-serif"
    fontSize: "20sp"
    fontWeight: 700
    lineHeight: "28sp"
  body:
    fontFamily: "Commissioner, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "Commissioner, sans-serif"
    fontSize: "12sp"
    fontWeight: 600
    lineHeight: "16sp"

rounded:
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  full: "9999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
  lg: "24dp"
components:
  card:
    backgroundColor: "{colors.surface}"
    textColor: "#1A1C1A"
    rounded: "{rounded.lg}"
    padding: "12dp 16dp"
  filter-chip:
    backgroundColor: "{colors.surface-variant}"
    textColor: "#1A1C1A"
    rounded: "{rounded.sm}"
    padding: "6dp 12dp"
  fab:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.lg}"
    padding: "16dp"
---

## Overview

**Herbario & Calidez Botánica** es la identidad visual global de FamilyApp. Sustituye la estética fría y genérica de SaaS digital por una atmósfera hogareña, orgánica y serena inspirada en la cocina familiar, notas de papel lino, especieros y huertos domésticos. Su objetivo es transmitir calma y claridad en las rutinas diarias del hogar (compras, quehaceres y calendarios), reduciendo la sobrecarga cognitiva.

## Colors

- **Verde Bosque Botánico (`#1E523A` / Dark `#86D5A9`):** Color primario. Representa naturaleza, solidez, estabilidad y bienestar. Conduce las acciones primarias, botones de acción flotante (FAB) y selección activa de navegación.
- **Terracota Cálido (`#9E4726` / Dark `#FFB59E`):** Color secundario. Evoca arcilla cocida y notas cálidas. Utilizado para elementos de atención inmediata, tareas vencidas, estados secundarios y avatares destacados.
- **Ocre Dorado (`#7A5900` / Dark `#F2C04D`):** Color terciario. Tono trigo y cosecha para categorías de compra, eventos especiales e indicadores de sincronización.
- **Papel Lino / Arroz (`#FBF9F4` / Dark `#111412`):** Superficie de fondo neutra que reemplaza el blanco azulado digital frío. Genera una lectura descansada y acogedora a cualquier hora del día.
- **Paleta de Miembros del Hogar:** Colores terrosos armónicos (Salvia, Terracota, Azul Río, Ámbar) para distinguir a cada familiar de un vistazo.

## Typography

**Commissioner** (Kostas Bartsokas): Tipografía neogrotesca humanista de bajo contraste, diseñada específicamente para transmitir calma, claridad y calidez en interfaces de lectura intensiva. Totalmente empaquetada de manera local (`res/font/commissioner_regular.ttf`) para funcionamiento 100% offline.
- **HeadlineSmall / TitleLarge:** Gran presencia editorial en títulos de sección y encabezados de pantalla.
- **BodyLarge / BodyMedium:** Lectura fluida y descansada con tracking calibrado para listas y notas familiares.
- **LabelMedium / LabelSmall:** Alta legibilidad en tamaños reducidos para píldoras horarias, conteos y badges.


## Layout

- **Filtrado superior fluido:** Carrusel horizontal de chips tonales que permite saltar entre *"Todas"*, *"Mis tareas"* y cada familiar con su conteo.
- **Estructura en bloques temporales:** Jerarquización natural por urgencia (*Vencidas*, *Hoy*, *Próximas*, *Sin fecha*, *Completadas*).
- **Márgenes y Ritmo:** 16dp de margen exterior en smartphones; separación consistente de 10-12dp entre tarjetas.

## Elevation & Depth

- **Elevación tonal en lugar de sombras:** Variación en la luminosidad de la superficie (`surface` con elevación tonal 1.5dp para elementos activos; `surfaceVariant` atenuado para completados).
- **Contenedores de gestos:** Fondos redondeados de alto contraste semántico (`primaryContainer` para completar, `errorContainer` para eliminar).

## Shapes

- **Contenedores y Tarjetas:** Radios de curvatura de `16dp` que suavizan las esquinas y refuerzan la sensación táctil y orgánica.
- **Hojas Inferiores (`ModalBottomSheet`):** Esquinas superiores pronunciadas de `24dp` con drag handle centrado.
- **Chips e Indicadores:** Formas redondeadas de `8dp` y avatares en `CircleShape`.

## Components

- **Brand Mark Vectorial (`ic_family_logo`):** Isotipo exclusivo de FamilyApp: silueta minimalista de hogar cálido con brotes botánicos estilizados en su tejado, totalmente adaptable en tamaño y tintado tonal dinámico (Verde Bosque en claro, Salvia Luminoso en oscuro).
- **SwipeableTaskRow:** Fila interactiva con `SwipeToDismissBox` de Material 3, soporte táctil háptico y `Snackbar` con acción "Deshacer".
- **AddTaskBottomSheet:** Hoja inferior accesible con autoenfoque, selector de familiar en chips y presets de fecha ("Hoy", "Mañana", "Fin de semana").
- **MemberFilterBar:** Filtros superiores de chip con contador de pendientes.
- **SyncBadge:** Indicador visual outbox que muestra el estado de sincronización reactivo en la cabecera.

## Do's and Don'ts

### Do's
- Mantener siempre objetivos táctiles mínimos de **48×48 dp**.
- Aplicar tokens semánticos del tema (`MaterialTheme.colorScheme.*`) en lugar de colores hexadecimales fijos.
- Ofrecer retroalimentación háptica sutil en gestos y cambios de estado.
- Soportar tema oscuro como un esquema de primer nivel con contraste balanceado, permitiendo selección manual (Auto / Claro / Oscuro) con persistencia offline.
- Proveer un botón de acceso rápido (1 toque) en la cabecera superior para alternar instantáneamente entre modo claro y oscuro.

### Don'ts
- No utilizar colores fríos de neón o púrpuras genéricos que rompan la atmósfera cálida botánica.
- No emplear sombras duras con blur cero o bordes de 4px que parezcan neobrutalistas.
- No utilizar diálogos modales centrales intrusivos cuando una hoja inferior ergonómica resuelve mejor la tarea.
