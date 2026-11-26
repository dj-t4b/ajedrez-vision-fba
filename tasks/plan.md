# Plan de implementación: Sistema de ajedrez con visión por computadora (FBA)

Fuente: arquitectura acordada con Daniel (26/11/2026). El perfil (`perfil_DEFINITIVO.docx`) se está completando en otro hilo; cuando esté listo se ajustan los alcances.

## Resumen
App Android que fotografía el tablero, reconoce la posición en el propio dispositivo y envía la jugada/posición a una API REST (Spring Boot 3 en WildFly 41, MySQL). Una web React (Vite) da el panel, el CRUD de usuarios con roles y los reportes PDF/Excel. Todo se levanta con Docker Compose (WildFly + MySQL).

## Decisiones de arquitectura
- **Monorepo** `backend/`, `web/`, `android/`, `docs/`: un solo repo GitHub para Tecnologías Emergentes.
- **Backend**: Spring Boot 3.3+, empaquetado `.war` (`SpringBootServletInitializer`, Tomcat `provided`). Java 21 como `release` (WildFly 41 soporta 17/21/25; el JDK 24 instalado compila a 21).
- **Auth**: JWT stateless (Spring Security), roles `ADMIN`, `ARBITRO`, `JUGADOR`. Mismo token para web y Android.
- **Esquema DB**: Flyway para migraciones (reproducible en Docker y en la defensa).
- **Visión por computadora (riesgo principal)**: en lugar de clasificar cada pieza, detectar el tablero (4 esquinas + homografía con OpenCV), medir ocupación/color de las 64 casillas y **deducir la jugada comparando con la posición anterior** usando un generador de jugadas legales. Mucho más robusto y rápido de hacer que un modelo de clasificación de piezas. Clasificación con TFLite queda como mejora opcional.
- **Android**: Kotlin + CameraX + OpenCV Android SDK + Retrofit. Probar en **teléfono físico**, no emulador (el PC tiene 7,8 GB de RAM).
- **Reportes**: OpenPDF (PDF) y Apache POI (Excel) desde el backend.
- **Docs**: OpenAPI con springdoc (Swagger UI); UML (casos de uso, clases, ER) en `docs/` con PlantUML.

## Fases (sprints cortos, la entrega es la semana que viene)

### Sprint 0: Entorno y repo (hoy)
- Herramientas instaladas, repo git, GitHub, esqueletos que compilan.

### Sprint 1: Backend base + Docker (núcleo de ambas materias)
- Auth JWT + usuarios/roles, partidas y jugadas, Swagger, logs SLF4J, `.war` desplegado en WildFly por Docker Compose con MySQL.

### Sprint 2: Web React
- Login, dashboard, CRUD de usuarios con roles, listado de partidas, descarga de reportes.

### Sprint 3: Android + visión
- Captura con CameraX, detección de tablero, deducción de jugada, envío a la API. Fallback de corrección manual de la jugada si la visión falla.

### Sprint 4: Reportes, docs y defensa
- PDF/Excel, UML en `docs/`, README completo, guion de demo.

## Riesgos y mitigaciones
| Riesgo | Impacto | Mitigación |
|---|---|---|
| Visión no fiable a tiempo | Alto | Deducción por diferencia de ocupación + jugadas legales; corrección manual en la app; iluminación/ángulo fijos en la demo |
| RAM 7,8 GB: Docker + Android Studio + IntelliJ a la vez | Medio | No abrir todo junto; teléfono físico; limitar memoria de WSL2 (`.wslconfig`) |
| Docker Desktop requiere WSL2 + reinicio | Medio | Instalar hoy; si falla, WildFly local en `C:\wildfly` (ya es 41.0.0.Final) + MySQL de XAMPP como plan B |
| Plazo de una semana | Alto | Priorizar backend + Docker (exención) y flujo de demo de punta a punta antes de pulir |

## Preguntas abiertas
- Alcance exacto del perfil (¿registrar partidas de torneo, solo posiciones, rating?).
- ¿Teléfono Android físico disponible para la demo? ¿Qué versión de Android?
- Nombre del repositorio GitHub y si será público.
