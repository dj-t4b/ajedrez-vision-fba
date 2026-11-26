package bo.utb.ajedrez.dispositivo;

import bo.utb.ajedrez.partida.Jugada;
import bo.utb.ajedrez.partida.Partida;
import bo.utb.ajedrez.partida.PartidaDtos.JugadaRequest;
import bo.utb.ajedrez.partida.PartidaDtos.PartidaDetalle;
import bo.utb.ajedrez.partida.PartidaDtos.ResultadoRequest;
import bo.utb.ajedrez.partida.PartidaService;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.torneo.Acceso;
import bo.utb.ajedrez.torneo.Acceso.Permiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Celulares de las mesas. Las rutas /api/dispositivo/** las usa el celular con su token
 * (cabecera X-Dispositivo, sin cuenta de usuario); el resto, la web.
 */
@RestController
@Transactional
@Tag(name = "Celulares", description = "Vinculación de celulares a las mesas y registro de jugadas desde la cámara")
public class DispositivoController {

    public record VincularRequest(@NotBlank @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String pin) {}
    public record VincularResponse(String token, String codigo, int mesa, String torneo) {}
    public record EstadoResponse(String codigo, int mesa, String torneo, PartidaDetalle partida) {}
    public record JugadaCamara(@NotBlank @Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbn]?$") String uci,
                               @DecimalMin("0.0") @DecimalMax("1.0") Double confianza) {}
    public record DispositivoResponse(Long id, String codigo, int mesa, LocalDateTime vinculadoEn, LocalDateTime ultimoContacto) {
        static DispositivoResponse de(Dispositivo d) {
            return new DispositivoResponse(d.getId(), d.getCodigo(), d.getMesa(), d.getVinculadoEn(), d.getUltimoContacto());
        }
    }
    public record MesaRequest(@Positive int mesa) {}

    private final DispositivoService service;
    private final PartidaService partidas;
    private final Acceso acceso;

    public DispositivoController(DispositivoService service, PartidaService partidas, Acceso acceso) {
        this.service = service;
        this.partidas = partidas;
        this.acceso = acceso;
    }

    // ---- Desde el celular ----

    @PostMapping("/api/dispositivo/vincular")
    @SecurityRequirements
    @Operation(summary = "Vincular un celular con el PIN (o el QR) del torneo")
    public VincularResponse vincular(@Valid @RequestBody VincularRequest req) {
        var v = service.vincular(req.pin());
        Dispositivo d = v.dispositivo();
        return new VincularResponse(v.token(), d.getCodigo(), d.getMesa(), d.getTorneo().getNombre());
    }

    @GetMapping("/api/dispositivo/estado")
    @SecurityRequirements
    @Operation(summary = "Mesa del celular y la partida en curso en ella")
    public EstadoResponse estado(@RequestHeader(value = "X-Dispositivo", required = false) String token) {
        Dispositivo d = service.autenticar(token);
        PartidaDetalle p = partidas.enCurso(d.getTorneo().getId(), d.getMesa()).map(partidas::aDetalle).orElse(null);
        return new EstadoResponse(d.getCodigo(), d.getMesa(), d.getTorneo().getNombre(), p);
    }

    @PostMapping("/api/dispositivo/jugadas")
    @SecurityRequirements
    @Operation(summary = "Registrar la jugada que dedujo la cámara")
    public PartidaDetalle jugada(@RequestHeader(value = "X-Dispositivo", required = false) String token,
                                 @Valid @RequestBody JugadaCamara req) {
        Dispositivo d = service.autenticar(token);
        return partidas.registrarJugada(actual(d).getId(), new JugadaRequest(req.uci(), Jugada.Origen.VISION, req.confianza()), d.etiqueta());
    }

    @DeleteMapping("/api/dispositivo/jugadas/ultima")
    @SecurityRequirements
    public PartidaDetalle deshacer(@RequestHeader(value = "X-Dispositivo", required = false) String token) {
        return partidas.deshacerUltima(actual(service.autenticar(token)).getId());
    }

    @PostMapping("/api/dispositivo/resultado")
    @SecurityRequirements
    @Operation(summary = "Terminar la partida de la mesa con un resultado")
    public PartidaDetalle terminar(@RequestHeader(value = "X-Dispositivo", required = false) String token,
                                   @Valid @RequestBody ResultadoRequest req) {
        return partidas.finalizar(actual(service.autenticar(token)).getId(), req.resultado());
    }

    private Partida actual(Dispositivo d) {
        return partidas.enCurso(d.getTorneo().getId(), d.getMesa())
                .orElseThrow(() -> new ReglaNegocioException("No hay una partida en curso en la mesa " + d.getMesa()));
    }

    // ---- Desde la web ----

    @GetMapping("/api/torneos/{id}/dispositivos")
    public List<DispositivoResponse> listar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, id, Permiso.VER);
        return service.delTorneo(id).stream().map(DispositivoResponse::de).toList();
    }

    @PutMapping("/api/dispositivos/{id}/mesa")
    public DispositivoResponse cambiarMesa(@PathVariable Long id, @Valid @RequestBody MesaRequest req, @AuthenticationPrincipal Jwt jwt) {
        acceso.exigir(jwt, service.buscar(id).getTorneo().getId(), Permiso.GESTIONAR_PARTIDAS);
        return DispositivoResponse.de(service.cambiarMesa(id, req.mesa()));
    }

    @DeleteMapping("/api/dispositivos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Dispositivo d = service.buscar(id);
        acceso.exigirArbitrar(jwt, d.getTorneo().getId(), d.getMesa());
        service.desvincular(id);
    }
}
