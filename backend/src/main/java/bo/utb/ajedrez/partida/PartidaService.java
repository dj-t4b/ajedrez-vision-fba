package bo.utb.ajedrez.partida;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.partida.PartidaDtos.*;
import bo.utb.ajedrez.torneo.TorneoService;
import bo.utb.ajedrez.usuario.UsuarioRepository;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PartidaService {

    private static final Logger log = LoggerFactory.getLogger(PartidaService.class);

    private final PartidaRepository repo;
    private final UsuarioRepository usuarios;
    private final TorneoService torneos;

    public PartidaService(PartidaRepository repo, UsuarioRepository usuarios, TorneoService torneos) {
        this.repo = repo;
        this.usuarios = usuarios;
        this.torneos = torneos;
    }

    @Transactional(readOnly = true)
    public List<Partida> listar(Partida.Estado estado, Long torneoId) {
        return repo.buscar(estado, torneoId);
    }

    @Transactional(readOnly = true)
    public PartidaDetalle detalle(Long id) {
        return aDetalle(buscar(id));
    }

    /** La partida en curso de una mesa (la que toma el celular de esa mesa). */
    @Transactional(readOnly = true)
    public Optional<Partida> enCurso(Long torneoId, int mesa) {
        return repo.findFirstByTorneoIdAndMesaAndEstadoOrderByCreadaEnDesc(torneoId, mesa, Partida.Estado.EN_CURSO);
    }

    public PartidaDetalle crear(PartidaRequest req, Long usuarioId) {
        if (req.mesa() != null && enCurso(req.torneoId(), req.mesa()).isPresent()) {
            throw new ReglaNegocioException("La mesa " + req.mesa() + " ya tiene una partida en curso");
        }
        Partida p = new Partida();
        p.setTorneo(torneos.buscar(req.torneoId()));
        p.setMesa(req.mesa());
        p.setRonda(req.ronda());
        p.setBlancas(req.blancas().trim());
        p.setNegras(req.negras().trim());
        p.setRegistradaPor(usuarios.findById(usuarioId)
                .orElseThrow(() -> new NoEncontradoException("Usuario " + usuarioId + " no existe")));
        p = repo.save(p);
        log.info("Partida creada id={} mesa={} {} vs {}", p.getId(), p.getMesa(), p.getBlancas(), p.getNegras());
        return aDetalle(p);
    }

    /**
     * Valida la jugada contra la posición actual; la app Android solo envía casilla de origen y destino.
     * @param autor quién la registra: el celular (C-07 · Mesa 3) o el nombre del árbitro
     */
    public PartidaDetalle registrarJugada(Long id, JugadaRequest req, String autor) {
        Partida p = buscar(id);
        aplicar(p, req, autor);
        return aDetalle(p);
    }

    /**
     * Corrección desde la web: reemplaza la jugada número [numero] (y descarta las posteriores)
     * por [req]. La cámara sigue desde la posición corregida.
     */
    public PartidaDetalle corregir(Long id, int numero, JugadaRequest req, String autor) {
        Partida p = buscar(id);
        List<Jugada> js = p.getJugadas();
        if (numero < 1 || numero > js.size()) throw new ReglaNegocioException("La partida no tiene la jugada " + numero);
        List<String> descartadas = js.subList(numero - 1, js.size()).stream().map(Jugada::getSan).toList();
        js.subList(numero - 1, js.size()).clear();
        repo.flush(); // libera los números de jugada antes de volver a usarlos
        p.setFenActual(numero == 1 ? Partida.FEN_INICIAL : js.get(numero - 2).getFenResultante());
        p.setEstado(Partida.Estado.EN_CURSO);
        p.setResultado("*");
        log.info("Corrección partida={} desde #{} descartadas={} por {}", id, numero, descartadas, autor);
        aplicar(p, req, autor);
        return aDetalle(p);
    }

    /** Deshace la última jugada. */
    public PartidaDetalle deshacerUltima(Long id) {
        Partida p = buscar(id);
        List<Jugada> js = p.getJugadas();
        if (js.isEmpty()) {
            throw new ReglaNegocioException("La partida no tiene jugadas");
        }
        Jugada quitada = js.remove(js.size() - 1);
        p.setFenActual(js.isEmpty() ? Partida.FEN_INICIAL : js.get(js.size() - 1).getFenResultante());
        p.setEstado(Partida.Estado.EN_CURSO);
        p.setResultado("*");
        log.info("Jugada deshecha partida={} #{} {}", id, quitada.getNumero(), quitada.getSan());
        return aDetalle(p);
    }

    public PartidaDetalle finalizar(Long id, String resultado) {
        Partida p = buscar(id);
        terminar(p, resultado);
        return aDetalle(p);
    }

    public PartidaDetalle reabrir(Long id) {
        Partida p = buscar(id);
        if (p.getMesa() != null && enCurso(p.getTorneo().getId(), p.getMesa()).filter(o -> !o.getId().equals(id)).isPresent()) {
            throw new ReglaNegocioException("La mesa " + p.getMesa() + " ya tiene otra partida en curso");
        }
        p.setEstado(Partida.Estado.EN_CURSO);
        p.setResultado("*");
        log.info("Partida reabierta id={}", id);
        return aDetalle(p);
    }

    public void eliminar(Long id) {
        repo.delete(buscar(id));
        log.info("Partida eliminada id={}", id);
    }

    public Partida buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoEncontradoException("Partida " + id + " no existe"));
    }

    private void aplicar(Partida p, JugadaRequest req, String autor) {
        if (p.getEstado() != Partida.Estado.EN_CURSO) {
            throw new ReglaNegocioException("La partida " + p.getId() + " ya terminó");
        }
        Board tablero = new Board();
        tablero.loadFromFen(p.getFenActual());
        Move mov = new Move(req.uci(), tablero.getSideToMove());
        if (!tablero.legalMoves().contains(mov)) {
            log.warn("Jugada ilegal partida={} uci={} fen={}", p.getId(), req.uci(), p.getFenActual());
            throw new ReglaNegocioException("Jugada ilegal: " + req.uci());
        }
        MoveList ml = new MoveList(p.getFenActual());
        ml.add(mov);
        String san = ml.toSanArray()[0];
        tablero.doMove(mov);

        p.getJugadas().add(new Jugada(p, p.getJugadas().size() + 1, req.uci(), san, tablero.getFen(),
                req.origen(), req.confianza(), autor));
        p.setFenActual(tablero.getFen());
        log.info("Jugada partida={} #{} {} origen={} confianza={} por {}", p.getId(), p.getJugadas().size(), san,
                req.origen(), req.confianza(), autor);

        if (tablero.isMated()) {
            terminar(p, tablero.getSideToMove() == Side.WHITE ? "0-1" : "1-0");
        } else if (tablero.isStaleMate() || tablero.isInsufficientMaterial()) {
            terminar(p, "1/2-1/2");
        }
    }

    private void terminar(Partida p, String resultado) {
        p.setEstado(Partida.Estado.FINALIZADA);
        p.setResultado(resultado);
        log.info("Partida finalizada id={} resultado={}", p.getId(), resultado);
    }

    public PartidaDetalle aDetalle(Partida p) {
        return new PartidaDetalle(PartidaResumen.de(p),
                p.getJugadas().stream().map(JugadaResponse::de).toList(), pgn(p), null);
    }

    public static String pgn(Partida p) {
        StringBuilder sb = new StringBuilder();
        List<Jugada> js = p.getJugadas();
        for (int i = 0; i < js.size(); i++) {
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(js.get(i).getSan()).append(' ');
        }
        return sb.append(p.getResultado()).toString();
    }
}
