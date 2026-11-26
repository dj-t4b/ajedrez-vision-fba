package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.torneo.Asignacion.RolTorneo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Matriz de permisos por torneo. El administrador puede todo; los demás según su rol en ese torneo.
 * Ver docs: "Roles, permisos y vinculación de celulares".
 */
@Component
@Transactional(readOnly = true)
public class Acceso {

    public enum Permiso {
        /** Ver el torneo y sus partidas (el árbitro, solo sus mesas: ver [#puedeMesa]). */
        VER,
        /** Editar nombre, categoría, ciudad y fecha del torneo. */
        EDITAR_TORNEO,
        /** Nombrar al árbitro principal. */
        ASIGNAR_PRINCIPAL,
        /** Designar árbitros y sus mesas. */
        ASIGNAR_ARBITROS,
        /** Cargar partidas y jugadores, abrir la vinculación, reasignar mesas, reabrir partidas. */
        GESTIONAR_PARTIDAS,
        /** Descargar reportes del torneo. */
        REPORTES
    }

    private final AsignacionRepository asignaciones;

    public Acceso(AsignacionRepository asignaciones) {
        this.asignaciones = asignaciones;
    }

    public static boolean esAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("rol"));
    }

    public static long uid(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    public Optional<Asignacion> asignacion(Jwt jwt, Long torneoId) {
        return asignaciones.findByTorneoIdAndUsuarioId(torneoId, uid(jwt));
    }

    /** "ADMIN", el rol en el torneo, o null si no participa. */
    public String miRol(Jwt jwt, Long torneoId) {
        if (esAdmin(jwt)) return "ADMIN";
        return asignacion(jwt, torneoId).map(a -> a.getRol().name()).orElse(null);
    }

    public boolean puede(Jwt jwt, Long torneoId, Permiso p) {
        if (esAdmin(jwt)) return true;
        RolTorneo rol = asignacion(jwt, torneoId).map(Asignacion::getRol).orElse(null);
        if (rol == null) return false;
        return switch (p) {
            case VER, REPORTES -> true;
            case EDITAR_TORNEO, ASIGNAR_PRINCIPAL -> rol == RolTorneo.ORGANIZADOR;
            case ASIGNAR_ARBITROS, GESTIONAR_PARTIDAS -> rol == RolTorneo.ARBITRO_PRINCIPAL;
        };
    }

    /** Corregir jugadas, finalizar partidas y desvincular celulares de esa mesa. */
    public boolean puedeArbitrar(Jwt jwt, Long torneoId, Integer mesa) {
        return esAdmin(jwt) || asignacion(jwt, torneoId).map(a -> a.atiendeMesa(mesa)).orElse(false);
    }

    /** Ver la partida de esa mesa: el organizador y el árbitro principal ven todas; el árbitro, las suyas. */
    public boolean puedeVerMesa(Jwt jwt, Long torneoId, Integer mesa) {
        if (esAdmin(jwt)) return true;
        return asignacion(jwt, torneoId)
                .map(a -> a.getRol() != RolTorneo.ARBITRO || a.atiendeMesa(mesa))
                .orElse(false);
    }

    public void exigir(Jwt jwt, Long torneoId, Permiso p) {
        if (!puede(jwt, torneoId, p)) throw new AccessDeniedException("sin permiso " + p);
    }

    public void exigirArbitrar(Jwt jwt, Long torneoId, Integer mesa) {
        if (!puedeArbitrar(jwt, torneoId, mesa)) throw new AccessDeniedException("no arbitra la mesa " + mesa);
    }
}
