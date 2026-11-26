package bo.utb.ajedrez.partida;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PartidaRepository extends JpaRepository<Partida, Long> {

    /** Partidas filtradas por estado y/o torneo (null = sin filtro), las más recientes primero. */
    @Query("select p from Partida p where (:estado is null or p.estado = :estado)"
            + " and (:torneoId is null or p.torneo.id = :torneoId) order by p.creadaEn desc")
    List<Partida> buscar(Partida.Estado estado, Long torneoId);

    long countByEstado(Partida.Estado estado);
    long countByTorneoId(Long torneoId);
    long countByTorneoIdAndEstado(Long torneoId, Partida.Estado estado);

    @Query("select count(j) from Jugada j where j.origen = :origen")
    long contarJugadasPorOrigen(Jugada.Origen origen);
}
