import { useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { api } from '../api.js'
import { useSesion } from '../sesion.jsx'
import { useDatos } from '../componentes/useDatos.js'
import { ROLES } from '../Estructura.jsx'

const VACIO = { nombre: '', email: '', password: '', rol: 'ARBITRO', activo: true }

export default function Usuarios() {
  const { sesion } = useSesion()
  const { datos: usuarios, error, recargar } = useDatos('/api/usuarios')
  const [editando, setEditando] = useState(null) // null | 'nuevo' | usuario
  const [borrando, setBorrando] = useState(null)
  const [errorAccion, setErrorAccion] = useState('')

  async function eliminar(u) {
    setErrorAccion('')
    try {
      await api(`/api/usuarios/${u.id}`, { metodo: 'DELETE' })
      setBorrando(null)
      recargar()
    } catch (e) {
      setErrorAccion(e.message)
    }
  }

  return (
    <>
      <div className="cabecera">
        <div>
          <h1>Usuarios</h1>
          <p>Árbitros registran partidas desde la app; jugadores solo consultan.</p>
        </div>
        {!editando && <button className="primario" onClick={() => setEditando('nuevo')}><Plus size={18} aria-hidden /> Nuevo usuario</button>}
      </div>

      {editando && (
        <FormUsuario
          key={editando === 'nuevo' ? 'nuevo' : editando.id}
          usuario={editando === 'nuevo' ? null : editando}
          onCancelar={() => setEditando(null)}
          onGuardado={() => { setEditando(null); recargar() }}
        />
      )}
      {(error || errorAccion) && <div className="error" role="alert" style={{ marginBottom: 16 }}>{error || errorAccion}</div>}

      <table className="tabla">
        <thead>
          <tr><th>Nombre</th><th>Email</th><th>Rol</th><th>Estado</th><th><span className="sr-only">Acciones</span></th></tr>
        </thead>
        <tbody>
          {usuarios?.map((u) => (
            <tr key={u.id}>
              <td>{u.nombre}{u.id === sesion.id && <span className="secundario"> (usted)</span>}</td>
              <td>{u.email}</td>
              <td>{ROLES[u.rol]}</td>
              <td>{u.activo ? <span className="etiqueta">Activo</span> : <span className="etiqueta secundario">Inactivo</span>}</td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                {borrando === u.id ? (
                  <>
                    <span className="secundario" style={{ marginRight: 8 }}>¿Eliminar a {u.nombre}?</span>
                    <button className="peligro lleno" onClick={() => eliminar(u)}>Eliminar</button>{' '}
                    <button className="fantasma" onClick={() => setBorrando(null)}>No</button>
                  </>
                ) : (
                  <>
                    <button className="fantasma" onClick={() => setEditando(u)} aria-label={`Editar a ${u.nombre}`}><Pencil size={16} aria-hidden /></button>
                    <button className="fantasma peligro" onClick={() => setBorrando(u.id)} aria-label={`Eliminar a ${u.nombre}`}><Trash2 size={16} aria-hidden /></button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

function FormUsuario({ usuario, onCancelar, onGuardado }) {
  const [f, setF] = useState(usuario ? { ...usuario, password: '' } : VACIO)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    setError('')
    try {
      const cuerpo = { nombre: f.nombre, email: f.email, rol: f.rol, activo: f.activo, password: f.password || null }
      await api(usuario ? `/api/usuarios/${usuario.id}` : '/api/usuarios', { metodo: usuario ? 'PUT' : 'POST', cuerpo })
      onGuardado()
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <h2>{usuario ? `Editar a ${usuario.nombre}` : 'Nuevo usuario'}</h2>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="campos">
        <label>Nombre<input required maxLength={100} {...campo('nombre')} /></label>
        <label>Email<input type="email" required maxLength={120} {...campo('email')} /></label>
        <label>
          {usuario ? 'Nueva contraseña' : 'Contraseña'}
          <input type="password" autoComplete="new-password" minLength={6} maxLength={72} required={!usuario}
            placeholder={usuario ? 'Vacío para no cambiarla' : 'Mínimo 6 caracteres'} {...campo('password')} />
        </label>
        <label>Rol
          <select {...campo('rol')}>
            {Object.entries(ROLES).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
          </select>
        </label>
      </div>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 400 }}>
        <input type="checkbox" checked={f.activo} onChange={(e) => setF({ ...f, activo: e.target.checked })} /> Puede entrar al sistema
      </label>
      <div className="acciones">
        <button type="button" className="fantasma" onClick={onCancelar}>Cancelar</button>
        <button className="primario" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</button>
      </div>
    </form>
  )
}
