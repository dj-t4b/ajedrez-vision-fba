package bo.utb.ajedrez.app

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

/**
 * Deduce la jugada a partir de cuánto cambió cada casilla entre dos fotos del tablero.
 *
 * No reconoce piezas: entre las jugadas legales de la posición, elige la que explica
 * mejor las casillas que cambiaron (origen, destino y, si aplica, torre del enroque
 * o peón capturado al paso).
 */
object Deduccion {

    /** @param san la jugada en notación algebraica (Cf3), para mostrarla al árbitro */
    data class Candidata(val uci: String, val puntaje: Double, val san: String = uci)

    data class Resultado(val candidatas: List<Candidata>, val confianza: Double) {
        val mejor: Candidata? get() = candidatas.firstOrNull()
    }

    /** Cambio mínimo (0..255, diferencia media de gris) para considerar que hubo movimiento. */
    const val UMBRAL_MOVIMIENTO = 12.0

    /**
     * @param cambios 64 valores indexados por casilla: índice = fila * 8 + columna, con la fila 0 = rank 8
     *                y la columna 0 = columna a (el tablero visto desde las blancas, a8 arriba a la izquierda).
     */
    fun deducir(fen: String, cambios: DoubleArray): Resultado {
        require(cambios.size == 64) { "Se esperan 64 casillas" }
        val tablero = Board().apply { loadFromFen(fen) }
        val ruido = cambios.sorted()[32] // mediana: cambio típico de una casilla que no se movió
        // Una casilla suma si cambió claramente más que el ruido y resta si no: así una jugada
        // no gana por tocar más casillas (p. ej. el enroque frente a mover el rey una casilla).
        val corte = ruido + 0.35 * (cambios.max() - ruido)

        val candidatas = tablero.legalMoves()
            .map { m -> Candidata(uci(m), casillasAfectadas(tablero, m).sumOf { cambios[indice(it)] - corte }) }
            .groupBy { it.uci.take(4) } // las 4 coronaciones tocan las mismas casillas: queda la de dama
            .map { (_, grupo) -> grupo.firstOrNull { it.uci.endsWith("q") } ?: grupo.first() }
            .sortedByDescending { it.puntaje }

        val segundoMayor = cambios.sortedDescending()[1]
        if (candidatas.isEmpty() || segundoMayor < UMBRAL_MOVIMIENTO) return Resultado(emptyList(), 0.0)

        // Confianza: cuánto le gana la mejor a la segunda comparado con el ruido de la cámara (la
        // dispersión de las casillas quietas). No se divide por el puntaje de la mejor: jugadas que
        // comparten la casilla de origen (Cf3, Ce2, Ch3) tienen puntajes parecidos sin ser dudosas.
        val p1 = candidatas[0].puntaje
        val p2 = candidatas.getOrNull(1)?.puntaje ?: 0.0
        val quietas = cambios.sorted().take(48)
        val media = quietas.average()
        val sigma = Math.sqrt(quietas.sumOf { (it - media) * (it - media) } / quietas.size).coerceAtLeast(1.0)
        val confianza = if (p1 <= 0) 0.0 else (1 - Math.exp(-(p1 - p2) / (4 * sigma))).coerceIn(0.0, 1.0)
        return Resultado(conSan(fen, candidatas.take(5)), confianza)
    }

    private fun conSan(fen: String, cs: List<Candidata>): List<Candidata> = cs.map { c ->
        val b = Board().apply { loadFromFen(fen) }
        val m = b.legalMoves().first { uci(it) == c.uci }
        val ml = MoveList(fen).apply { add(m) }
        c.copy(san = ml.toSanArray()[0].replace('N', 'C').replace('B', 'A').replace('R', 'T').replace('Q', 'D').replace('K', 'R'))
    }

    private fun casillasAfectadas(b: Board, m: Move): List<Square> {
        val pieza = b.getPiece(m.from)
        val desde = m.from
        val hasta = m.to
        val res = mutableListOf(desde, hasta)
        if (pieza.pieceType == PieceType.KING && Math.abs(desde.file.ordinal - hasta.file.ordinal) == 2) {
            val fila = desde.rank.notation
            if (hasta.file.ordinal > desde.file.ordinal) res += listOf(sq("h$fila"), sq("f$fila"))
            else res += listOf(sq("a$fila"), sq("d$fila"))
        }
        if (pieza.pieceType == PieceType.PAWN && desde.file != hasta.file && b.getPiece(hasta).pieceType == null) {
            res += sq("${hasta.file.notation.lowercase()}${desde.rank.notation}") // captura al paso
        }
        return res
    }

    private fun sq(nombre: String) = Square.valueOf(nombre.uppercase())

    fun indice(s: Square): Int = (7 - s.rank.ordinal) * 8 + s.file.ordinal

    fun uci(m: Move): String {
        val base = m.from.value().lowercase() + m.to.value().lowercase()
        val corona = m.promotion
        return if (corona == null || corona.pieceType == null) base else base + corona.fenSymbol.lowercase()
    }
}
