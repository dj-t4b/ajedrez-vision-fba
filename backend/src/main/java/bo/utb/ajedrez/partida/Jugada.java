package bo.utb.ajedrez.partida;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jugada")
public class Jugada {

    /** VISION: deducida por la cámara de la app Android. MANUAL: ingresada o corregida a mano. */
    public enum Origen { VISION, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "partida_id")
    private Partida partida;

    /** Número de medio movimiento (ply), empieza en 1. */
    @Column(nullable = false)
    private int numero;

    @Column(nullable = false, length = 5)
    private String uci;

    @Column(nullable = false, length = 10)
    private String san;

    @Column(name = "fen_resultante", nullable = false, length = 100)
    private String fenResultante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Origen origen;

    /** Confianza de la visión (0..1); null para jugadas manuales. */
    private Double confianza;

    @Column(name = "registrada_en", nullable = false)
    private LocalDateTime registradaEn = LocalDateTime.now();

    /** Quién la registró: el código del celular (C-07 · Mesa 3) o el nombre del árbitro. */
    @Column(name = "registrada_por", length = 100)
    private String registradaPor;

    protected Jugada() {}

    public Jugada(Partida partida, int numero, String uci, String san, String fenResultante, Origen origen, Double confianza, String registradaPor) {
        this.registradaPor = registradaPor;
        this.partida = partida;
        this.numero = numero;
        this.uci = uci;
        this.san = san;
        this.fenResultante = fenResultante;
        this.origen = origen;
        this.confianza = confianza;
    }

    public Long getId() { return id; }
    public Partida getPartida() { return partida; }
    public int getNumero() { return numero; }
    public String getUci() { return uci; }
    public String getSan() { return san; }
    public String getFenResultante() { return fenResultante; }
    public Origen getOrigen() { return origen; }
    public Double getConfianza() { return confianza; }
    public LocalDateTime getRegistradaEn() { return registradaEn; }
    public String getRegistradaPor() { return registradaPor; }
}
