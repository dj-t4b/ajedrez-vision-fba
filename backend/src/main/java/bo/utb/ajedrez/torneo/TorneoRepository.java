package bo.utb.ajedrez.torneo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TorneoRepository extends JpaRepository<Torneo, Long> {
    List<Torneo> findAllByOrderByCreadoEnDesc();
    Optional<Torneo> findByVinculacionPinHashAndVinculacionVenceEnAfter(String pinHash, LocalDateTime ahora);
}
