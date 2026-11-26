package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.torneo.Acceso.Permiso;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoRequest;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoResponse;
import bo.utb.ajedrez.torneo.TorneoDtos.Vinculacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/torneos")
@Tag(name = "Torneos", description = "Torneos (puede haber varios a la vez, p. ej. por categoría)")
public class TorneoController {

    private final TorneoService service;
    private final Acceso acceso;

    public TorneoController(TorneoService service, Acceso acceso) {
        this.service = service;
        this.acceso = acceso;
    }

    /** El administrador ve todos; los demás, los torneos donde tienen un rol. */
    @GetMapping
    public List<TorneoResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return service.todos().stream()
                .filter(t -> acceso.puede(jwt, t.getId(), Permiso.VER))
                .map(t -> service.aRespuesta(t, id -> acceso.miRol(jwt, id)))
                .toList();
    }

    @GetMapping("/{id}")
    public TorneoResponse detalle(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, id, Permiso.VER);
        return service.aRespuesta(service.buscar(id), t -> acceso.miRol(jwt, t));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TorneoResponse crear(@Valid @RequestBody TorneoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.aRespuesta(service.crear(req), t -> "ADMIN");
    }

    @PutMapping("/{id}")
    public TorneoResponse actualizar(@PathVariable Long id, @Valid @RequestBody TorneoRequest req, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, id, Permiso.EDITAR_TORNEO);
        return service.aRespuesta(service.actualizar(id, req), t -> acceso.miRol(jwt, t));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }

    @PostMapping("/{id}/vinculacion")
    @Operation(summary = "Abrir la vinculación de celulares", description = "Devuelve un PIN de 6 dígitos válido 15 minutos; el QR de la web lleva el servidor y el PIN")
    public Vinculacion abrirVinculacion(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, id, Permiso.GESTIONAR_PARTIDAS);
        return service.abrirVinculacion(id);
    }

    @DeleteMapping("/{id}/vinculacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarVinculacion(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, id, Permiso.GESTIONAR_PARTIDAS);
        service.cerrarVinculacion(id);
    }
}
