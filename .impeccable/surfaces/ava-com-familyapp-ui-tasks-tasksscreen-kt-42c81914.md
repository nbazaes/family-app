---
version: 1
slug: "ava-com-familyapp-ui-tasks-tasksscreen-kt-42c81914"
primary_target: "android/app/src/main/java/com/familyapp/ui/tasks/TasksScreen.kt"
related_targets: []
---

# Surface Brief: Tareas Familiares (TasksScreen)

- **Target:** `android/app/src/main/java/com/familyapp/ui/tasks/TasksScreen.kt`
- **Mode:** `Operate`
- **Platform:** `android`

## Direction contract

### THESIS
Gestión de tareas del hogar sin fricción burocrática ni listas planas interminables: una agenda viva donde cada miembro del hogar identifica de un vistazo qué le corresponde resolver hoy, qué requiere atención inmediata y qué fue completado, operando con interacciones táctiles directas (Swipe to complete / delete con Undo) y captura ágil con presets naturales ("Hoy", "Mañana", "Fin de semana").

### OWN-WORLD
Material Design 3 nativo estricto con roles semánticos de color (`surfaceContainerLowest`, `surfaceContainer`, `secondaryContainer`, `errorContainer`, `outlineVariant`). Tipografía escalable mediante tokens Material (`TitleMedium`, `BodyLarge`, `LabelMedium`). Chips tonales con avatares de iniciales para miembros de la familia, badge de sincronización reactiva outbox y cards con elevación tonal y radios redondeados de 16dp.

### STORY
Cualquier integrante de la familia abre la pestaña de Tareas y en menos de 2 segundos comprende sus responsabilidades del día. Con un solo gesto de deslizamiento completa una tarea recibiendo feedback háptico y un Snackbar accesible para deshacer. El botón de acción flotante (FAB) abre un ModalBottomSheet ergonómico al alcance del pulgar con selección instantánea de responsable y fecha.

### FIRST VIEWPORT
En la parte superior, carrusel horizontal de chips de filtrado ("Todas", "Mis tareas", miembros dinámicos con badge numérico). Inmediatamente debajo, lista estructurada en secciones temporales claras: "Atrasadas / Hoy", "Próximas", "Sin fecha programada", y sección colapsable "Completadas". FAB destacado abajo a la derecha sobre el Scaffold respetando window insets.

### FORM
Android Native Material 3 Household Kanban-List (Operate mode).

### FINISH
unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
