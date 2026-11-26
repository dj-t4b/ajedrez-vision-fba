package bo.utb.ajedrez.auditoria;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Registro de auditoría: solo el administrador lo consulta y lo exporta. */
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Auditoría", description = "Todo lo que hace cada usuario y cada celular (solo administrador)")
public class AuditoriaController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditoriaRepository repo;

    public AuditoriaController(AuditoriaRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Auditoria> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                  @RequestParam(required = false) Auditoria.Tipo tipo,
                                  @RequestParam(required = false) String texto,
                                  @RequestParam(defaultValue = "500") int limite) {
        return buscar(desde, hasta, tipo, texto, Math.min(Math.max(limite, 1), 2000));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) Auditoria.Tipo tipo,
                                      @RequestParam(required = false) String texto) {
        StringBuilder sb = new StringBuilder("﻿fecha;actor;tipo;accion;metodo;ruta;estado;ip;detalle\n"); // BOM: Excel abre bien los acentos
        for (Auditoria a : buscar(desde, hasta, tipo, texto, 100_000)) {
            sb.append(String.join(";", FECHA.format(a.getFecha()), c(a.getActor()), a.getTipo().name(), c(a.getAccion()),
                    a.getMetodo(), c(a.getRuta()), String.valueOf(a.getEstado()), c(a.getIp()), c(a.getDetalle()))).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("auditoria.csv").build().toString())
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<Auditoria> buscar(LocalDate desde, LocalDate hasta, Auditoria.Tipo tipo, String texto, int limite) {
        String t = texto == null || texto.isBlank() ? null : "%" + texto.trim().toLowerCase() + "%";
        return repo.buscar(desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                tipo, t, PageRequest.of(0, limite));
    }

    /** Celda CSV entre comillas (y sin fórmulas: un valor que empieza con = + - @ se antepone con '). */
    private static String c(String s) {
        if (s == null) return "";
        String v = s.matches("^[=+\\-@].*") ? "'" + s : s;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
