package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.torneo.TorneoDtos.TorneoRequest;
import bo.utb.ajedrez.torneo.TorneoDtos.TorneoResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/torneos")
@Tag(name = "Torneos", description = "Torneos (puede haber varios a la vez, p. ej. por categoría)")
public class TorneoController {

    private final TorneoService service;

    public TorneoController(TorneoService service) {
        this.service = service;
    }

    @GetMapping
    public List<TorneoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public TorneoResponse detalle(@PathVariable Long id) {
        return service.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
    public TorneoResponse crear(@Valid @RequestBody TorneoRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
    public TorneoResponse actualizar(@PathVariable Long id, @Valid @RequestBody TorneoRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
