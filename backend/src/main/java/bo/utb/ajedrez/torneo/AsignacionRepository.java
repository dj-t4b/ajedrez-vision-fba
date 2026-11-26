package bo.utb.ajedrez.torneo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {
    Optional<Asignacion> findByTorneoIdAndUsuarioId(Long torneoId, Long usuarioId);
    List<Asignacion> findByTorneoIdOrderByRol(Long torneoId);
    List<Asignacion> findByUsuarioId(Long usuarioId);
}
