package bo.utb.ajedrez.auditoria;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /** Filtros opcionales (null = sin filtro); texto busca en actor, acción, ruta y detalle. */
    @Query("select a from Auditoria a where (:desde is null or a.fecha >= :desde) and (:hasta is null or a.fecha < :hasta)"
            + " and (:tipo is null or a.tipo = :tipo)"
            + " and (:texto is null or lower(a.actor) like :texto or lower(a.accion) like :texto"
            + "      or lower(a.ruta) like :texto or lower(a.detalle) like :texto)"
            + " order by a.fecha desc, a.id desc")
    List<Auditoria> buscar(LocalDateTime desde, LocalDateTime hasta, Auditoria.Tipo tipo, String texto, Pageable pagina);
}
