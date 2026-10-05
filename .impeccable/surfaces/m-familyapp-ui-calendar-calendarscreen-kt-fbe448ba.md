---
version: 1
slug: "m-familyapp-ui-calendar-calendarscreen-kt-fbe448ba"
primary_target: "android/app/src/main/java/com/familyapp/ui/calendar/CalendarScreen.kt"
related_targets: []
---

# Surface Brief: Calendario Familiar (CalendarScreen)

- **Target:** `android/app/src/main/java/com/familyapp/ui/calendar/CalendarScreen.kt`
- **Mode:** `Operate`
- **Platform:** `android`

## Direction contract

### THESIS
Agenda familiar viva, visual y libre de fricción: franja semanal interactiva táctil ("Week Strip") para cambiar de día con un toque, combinada con una lista cronológica clara de eventos en la paleta botánica cálida, filtrado instantáneo por miembro del hogar y captura ágil mediante ModalBottomSheet con presets naturales de franja horaria.

### OWN-WORLD
Tokens del sistema "Herbario & Calidez Botánica": verde bosque botánico (`primary`), terracota cálido (`secondary`), ocre dorado (`tertiary`) y superficie neutra de papel lino (`background`). Tarjetas de día con elevación tonal, píldoras horarias contrastadas, avatares de iniciales por familiar y chips de color orgánicos en lugar de barras genéricas.

### STORY
Cualquier familiar consulta la agenda del hogar y en un segundo sabe qué actividades hay hoy y en la semana. Con un chip superior filtra entre "Todos", "Mis eventos" o la agenda de un integrante específico. El FAB abre una hoja inferior ergonómica que permite registrar citas o actividades con selector de fecha visual y presets rápidos de franja horaria ("Mañana", "Tarde", "Noche" o "Todo el día").

### FIRST VIEWPORT
En la parte superior, filtro horizontal de miembros de la familia con sus iniciales. Debajo, franja semanal horizontal ("Week Strip") con tarjetas de días numerados, nombre de día y punto indicador de eventos activos. En el cuerpo principal, lista de eventos del día seleccionado y próximos días, con hora en píldora destacada, título y creador. FAB flotante inferior derecho.

### FORM
Android Native Material 3 Family Weekly Agenda & Timeline (Operate mode).

### FINISH
unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
