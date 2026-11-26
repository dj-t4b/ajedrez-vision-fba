# Tareas

## Sprint 0: Entorno y repo
- [x] T0.1 Instalar lo que falta (Docker Desktop + WSL2, Android Studio, GitHub CLI; opcional JDK 21). Verificación: `docker run hello-world`, `gh --version`, Android Studio abre.
- [x] T0.2 Repo git local con `.gitignore`, README y estructura. Verificación: `git log` muestra el commit inicial.
- [x] T0.3 Crear repo en GitHub y hacer push. Verificación: el repo se ve en github.com.

## Sprint 1: Backend + Docker
- [x] T1.1 Esqueleto Spring Boot `.war` con endpoint `/api/health`. Verificación: `mvn package` y despliegue en WildFly devuelve 200.
- [x] T1.2 Docker Compose (WildFly 41 + MySQL 8) con el `.war` desplegado. Verificación: `docker compose up` y `/api/health` responde.
- [x] T1.3 Usuarios y roles + login JWT (Flyway, Spring Security). Verificación: test de integración de login; Swagger muestra los endpoints.
- [x] T1.4 Partidas y jugadas (CRUD + endpoint que recibe la jugada desde Android). Verificación: tests de servicio; prueba en Swagger.
- [x] T1.5 Logs SLF4J en servicios y manejo global de errores. Verificación: los logs aparecen en WildFly.

### Checkpoint 1: API funcionando en Docker, revisión con Daniel

## Sprint 2: Web React
- [x] T2.1 Vite + React, login contra la API, ruta protegida. Verificación: login real y token guardado.
- [x] T2.2 Dashboard (resumen de partidas/usuarios). Verificación: datos reales de la API.
- [x] T2.3 CRUD de usuarios con roles (solo ADMIN). Verificación: test e2e con playwright. (probado a mano en el navegador; e2e con playwright pendiente)
- [x] T2.4 Listado/detalle de partidas con tablero. Verificación: manual.

### Checkpoint 2: flujo web completo

## Sprint 3: Android + visión
- [x] T3.1 App Kotlin con login (Retrofit) y CameraX. Verificación: login desde el teléfono.
- [x] T3.2 Detección del tablero y rejilla 8x8 con OpenCV. Verificación: fotos de prueba con la rejilla dibujada.
- [x] T3.3 Deducción de jugada por diferencia de ocupación + jugadas legales. Verificación: test unitario con posiciones conocidas.
- [x] T3.4 Envío de jugada a la API + corrección manual. Verificación: la jugada aparece en la web.

### Checkpoint 3: demo de punta a punta (foto → jugada → web)

## Sprint 4: Reportes y defensa
- [x] T4.1 Reportes PDF y Excel de partidas/usuarios. Verificación: descarga desde la web.
- [x] T4.2 UML (casos de uso, clases, ER) en `docs/`. Verificación: diagramas renderizados.
- [x] T4.3 README final (instalación, Docker, capturas). Verificación: seguir el README en limpio.
- [ ] T4.4 Guion de la defensa en vivo.
