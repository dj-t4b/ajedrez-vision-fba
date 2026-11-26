package bo.utb.ajedrez.dispositivo;

import bo.utb.ajedrez.torneo.Torneo;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Celular vinculado a una mesa de un torneo. No tiene cuenta: se identifica con un token propio. */
@Entity
@Table(name = "dispositivo")
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    /** Identificador corto que el celular muestra en pantalla, p. ej. C-07. */
    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false)
    private int mesa;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "vinculado_en", nullable = false)
    private LocalDateTime vinculadoEn = LocalDateTime.now();

    @Column(name = "ultimo_contacto")
    private LocalDateTime ultimoContacto;

    @Column(nullable = false)
    private boolean revocado;

    protected Dispositivo() {}

    public Dispositivo(Torneo torneo, String codigo, int mesa, String tokenHash) {
        this.torneo = torneo;
        this.codigo = codigo;
        this.mesa = mesa;
        this.tokenHash = tokenHash;
    }

    public String etiqueta() { return codigo + " · Mesa " + mesa; }

    public Long getId() { return id; }
    public Torneo getTorneo() { return torneo; }
    public String getCodigo() { return codigo; }
    public int getMesa() { return mesa; }
    public void setMesa(int mesa) { this.mesa = mesa; }
    public LocalDateTime getVinculadoEn() { return vinculadoEn; }
    public LocalDateTime getUltimoContacto() { return ultimoContacto; }
    public void setUltimoContacto(LocalDateTime t) { this.ultimoContacto = t; }
    public boolean isRevocado() { return revocado; }
    public void setRevocado(boolean revocado) { this.revocado = revocado; }
}
