import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileSpreadsheet, FileText, Plus } from 'lucide-react'
import { api, descargar } from '../api.js'
import { useSesion } from '../sesion.jsx'
import { useDatos } from '../componentes/useDatos.js'

const FILTROS = [['', 'Todas'], ['EN_CURSO', 'En curso'], ['FINALIZADA', 'Finalizadas']]
export const nombreTorneo = (t) => (t.categoria ? `${t.nombre} · ${t.categoria}` : t.nombre)
const consulta = (params) => {
  const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v)).toString()
  return q ? `?${q}` : ''
}

/** Todas las partidas que la persona puede ver, de todos sus torneos. */
export default function Partidas() {
  const { sesion } = useSesion()
  const [estado, setEstado] = useState('')
  const [torneoId, setTorneoId] = useState('')
  const { datos: partidas, error } = useDatos(`/api/partidas${consulta({ estado, torneoId })}`, 5000)
  const { datos: torneos } = useDatos('/api/torneos')

  return (
    <>
      <div className="cabecera">
        <div>
          <h1>Partidas</h1>
          <p>Las partidas se cargan dentro de cada torneo; las jugadas llegan de los celulares de las mesas.</p>
        </div>
        {sesion.rol === 'ADMIN' && <BotonesReporte sufijo={consulta({ torneoId })} />}
      </div>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="filtros" role="group" aria-label="Filtrar partidas">
        <FiltroEstado estado={estado} setEstado={setEstado} />
        <select aria-label="Torneo" value={torneoId} onChange={(e) => setTorneoId(e.target.value)} style={{ marginLeft: 8 }}>
          <option value="">Todos mis torneos</option>
          {torneos?.map((t) => <option key={t.id} value={t.id}>{nombreTorneo(t)}</option>)}
        </select>
      </div>
      <TablaPartidas partidas={partidas} conTorneo />
    </>
  )
}

/** Partidas de un torneo; el árbitro principal (o el administrador) carga rondas y jugadores. */
export function PartidasDelTorneo({ torneo }) {
  const navegar = useNavigate()
  const [estado, setEstado] = useState('')
  const { datos: partidas, error } = useDatos(`/api/partidas${consulta({ estado, torneoId: torneo.id })}`, 5000)
  const [creando, setCreando] = useState(false)
  const carga = torneo.miRol === 'ADMIN' || torneo.miRol === 'ARBITRO_PRINCIPAL'

  return (
    <>
      <div className="filtros" role="group" aria-label="Filtrar partidas" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
        <div style={{ display: 'flex', gap: 4 }}><FiltroEstado estado={estado} setEstado={setEstado} /></div>
        <div className="barra-acciones">
          <BotonesReporte sufijo={consulta({ torneoId: torneo.id })} />
          {carga && !creando && <button className="primario" onClick={() => setCreando(true)}><Plus size={18} aria-hidden /> Nueva partida</button>}
        </div>
      </div>
      {creando && <NuevaPartida torneoId={torneo.id} onCancelar={() => setCreando(false)} onCreada={(id) => navegar(`/partidas/${id}`)} />}
      {error && <div className="error" role="alert">{error}</div>}
      <TablaPartidas partidas={partidas} />
    </>
  )
}

function FiltroEstado({ estado, setEstado }) {
  return FILTROS.map(([valor, texto]) => (
    <button key={valor} aria-pressed={estado === valor} onClick={() => setEstado(valor)}>{texto}</button>
  ))
}

function BotonesReporte({ sufijo }) {
  const [error, setError] = useState('')
  const bajar = (ruta, nombre) => descargar(ruta + sufijo, nombre).catch((e) => setError(e.message))
  return (
    <>
      <button onClick={() => bajar('/api/reportes/partidas.pdf', 'partidas.pdf')}><FileText size={16} aria-hidden /> PDF</button>
      <button onClick={() => bajar('/api/reportes/partidas.xlsx', 'partidas.xlsx')}><FileSpreadsheet size={16} aria-hidden /> Excel</button>
      {error && <span className="error" role="alert">{error}</span>}
    </>
  )
}

function TablaPartidas({ partidas, conTorneo = false }) {
  const navegar = useNavigate()
  if (partidas?.length === 0) return <div className="vacio">No hay partidas con este filtro.</div>
  return (
    <table className="tabla">
      <thead>
        <tr>{conTorneo && <th>Torneo</th>}<th>Mesa</th><th>Ronda</th><th>Blancas</th><th>Negras</th><th>Estado</th></tr>
      </thead>
      <tbody>
        {partidas?.map((p) => (
          <tr key={p.id} className="enlace" onClick={() => navegar(`/partidas/${p.id}`)}>
            {conTorneo && <td>{nombreTorneo({ nombre: p.torneo, categoria: p.categoria })}</td>}
            <td className="num">{p.mesa ?? '–'}</td>
            <td className="num">{p.ronda ?? '–'}</td>
            <td><a href={`/partidas/${p.id}`} onClick={(e) => e.preventDefault()}>{p.blancas}</a></td>
            <td>{p.negras}</td>
            <td>{p.estado === 'EN_CURSO' ? <span className="etiqueta vivo">En curso</span> : <span className="etiqueta">{p.resultado}</span>}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function NuevaPartida({ torneoId, onCancelar, onCreada }) {
  const [f, setF] = useState({ mesa: '', ronda: '', blancas: '', negras: '' })
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api('/api/partidas', {
        metodo: 'POST',
        cuerpo: { ...f, torneoId, mesa: Number(f.mesa), ronda: f.ronda ? Number(f.ronda) : null },
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
      <p className="ayuda">El celular de esa mesa toma la partida solo, apenas se crea.</p>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="campos">
        <label>Mesa<input type="number" required min={1} {...campo('mesa')} /></label>
        <label>Ronda<input type="number" min={1} placeholder="Opcional" {...campo('ronda')} /></label>
        <label>Blancas<input required maxLength={100} {...campo('blancas')} /></label>
        <label>Negras<input required maxLength={100} {...campo('negras')} /></label>
      </div>
      <div className="acciones">
        <button type="button" className="fantasma" onClick={onCancelar}>Cancelar</button>
        <button className="primario" disabled={enviando}>{enviando ? 'Creando…' : 'Crear partida'}</button>
      </div>
    </form>
  )
}
