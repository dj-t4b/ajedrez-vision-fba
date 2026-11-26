import { createContext, useContext, useState } from 'react'
import { api, guardarSesion, leerSesion } from './api.js'

const Ctx = createContext(null)

export function ProveedorSesion({ children }) {
  const [sesion, setSesion] = useState(leerSesion)

  async function entrar(email, password) {
    const r = await api('/api/auth/login', { metodo: 'POST', cuerpo: { email, password } })
    const s = { token: r.token, id: r.id, nombre: r.nombre, rol: r.rol, expira: Date.now() + r.expiraEnSegundos * 1000 }
    guardarSesion(s)
    setSesion(s)
  }

  function salir() {
    guardarSesion(null)
    setSesion(null)
  }

  return <Ctx.Provider value={{ sesion, entrar, salir }}>{children}</Ctx.Provider>
}

export const useSesion = () => useContext(Ctx)

export const puedeRegistrar = (s) => s?.rol === 'ADMIN' || s?.rol === 'ARBITRO'
