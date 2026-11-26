import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import './index.css'
import { ProveedorSesion, useSesion } from './sesion.jsx'
import Estructura from './Estructura.jsx'
import Login from './paginas/Login.jsx'
import Panel from './paginas/Panel.jsx'
import Partidas from './paginas/Partidas.jsx'
import Partida from './paginas/Partida.jsx'
import Usuarios from './paginas/Usuarios.jsx'
import Torneos from './paginas/Torneos.jsx'

function Protegida({ children, rol }) {
  const { sesion } = useSesion()
  if (!sesion) return <Navigate to="/login" replace />
  if (rol && sesion.rol !== rol) return <Navigate to="/" replace />
  return children
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ProveedorSesion>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<Protegida><Estructura /></Protegida>}>
            <Route index element={<Panel />} />
            <Route path="torneos" element={<Torneos />} />
            <Route path="torneos/:id" element={<Partidas />} />
            <Route path="partidas" element={<Partidas />} />
            <Route path="partidas/:id" element={<Partida />} />
            <Route path="usuarios" element={<Protegida rol="ADMIN"><Usuarios /></Protegida>} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ProveedorSesion>
  </StrictMode>,
)
