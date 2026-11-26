package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.comun.Secretos;
import bo.utb.ajedrez.partida.Partida;
import bo.utb.ajedrez.partida.PartidaRepository;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoRequest;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoResponse;
import bo.utb.ajedrez.torneo.TorneoDtos.Vinculacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Service
@Transactional
public class TorneoService {

    private static final Logger log = LoggerFactory.getLogger(TorneoService.class);
    private static final int MINUTOS_VINCULACION = 15;

    private final TorneoRepository repo;
    private final PartidaRepository partidas;
    private final String servidorPublico;

    public TorneoService(TorneoRepository repo, PartidaRepository partidas,
                         @Value("${app.servidor-publico}") String servidorPublico) {
        this.repo = repo;
        this.partidas = partidas;
        this.servidorPublico = servidorPublico;
    }

    @Transactional(readOnly = true)
    public List<Torneo> todos() {
        return repo.findAllByOrderByCreadoEnDesc();
    }

    public Torneo buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoEncontradoException("Torneo " + id + " no existe"));
    }

    public Torneo crear(TorneoRequest req) {
        Torneo t = repo.save(aplicar(new Torneo(), req));
        log.info("Torneo creado id={} nombre={} categoria={}", t.getId(), t.getNombre(), t.getCategoria());
        return t;
    }

    public Torneo actualizar(Long id, TorneoRequest req) {
        Torneo t = aplicar(buscar(id), req);
        log.info("Torneo actualizado id={}", id);
        return t;
    }

    public void eliminar(Long id) {
        Torneo t = buscar(id);
        if (partidas.countByTorneoId(id) > 0) {
            throw new ReglaNegocioException("El torneo tiene partidas; elimínelas antes de borrar el torneo");
        }
        repo.delete(t);
        log.info("Torneo eliminado id={}", id);
    }

    /** Abre la vinculación de celulares: un PIN de 6 dígitos válido por 15 minutos. */
    public Vinculacion abrirVinculacion(Long id) {
        Torneo t = buscar(id);
        String pin;
        do {
            pin = Secretos.pin();
        } while (repo.findByVinculacionPinHashAndVinculacionVenceEnAfter(Secretos.hash(pin), LocalDateTime.now()).isPresent());
        t.setVinculacionPinHash(Secretos.hash(pin));
        t.setVinculacionVenceEn(LocalDateTime.now().plusMinutes(MINUTOS_VINCULACION));
        log.info("Vinculación abierta torneo={} hasta {}", id, t.getVinculacionVenceEn());
        return new Vinculacion(pin, t.getVinculacionVenceEn(), servidorPublico);
    }

    public void cerrarVinculacion(Long id) {
        Torneo t = buscar(id);
        t.setVinculacionPinHash(null);
        t.setVinculacionVenceEn(null);
        log.info("Vinculación cerrada torneo={}", id);
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

    @Transactional(readOnly = true)
    public TorneoResponse aRespuesta(Torneo t, Function<Long, String> miRol) {
        LocalDateTime hasta = t.getVinculacionVenceEn();
        return new TorneoResponse(t.getId(), t.getNombre(), t.getCategoria(), t.getCiudad(), t.getFechaInicio(),
                partidas.countByTorneoId(t.getId()),
                partidas.countByTorneoIdAndEstado(t.getId(), Partida.Estado.EN_CURSO),
                miRol.apply(t.getId()),
                hasta != null && hasta.isAfter(LocalDateTime.now()) ? hasta : null);
    }
}
