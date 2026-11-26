import { NavLink, Outlet } from 'react-router-dom'
import { LayoutDashboard, LogOut, ScrollText, Swords, Trophy, Users } from 'lucide-react'
import { useSesion } from './sesion.jsx'
import Marca from './componentes/Marca.jsx'

const ROLES = { ADMIN: 'Administrador', USUARIO: 'Usuario' }
/** Roles dentro de un torneo. */
const ROLES_TORNEO = { ADMIN: 'Administrador', ORGANIZADOR: 'Organizador', ARBITRO_PRINCIPAL: 'Árbitro principal', ARBITRO: 'Árbitro' }

export default function Estructura() {
  const { sesion, salir } = useSesion()
  return (
    <div className="app">
      <aside className="lateral">
        <div className="marca">
          <Marca tamano={28} />
          <div>FBA<small>Registro de partidas</small></div>
        </div>
        <nav className="nav" aria-label="Principal">
          <NavLink to="/" end><LayoutDashboard size={18} aria-hidden /> Panel</NavLink>
          <NavLink to="/torneos"><Trophy size={18} aria-hidden /> Torneos</NavLink>
          <NavLink to="/partidas"><Swords size={18} aria-hidden /> Partidas</NavLink>
          {sesion.rol === 'ADMIN' && <NavLink to="/usuarios"><Users size={18} aria-hidden /> Usuarios</NavLink>}
          {sesion.rol === 'ADMIN' && <NavLink to="/registro"><ScrollText size={18} aria-hidden /> Registro</NavLink>}
        </nav>
        <div className="usuario-actual">
          <div>{sesion.nombre}</div>
          <div className="rol">{ROLES[sesion.rol]}</div>
          <button onClick={salir}><LogOut size={16} aria-hidden /> Salir</button>
        </div>
      </aside>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}

export { ROLES, ROLES_TORNEO }
