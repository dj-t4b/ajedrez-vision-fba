package bo.utb.ajedrez.partida;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PartidaRepository extends JpaRepository<Partida, Long> {
    List<Partida> findAllByOrderByCreadaEnDesc();
    List<Partida> findByEstadoOrderByCreadaEnDesc(Partida.Estado estado);
    long countByEstado(Partida.Estado estado);

    @Query("select count(j) from Jugada j where j.origen = :origen")
    long contarJugadasPorOrigen(Jugada.Origen origen);
}
