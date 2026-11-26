/** Marca: un tablero de 2x2 con la casilla de la jugada resaltada. */
export default function Marca({ tamano = 24 }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 24 24" aria-hidden="true">
      <rect x="0" y="0" width="12" height="12" rx="2" fill="var(--casilla-clara)" />
      <rect x="12" y="0" width="12" height="12" rx="2" fill="var(--casilla-oscura)" />
      <rect x="0" y="12" width="12" height="12" rx="2" fill="var(--casilla-oscura)" />
      <rect x="12" y="12" width="12" height="12" rx="2" fill="var(--casilla-clara)" />
      <circle cx="18" cy="18" r="3.2" fill="var(--acento)" />
    </svg>
  )
}
