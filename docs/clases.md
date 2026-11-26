# Diagrama de clases (backend)

Clases principales del paquete `bo.utb.ajedrez`. Los controladores REST dependen de servicios; los servicios usan repositorios Spring Data JPA.

```mermaid
classDiagram
    direction LR

    class Usuario {
        -Long id
        -String nombre
        -String email
        -String passwordHash
        -Rol rol
        -boolean activo
        -LocalDateTime creadoEn
    }
    class Rol {
        <<enumeration>>
        ADMIN
        ARBITRO
        JUGADOR
    }
    class Partida {
        -Long id
        -String torneo
        -Integer ronda
        -String blancas
        -String negras
        -Estado estado
        -String resultado
        -String fenActual
        -LocalDateTime creadaEn
    }
    class Estado {
        <<enumeration>>
        EN_CURSO
        FINALIZADA
    }
    class Jugada {
        -Long id
        -int numero
        -String uci
        -String san
        -String fenResultante
        -Origen origen
        -Double confianza
        -LocalDateTime registradaEn
    }
    class Origen {
        <<enumeration>>
        VISION
        MANUAL
    }

    class UsuarioService {
        +listar() List~Usuario~
        +crear(UsuarioRequest) Usuario
        +actualizar(Long, UsuarioRequest) Usuario
        +eliminar(Long)
    }
    class PartidaService {
        +listar(Estado) List~PartidaResumen~
        +crear(PartidaRequest, Long) PartidaDetalle
        +registrarJugada(Long, JugadaRequest) PartidaDetalle
        +deshacerUltima(Long) PartidaDetalle
        +finalizar(Long, String) PartidaDetalle
    }
    class ReporteService {
        +partidasPdf() byte[]
        +planillaPdf(Long) byte[]
        +partidasExcel() byte[]
    }
    class AuthController {
        +login(LoginRequest) LoginResponse
        +yo(Jwt) Yo
    }
    class UsuarioController
    class PartidaController
    class ReporteController
    class DashboardController
    class UsuarioRepository {
        <<interface>>
    }
    class PartidaRepository {
        <<interface>>
    }

    Usuario --> Rol
    Partida --> Estado
    Jugada --> Origen
    Partida "1" *-- "0..*" Jugada : jugadas
    Partida "0..*" --> "1" Usuario : registradaPor

    UsuarioController --> UsuarioService
    PartidaController --> PartidaService
    ReporteController --> ReporteService
    AuthController --> UsuarioRepository
    DashboardController --> UsuarioRepository
    DashboardController --> PartidaRepository
    UsuarioService --> UsuarioRepository
    PartidaService --> PartidaRepository
    PartidaService --> UsuarioRepository
    ReporteService --> PartidaRepository
```

`PartidaService.registrarJugada` usa la librería chesslib para validar que la jugada UCI sea legal en `fenActual`, obtener la notación SAN y detectar mate, ahogado o material insuficiente.
