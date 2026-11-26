-- Las partidas pasan a pertenecer a un torneo (puede haber varios a la vez, p. ej. por categoría).
CREATE TABLE torneo (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(120) NOT NULL,
    categoria    VARCHAR(60),
    ciudad       VARCHAR(80),
    fecha_inicio DATE,
    creado_en    DATETIME(6)  NOT NULL
);

-- Cada nombre de torneo que ya tenían las partidas se convierte en un torneo
INSERT INTO torneo (nombre, creado_en)
SELECT DISTINCT COALESCE(torneo, 'Sin torneo'), CURRENT_TIMESTAMP FROM partida;

ALTER TABLE partida ADD COLUMN torneo_id BIGINT;

UPDATE partida SET torneo_id = (
    SELECT MIN(t.id) FROM torneo t WHERE t.nombre = COALESCE(partida.torneo, 'Sin torneo'));

ALTER TABLE partida MODIFY torneo_id BIGINT NOT NULL;
ALTER TABLE partida ADD CONSTRAINT fk_partida_torneo FOREIGN KEY (torneo_id) REFERENCES torneo (id);
ALTER TABLE partida DROP COLUMN torneo;
