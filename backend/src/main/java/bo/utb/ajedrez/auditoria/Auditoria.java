package bo.utb.ajedrez.auditoria;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Una acción registrada: quién, qué, cuándo, desde dónde y con qué datos. */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    /** Quién actuó: un usuario (ADMIN o USUARIO), un celular, o alguien sin sesión (p. ej. un login fallido). */
    public enum Tipo { ADMIN, USUARIO, CELULAR, ANONIMO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(nullable = false, length = 160)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 80)
    private String accion;

    @Column(nullable = false, length = 8)
    private String metodo;

    @Column(nullable = false, length = 200)
    private String ruta;

    @Column(nullable = false)
    private int estado;

    @Column(length = 45)
    private String ip;

    @Column(length = 1000)
    private String detalle;

    protected Auditoria() {}

    public Auditoria(String actor, Tipo tipo, String accion, String metodo, String ruta, int estado, String ip, String detalle) {
        this.actor = recortar(actor, 160);
        this.tipo = tipo;
        this.accion = recortar(accion, 80);
        this.metodo = metodo;
        this.ruta = recortar(ruta, 200);
        this.estado = estado;
        this.ip = ip;
        this.detalle = recortar(detalle, 1000);
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Long getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public String getActor() { return actor; }
    public Tipo getTipo() { return tipo; }
    public String getAccion() { return accion; }
    public String getMetodo() { return metodo; }
    public String getRuta() { return ruta; }
    public int getEstado() { return estado; }
    public String getIp() { return ip; }
    public String getDetalle() { return detalle; }
}
