CREATE TABLE usuario (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre        VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol           VARCHAR(20)  NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en     DATETIME(6)  NOT NULL
);

CREATE TABLE partida (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo         VARCHAR(120),
    ronda          INT,
    blancas        VARCHAR(100) NOT NULL,
    negras         VARCHAR(100) NOT NULL,
    estado         VARCHAR(20)  NOT NULL,
    resultado      VARCHAR(7)   NOT NULL,
    fen_actual     VARCHAR(100) NOT NULL,
    registrada_por BIGINT       NOT NULL,
    creada_en      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_partida_usuario FOREIGN KEY (registrada_por) REFERENCES usuario (id)
);

CREATE TABLE jugada (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    partida_id     BIGINT       NOT NULL,
    numero         INT          NOT NULL,
    uci            VARCHAR(5)   NOT NULL,
    san            VARCHAR(10)  NOT NULL,
    fen_resultante VARCHAR(100) NOT NULL,
    origen         VARCHAR(10)  NOT NULL,
    confianza      DOUBLE,
    registrada_en  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_jugada_partida FOREIGN KEY (partida_id) REFERENCES partida (id) ON DELETE CASCADE,
    CONSTRAINT uq_jugada_numero UNIQUE (partida_id, numero)
);
