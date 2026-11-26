package bo.utb.ajedrez.app

import com.github.bhlangonijr.chesslib.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DeduccionTest {

    private val inicial = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    /** Ruido de cámara en todas las casillas y un cambio fuerte en las indicadas. */
    private fun cambios(vararg casillas: String, fuerza: Double = 60.0, semilla: Int = 7): DoubleArray {
        val r = Random(semilla)
        val c = DoubleArray(64) { 2.0 + r.nextDouble() * 4.0 }
        for (n in casillas) c[Deduccion.indice(Square.valueOf(n.uppercase()))] = fuerza + r.nextDouble() * 10
        return c
    }

    @Test fun peonDeRey() {
        val r = Deduccion.deducir(inicial, cambios("e2", "e4"))
        assertEquals("e2e4", r.mejor?.uci)
        assertTrue("confianza ${r.confianza}", r.confianza > 0.3)
    }

    @Test fun caballo() {
        assertEquals("g1f3", Deduccion.deducir(inicial, cambios("g1", "f3")).mejor?.uci)
    }

    @Test fun enroqueCorto() {
        val fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        assertEquals("e1g1", Deduccion.deducir(fen, cambios("e1", "f1", "g1", "h1")).mejor?.uci)
    }

    @Test fun movimientoDelReyNoSeConfundeConEnroque() {
        val fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        assertEquals("e1f1", Deduccion.deducir(fen, cambios("e1", "f1")).mejor?.uci)
    }

    @Test fun capturaAlPaso() {
        val fen = "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3"
        assertEquals("e5f6", Deduccion.deducir(fen, cambios("e5", "f6", "f5")).mejor?.uci)
    }

    @Test fun coronacionEligeDama() {
        val fen = "8/4P3/8/8/8/8/k7/4K3 w - - 0 1"
        assertEquals("e7e8q", Deduccion.deducir(fen, cambios("e7", "e8")).mejor?.uci)
    }

    @Test fun jugadaDeNegras() {
        val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
        assertEquals("c7c5", Deduccion.deducir(fen, cambios("c7", "c5")).mejor?.uci)
    }

    @Test fun sinMovimientoNoInventaJugada() {
        val r = Deduccion.deducir(inicial, cambios())
        assertNull(r.mejor)
        assertEquals(0.0, r.confianza, 0.0)
    }

    // Cambios medidos de verdad en la prueba 2 con el tablero de Daniel (fila 0 = rank 8)
    private fun medidos(vararg filas: String) = filas.flatMap { f -> f.trim().split(Regex("\\s+")).map { it.toDouble() } }.toDoubleArray()

    @Test fun pruebaRealE5SeRegistraSola() {
        val c = medidos(
            "4 3 6 5 5 2 4 3", "4 7 3 10 36 6 3 6", "6 5 7 6 11 6 7 6", "2 7 5 6 24 6 7 7",
            "6 4 7 5 5 4 6 4", "3 6 8 7 5 6 5 6", "6 5 4 6 7 7 6 6", "8 4 5 4 4 5 6 6")
        val r = Deduccion.deducir("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", c)
        assertEquals("e7e5", r.mejor?.uci)
        assertEquals("e5", r.mejor?.san)
        assertTrue("confianza ${r.confianza}", r.confianza >= 0.5)
    }

    @Test fun pruebaRealCaballoF3SeRegistraSola() {
        val c = medidos(
            "1 3 3 2 2 1 2 1", "2 5 2 3 1 2 1 3", "2 1 1 1 1 1 1 1", "5 1 1 1 1 1 1 1",
            "3 1 2 1 3 1 1 1", "1 3 1 2 0 29 6 1", "4 5 7 5 1 4 4 5", "2 5 8 6 5 2 133 2")
        val r = Deduccion.deducir("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", c)
        assertEquals("g1f3", r.mejor?.uci)
        assertEquals("Cf3", r.mejor?.san)
        assertTrue("confianza ${r.confianza}", r.confianza >= 0.5)
    }

    @Test fun indiceEsA8ArribaIzquierda() {
        assertEquals(0, Deduccion.indice(Square.A8))
        assertEquals(7, Deduccion.indice(Square.H8))
        assertEquals(56, Deduccion.indice(Square.A1))
        assertEquals(63, Deduccion.indice(Square.H1))
    }
}
