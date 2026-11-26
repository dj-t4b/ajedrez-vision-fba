package bo.utb.ajedrez.partida;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.partida.PartidaDtos.*;
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

@Service
@Transactional
public class PartidaService {

    private static final Logger log = LoggerFactory.getLogger(PartidaService.class);

    private final PartidaRepository repo;
    private final UsuarioRepository usuarios;

    public PartidaService(PartidaRepository repo, UsuarioRepository usuarios) {
        this.repo = repo;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<PartidaResumen> listar(Partida.Estado estado) {
        List<Partida> ps = estado == null ? repo.findAllByOrderByCreadaEnDesc() : repo.findByEstadoOrderByCreadaEnDesc(estado);
        return ps.stream().map(PartidaResumen::de).toList();
    }

    @Transactional(readOnly = true)
    public PartidaDetalle detalle(Long id) {
        return aDetalle(buscar(id));
    }

    public PartidaDetalle crear(PartidaRequest req, Long usuarioId) {
        Partida p = new Partida();
        p.setTorneo(req.torneo());
        p.setRonda(req.ronda());
        p.setBlancas(req.blancas().trim());
        p.setNegras(req.negras().trim());
        p.setRegistradaPor(usuarios.findById(usuarioId)
                .orElseThrow(() -> new NoEncontradoException("Usuario " + usuarioId + " no existe")));
        p = repo.save(p);
        log.info("Partida creada id={} {} vs {}", p.getId(), p.getBlancas(), p.getNegras());
        return aDetalle(p);
    }

    /** Valida la jugada contra la posición actual; la app Android solo envía casilla de origen y destino. */
    public PartidaDetalle registrarJugada(Long id, JugadaRequest req) {
        Partida p = buscar(id);
        if (p.getEstado() != Partida.Estado.EN_CURSO) {
            throw new ReglaNegocioException("La partida " + id + " ya terminó");
        }
        Board tablero = new Board();
        tablero.loadFromFen(p.getFenActual());
        Move mov = new Move(req.uci(), tablero.getSideToMove());
        if (!tablero.legalMoves().contains(mov)) {
            log.warn("Jugada ilegal partida={} uci={} fen={}", id, req.uci(), p.getFenActual());
            throw new ReglaNegocioException("Jugada ilegal: " + req.uci());
        }
        MoveList ml = new MoveList(p.getFenActual());
        ml.add(mov);
        String san = ml.toSanArray()[0];
        tablero.doMove(mov);

        p.getJugadas().add(new Jugada(p, p.getJugadas().size() + 1, req.uci(), san, tablero.getFen(),
                req.origen(), req.confianza()));
        p.setFenActual(tablero.getFen());
        log.info("Jugada partida={} #{} {} origen={} confianza={}", id, p.getJugadas().size(), san, req.origen(), req.confianza());

        if (tablero.isMated()) {
            terminar(p, tablero.getSideToMove() == Side.WHITE ? "0-1" : "1-0");
        } else if (tablero.isStaleMate() || tablero.isInsufficientMaterial()) {
            terminar(p, "1/2-1/2");
        }
        return aDetalle(p);
    }

    /** Corrección: deshace la última jugada (p. ej. si la visión se equivocó). */
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

    public void eliminar(Long id) {
        repo.delete(buscar(id));
        log.info("Partida eliminada id={}", id);
    }

    private void terminar(Partida p, String resultado) {
        p.setEstado(Partida.Estado.FINALIZADA);
        p.setResultado(resultado);
        log.info("Partida finalizada id={} resultado={}", p.getId(), resultado);
    }

    private Partida buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoEncontradoException("Partida " + id + " no existe"));
    }

    private PartidaDetalle aDetalle(Partida p) {
        return new PartidaDetalle(PartidaResumen.de(p),
                p.getJugadas().stream().map(JugadaResponse::de).toList(), pgn(p));
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
