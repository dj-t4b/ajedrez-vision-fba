import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Camera, Copy, FileText, Hand, Undo2 } from 'lucide-react'
import { api, descargar } from '../api.js'
import { puedeRegistrar, useSesion } from '../sesion.jsx'
import { useDatos } from '../componentes/useDatos.js'
import Tablero from '../componentes/Tablero.jsx'

export default function Partida() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const { datos, setDatos, error: errorCarga } = useDatos(`/api/partidas/${id}`, 3000)
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [copiado, setCopiado] = useState(false)

  if (errorCarga && !datos) return <div className="error" role="alert">{errorCarga}</div>
  if (!datos) return <p className="secundario">Cargando partida…</p>

  const { partida: p, jugadas, pgn } = datos
  const enCurso = p.estado === 'EN_CURSO'
  const editable = puedeRegistrar(sesion)
  const ultima = jugadas.at(-1)

  async function accion(ruta, metodo, cuerpo) {
    setError('')
    setOcupado(true)
    try {
      setDatos(await api(ruta, { metodo, cuerpo }))
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupado(false)
    }
  }

  const jugarManual = (uci) => accion(`/api/partidas/${id}/jugadas`, 'POST', { uci, origen: 'MANUAL' })

  async function copiar() {
    await navigator.clipboard.writeText(pgn)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 1600)
  }

  const filas = []
  for (let i = 0; i < jugadas.length; i += 2) filas.push([jugadas[i], jugadas[i + 1]])

  return (
    <>
      <div className="cabecera">
        <div>
          <Link to="/partidas" className="secundario" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            <ArrowLeft size={16} aria-hidden /> Partidas
          </Link>
          <h1 style={{ marginTop: 8 }}>{p.blancas} – {p.negras}</h1>
          <p>{[p.torneo, p.ronda && `Ronda ${p.ronda}`].filter(Boolean).join(' · ') || 'Partida sin torneo'}</p>
        </div>
        {enCurso ? <span className="etiqueta vivo">En curso</span> : <span className="etiqueta">Finalizada {p.resultado}</span>}
      </div>

      <div className="partida">
        <div className="tablero">
          <Tablero fen={p.fenActual} ultima={ultima?.uci} onJugada={editable && enCurso && !ocupado ? jugarManual : undefined} />
        </div>

        <div className="lado">
          {error && <div className="error" role="alert">{error}</div>}
          {editable && enCurso && (
            <p className="ayuda">Arrastre una pieza para cargar una jugada a mano. Si la cámara leyó mal, deshaga la última.</p>
          )}

          <div className="jugadas">
            {jugadas.length === 0 ? (
              <div className="vacio">Todavía no hay jugadas.</div>
            ) : (
              <table aria-label="Jugadas">
                <tbody>
                  {filas.map(([b, n], i) => (
                    <tr key={i}>
                      <td className="n">{i + 1}.</td>
                      <td><Movimiento j={b} /></td>
                      <td>{n && <Movimiento j={n} />}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {editable && (
            <div className="barra-acciones">
              <button onClick={() => accion(`/api/partidas/${id}/jugadas/ultima`, 'DELETE')} disabled={ocupado || jugadas.length === 0}>
                <Undo2 size={16} aria-hidden /> Deshacer última
              </button>
              <button onClick={() => descargar(`/api/reportes/partidas/${id}.pdf`, `planilla-${id}.pdf`).catch((e) => setError(e.message))}>
                <FileText size={16} aria-hidden /> Planilla PDF
              </button>
              {enCurso && (
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="secundario">Cerrar con</span>
                  <select value="" disabled={ocupado} onChange={(e) => e.target.value && accion(`/api/partidas/${id}/resultado`, 'PATCH', { resultado: e.target.value })}>
                    <option value="">Resultado…</option>
                    <option value="1-0">1-0 (ganan blancas)</option>
                    <option value="0-1">0-1 (ganan negras)</option>
                    <option value="1/2-1/2">½-½ (tablas)</option>
                  </select>
                </label>
              )}
            </div>
          )}

          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <h2>PGN</h2>
              <button className="fantasma" onClick={copiar}><Copy size={16} aria-hidden /> {copiado ? 'Copiado' : 'Copiar'}</button>
            </div>
            <p className="pgn mono">{pgn}</p>
          </div>
        </div>
      </div>
    </>
  )
}

function Movimiento({ j }) {
  const vision = j.origen === 'VISION'
  const titulo = vision
    ? `Leída por la cámara${j.confianza != null ? `, confianza ${Math.round(j.confianza * 100)}%` : ''}`
    : 'Cargada a mano'
  return (
    <span className="san" title={titulo}>
      {j.san}
      <span className="origen" aria-label={titulo}>{vision ? <Camera size={14} /> : <Hand size={14} />}</span>
    </span>
  )
}
