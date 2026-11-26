package bo.utb.ajedrez.reporte;

import bo.utb.ajedrez.partida.PartidaService;
import bo.utb.ajedrez.torneo.Acceso;
import bo.utb.ajedrez.torneo.Acceso.Permiso;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** Reportes de un torneo (cualquier rol del torneo) o de todos (solo el administrador). */
@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes", description = "Reportes PDF y Excel")
public class ReporteController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReporteService service;
    private final PartidaService partidas;
    private final Acceso acceso;

    public ReporteController(ReporteService service, PartidaService partidas, Acceso acceso) {
        this.service = service;
        this.partidas = partidas;
        this.acceso = acceso;
    }

    @GetMapping("/partidas.pdf")
    public ResponseEntity<byte[]> partidasPdf(@RequestParam(required = false) Long torneoId, @AuthenticationPrincipal Jwt jwt) {
        exigir(jwt, torneoId);
        return archivo(service.partidasPdf(torneoId), MediaType.APPLICATION_PDF, "partidas.pdf");
    }

    @GetMapping("/partidas.xlsx")
    public ResponseEntity<byte[]> partidasExcel(@RequestParam(required = false) Long torneoId, @AuthenticationPrincipal Jwt jwt) throws IOException {
        exigir(jwt, torneoId);
        return archivo(service.partidasExcel(torneoId), XLSX, "partidas.xlsx");
    }

    @GetMapping("/partidas/{id}.pdf")
    public ResponseEntity<byte[]> planilla(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        exigir(jwt, partidas.buscar(id).getTorneo().getId());
        return archivo(service.planillaPdf(id), MediaType.APPLICATION_PDF, "planilla-" + id + ".pdf");
    }

    private void exigir(Jwt jwt, Long torneoId) {
        if (torneoId == null ? !Acceso.esAdmin(jwt) : !acceso.puede(jwt, torneoId, Permiso.REPORTES)) {
            throw new AccessDeniedException("sin permiso para reportes");
        }
    }

    private static ResponseEntity<byte[]> archivo(byte[] datos, MediaType tipo, String nombre) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(datos);
    }
}
