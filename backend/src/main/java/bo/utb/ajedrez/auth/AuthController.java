package bo.utb.ajedrez.auth;

import bo.utb.ajedrez.usuario.Rol;
import bo.utb.ajedrez.usuario.Usuario;
import bo.utb.ajedrez.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record LoginResponse(String token, long expiraEnSegundos, Long id, String nombre, Rol rol) {}
    public record Yo(Long id, String email, String nombre, String rol) {}

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final Duration duracion;

    public AuthController(UsuarioRepository repo, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                          @Value("${app.jwt.horas}") long horas) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.duracion = Duration.ofHours(horas);
    }

    @PostMapping("/login")
    @SecurityRequirements
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario u = repo.findByEmail(req.email().trim().toLowerCase())
                .filter(Usuario::isActivo)
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Login fallido para {}", req.email());
                    return new BadCredentialsException("credenciales");
                });

        Instant ahora = Instant.now();
        // shortcut: un usuario desactivado conserva su token hasta que expira (app.jwt.horas); usar lista de revocación si hace falta
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ajedrez-fba")
                .subject(u.getEmail())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .claim("uid", u.getId())
                .claim("nombre", u.getNombre())
                .claim("rol", u.getRol().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        log.info("Login correcto usuario={} rol={}", u.getEmail(), u.getRol());
        return new LoginResponse(token, duracion.toSeconds(), u.getId(), u.getNombre(), u.getRol());
    }

    @GetMapping("/yo")
    public Yo yo(@AuthenticationPrincipal Jwt jwt) {
        return new Yo(((Number) jwt.getClaim("uid")).longValue(), jwt.getSubject(),
                jwt.getClaimAsString("nombre"), jwt.getClaimAsString("rol"));
    }
}
