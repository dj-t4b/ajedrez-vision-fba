-- Roles: el rol global queda en ADMIN o USUARIO; los roles de árbitro y organizador pasan a ser por torneo.
UPDATE usuario SET rol = 'USUARIO' WHERE rol <> 'ADMIN';

CREATE TABLE asignacion_torneo (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT      NOT NULL,
    torneo_id  BIGINT      NOT NULL,
    rol        VARCHAR(20) NOT NULL,
    CONSTRAINT uq_asignacion UNIQUE (usuario_id, torneo_id),
    CONSTRAINT fk_asignacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_asignacion_torneo FOREIGN KEY (torneo_id) REFERENCES torneo (id) ON DELETE CASCADE
);

-- Mesas a las que accede un árbitro; sin filas = todas las mesas del torneo
CREATE TABLE acceso_tablero (
    asignacion_id BIGINT NOT NULL,
    mesa          INT    NOT NULL,
    PRIMARY KEY (asignacion_id, mesa),
    CONSTRAINT fk_acceso_asignacion FOREIGN KEY (asignacion_id) REFERENCES asignacion_torneo (id) ON DELETE CASCADE
);

ALTER TABLE partida ADD COLUMN mesa INT;
ALTER TABLE jugada ADD COLUMN registrada_por VARCHAR(100);

-- Ventana de vinculación de celulares (PIN de un torneo, guardado como hash)
ALTER TABLE torneo ADD COLUMN vinculacion_pin_hash VARCHAR(64);
ALTER TABLE torneo ADD COLUMN vinculacion_vence_en DATETIME(6);

CREATE TABLE dispositivo (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id       BIGINT      NOT NULL,
    codigo          VARCHAR(10) NOT NULL,
    mesa            INT         NOT NULL,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    vinculado_en    DATETIME(6) NOT NULL,
    ultimo_contacto DATETIME(6),
    revocado        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_dispositivo_torneo FOREIGN KEY (torneo_id) REFERENCES torneo (id) ON DELETE CASCADE
);
