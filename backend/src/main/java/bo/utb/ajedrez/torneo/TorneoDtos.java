package bo.utb.ajedrez.torneo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class TorneoDtos {
    private TorneoDtos() {}

    public record TorneoRequest(
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 60) String categoria,
            @Size(max = 80) String ciudad,
            LocalDate fechaInicio) {}

    public record TorneoResponse(Long id, String nombre, String categoria, String ciudad, LocalDate fechaInicio,
                                 long partidas, long enCurso) {}
}
