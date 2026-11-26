# Diagrama entidad-relación (MySQL)

El esquema lo crea Flyway con `backend/src/main/resources/db/migration/` (V1: esquema inicial; V2: torneos; V3: roles por torneo y celulares; V4: auditoría; con migración de los datos existentes). La tabla AUDITORIA no tiene claves foráneas a propósito: el registro se conserva aunque se borre lo que describe..

```mermaid
erDiagram
    TORNEO ||--o{ PARTIDA : agrupa
    TORNEO ||--o{ ASIGNACION_TORNEO : "equipo"
    USUARIO ||--o{ ASIGNACION_TORNEO : "tiene rol en"
    ASIGNACION_TORNEO ||--o{ ACCESO_TABLERO : "mesas del árbitro"
    TORNEO ||--o{ DISPOSITIVO : "celulares"
    USUARIO ||--o{ PARTIDA : registra
    PARTIDA ||--o{ JUGADA : contiene

    USUARIO {
        BIGINT id PK
        VARCHAR nombre
        VARCHAR email UK
        VARCHAR password_hash "BCrypt"
        VARCHAR rol "ADMIN, USUARIO"
        BOOLEAN activo
        DATETIME creado_en
    }
    TORNEO {
        BIGINT id PK
        VARCHAR nombre
        VARCHAR categoria "Absoluto, Sub-14..."
        VARCHAR ciudad
        DATE fecha_inicio
        DATETIME creado_en
        VARCHAR vinculacion_pin_hash "SHA-256 del PIN"
        DATETIME vinculacion_vence_en
    }
    ASIGNACION_TORNEO {
        BIGINT id PK
        BIGINT usuario_id FK
        BIGINT torneo_id FK
        VARCHAR rol "ORGANIZADOR, ARBITRO_PRINCIPAL, ARBITRO"
    }
    ACCESO_TABLERO {
        BIGINT asignacion_id PK
        INT mesa PK "sin filas = todas"
    }
    AUDITORIA {
        BIGINT id PK
        DATETIME fecha
        VARCHAR actor "persona o celular"
        VARCHAR tipo "ADMIN, USUARIO, CELULAR, ANONIMO"
        VARCHAR accion
        VARCHAR metodo
        VARCHAR ruta
        INT estado "HTTP"
        VARCHAR ip
        VARCHAR detalle "sin contraseñas ni PIN"
    }
    DISPOSITIVO {
        BIGINT id PK
        BIGINT torneo_id FK
        VARCHAR codigo "C-07"
        INT mesa
        VARCHAR token_hash UK
        DATETIME vinculado_en
        DATETIME ultimo_contacto
        BOOLEAN revocado
    }
    PARTIDA {
        BIGINT id PK
        BIGINT torneo_id FK
        INT mesa
        INT ronda
        VARCHAR blancas
        VARCHAR negras
        VARCHAR estado "EN_CURSO, FINALIZADA"
        VARCHAR resultado "1-0, 0-1, 1/2-1/2, *"
        VARCHAR fen_actual
        BIGINT registrada_por FK
        DATETIME creada_en
    }
    JUGADA {
        BIGINT id PK
        BIGINT partida_id FK "ON DELETE CASCADE"
        INT numero "único por partida"
        VARCHAR uci "e2e4"
        VARCHAR san "e4"
        VARCHAR fen_resultante
        VARCHAR origen "VISION, MANUAL"
        DOUBLE confianza "0..1"
        VARCHAR registrada_por "C-07 · Mesa 3 o el árbitro"
        DATETIME registrada_en
    }
```
