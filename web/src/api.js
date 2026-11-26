const BASE = import.meta.env.VITE_API_URL ?? '/ajedrez'
const CLAVE = 'ajedrez.sesion'

export function leerSesion() {
  try {
    const s = JSON.parse(localStorage.getItem(CLAVE))
    return s && s.expira > Date.now() ? s : null
  } catch {
    return null
  }
}

export function guardarSesion(s) {
  if (s) localStorage.setItem(CLAVE, JSON.stringify(s))
  else localStorage.removeItem(CLAVE)
}

export class ErrorApi extends Error {
  constructor(status, mensaje) {
    super(mensaje)
    this.status = status
  }
}

/** Descarga un archivo protegido (PDF/Excel) con el token y lo guarda con el nombre que manda el servidor. */
export async function descargar(ruta, nombre) {
  const sesion = leerSesion()
  const res = await fetch(BASE + ruta, { headers: sesion ? { Authorization: `Bearer ${sesion.token}` } : {} })
  if (!res.ok) throw new ErrorApi(res.status, `No se pudo generar el reporte (error ${res.status})`)
  const url = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.click()
  URL.revokeObjectURL(url)
}

/** fetch con el token de la sesión; lanza ErrorApi con el detalle del ProblemDetail del backend. */
export async function api(ruta, { metodo = 'GET', cuerpo } = {}) {
  const sesion = leerSesion()
  const headers = {}
  if (sesion) headers.Authorization = `Bearer ${sesion.token}`
  if (cuerpo !== undefined) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(BASE + ruta, {
      method: metodo,
      headers,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    throw new ErrorApi(0, 'No hay conexión con el servidor. Revise que WildFly esté encendido.')
  }

  if (res.status === 401 && sesion) {
    guardarSesion(null)
    window.location.assign('/login')
  }
  if (!res.ok) {
    let detalle = `Error ${res.status}`
    try {
      detalle = (await res.json()).detail ?? detalle
    } catch { /* respuesta sin JSON */ }
    throw new ErrorApi(res.status, detalle)
  }
  return res.status === 204 ? null : res.json()
}
