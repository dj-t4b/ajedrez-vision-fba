import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, Camera, Copy, FileText, Hand, RotateCcw, Trash2, Undo2, X } from 'lucide-react'
import { api, descargar } from '../api.js'
import { useDatos } from '../componentes/useDatos.js'
import Tablero from '../componentes/Tablero.jsx'

const FEN_INICIAL = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
/** Por debajo de esta confianza la jugada leída por la cámara se marca para revisión. */
const CONFIANZA_DUDOSA = 0.5
const dudosa = (j) => j.origen === 'VISION' && j.confianza != null && j.confianza < CONFIANZA_DUDOSA

export default function Partida() {
  const { id } = useParams()
  const { datos, setDatos, error: errorCarga } = useDatos(`/api/partidas/${id}`, 3000)
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const [corrigiendo, setCorrigiendo] = useState(null) // número de jugada que se está corrigiendo
  const [borrando, setBorrando] = useState(false)
  const navegar = useNavigate()

  if (errorCarga && !datos) return <div className="error" role="alert">{errorCarga}</div>
  if (!datos) return <p className="secundario">Cargando partida…</p>

  const { partida: p, jugadas, pgn, permisos } = datos
  const enCurso = p.estado === 'EN_CURSO'
  const arbitra = permisos?.arbitrar
  const ultima = jugadas.at(-1)
  const aCorregir = corrigiendo && jugadas[corrigiendo - 1]
  const fenTablero = aCorregir ? (corrigiendo === 1 ? FEN_INICIAL : jugadas[corrigiendo - 2].fenResultante) : p.fenActual
  const dudosas = jugadas.filter(dudosa).length

  async function accion(ruta, metodo, cuerpo) {
    setError('')
    setOcupado(true)
    try {
      setDatos(await api(ruta, { metodo, cuerpo }))
      setCorrigiendo(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupado(false)
    }
  }

  async function eliminar() {
    setOcupado(true)
    try {
      await api(`/api/partidas/${id}`, { metodo: 'DELETE' })
      navegar(`/torneos/${p.torneoId}`)
    } catch (e) {
      setError(e.message)
      setOcupado(false)
      setBorrando(false)
    }
  }

  const alSoltar = (uci) => aCorregir
    ? accion(`/api/partidas/${id}/jugadas/${corrigiendo}/corregir`, 'POST', { uci })
    : accion(`/api/partidas/${id}/jugadas`, 'POST', { uci, origen: 'MANUAL' })

  async function copiar() {
    await navigator.clipboard.writeText(pgn)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 1600)
  }

  const filas = []
  for (let i = 0; i < jugadas.length; i += 2) filas.push([jugadas[i], jugadas[i + 1]])
  const mov = (j) => j && (
    <Movimiento j={j} activa={corrigiendo === j.numero}
      onClick={arbitra ? () => setCorrigiendo(corrigiendo === j.numero ? null : j.numero) : undefined} />
  )

  return (
    <>
      <div className="cabecera">
        <div>
          <Link to={`/torneos/${p.torneoId}`} className="secundario" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            <ArrowLeft size={16} aria-hidden /> {p.categoria ? `${p.torneo} · ${p.categoria}` : p.torneo}
          </Link>
          <h1 style={{ marginTop: 8 }}>{p.blancas} – {p.negras}</h1>
          <p>{[p.mesa && `Mesa ${p.mesa}`, p.ronda && `Ronda ${p.ronda}`].filter(Boolean).join(' · ')}</p>
        </div>
        {enCurso ? <span className="etiqueta vivo">En curso</span> : <span className="etiqueta">Finalizada {p.resultado}</span>}
      </div>

      <div className="partida">
        <div className="tablero">
          <Tablero fen={fenTablero} ultima={aCorregir ? undefined : ultima?.uci}
            onJugada={arbitra && (enCurso || aCorregir) && !ocupado ? alSoltar : undefined} />
        </div>

        <div className="lado">
          {error && <div className="error" role="alert">{error}</div>}
          {aCorregir ? (
            <div className="aviso-correccion" role="status">
              <span>Corrigiendo la jugada {Math.ceil(corrigiendo / 2)}{corrigiendo % 2 ? '.' : '…'} {aCorregir.san}: arrastre la pieza correcta. Las jugadas posteriores se descartan.</span>
              <button className="fantasma" onClick={() => setCorrigiendo(null)} aria-label="Cancelar corrección"><X size={16} aria-hidden /></button>
            </div>
          ) : arbitra && (
            <p className="ayuda">
              {dudosas > 0 && <><AlertTriangle size={14} aria-hidden style={{ verticalAlign: '-2px' }} /> {dudosas} {dudosas === 1 ? 'jugada dudosa' : 'jugadas dudosas'} para revisar. </>}
              Toque una jugada para corregirla{enCurso ? ', o arrastre una pieza para cargar la siguiente' : ''}.
            </p>
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
                      <td>{mov(b)}</td>
                      <td>{mov(n)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="barra-acciones">
            {arbitra && (
              <button onClick={() => accion(`/api/partidas/${id}/jugadas/ultima`, 'DELETE')} disabled={ocupado || jugadas.length === 0}>
                <Undo2 size={16} aria-hidden /> Deshacer última
              </button>
            )}
            <button onClick={() => descargar(`/api/reportes/partidas/${id}.pdf`, `planilla-${id}.pdf`).catch((e) => setError(e.message))}>
              <FileText size={16} aria-hidden /> Planilla PDF
            </button>
            {arbitra && enCurso && (
              <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span className="secundario">Finalizar con</span>
                <select value="" disabled={ocupado} onChange={(e) => e.target.value && accion(`/api/partidas/${id}/resultado`, 'PATCH', { resultado: e.target.value })}>
                  <option value="">Resultado…</option>
                  <option value="1-0">1-0 (ganan blancas)</option>
                  <option value="0-1">0-1 (ganan negras)</option>
                  <option value="1/2-1/2">½-½ (tablas)</option>
                </select>
              </label>
            )}
            {permisos?.gestionar && !enCurso && (
              <button onClick={() => accion(`/api/partidas/${id}/reabrir`, 'POST')} disabled={ocupado}>
                <RotateCcw size={16} aria-hidden /> Reabrir
              </button>
            )}
            {permisos?.gestionar && (
              borrando ? (
                <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                  <span className="secundario">¿Eliminar la partida y sus {jugadas.length} jugadas?</span>
                  <button className="peligro lleno" onClick={eliminar} disabled={ocupado}>Eliminar</button>
                  <button className="fantasma" onClick={() => setBorrando(false)}>No</button>
                </span>
              ) : (
                <button className="fantasma peligro" onClick={() => setBorrando(true)}><Trash2 size={16} aria-hidden /> Eliminar partida</button>
              )
            )}
          </div>

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

function Movimiento({ j, activa, onClick }) {
  const vision = j.origen === 'VISION'
  const titulo = [
    vision ? `Leída por la cámara${j.confianza != null ? `, confianza ${Math.round(j.confianza * 100)}%` : ''}` : 'Cargada a mano',
    j.registradaPor && `por ${j.registradaPor}`,
  ].filter(Boolean).join(' ')
  const clase = ['san', dudosa(j) && 'dudosa', activa && 'activa', onClick && 'tocable'].filter(Boolean).join(' ')
  const contenido = (
    <>
      {j.san}
      <span className="origen" aria-hidden>{dudosa(j) ? <AlertTriangle size={14} /> : vision ? <Camera size={14} /> : <Hand size={14} />}</span>
    </>
  )
  return onClick
    ? <button type="button" className={clase} title={titulo} aria-label={`${j.san}, ${titulo}. Corregir`} aria-pressed={activa} onClick={onClick}>{contenido}</button>
    : <span className={clase} title={titulo}>{contenido}</span>
}
