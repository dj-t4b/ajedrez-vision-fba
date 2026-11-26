import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { api } from '../api.js'
import { puedeRegistrar, useSesion } from '../sesion.jsx'
import { useDatos } from '../componentes/useDatos.js'

const FILTROS = [['', 'Todas'], ['EN_CURSO', 'En curso'], ['FINALIZADA', 'Finalizadas']]
const fecha = (iso) => new Date(iso).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' })

export default function Partidas() {
  const { sesion } = useSesion()
  const navegar = useNavigate()
  const [estado, setEstado] = useState('')
  const { datos: partidas, error } = useDatos(`/api/partidas${estado ? `?estado=${estado}` : ''}`, 5000)
  const [creando, setCreando] = useState(false)

  return (
    <>
      <div className="cabecera">
        <div>
          <h1>Partidas</h1>
          <p>Las jugadas llegan desde la app Android o se cargan a mano en cada partida.</p>
        </div>
        {puedeRegistrar(sesion) && !creando && (
          <button className="primario" onClick={() => setCreando(true)}><Plus size={18} aria-hidden /> Nueva partida</button>
        )}
      </div>

      {creando && <NuevaPartida onCancelar={() => setCreando(false)} onCreada={(id) => navegar(`/partidas/${id}`)} />}
      {error && <div className="error" role="alert">{error}</div>}

      <div className="filtros" role="group" aria-label="Filtrar por estado">
        {FILTROS.map(([valor, texto]) => (
          <button key={valor} aria-pressed={estado === valor} onClick={() => setEstado(valor)}>{texto}</button>
        ))}
      </div>

      {partidas?.length === 0 ? (
        <div className="vacio">No hay partidas con este filtro.</div>
      ) : (
        <table className="tabla">
          <thead>
            <tr><th>Blancas</th><th>Negras</th><th>Torneo</th><th>Ronda</th><th>Estado</th><th>Creada</th></tr>
          </thead>
          <tbody>
            {partidas?.map((p) => (
              <tr key={p.id} className="enlace" onClick={() => navegar(`/partidas/${p.id}`)}>
                <td><a href={`/partidas/${p.id}`} onClick={(e) => e.preventDefault()}>{p.blancas}</a></td>
                <td>{p.negras}</td>
                <td>{p.torneo || <span className="secundario">Sin torneo</span>}</td>
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

function NuevaPartida({ onCancelar, onCreada }) {
  const [f, setF] = useState({ blancas: '', negras: '', torneo: '', ronda: '' })
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api('/api/partidas', {
        metodo: 'POST',
        cuerpo: { ...f, torneo: f.torneo || null, ronda: f.ronda ? Number(f.ronda) : null },
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
      <div className="campos">
        <label>Blancas<input required maxLength={100} {...campo('blancas')} /></label>
        <label>Negras<input required maxLength={100} {...campo('negras')} /></label>
        <label>Torneo<input maxLength={120} placeholder="Opcional" {...campo('torneo')} /></label>
        <label>Ronda<input type="number" min={1} placeholder="Opcional" {...campo('ronda')} /></label>
      </div>
      <div className="acciones">
        <button type="button" className="fantasma" onClick={onCancelar}>Cancelar</button>
        <button className="primario" disabled={enviando}>{enviando ? 'Creando…' : 'Crear partida'}</button>
      </div>
    </form>
  )
}
