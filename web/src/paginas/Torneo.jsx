import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Link2, Plus, Smartphone, Trash2, X } from 'lucide-react'
import QRCode from 'qrcode'
import { api } from '../api.js'
import { useDatos } from '../componentes/useDatos.js'
import { ROLES_TORNEO } from '../Estructura.jsx'
import { nombreTorneo, PartidasDelTorneo } from './Partidas.jsx'

const PESTANAS = [['partidas', 'Partidas'], ['equipo', 'Equipo'], ['celulares', 'Celulares']]

/** Un torneo: sus partidas por mesa, su equipo (roles por torneo) y los celulares de las mesas. */
export default function Torneo() {
  const { id } = useParams()
  const { datos: torneo, error, recargar } = useDatos(`/api/torneos/${id}`, 5000)
  const [pestana, setPestana] = useState('partidas')

  if (error && !torneo) return <div className="error" role="alert">{error}</div>
  if (!torneo) return <p className="secundario">Cargando torneo…</p>

  return (
    <>
      <div className="cabecera">
        <div>
          <Link to="/torneos" className="secundario" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            <ArrowLeft size={16} aria-hidden /> Torneos
          </Link>
          <h1 style={{ marginTop: 8 }}>{nombreTorneo(torneo)}</h1>
          <p>{[torneo.ciudad, ROLES_TORNEO[torneo.miRol] && `Su rol: ${ROLES_TORNEO[torneo.miRol]}`].filter(Boolean).join(' · ')}</p>
        </div>
      </div>
      <div className="filtros" role="tablist" aria-label="Secciones del torneo">
        {PESTANAS.map(([v, t]) => (
          <button key={v} role="tab" aria-selected={pestana === v} aria-pressed={pestana === v} onClick={() => setPestana(v)}>{t}</button>
        ))}
      </div>
      {pestana === 'partidas' && <PartidasDelTorneo torneo={torneo} />}
      {pestana === 'equipo' && <Equipo torneo={torneo} />}
      {pestana === 'celulares' && <Celulares torneo={torneo} onCambio={recargar} />}
    </>
  )
}

/** Qué roles puede asignar cada uno: admin → organizador; organizador → árbitro principal; árbitro principal → árbitros. */
function rolesAsignables(miRol) {
  if (miRol === 'ADMIN') return ['ORGANIZADOR', 'ARBITRO_PRINCIPAL', 'ARBITRO']
  if (miRol === 'ORGANIZADOR') return ['ARBITRO_PRINCIPAL']
  if (miRol === 'ARBITRO_PRINCIPAL') return ['ARBITRO']
  return []
}

const textoMesas = (mesas) => (mesas.length ? [...mesas].sort((a, b) => a - b).join(', ') : 'Todas')
const leerMesas = (texto) => [...new Set(texto.split(/[\s,;]+/).filter(Boolean).map(Number).filter((n) => n > 0))]

