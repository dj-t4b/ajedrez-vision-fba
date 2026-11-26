import { useState } from 'react'
import { Download } from 'lucide-react'
import { descargar } from '../api.js'
import { useDatos } from '../componentes/useDatos.js'

const TIPOS = { '': 'Todos', ADMIN: 'Administrador', USUARIO: 'Usuario', CELULAR: 'Celular', ANONIMO: 'Sin sesión' }
const hora = (iso) => new Date(iso).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'medium' })

/** Registro de auditoría: todo lo que hace cada usuario y cada celular. Solo el administrador. */
export default function Registro() {
  const [f, setF] = useState({ texto: '', tipo: '', desde: '', hasta: '' })
  const [aplicado, setAplicado] = useState(f)
  const consulta = new URLSearchParams(Object.entries(aplicado).filter(([, v]) => v)).toString()
  const { datos: filas, error } = useDatos(`/api/auditoria${consulta ? `?${consulta}` : ''}`, 10000)
  const [errorCsv, setErrorCsv] = useState('')
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  return (
    <>
      <div className="cabecera">
        <div>
          <h1>Registro</h1>
          <p>Todo lo que hacen los usuarios (administrador incluido) y los celulares, con su resultado. Las contraseñas y el PIN no se guardan.</p>
        </div>
        <button onClick={() => descargar(`/api/auditoria/csv${consulta ? `?${consulta}` : ''}`, 'auditoria.csv').catch((e) => setErrorCsv(e.message))}>
          <Download size={16} aria-hidden /> Exportar CSV
        </button>
      </div>

      <form className="formulario" onSubmit={(e) => { e.preventDefault(); setAplicado(f) }}>
        <div className="campos">
          <label>Buscar<input placeholder="Persona, acción, mesa, jugada…" {...campo('texto')} /></label>
          <label>Quién
            <select {...campo('tipo')}>{Object.entries(TIPOS).map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select>
          </label>
          <label>Desde<input type="date" {...campo('desde')} /></label>
          <label>Hasta<input type="date" {...campo('hasta')} /></label>
        </div>
        <div className="acciones">
          <button type="button" className="fantasma" onClick={() => { const v = { texto: '', tipo: '', desde: '', hasta: '' }; setF(v); setAplicado(v) }}>Limpiar</button>
          <button className="primario">Filtrar</button>
        </div>
      </form>

      {(error || errorCsv) && <div className="error" role="alert" style={{ marginBottom: 16 }}>{error || errorCsv}</div>}
      {filas?.length === 0 ? (
        <div className="vacio">No hay acciones con este filtro.</div>
      ) : (
        <table className="tabla">
          <thead><tr><th>Fecha</th><th>Quién</th><th>Acción</th><th>Detalle</th><th>Resultado</th></tr></thead>
          <tbody>
            {filas?.map((a) => (
              <tr key={a.id}>
                <td className="secundario" style={{ whiteSpace: 'nowrap' }}>{hora(a.fecha)}</td>
                <td>{a.actor}<div className="secundario" style={{ fontSize: '0.8125rem' }}>{TIPOS[a.tipo]} · {a.ip}</div></td>
                <td>{a.accion}<div className="secundario mono" style={{ fontSize: '0.75rem' }}>{a.metodo} {a.ruta}</div></td>
                <td className="mono" style={{ fontSize: '0.8125rem', maxWidth: 320, overflowWrap: 'anywhere' }}>{a.detalle ?? '–'}</td>
                <td>{a.estado < 400 ? <span className="etiqueta">{a.estado} OK</span> : <span className="etiqueta" style={{ color: 'var(--peligro)' }}>{a.estado}</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {filas?.length === 500 && <p className="ayuda" style={{ marginTop: 12 }}>Se muestran las 500 acciones más recientes; filtre o exporte el CSV para ver más.</p>}
    </>
  )
}
