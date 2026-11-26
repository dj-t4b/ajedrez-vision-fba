package bo.utb.ajedrez.dispositivo;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.comun.Secretos;
import bo.utb.ajedrez.torneo.Torneo;
import bo.utb.ajedrez.torneo.TorneoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class DispositivoService {

    private static final Logger log = LoggerFactory.getLogger(DispositivoService.class);

    /** Resultado de vincular: el token solo se entrega aquí, una vez. */
    public record Vinculado(String token, Dispositivo dispositivo) {}

    private final DispositivoRepository repo;
    private final TorneoRepository torneos;

    public DispositivoService(DispositivoRepository repo, TorneoRepository torneos) {
        this.repo = repo;
        this.torneos = torneos;
    }

    /**
     * Canjea el PIN de la vinculación abierta de un torneo por un token de dispositivo.
     * El celular recibe un código corto (C-07) y la primera mesa libre; el árbitro principal la puede cambiar.
     */
    // shortcut: sin bloqueo por intentos fallidos (el PIN de 6 dígitos vale 15 minutos); agregarlo si se publica en internet
    public Vinculado vincular(String pin) {
        Torneo t = torneos.findByVinculacionPinHashAndVinculacionVenceEnAfter(Secretos.hash(pin.trim()), LocalDateTime.now())
                .orElseThrow(() -> new BadCredentialsException("PIN incorrecto o vencido"));
        Set<Integer> ocupadas = repo.findByTorneoIdAndRevocadoFalseOrderByMesa(t.getId()).stream()
                .map(Dispositivo::getMesa).collect(Collectors.toSet());
        int mesa = 1;
        while (ocupadas.contains(mesa)) mesa++;
        String codigo = String.format("C-%02d", repo.countByTorneoId(t.getId()) + 1);
        String token = Secretos.token();
        Dispositivo d = repo.save(new Dispositivo(t, codigo, mesa, Secretos.hash(token)));
        log.info("Celular vinculado torneo={} {} ", t.getId(), d.etiqueta());
        return new Vinculado(token, d);
    }

    /** El celular se identifica con su token; uno desvinculado ya no entra. */
    public Dispositivo autenticar(String token) {
        if (token == null || token.isBlank()) throw new BadCredentialsException("Falta el token del celular");
        Dispositivo d = repo.findByTokenHashAndRevocadoFalse(Secretos.hash(token))
                .orElseThrow(() -> new BadCredentialsException("Este celular fue desvinculado; vuelva a vincularlo"));
        d.setUltimoContacto(LocalDateTime.now());
        return d;
    }

    @Transactional(readOnly = true)
    public List<Dispositivo> delTorneo(Long torneoId) {
        return repo.findByTorneoIdAndRevocadoFalseOrderByMesa(torneoId);
    }

    public Dispositivo buscar(Long id) {
        return repo.findById(id).filter(d -> !d.isRevocado())
                .orElseThrow(() -> new NoEncontradoException("Celular " + id + " no existe"));
    }

    public Dispositivo cambiarMesa(Long id, int mesa) {
        if (mesa < 1) throw new ReglaNegocioException("La mesa debe ser 1 o mayor");
        Dispositivo d = buscar(id);
        boolean ocupada = delTorneo(d.getTorneo().getId()).stream().anyMatch(o -> o.getMesa() == mesa && !o.getId().equals(id));
        if (ocupada) throw new ReglaNegocioException("La mesa " + mesa + " ya tiene un celular");
        d.setMesa(mesa);
        log.info("Celular {} pasa a la mesa {}", d.getCodigo(), mesa);
        return d;
    }

    public void desvincular(Long id) {
        Dispositivo d = buscar(id);
        d.setRevocado(true);
        log.info("Celular desvinculado {}", d.etiqueta());
    }
}
