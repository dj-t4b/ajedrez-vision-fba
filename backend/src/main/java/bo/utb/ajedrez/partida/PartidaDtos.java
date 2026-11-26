package bo.utb.ajedrez.partida;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class PartidaDtos {
    private PartidaDtos() {}

    public record PartidaRequest(
            @NotNull Long torneoId,
            @Positive Integer mesa,
            @Positive Integer ronda,
            @NotBlank @Size(max = 100) String blancas,
            @NotBlank @Size(max = 100) String negras) {}

    /** Jugada en notación UCI, p. ej. e2e4 o e7e8q (coronación). */
    public record JugadaRequest(
            @NotBlank @Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbn]?$", message = "debe ser UCI, p. ej. e2e4") String uci,
            @NotNull Jugada.Origen origen,
            @DecimalMin("0.0") @DecimalMax("1.0") Double confianza) {}

    public record ResultadoRequest(
            @NotBlank @Pattern(regexp = "^(1-0|0-1|1/2-1/2)$", message = "debe ser 1-0, 0-1 o 1/2-1/2") String resultado) {}

    public record JugadaResponse(int numero, String uci, String san, String fenResultante,
                                 Jugada.Origen origen, Double confianza, LocalDateTime registradaEn, String registradaPor) {
        static JugadaResponse de(Jugada j) {
            return new JugadaResponse(j.getNumero(), j.getUci(), j.getSan(), j.getFenResultante(),
                    j.getOrigen(), j.getConfianza(), j.getRegistradaEn(), j.getRegistradaPor());
        }
    }

    public record PartidaResumen(Long id, Long torneoId, String torneo, String categoria, Integer mesa, Integer ronda, String blancas, String negras,
                                 Partida.Estado estado, String resultado, String fenActual,
                                 String registradaPor, LocalDateTime creadaEn) {
        static PartidaResumen de(Partida p) {
            return new PartidaResumen(p.getId(), p.getTorneo().getId(), p.getTorneo().getNombre(), p.getTorneo().getCategoria(), p.getMesa(), p.getRonda(), p.getBlancas(), p.getNegras(),
                    p.getEstado(), p.getResultado(), p.getFenActual(), p.getRegistradaPor().getNombre(), p.getCreadaEn());
        }
    }

    /** @param permisos qué puede hacer quien consulta (null para el celular) */
    public record PartidaDetalle(PartidaResumen partida, List<JugadaResponse> jugadas, String pgn, Permisos permisos) {
        public PartidaDetalle con(Permisos p) { return new PartidaDetalle(partida, jugadas, pgn, p); }
    }

    /** @param arbitrar corregir jugadas y finalizar; @param gestionar reabrir y eliminar (árbitro principal) */
    public record Permisos(boolean arbitrar, boolean gestionar) {}

    public record CorreccionRequest(
            @NotBlank @Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbn]?$", message = "debe ser UCI, p. ej. e2e4") String uci) {}
}
