package bo.utb.ajedrez.partida;

import bo.utb.ajedrez.partida.PartidaDtos.*;
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
@RequestMapping("/api/partidas")
@Tag(name = "Partidas", description = "Partidas y jugadas (las jugadas llegan desde la app Android)")
public class PartidaController {

    private final PartidaService service;

    public PartidaController(PartidaService service) {
        this.service = service;
    }

    @GetMapping
    public List<PartidaResumen> listar(@RequestParam(required = false) Partida.Estado estado) {
        return service.listar(estado);
    }

    @GetMapping("/{id}")
    public PartidaDetalle detalle(@PathVariable Long id) {
        return service.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
    public PartidaDetalle crear(@Valid @RequestBody PartidaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.crear(req, ((Number) jwt.getClaim("uid")).longValue());
    }

    @PostMapping("/{id}/jugadas")
    @PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
    @Operation(summary = "Registrar jugada", description = "Valida la jugada UCI contra la posición actual y devuelve la partida actualizada")
    public PartidaDetalle registrarJugada(@PathVariable Long id, @Valid @RequestBody JugadaRequest req) {
        return service.registrarJugada(id, req);
    }

    @DeleteMapping("/{id}/jugadas/ultima")
    @PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
    @Operation(summary = "Deshacer la última jugada")
    public PartidaDetalle deshacerUltima(@PathVariable Long id) {
        return service.deshacerUltima(id);
    }

    @PatchMapping("/{id}/resultado")
    @PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
    public PartidaDetalle finalizar(@PathVariable Long id, @Valid @RequestBody ResultadoRequest req) {
        return service.finalizar(id, req.resultado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