function Equipo({ torneo }) {
  const { datos: equipo, error, recargar } = useDatos(`/api/torneos/${torneo.id}/asignaciones`)
  const [errorAccion, setErrorAccion] = useState('')
  const asignables = rolesAsignables(torneo.miRol)
  const editaMesas = asignables.includes('ARBITRO')

  async function accion(promesa) {
    setErrorAccion('')
    try {
      await promesa
      recargar()
    } catch (e) {
      setErrorAccion(e.message)
    }
  }

  return (
    <>
      {asignables.length > 0 && <NuevaAsignacion torneo={torneo} roles={asignables} onHecho={recargar} />}
      {(error || errorAccion) && <div className="error" role="alert" style={{ marginBottom: 16 }}>{error || errorAccion}</div>}
      {equipo?.length === 0 ? (
        <div className="vacio">Todavía no hay nadie asignado a este torneo.</div>
      ) : (
        <table className="tabla">
          <thead><tr><th>Nombre</th><th>Email</th><th>Rol en el torneo</th><th>Mesas</th><th><span className="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            {equipo?.map((a) => (
              <tr key={a.id}>
                <td>{a.nombre}</td>
                <td className="secundario">{a.email}</td>
                <td>{ROLES_TORNEO[a.rol]}</td>
                <td>
                  {a.rol !== 'ARBITRO' ? <span className="secundario">{a.rol === 'ORGANIZADOR' ? '–' : 'Todas'}</span>
                    : editaMesas ? (
                      <input aria-label={`Mesas de ${a.nombre}`} defaultValue={a.mesas.join(', ')} placeholder="Todas" style={{ width: 140 }}
                        onBlur={(e) => accion(api(`/api/asignaciones/${a.id}/mesas`, { metodo: 'PUT', cuerpo: { mesas: leerMesas(e.target.value) } }))} />
                    ) : textoMesas(a.mesas)}
                </td>
                <td style={{ textAlign: 'right' }}>
                  {asignables.includes(a.rol) && (
                    <button className="fantasma peligro" aria-label={`Quitar a ${a.nombre}`}
                      onClick={() => accion(api(`/api/asignaciones/${a.id}`, { metodo: 'DELETE' }))}><Trash2 size={16} aria-hidden /></button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {editaMesas && <p className="ayuda" style={{ marginTop: 12 }}>Mesas de cada árbitro: números separados por comas. Vacío = todas las mesas.</p>}
    </>
  )
}

function NuevaAsignacion({ torneo, roles, onHecho }) {
  const { datos: personas } = useDatos('/api/usuarios/directorio')
  const [f, setF] = useState({ usuarioId: '', rol: roles[roles.length - 1], mesas: '' })
  const [error, setError] = useState('')

  async function enviar(e) {
    e.preventDefault()
    setError('')
    try {
      await api(`/api/torneos/${torneo.id}/asignaciones`, {
        metodo: 'POST',
        cuerpo: { usuarioId: Number(f.usuarioId), rol: f.rol, mesas: f.rol === 'ARBITRO' ? leerMesas(f.mesas) : [] },
      })
      setF({ ...f, usuarioId: '', mesas: '' })
      onHecho()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <h2>Agregar al equipo</h2>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="campos">
        <label>Persona
          <select required value={f.usuarioId} onChange={(e) => setF({ ...f, usuarioId: e.target.value })}>
            <option value="">Elegir…</option>
            {personas?.map((u) => <option key={u.id} value={u.id}>{u.nombre} ({u.email})</option>)}
          </select>
        </label>
        <label>Rol
          <select value={f.rol} onChange={(e) => setF({ ...f, rol: e.target.value })}>
            {roles.map((r) => <option key={r} value={r}>{ROLES_TORNEO[r]}</option>)}
          </select>
        </label>
        {f.rol === 'ARBITRO' && (
          <label>Mesas<input placeholder="Todas (o 1, 2, 5)" value={f.mesas} onChange={(e) => setF({ ...f, mesas: e.target.value })} /></label>
        )}
      </div>
      <div className="acciones"><button className="primario"><Plus size={18} aria-hidden /> Agregar</button></div>
    </form>
  )
}

const CLAVE_SERVIDOR = 'ajedrez.servidorPublico'
const haceCuanto = (iso) => {
  if (!iso) return 'nunca'
  const s = Math.round((Date.now() - new Date(iso)) / 1000)
  return s < 60 ? `hace ${s} s` : s < 3600 ? `hace ${Math.round(s / 60)} min` : new Date(iso).toLocaleString('es-BO')
}

/** Celulares de las mesas: el árbitro principal abre la vinculación (un QR para todo el torneo) y reasigna mesas. */
function Celulares({ torneo, onCambio }) {
  const { datos: celulares, error, recargar } = useDatos(`/api/torneos/${torneo.id}/dispositivos`, 3000)
  const [vinculacion, setVinculacion] = useState(null)
  const [errorAccion, setErrorAccion] = useState('')
  const gestiona = torneo.miRol === 'ADMIN' || torneo.miRol === 'ARBITRO_PRINCIPAL'
  const desvincula = gestiona || torneo.miRol === 'ARBITRO'

  async function accion(promesa, despues) {
    setErrorAccion('')
    try {
      const r = await promesa
      despues?.(r)
      recargar()
      onCambio()
    } catch (e) {
      setErrorAccion(e.message)
    }
  }

  return (
    <>
      {gestiona && (
        vinculacion
          ? <PanelVinculacion vinculacion={vinculacion}
              onCerrar={() => accion(api(`/api/torneos/${torneo.id}/vinculacion`, { metodo: 'DELETE' }), () => setVinculacion(null))} />
          : (
            <div className="formulario" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
              <div>
                <h2>Vincular celulares</h2>
                <p className="ayuda">Un solo QR para todo el torneo. Cada celular que lo escanee recibe un código (C-01, C-02…) y la siguiente mesa libre.</p>
                {torneo.vinculacionHasta && <p className="ayuda">Hay una vinculación abierta; ábrala de nuevo para ver un PIN nuevo.</p>}
              </div>
              <button className="primario" onClick={() => accion(api(`/api/torneos/${torneo.id}/vinculacion`, { metodo: 'POST' }), setVinculacion)}>
                <Link2 size={18} aria-hidden /> Abrir vinculación
              </button>
            </div>
          )
      )}
      {(error || errorAccion) && <div className="error" role="alert" style={{ marginBottom: 16 }}>{error || errorAccion}</div>}
      {celulares?.length === 0 ? (
        <div className="vacio">Todavía no hay celulares vinculados a este torneo.</div>
      ) : (
        <table className="tabla">
          <thead><tr><th>Celular</th><th>Mesa</th><th>Último contacto</th><th><span className="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            {celulares?.map((c) => (
              <tr key={c.id}>
                <td><span style={{ display: 'inline-flex', gap: 8, alignItems: 'center', fontWeight: 600 }}><Smartphone size={16} aria-hidden /> {c.codigo}</span></td>
                <td>
                  {gestiona ? (
                    <input type="number" min={1} aria-label={`Mesa de ${c.codigo}`} defaultValue={c.mesa} key={c.mesa} style={{ width: 80 }}
                      onBlur={(e) => Number(e.target.value) !== c.mesa &&
                        accion(api(`/api/dispositivos/${c.id}/mesa`, { metodo: 'PUT', cuerpo: { mesa: Number(e.target.value) } }))} />
                  ) : c.mesa}
                </td>
                <td className="secundario">{haceCuanto(c.ultimoContacto)}</td>
                <td style={{ textAlign: 'right' }}>
                  {desvincula && (
                    <button className="fantasma peligro" onClick={() => accion(api(`/api/dispositivos/${c.id}`, { metodo: 'DELETE' }))}>
                      <X size={16} aria-hidden /> Desvincular
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function PanelVinculacion({ vinculacion, onCerrar }) {
  const [servidor, setServidor] = useState(() => localStorage.getItem(CLAVE_SERVIDOR) || vinculacion.servidor)
  const [qr, setQr] = useState('')
  const contenido = `ajedrezfba://vincular?s=${encodeURIComponent(servidor)}&p=${vinculacion.pin}`

  useEffect(() => {
    QRCode.toDataURL(contenido, { width: 280, margin: 1 }).then(setQr)
  }, [contenido])

  return (
    <div className="formulario" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 24, alignItems: 'center' }}>
      {qr && <img src={qr} width={280} height={280} alt={`QR de vinculación, PIN ${vinculacion.pin}`} />}
      <div style={{ display: 'grid', gap: 12 }}>
        <h2>Escanee con la app de cada mesa</h2>
        <div>
          <div className="secundario">o escriba el PIN</div>
          <div className="num" style={{ fontSize: '2.75rem', fontWeight: 600, letterSpacing: '0.08em' }}>{vinculacion.pin}</div>
          <div className="ayuda">Válido hasta las {new Date(vinculacion.venceEn).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' })}</div>
        </div>
        <label>Dirección del servidor para los celulares
          <input value={servidor} onChange={(e) => { setServidor(e.target.value); localStorage.setItem(CLAVE_SERVIDOR, e.target.value) }} />
        </label>
        <p className="ayuda">Con el cable USB es http://localhost:8080/ajedrez; por Wi-Fi, la IP de esta computadora.</p>
        <div><button onClick={onCerrar}>Cerrar vinculación</button></div>
      </div>
    </div>
  )
}
