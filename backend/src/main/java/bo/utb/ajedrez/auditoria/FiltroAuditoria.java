package bo.utb.ajedrez.auditoria;

import bo.utb.ajedrez.comun.Secretos;
import bo.utb.ajedrez.dispositivo.DispositivoRepository;
import bo.utb.ajedrez.usuario.Rol;
import bo.utb.ajedrez.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Registra cada acción que cambia algo (y cada inicio de sesión y descarga de reporte), de cualquier
 * usuario o celular, admin incluido. Va dentro de la cadena de seguridad para saber quién actuó.
 * Las consultas (GET) no se registran: los celulares y la web consultan cada pocos segundos.
 */
public class FiltroAuditoria extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroAuditoria.class);
    private static final Set<String> CAMPOS_SECRETOS = Set.of("password", "pin", "token");

    /** Nombre legible de cada acción, por método y ruta (la ruta como está declarada en el controlador). */
    private static final Map<String, String> ACCIONES = Map.ofEntries(
            Map.entry("POST /api/auth/login", "Inicio de sesión"),
            Map.entry("POST /api/usuarios", "Creó usuario"),
            Map.entry("PUT /api/usuarios/{id}", "Editó usuario"),
            Map.entry("DELETE /api/usuarios/{id}", "Eliminó usuario"),
            Map.entry("POST /api/torneos", "Creó torneo"),
            Map.entry("PUT /api/torneos/{id}", "Editó torneo"),
            Map.entry("DELETE /api/torneos/{id}", "Eliminó torneo"),
            Map.entry("POST /api/torneos/{id}/vinculacion", "Abrió la vinculación de celulares"),
            Map.entry("DELETE /api/torneos/{id}/vinculacion", "Cerró la vinculación de celulares"),
            Map.entry("POST /api/torneos/{id}/asignaciones", "Asignó un rol en el torneo"),
            Map.entry("PUT /api/asignaciones/{id}/mesas", "Cambió las mesas de un árbitro"),
            Map.entry("DELETE /api/asignaciones/{id}", "Quitó un rol en el torneo"),
            Map.entry("POST /api/partidas", "Creó partida"),
            Map.entry("POST /api/partidas/{id}/jugadas", "Cargó una jugada a mano"),
            Map.entry("POST /api/partidas/{id}/jugadas/{numero}/corregir", "Corrigió una jugada"),
            Map.entry("DELETE /api/partidas/{id}/jugadas/ultima", "Deshizo la última jugada"),
            Map.entry("PATCH /api/partidas/{id}/resultado", "Finalizó la partida"),
            Map.entry("POST /api/partidas/{id}/reabrir", "Reabrió la partida"),
            Map.entry("DELETE /api/partidas/{id}", "Eliminó partida"),
            Map.entry("PUT /api/dispositivos/{id}/mesa", "Cambió la mesa de un celular"),
            Map.entry("DELETE /api/dispositivos/{id}", "Desvinculó un celular"),
            Map.entry("POST /api/dispositivo/vincular", "Celular vinculado"),
            Map.entry("POST /api/dispositivo/jugadas", "Jugada leída por la cámara"),
            Map.entry("DELETE /api/dispositivo/jugadas/ultima", "Deshizo la última jugada (celular)"),
            Map.entry("POST /api/dispositivo/resultado", "Terminó la partida (celular)"));

    private final AuditoriaRepository repo;
    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;
    private final ObjectMapper json;

    public FiltroAuditoria(AuditoriaRepository repo, DispositivoRepository dispositivos, UsuarioRepository usuarios, ObjectMapper json) {
        this.repo = repo;
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String ruta = ruta(req);
        if (!ruta.startsWith("/api/")) return true;
        return "GET".equals(req.getMethod()) ? !ruta.startsWith("/api/reportes/") : "OPTIONS".equals(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper envuelta = new ContentCachingRequestWrapper(req);
        try {
            chain.doFilter(envuelta, res);
        } finally {
            try {
                registrar(envuelta, res.getStatus());
            } catch (RuntimeException e) {
                log.error("No se pudo registrar la auditoría de {} {}", req.getMethod(), req.getRequestURI(), e); // no rompe la respuesta
            }
        }
    }

    private void registrar(ContentCachingRequestWrapper req, int estado) {
        String metodo = req.getMethod();
        String ruta = ruta(req);
        Object patron = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String accion = ruta.startsWith("/api/reportes/") ? "Descargó un reporte"
                : ACCIONES.getOrDefault(metodo + " " + patron, metodo + " " + (patron != null ? patron : ruta));
        if (ruta.equals("/api/auth/login") && estado >= 400) accion = "Inicio de sesión fallido";
        else if (estado >= 400) accion += " (rechazado)";

        JsonNode cuerpo = leerCuerpo(req);
        String actor;
        Auditoria.Tipo tipo;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String tokenCelular = req.getHeader("X-Dispositivo");
        if (auth instanceof JwtAuthenticationToken jwt) {
            actor = jwt.getToken().getClaimAsString("nombre") + " <" + jwt.getToken().getSubject() + ">";
            tipo = "ADMIN".equals(jwt.getToken().getClaimAsString("rol")) ? Auditoria.Tipo.ADMIN : Auditoria.Tipo.USUARIO;
        } else if (tokenCelular != null) {
            actor = dispositivos.findByTokenHashAndRevocadoFalse(Secretos.hash(tokenCelular))
                    .map(d -> d.etiqueta() + " · " + d.getTorneo().getNombre()).orElse("Celular desconocido");
            tipo = Auditoria.Tipo.CELULAR;
        } else {
            actor = cuerpo != null && cuerpo.hasNonNull("email") ? cuerpo.get("email").asText() : "Sin sesión";
            tipo = Auditoria.Tipo.ANONIMO;
        }
        if (ruta.equals("/api/dispositivo/vincular") && estado < 400) actor = "Celular nuevo";
        if (ruta.equals("/api/auth/login") && estado < 400) {
            String email = actor;
            tipo = usuarios.findByEmail(email.trim().toLowerCase())
                    .map(u -> u.getRol() == Rol.ADMIN ? Auditoria.Tipo.ADMIN : Auditoria.Tipo.USUARIO).orElse(tipo);
        }

        repo.save(new Auditoria(actor, tipo, accion, metodo, req.getRequestURI(), estado, req.getRemoteAddr(), sinSecretos(cuerpo)));
    }

    private JsonNode leerCuerpo(ContentCachingRequestWrapper req) {
        byte[] b = req.getContentAsByteArray();
        if (b.length == 0) return null;
        try {
            return json.readTree(new String(b, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** El detalle es el cuerpo de la petición sin contraseñas, PIN ni tokens. */
    private String sinSecretos(JsonNode cuerpo) {
        if (cuerpo == null) return null;
        if (cuerpo instanceof ObjectNode o) CAMPOS_SECRETOS.forEach(o::remove);
        return cuerpo.toString();
    }

    private static String ruta(HttpServletRequest req) {
        return req.getRequestURI().substring(req.getContextPath().length());
    }
}
