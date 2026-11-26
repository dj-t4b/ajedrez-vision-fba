-- Registro de todo lo que hace cada usuario y cada celular (solo lo ve el administrador)
CREATE TABLE auditoria (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha   DATETIME(6)   NOT NULL,
    actor   VARCHAR(160)  NOT NULL,
    tipo    VARCHAR(20)   NOT NULL,
    accion  VARCHAR(80)   NOT NULL,
    metodo  VARCHAR(8)    NOT NULL,
    ruta    VARCHAR(200)  NOT NULL,
    estado  INT           NOT NULL,
    ip      VARCHAR(45),
    detalle VARCHAR(1000)
);

CREATE INDEX ix_auditoria_fecha ON auditoria (fecha);
