# Sistema de ajedrez con visión por computadora (FBA)

Proyecto de grado, Ingeniería de Sistemas (UTB).

Una app Android reconoce el tablero con la cámara y envía las jugadas a una API REST. Una web muestra el panel, gestiona usuarios por rol y genera reportes.

| Carpeta | Contenido |
|---|---|
| `backend/` | API REST Spring Boot 3 (`.war` en WildFly 41), JWT, Swagger, MySQL |
| `web/` | Panel React + Vite |
| `android/` | App Kotlin con CameraX y OpenCV |
| `docs/` | Diagramas UML (casos de uso, clases, ER) |
| `tasks/` | Plan y tareas por sprint |

Las instrucciones de instalación y ejecución con Docker Compose se agregan al terminar el Sprint 1.
