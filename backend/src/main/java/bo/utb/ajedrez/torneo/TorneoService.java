package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.partida.Partida;
import bo.utb.ajedrez.partida.PartidaRepository;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoRequest;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TorneoService {

    private static final Logger log = LoggerFactory.getLogger(TorneoService.class);

    private final TorneoRepository repo;
    private final PartidaRepository partidas;

    public TorneoService(TorneoRepository repo, PartidaRepository partidas) {
        this.repo = repo;
        this.partidas = partidas;
    }

    @Transactional(readOnly = true)
    public List<TorneoResponse> listar() {
        return repo.findAllByOrderByCreadoEnDesc().stream().map(this::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public TorneoResponse detalle(Long id) {
        return aRespuesta(buscar(id));
    }

    public Torneo buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoEncontradoException("Torneo " + id + " no existe"));
    }

    public TorneoResponse crear(TorneoRequest req) {
        Torneo t = repo.save(aplicar(new Torneo(), req));
        log.info("Torneo creado id={} nombre={} categoria={}", t.getId(), t.getNombre(), t.getCategoria());
        return aRespuesta(t);
    }

    public TorneoResponse actualizar(Long id, TorneoRequest req) {
        Torneo t = aplicar(buscar(id), req);
        log.info("Torneo actualizado id={}", id);
        return aRespuesta(t);
    }

    public void eliminar(Long id) {
        Torneo t = buscar(id);
        if (partidas.countByTorneoId(id) > 0) {
            throw new ReglaNegocioException("El torneo tiene partidas; elimínelas antes de borrar el torneo");
        }
        repo.delete(t);
        log.info("Torneo eliminado id={}", id);
    }

    private Torneo aplicar(Torneo t, TorneoRequest req) {
        t.setNombre(req.nombre().trim());
        t.setCategoria(vacioANull(req.categoria()));
        t.setCiudad(vacioANull(req.ciudad()));
        t.setFechaInicio(req.fechaInicio());
        return t;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private TorneoResponse aRespuesta(Torneo t) {
        return new TorneoResponse(t.getId(), t.getNombre(), t.getCategoria(), t.getCiudad(), t.getFechaInicio(),
                partidas.countByTorneoId(t.getId()),
                partidas.countByTorneoIdAndEstado(t.getId(), Partida.Estado.EN_CURSO));
    }
}
