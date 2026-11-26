package bo.utb.ajedrez.reporte;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAnyRole('ADMIN','ARBITRO')")
@Tag(name = "Reportes", description = "Reportes PDF y Excel")
public class ReporteController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReporteService service;

    public ReporteController(ReporteService service) {
        this.service = service;
    }

    @GetMapping("/partidas.pdf")
    public ResponseEntity<byte[]> partidasPdf() {
        return archivo(service.partidasPdf(), MediaType.APPLICATION_PDF, "partidas.pdf");
    }

    @GetMapping("/partidas.xlsx")
    public ResponseEntity<byte[]> partidasExcel() throws IOException {
        return archivo(service.partidasExcel(), XLSX, "partidas.xlsx");
    }

    @GetMapping("/partidas/{id}.pdf")
    public ResponseEntity<byte[]> planilla(@PathVariable Long id) {
        return archivo(service.planillaPdf(id), MediaType.APPLICATION_PDF, "planilla-" + id + ".pdf");
    }

    private static ResponseEntity<byte[]> archivo(byte[] datos, MediaType tipo, String nombre) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(datos);
    }
}
