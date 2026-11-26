import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { useDatos } from '../componentes/useDatos.js'
import Tablero from '../componentes/Tablero.jsx'
import { ROLES } from '../Estructura.jsx'

export default function Panel() {
  const { datos: r, error } = useDatos('/api/dashboard', 5000)
  const { datos: vivas } = useDatos('/api/partidas?estado=EN_CURSO', 5000)

  const totalJugadas = r ? r.jugadasVision + r.jugadasManuales : 0
  const pctVision = totalJugadas ? Math.round((r.jugadasVision / totalJugadas) * 100) : 0

  return (
    <>
      <div className="cabecera">
        <div>
          <h1>Panel</h1>
          <p>Se actualiza solo cada 5 segundos.</p>
        </div>
      </div>
      {error && <div className="error" role="alert">{error}</div>}

      <div className="resumen">
        <section className="bloque">
          <h2>Partidas en curso</h2>
          {vivas?.length === 0 && (
            <div className="vacio">No hay partidas en curso. <Link to="/partidas">Cree una</Link> para empezar a registrar.</div>
          )}
          <div className="vivas">
            {vivas?.slice(0, 6).map((p) => (
              <Link key={p.id} to={`/partidas/${p.id}`} className="viva">
                <div className="tablero"><Tablero id={`mini-${p.id}`} fen={p.fenActual} /></div>
                <div className="jugadores">{p.blancas} – {p.negras}</div>
              </Link>
            ))}
          </div>
        </section>

        {r && (
          <section className="bloque">
            <h2>Resumen</h2>
            <dl className="cifras">
              <dt>Partidas en curso</dt><dd>{r.partidasEnCurso}</dd>
              <dt>Partidas finalizadas</dt><dd>{r.partidasFinalizadas}</dd>
              <dt>Usuarios</dt><dd>{r.usuarios}</dd>
              {Object.entries(r.usuariosPorRol).map(([rol, n]) => (
                <Fragment key={rol}><dt className="secundario">· {ROLES[rol]}</dt><dd className="secundario">{n}</dd></Fragment>
              ))}
            </dl>

            <h2 style={{ marginTop: 28 }}>Jugadas registradas</h2>
            <div className="barra-origen" role="img" aria-label={`${pctVision}% de las jugadas las leyó la cámara`}>
              <span style={{ width: `${pctVision}%` }} />
            </div>
            <div className="leyenda">
              <span>Cámara: {r.jugadasVision}</span>
              <span>Manual: {r.jugadasManuales}</span>
            </div>
          </section>
        )}
      </div>
    </>
  )
}
