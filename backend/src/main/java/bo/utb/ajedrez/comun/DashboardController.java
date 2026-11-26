package bo.utb.ajedrez.comun;

import bo.utb.ajedrez.partida.Jugada;
import bo.utb.ajedrez.partida.Partida;
import bo.utb.ajedrez.partida.PartidaRepository;
import bo.utb.ajedrez.torneo.TorneoRepository;
import bo.utb.ajedrez.usuario.Rol;
import bo.utb.ajedrez.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@Tag(name = "Dashboard")
public class DashboardController {

    public record Resumen(long torneos, long usuarios, Map<Rol, Long> usuariosPorRol, long partidasEnCurso,
                          long partidasFinalizadas, long jugadasVision, long jugadasManuales) {}

    private final UsuarioRepository usuarios;
    private final PartidaRepository partidas;
    private final TorneoRepository torneos;

    public DashboardController(UsuarioRepository usuarios, PartidaRepository partidas, TorneoRepository torneos) {
        this.usuarios = usuarios;
        this.partidas = partidas;
        this.torneos = torneos;
    }

    @GetMapping("/api/dashboard")
    public Resumen resumen() {
        return new Resumen(
                torneos.count(),
                usuarios.count(),
                Arrays.stream(Rol.values()).collect(Collectors.toMap(Function.identity(), usuarios::countByRol)),
                partidas.countByEstado(Partida.Estado.EN_CURSO),
                partidas.countByEstado(Partida.Estado.FINALIZADA),
                partidas.contarJugadasPorOrigen(Jugada.Origen.VISION),
                partidas.contarJugadasPorOrigen(Jugada.Origen.MANUAL));
    }
}
