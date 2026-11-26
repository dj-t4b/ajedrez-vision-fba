import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { api } from '../api.js'
import { useSesion } from '../sesion.jsx'
import { useDatos } from '../componentes/useDatos.js'
import { ROLES_TORNEO } from '../Estructura.jsx'

const VACIO = { nombre: '', categoria: '', ciudad: '', fechaInicio: '' }
const dia = (f) => (f ? new Date(f + 'T00:00').toLocaleDateString('es-BO') : '–')

/** Solo el administrador crea y elimina torneos; el organizador edita los datos del suyo. */
export default function Torneos() {
  const { sesion } = useSesion()
  const navegar = useNavigate()
  const { datos: torneos, error, recargar } = useDatos('/api/torneos', 5000)
  const [editando, setEditando] = useState(null) // null | 'nuevo' | torneo
  const [borrando, setBorrando] = useState(null)
  const [errorAccion, setErrorAccion] = useState('')
  const esAdmin = sesion.rol === 'ADMIN'
  const puedeEditar = (t) => esAdmin || t.miRol === 'ORGANIZADOR'
  const conAcciones = esAdmin || torneos?.some(puedeEditar)

  async function eliminar(t) {
    setErrorAccion('')
    try {
      await api(`/api/torneos/${t.id}`, { metodo: 'DELETE' })
      setBorrando(null)
      recargar()
    } catch (e) {
      setErrorAccion(e.message)
      setBorrando(null)
    }
  }

  return (
    <>
      <div className="cabecera">
        <div>
          <h1>Torneos</h1>
          <p>Cada torneo tiene su equipo, sus mesas y sus partidas. Puede haber varios a la vez, por ejemplo uno por categoría.</p>
        </div>
        {esAdmin && !editando && <button className="primario" onClick={() => setEditando('nuevo')}><Plus size={18} aria-hidden /> Nuevo torneo</button>}
      </div>

      {editando && (
        <FormTorneo
          key={editando === 'nuevo' ? 'nuevo' : editando.id}
          torneo={editando === 'nuevo' ? null : editando}
          onCancelar={() => setEditando(null)}
          onGuardado={() => { setEditando(null); recargar() }}
        />
      )}
      {(error || errorAccion) && <div className="error" role="alert" style={{ marginBottom: 16 }}>{error || errorAccion}</div>}

      {torneos?.length === 0 ? (
        <div className="vacio">
          {esAdmin ? 'Todavía no hay torneos. Cree el primero con «Nuevo torneo».' : 'Todavía no tiene un rol en ningún torneo. Pídaselo al organizador o al árbitro principal.'}
        </div>
      ) : (
        <table className="tabla">
          <thead>
            <tr><th>Torneo</th><th>Categoría</th><th>Ciudad</th><th>Inicio</th><th>Partidas</th><th>Mi rol</th>{conAcciones && <th><span className="sr-only">Acciones</span></th>}</tr>
          </thead>
          <tbody>
            {torneos?.map((t) => (
              <tr key={t.id} className="enlace" onClick={() => navegar(`/torneos/${t.id}`)}>
                <td><a href={`/torneos/${t.id}`} onClick={(e) => e.preventDefault()}>{t.nombre}</a></td>
                <td>{t.categoria ?? <span className="secundario">–</span>}</td>
                <td>{t.ciudad ?? <span className="secundario">–</span>}</td>
                <td className="secundario">{dia(t.fechaInicio)}</td>
                <td>
                  {t.partidas}
                  {t.enCurso > 0 && <span className="etiqueta vivo" style={{ marginLeft: 8 }}>{t.enCurso} en curso</span>}
                </td>
                <td className="secundario">{ROLES_TORNEO[t.miRol]}</td>
                {conAcciones && (
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    {borrando === t.id ? (
                      <>
                        <span className="secundario" style={{ marginRight: 8 }}>¿Eliminar {t.nombre}?</span>
                        <button className="peligro lleno" onClick={() => eliminar(t)}>Eliminar</button>{' '}
                        <button className="fantasma" onClick={() => setBorrando(null)}>No</button>
                      </>
                    ) : (
                      <>
                        {puedeEditar(t) && <button className="fantasma" onClick={() => setEditando(t)} aria-label={`Editar ${t.nombre}`}><Pencil size={16} aria-hidden /></button>}
                        {esAdmin && <button className="fantasma peligro" onClick={() => setBorrando(t.id)} aria-label={`Eliminar ${t.nombre}`}><Trash2 size={16} aria-hidden /></button>}
                      </>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function FormTorneo({ torneo, onCancelar, onGuardado }) {
  const [f, setF] = useState(torneo
    ? { nombre: torneo.nombre, categoria: torneo.categoria ?? '', ciudad: torneo.ciudad ?? '', fechaInicio: torneo.fechaInicio ?? '' }
    : VACIO)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    setError('')
    try {
      const cuerpo = { nombre: f.nombre, categoria: f.categoria, ciudad: f.ciudad, fechaInicio: f.fechaInicio || null }
      await api(torneo ? `/api/torneos/${torneo.id}` : '/api/torneos', { metodo: torneo ? 'PUT' : 'POST', cuerpo })
      onGuardado()
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <h2>{torneo ? `Editar ${torneo.nombre}` : 'Nuevo torneo'}</h2>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="campos">
        <label>Nombre<input required maxLength={120} placeholder="Abierto de La Paz 2026" {...campo('nombre')} /></label>
        <label>Categoría<input maxLength={60} placeholder="Absoluto, Sub-14, Femenino…" {...campo('categoria')} /></label>
        <label>Ciudad<input maxLength={80} {...campo('ciudad')} /></label>
        <label>Fecha de inicio<input type="date" {...campo('fechaInicio')} /></label>
      </div>
      <div className="acciones">
        <button type="button" className="fantasma" onClick={onCancelar}>Cancelar</button>
        <button className="primario" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</button>
      </div>
    </form>
  )
}
