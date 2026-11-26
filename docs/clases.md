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
    class Torneo {
        -Long id
        -String nombre
        -String categoria
        -String ciudad
        -LocalDate fechaInicio
    }
    class Partida {
        -Long id
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
        +listar(Estado, Long torneoId) List~PartidaResumen~
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
    Partida "0..*" --> "1" Torneo : torneo
    class TorneoService {
        +listar() List~TorneoResponse~
        +crear(TorneoRequest) TorneoResponse
        +actualizar(Long, TorneoRequest) TorneoResponse
        +eliminar(Long)
    }
    class TorneoController
    TorneoController --> TorneoService
    PartidaService --> TorneoService

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

## App Android

```mermaid
classDiagram
    direction LR
    class CamaraActivity {
        -Mat referencia
        -String fen
        +buscarTableroAuto()
        +leerJugada()
        +deshacer()
    }
    class Vision {
        <<object>>
        +buscarTablero(Bitmap) List~P~
        +enderezar(Bitmap, List~P~) Mat
        +cambios(Mat, Mat, Lado) DoubleArray
        +cuadricula(List~P~) List~P~
    }
    class Geometria {
        <<object>>
        +ajustarLineas(DoubleArray, Int)
        +orientar(DoubleArray) IntArray
        +puntajeInicial(DoubleArray) Double
        +ladoCercano(List~P~) Lado
        +zonaBase(Lado) DoubleArray
    }
    class Deduccion {
        <<object>>
        +deducir(String fen, DoubleArray cambios) Resultado
    }
    class Api {
        +login(email, password)
        +partidasEnCurso()
        +registrarJugada(id, uci, confianza)
        +deshacer(id)
    }
    class LoginActivity
    class PartidasActivity
    class Marcas

    LoginActivity --> Api
    PartidasActivity --> Api
    CamaraActivity --> Api
    CamaraActivity --> Vision
    CamaraActivity --> Deduccion
    CamaraActivity --> Marcas
    Vision --> Geometria
```

`Vision` usa OpenCV; `Geometria` y `Deduccion` son lógica pura con tests unitarios (19 tests, dos con datos medidos en una prueba real).

## Validación de jugadas en el servidor

`PartidaService.registrarJugada` usa la librería chesslib para validar que la jugada UCI sea legal en `fenActual`, obtener la notación SAN y detectar mate, ahogado o material insuficiente.
