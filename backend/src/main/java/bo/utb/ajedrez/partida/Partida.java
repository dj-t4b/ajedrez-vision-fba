package bo.utb.ajedrez.partida;

import bo.utb.ajedrez.torneo.Torneo;
import bo.utb.ajedrez.usuario.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "partida")
public class Partida {

    public static final String FEN_INICIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public enum Estado { EN_CURSO, FINALIZADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    private Integer ronda;

    /** Número de mesa (tablero) dentro del torneo. */
    private Integer mesa;

    @Column(nullable = false, length = 100)
    private String blancas;

    @Column(nullable = false, length = 100)
    private String negras;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.EN_CURSO;

    /** Notación PGN: 1-0, 0-1, 1/2-1/2 o * (en curso). */
    @Column(nullable = false, length = 7)
    private String resultado = "*";

    @Column(name = "fen_actual", nullable = false, length = 100)
    private String fenActual = FEN_INICIAL;

    @ManyToOne(optional = false)
    @JoinColumn(name = "registrada_por")
    private Usuario registradaPor;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @OneToMany(mappedBy = "partida", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<Jugada> jugadas = new ArrayList<>();

    public Long getId() { return id; }
    public Torneo getTorneo() { return torneo; }
    public void setTorneo(Torneo torneo) { this.torneo = torneo; }
    public Integer getRonda() { return ronda; }
    public Integer getMesa() { return mesa; }
    public void setMesa(Integer mesa) { this.mesa = mesa; }
    public void setRonda(Integer ronda) { this.ronda = ronda; }
    public String getBlancas() { return blancas; }
    public void setBlancas(String blancas) { this.blancas = blancas; }
    public String getNegras() { return negras; }
    public void setNegras(String negras) { this.negras = negras; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public String getFenActual() { return fenActual; }
    public void setFenActual(String fenActual) { this.fenActual = fenActual; }
    public Usuario getRegistradaPor() { return registradaPor; }
    public void setRegistradaPor(Usuario registradaPor) { this.registradaPor = registradaPor; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public List<Jugada> getJugadas() { return jugadas; }
}
