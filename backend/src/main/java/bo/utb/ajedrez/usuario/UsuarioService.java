package bo.utb.ajedrez.usuario;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.comun.ReglaNegocioException;
import bo.utb.ajedrez.usuario.UsuarioDtos.UsuarioRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoEncontradoException("Usuario " + id + " no existe"));
    }

    public Usuario crear(UsuarioRequest req) {
        if (req.password() == null || req.password().isBlank()) {
            throw new ReglaNegocioException("La contraseña es obligatoria");
        }
        String email = req.email().trim().toLowerCase();
        if (repo.existsByEmail(email)) {
            throw new ReglaNegocioException("Ya existe un usuario con el email " + email);
        }
        Usuario u = new Usuario();
        aplicar(u, req, email);
        u = repo.save(u);
        log.info("Usuario creado id={} email={} rol={}", u.getId(), u.getEmail(), u.getRol());
        return u;
    }

    public Usuario actualizar(Long id, UsuarioRequest req) {
        Usuario u = obtener(id);
        String email = req.email().trim().toLowerCase();
        if (!email.equals(u.getEmail()) && repo.existsByEmail(email)) {
            throw new ReglaNegocioException("Ya existe un usuario con el email " + email);
        }
        if (u.getRol() == Rol.ADMIN && (req.rol() != Rol.ADMIN || Boolean.FALSE.equals(req.activo()))) {
            exigirOtroAdmin();
        }
        aplicar(u, req, email);
        log.info("Usuario actualizado id={} rol={} activo={}", u.getId(), u.getRol(), u.isActivo());
        return u;
    }

    public void eliminar(Long id) {
        Usuario u = obtener(id);
        if (u.getRol() == Rol.ADMIN) {
            exigirOtroAdmin();
        }
        repo.delete(u);
        log.info("Usuario eliminado id={}", id);
    }

    private void exigirOtroAdmin() {
        if (repo.countByRol(Rol.ADMIN) <= 1) {
            throw new ReglaNegocioException("No se puede quitar el último administrador");
        }
    }

    private void aplicar(Usuario u, UsuarioRequest req, String email) {
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        u.setRol(req.rol());
        if (req.activo() != null) u.setActivo(req.activo());
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(encoder.encode(req.password()));
        }
    }
}
