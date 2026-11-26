package bo.utb.ajedrez.dispositivo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {
    Optional<Dispositivo> findByTokenHashAndRevocadoFalse(String tokenHash);
    List<Dispositivo> findByTorneoIdAndRevocadoFalseOrderByMesa(Long torneoId);
    long countByTorneoId(Long torneoId);
}
