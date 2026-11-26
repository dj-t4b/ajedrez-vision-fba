package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.torneo.Acceso.Permiso;
import bo.utb.ajedrez.torneo.Asignacion.RolTorneo;
import bo.utb.ajedrez.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** Equipo de cada torneo: organizador, árbitro principal y árbitros (con sus mesas). */
@RestController
@Transactional
@Tag(name = "Equipo del torneo", description = "Roles por torneo y mesas de cada árbitro")
public class AsignacionController {

    private static final Logger log = LoggerFactory.getLogger(AsignacionController.class);

    public record AsignacionRequest(@NotNull Long usuarioId, @NotNull RolTorneo rol, Set<Integer> mesas) {}
    public record MesasRequest(Set<Integer> mesas) {}
    public record AsignacionResponse(Long id, Long usuarioId, String nombre, String email, RolTorneo rol, Set<Integer> mesas) {
        static AsignacionResponse de(Asignacion a) {
            return new AsignacionResponse(a.getId(), a.getUsuario().getId(), a.getUsuario().getNombre(),
                    a.getUsuario().getEmail(), a.getRol(), Set.copyOf(a.getMesas()));
        }
    }

    private final AsignacionRepository repo;
    private final TorneoService torneos;
    private final UsuarioRepository usuarios;
    private final Acceso acceso;

    public AsignacionController(AsignacionRepository repo, TorneoService torneos, UsuarioRepository usuarios, Acceso acceso) {
        this.repo = repo;
        this.torneos = torneos;
        this.usuarios = usuarios;
        this.acceso = acceso;
    }

    @GetMapping("/api/torneos/{id}/asignaciones")
    public List<AsignacionResponse> listar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, id, Permiso.VER);
        return repo.findByTorneoIdOrderByRol(id).stream().map(AsignacionResponse::de).toList();
    }

    @PostMapping("/api/torneos/{id}/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public AsignacionResponse crear(@PathVariable Long id, @Valid @RequestBody AsignacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        exigirPuedeAsignar(jwt, id, req.rol());
        if (repo.findByTorneoIdAndUsuarioId(id, req.usuarioId()).isPresent()) {
            throw new ReglaNegocioException("Esa persona ya tiene un rol en este torneo");
        }
        var usuario = usuarios.findById(req.usuarioId())
                .orElseThrow(() -> new NoEncontradoException("Usuario " + req.usuarioId() + " no existe"));
        Asignacion a = new Asignacion(usuario, torneos.buscar(id), req.rol());
        if (req.rol() == RolTorneo.ARBITRO && req.mesas() != null) a.getMesas().addAll(req.mesas());
        a = repo.save(a);
        log.info("Asignación torneo={} usuario={} rol={} mesas={}", id, usuario.getEmail(), req.rol(), a.getMesas());
        return AsignacionResponse.de(a);
    }

    /** Mesas de un árbitro (vacío = todas). */
    @PutMapping("/api/asignaciones/{id}/mesas")
    public AsignacionResponse mesas(@PathVariable Long id, @RequestBody MesasRequest req, @AuthenticationPrincipal Jwt jwt) {
        Asignacion a = buscar(id);
        acceso.exigir(jwt, a.getTorneo().getId(), Permiso.ASIGNAR_ARBITROS);
        if (a.getRol() != RolTorneo.ARBITRO) throw new ReglaNegocioException("Solo los árbitros tienen mesas asignadas");
        a.getMesas().clear();
        if (req.mesas() != null) a.getMesas().addAll(req.mesas());
        log.info("Mesas de asignación={} → {}", id, a.getMesas());
        return AsignacionResponse.de(a);
    }

    @DeleteMapping("/api/asignaciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Asignacion a = buscar(id);
        exigirPuedeAsignar(jwt, a.getTorneo().getId(), a.getRol());
        repo.delete(a);
        log.info("Asignación quitada id={}", id);
    }

    /** El administrador nombra organizadores; el organizador, al árbitro principal; el árbitro principal, a los árbitros. */
    private void exigirPuedeAsignar(Jwt jwt, Long torneoId, RolTorneo rol) {
        boolean ok = switch (rol) {
            case ORGANIZADOR -> Acceso.esAdmin(jwt);
            case ARBITRO_PRINCIPAL -> acceso.puede(jwt, torneoId, Permiso.ASIGNAR_PRINCIPAL);
            case ARBITRO -> acceso.puede(jwt, torneoId, Permiso.ASIGNAR_ARBITROS);
        };
        if (!ok) throw new AccessDeniedException("no puede asignar " + rol);
    }

    private Asignacion buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoEncontradoException("Asignación " + id + " no existe"));
    }
}
