import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'

/** Carga una ruta de la API; con cadaMs > 0 la refresca en segundo plano (partidas en vivo). */
export function useDatos(ruta, cadaMs = 0) {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')

  const cargar = useCallback(async () => {
    try {
      setDatos(await api(ruta))
      setError('')
    } catch (e) {
      setError(e.message)
    }
  }, [ruta])

  useEffect(() => {
    cargar()
    if (!cadaMs) return
    const t = setInterval(cargar, cadaMs)
    return () => clearInterval(t)
  }, [cargar, cadaMs])

  return { datos, setDatos, error, recargar: cargar }
}
