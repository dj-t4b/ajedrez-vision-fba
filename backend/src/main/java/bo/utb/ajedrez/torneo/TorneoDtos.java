package bo.utb.ajedrez.torneo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TorneoDtos {
    private TorneoDtos() {}

    public record TorneoRequest(
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 60) String categoria,
            @Size(max = 80) String ciudad,
            LocalDate fechaInicio) {}

    /** @param miRol rol de quien consulta: ADMIN, ORGANIZADOR, ARBITRO_PRINCIPAL o ARBITRO */
    public record TorneoResponse(Long id, String nombre, String categoria, String ciudad, LocalDate fechaInicio,
                                 long partidas, long enCurso, String miRol, LocalDateTime vinculacionHasta) {}

    /** El PIN solo se muestra una vez, al abrir la vinculación. El QR lleva servidor + PIN. */
    public record Vinculacion(String pin, LocalDateTime venceEn, String servidor) {}
}
