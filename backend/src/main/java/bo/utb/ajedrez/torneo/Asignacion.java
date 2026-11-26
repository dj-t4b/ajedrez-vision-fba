package bo.utb.ajedrez.torneo;

import bo.utb.ajedrez.usuario.Usuario;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/** Rol de un usuario dentro de un torneo. Una persona tiene un solo rol por torneo. */
@Entity
@Table(name = "asignacion_torneo")
public class Asignacion {

    public enum RolTorneo { ORGANIZADOR, ARBITRO_PRINCIPAL, ARBITRO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolTorneo rol;

    /** Mesas que atiende un árbitro; vacío = todas las mesas del torneo. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "acceso_tablero", joinColumns = @JoinColumn(name = "asignacion_id"))
    @Column(name = "mesa")
    private Set<Integer> mesas = new HashSet<>();

    protected Asignacion() {}

    public Asignacion(Usuario usuario, Torneo torneo, RolTorneo rol) {
        this.usuario = usuario;
        this.torneo = torneo;
        this.rol = rol;
    }

    /** ¿Puede actuar en esa mesa? El árbitro principal en todas; el árbitro en las suyas (vacío = todas). */
    public boolean atiendeMesa(Integer mesa) {
        return switch (rol) {
            case ARBITRO_PRINCIPAL -> true;
            case ARBITRO -> mesas.isEmpty() || (mesa != null && mesas.contains(mesa));
            case ORGANIZADOR -> false;
        };
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Torneo getTorneo() { return torneo; }
    public RolTorneo getRol() { return rol; }
    public void setRol(RolTorneo rol) { this.rol = rol; }
    public Set<Integer> getMesas() { return mesas; }
}
