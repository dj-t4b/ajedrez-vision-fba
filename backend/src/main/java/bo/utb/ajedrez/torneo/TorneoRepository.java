package bo.utb.ajedrez.torneo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TorneoRepository extends JpaRepository<Torneo, Long> {
    List<Torneo> findAllByOrderByCreadoEnDesc();
}
