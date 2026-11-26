import { Chessboard } from 'react-chessboard'

/**
 * Tablero con los colores del panel. Si recibe onJugada, permite arrastrar piezas
 * y entrega la jugada en UCI (coronación siempre a dama).
 */
export default function Tablero({ fen, onJugada, ultima, id = 'tablero' }) {
  const resaltado = {}
  if (ultima) {
    for (const casilla of [ultima.slice(0, 2), ultima.slice(2, 4)]) {
      resaltado[casilla] = { background: 'oklch(0.85 0.12 95 / 0.65)' }
    }
  }
  return (
    <Chessboard
      options={{
        id,
        position: fen,
        allowDragging: Boolean(onJugada),
        animationDurationInMs: 220,
        squareStyles: resaltado,
        lightSquareStyle: { backgroundColor: 'var(--casilla-clara)' },
        darkSquareStyle: { backgroundColor: 'var(--casilla-oscura)' },
        onPieceDrop: ({ piece, sourceSquare, targetSquare }) => {
          if (!onJugada || !targetSquare || sourceSquare === targetSquare) return false
          const corona = piece.pieceType[1] === 'P' && (targetSquare[1] === '8' || targetSquare[1] === '1')
          onJugada(sourceSquare + targetSquare + (corona ? 'q' : ''))
          return true
        },
      }}
    />
  )
}
