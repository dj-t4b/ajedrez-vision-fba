package bo.utb.ajedrez.partida;

import bo.utb.ajedrez.partida.PartidaDtos.*;
import bo.utb.ajedrez.torneo.Acceso;
import bo.utb.ajedrez.torneo.Acceso.Permiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Partidas vistas desde la web; los permisos dependen del rol de cada persona en el torneo. */
@RestController
@RequestMapping("/api/partidas")
@Tag(name = "Partidas", description = "Partidas y jugadas (las jugadas llegan desde los celulares de cada mesa)")
public class PartidaController {

    private final PartidaService service;
    private final Acceso acceso;

    public PartidaController(PartidaService service, Acceso acceso) {
        this.service = service;
        this.acceso = acceso;
    }

    @GetMapping
    public List<PartidaResumen> listar(@RequestParam(required = false) Partida.Estado estado,
                                       @RequestParam(required = false) Long torneoId,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.listar(estado, torneoId).stream()
                .filter(p -> acceso.puedeVerMesa(jwt, p.getTorneo().getId(), p.getMesa()))
                .map(PartidaResumen::de).toList();
    }

    @GetMapping("/{id}")
    public PartidaDetalle detalle(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Partida p = service.buscar(id);
        if (!acceso.puedeVerMesa(jwt, p.getTorneo().getId(), p.getMesa())) throw new AccessDeniedException("no ve esa mesa");
        return conPermisos(service.detalle(id), p, jwt);
    }

    /** Solo el árbitro principal (o el administrador) carga rondas y partidas con sus jugadores. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartidaDetalle crear(@Valid @RequestBody PartidaRequest req, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, req.torneoId(), Permiso.GESTIONAR_PARTIDAS);
        return service.crear(req, Acceso.uid(jwt));
    }

    @PostMapping("/{id}/jugadas")
    @Operation(summary = "Cargar una jugada a mano", description = "Valida la jugada UCI contra la posición actual")
    public PartidaDetalle registrarJugada(@PathVariable Long id, @Valid @RequestBody JugadaRequest req, @AuthenticationPrincipal Jwt jwt) {
        Partida p = arbitrable(id, jwt);
        return conPermisos(service.registrarJugada(id, req, jwt.getClaimAsString("nombre")), p, jwt);
    }

    @PostMapping("/{id}/jugadas/{numero}/corregir")
    @Operation(summary = "Corregir una jugada", description = "Reemplaza la jugada indicada y descarta las posteriores")
    public PartidaDetalle corregir(@PathVariable Long id, @PathVariable int numero, @Valid @RequestBody CorreccionRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        Partida p = arbitrable(id, jwt);
        return conPermisos(service.corregir(id, numero, new JugadaRequest(req.uci(), Jugada.Origen.MANUAL, null),
                jwt.getClaimAsString("nombre")), p, jwt);
    }

    @DeleteMapping("/{id}/jugadas/ultima")
    public PartidaDetalle deshacerUltima(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Partida p = arbitrable(id, jwt);
        return conPermisos(service.deshacerUltima(id), p, jwt);
    }

    @PatchMapping("/{id}/resultado")
    public PartidaDetalle finalizar(@PathVariable Long id, @Valid @RequestBody ResultadoRequest req, @AuthenticationPrincipal Jwt jwt) {
        Partida p = arbitrable(id, jwt);
        return conPermisos(service.finalizar(id, req.resultado()), p, jwt);
    }

    @PostMapping("/{id}/reabrir")
    public PartidaDetalle reabrir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Partida p = service.buscar(id);
        acceso.exigir(jwt, p.getTorneo().getId(), Permiso.GESTIONAR_PARTIDAS);
        return conPermisos(service.reabrir(id), p, jwt);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, service.buscar(id).getTorneo().getId(), Permiso.GESTIONAR_PARTIDAS);
        service.eliminar(id);
    }

    private Partida arbitrable(Long id, Jwt jwt) {
        Partida p = service.buscar(id);
        acceso.exigirArbitrar(jwt, p.getTorneo().getId(), p.getMesa());
        return p;
    }

    private PartidaDetalle conPermisos(PartidaDetalle d, Partida p, Jwt jwt) {
        Long t = p.getTorneo().getId();
        return d.con(new Permisos(acceso.puedeArbitrar(jwt, t, p.getMesa()), acceso.puede(jwt, t, Permiso.GESTIONAR_PARTIDAS)));
    }
}
