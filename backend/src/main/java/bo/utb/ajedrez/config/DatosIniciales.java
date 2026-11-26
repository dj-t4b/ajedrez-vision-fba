package bo.utb.ajedrez.config;

import bo.utb.ajedrez.usuario.Rol;
import bo.utb.ajedrez.usuario.Usuario;
import bo.utb.ajedrez.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el primer administrador si la base está vacía, para poder entrar al sistema. */
@Component
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public DatosIniciales(UsuarioRepository repo, PasswordEncoder encoder,
                          @Value("${app.admin.email}") String email,
                          @Value("${app.admin.password}") String password) {
        this.repo = repo;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) return;
        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRol(Rol.ADMIN);
        repo.save(admin);
        log.warn("Base vacía: se creó el administrador inicial {}. Cambie su contraseña.", email);
    }
}
