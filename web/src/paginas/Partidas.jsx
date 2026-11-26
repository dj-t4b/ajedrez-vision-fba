import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, FileSpreadsheet, FileText, Plus } from 'lucide-react'
import { api, descargar } from '../api.js'
import { puedeRegistrar, useSesion } from '../sesion.jsx'
import { useDatos } from '../componentes/useDatos.js'

const FILTROS = [['', 'Todas'], ['EN_CURSO', 'En curso'], ['FINALIZADA', 'Finalizadas']]
const fecha = (iso) => new Date(iso).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' })
export const nombreTorneo = (t) => (t.categoria ? `${t.nombre} · ${t.categoria}` : t.nombre)

/** Lista de partidas; en /torneos/:id muestra solo las de ese torneo. */
export default function Partidas() {
  const { id: torneoId } = useParams()
  const { sesion } = useSesion()
  const navegar = useNavigate()
  const [estado, setEstado] = useState('')
  const [filtroTorneo, setFiltroTorneo] = useState('')
  const torneoSel = torneoId ?? filtroTorneo
  const consulta = new URLSearchParams({ ...(estado && { estado }), ...(torneoSel && { torneoId: torneoSel }) }).toString()
  const { datos: partidas, error } = useDatos(`/api/partidas${consulta ? `?${consulta}` : ''}`, 5000)
  const { datos: torneos } = useDatos('/api/torneos')
  const torneo = torneoId && torneos?.find((t) => String(t.id) === torneoId)
  const [creando, setCreando] = useState(false)
  const [errorReporte, setErrorReporte] = useState('')
  const sufijo = torneoSel ? `?torneoId=${torneoSel}` : ''

  async function bajar(ruta, nombre) {
    setErrorReporte('')
    try {
      await descargar(ruta, nombre)
    } catch (e) {
      setErrorReporte(e.message)
    }
  }

  return (
    <>
      <div className="cabecera">
        <div>
          {torneoId && (
            <Link to="/torneos" className="secundario" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
              <ArrowLeft size={16} aria-hidden /> Torneos
            </Link>
          )}
          <h1 style={torneoId ? { marginTop: 8 } : undefined}>{torneo ? nombreTorneo(torneo) : 'Partidas'}</h1>
          <p>
            {torneo
              ? [torneo.ciudad, torneo.fechaInicio && `desde el ${new Date(torneo.fechaInicio + 'T00:00').toLocaleDateString('es-BO')}`].filter(Boolean).join(' · ') || 'Partidas de este torneo'
              : 'Las jugadas llegan desde la app Android o se cargan a mano en cada partida.'}
          </p>
        </div>
        {puedeRegistrar(sesion) && (
          <div className="barra-acciones">
            <button onClick={() => bajar(`/api/reportes/partidas.pdf${sufijo}`, 'partidas.pdf')}><FileText size={16} aria-hidden /> PDF</button>
            <button onClick={() => bajar(`/api/reportes/partidas.xlsx${sufijo}`, 'partidas.xlsx')}><FileSpreadsheet size={16} aria-hidden /> Excel</button>
            {!creando && <button className="primario" onClick={() => setCreando(true)}><Plus size={18} aria-hidden /> Nueva partida</button>}
          </div>
        )}
      </div>

      {creando && (
        <NuevaPartida torneos={torneos ?? []} torneoFijo={torneoId}
          onCancelar={() => setCreando(false)} onCreada={(id) => navegar(`/partidas/${id}`)} />
      )}
      {(error || errorReporte) && <div className="error" role="alert">{error || errorReporte}</div>}

      <div className="filtros" role="group" aria-label="Filtrar partidas">
        {FILTROS.map(([valor, texto]) => (
          <button key={valor} aria-pressed={estado === valor} onClick={() => setEstado(valor)}>{texto}</button>
        ))}
        {!torneoId && (
          <select aria-label="Torneo" value={filtroTorneo} onChange={(e) => setFiltroTorneo(e.target.value)} style={{ marginLeft: 8 }}>
            <option value="">Todos los torneos</option>
            {torneos?.map((t) => <option key={t.id} value={t.id}>{nombreTorneo(t)}</option>)}
          </select>
        )}
      </div>

      {partidas?.length === 0 ? (
        <div className="vacio">No hay partidas con este filtro.</div>
      ) : (
        <table className="tabla">
          <thead>
            <tr><th>Blancas</th><th>Negras</th>{!torneoId && <th>Torneo</th>}<th>Ronda</th><th>Estado</th><th>Creada</th></tr>
          </thead>
          <tbody>
            {partidas?.map((p) => (
              <tr key={p.id} className="enlace" onClick={() => navegar(`/partidas/${p.id}`)}>
                <td><a href={`/partidas/${p.id}`} onClick={(e) => e.preventDefault()}>{p.blancas}</a></td>
                <td>{p.negras}</td>
                {!torneoId && <td>{nombreTorneo({ nombre: p.torneo, categoria: p.categoria })}</td>}
                <td>{p.ronda ?? '–'}</td>
                <td>{p.estado === 'EN_CURSO' ? <span className="etiqueta vivo">En curso</span> : <span className="etiqueta">{p.resultado}</span>}</td>
                <td className="secundario">{fecha(p.creadaEn)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function NuevaPartida({ torneos, torneoFijo, onCancelar, onCreada }) {
  const [f, setF] = useState({ blancas: '', negras: '', torneoId: torneoFijo ?? '', ronda: '' })
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api('/api/partidas', {
        metodo: 'POST',
        cuerpo: { ...f, torneoId: Number(f.torneoId), ronda: f.ronda ? Number(f.ronda) : null },
      })
      onCreada(r.partida.id)
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <h2>Nueva partida</h2>
      {error && <div className="error" role="alert">{error}</div>}
      {torneos.length === 0 && <div className="error" role="alert">Primero cree un torneo en la sección Torneos.</div>}
      <div className="campos">
        {!torneoFijo && (
          <label>Torneo
            <select required {...campo('torneoId')}>
              <option value="">Elegir…</option>
              {torneos.map((t) => <option key={t.id} value={t.id}>{nombreTorneo(t)}</option>)}
            </select>
          </label>
        )}
        <label>Blancas<input required maxLength={100} {...campo('blancas')} /></label>
        <label>Negras<input required maxLength={100} {...campo('negras')} /></label>
        <label>Ronda<input type="number" min={1} placeholder="Opcional" {...campo('ronda')} /></label>
      </div>
      <div className="acciones">
        <button type="button" className="fantasma" onClick={onCancelar}>Cancelar</button>
        <button className="primario" disabled={enviando}>{enviando ? 'Creando…' : 'Crear partida'}</button>
      </div>
    </form>
  )
}
