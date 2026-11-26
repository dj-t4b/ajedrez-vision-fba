import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useSesion } from '../sesion.jsx'
import Marca from '../componentes/Marca.jsx'
import Tablero from '../componentes/Tablero.jsx'

// Posición de medio juego solo decorativa.
const FEN_DECO = 'r1bk3r/p2pBpNp/n4n2/1p1NP2P/6P1/3P4/P1P1K3/q5b1 w - - 0 1'

export default function Login() {
  const { sesion, entrar } = useSesion()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  if (sesion) return <Navigate to="/" replace />

  async function enviar(e) {
    e.preventDefault()
    setError('')
    setEnviando(true)
    try {
      await entrar(email, password)
    } catch (err) {
      setError(err.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="login">
      <div className="arte" aria-hidden="true">
        <div className="tablero-deco tablero"><Tablero id="deco" fen={FEN_DECO} /></div>
      </div>
      <div className="panel">
        <form onSubmit={enviar}>
          <Marca tamano={40} />
          <div>
            <h1>Registro de partidas</h1>
            <p className="secundario">Federación Boliviana de Ajedrez</p>
          </div>
          {error && <div className="error" role="alert">{error}</div>}
          <label>
            Email
            <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label>
            Contraseña
            <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <button className="primario" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</button>
        </form>
      </div>
    </div>
  )
}
