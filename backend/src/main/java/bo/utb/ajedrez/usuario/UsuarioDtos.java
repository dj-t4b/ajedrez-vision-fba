package bo.utb.ajedrez.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class UsuarioDtos {
    private UsuarioDtos() {}

    /** password es obligatorio al crear y opcional al editar (vacío = no cambiar). */
    public record UsuarioRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Email @Size(max = 120) String email,
            @Size(min = 6, max = 72) String password,
            @NotNull Rol rol,
            Boolean activo) {}

    public record UsuarioResponse(Long id, String nombre, String email, Rol rol, boolean activo, LocalDateTime creadoEn) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.isActivo(), u.getCreadoEn());
        }
    }
}
