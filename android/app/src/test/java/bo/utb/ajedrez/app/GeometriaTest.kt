package bo.utb.ajedrez.app

import bo.utb.ajedrez.app.Geometria.Lado
import bo.utb.ajedrez.app.Geometria.P
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometriaTest {

    // Cámara inclinada desde el lado de las blancas: el borde de abajo (fila 1) se ve más ancho.
    private val trapecio = listOf(P(300.0, 200.0), P(700.0, 200.0), P(900.0, 800.0), P(100.0, 800.0))

    @Test fun ladoCercanoEsElBordeMasLargo() {
        assertEquals(Lado.ABAJO, Geometria.ladoCercano(trapecio))
        val desdeLaDerecha = listOf(P(100.0, 100.0), P(800.0, 0.0), P(800.0, 1000.0), P(100.0, 900.0))
        assertEquals(Lado.DERECHA, Geometria.ladoCercano(desdeLaDerecha))
    }

    @Test fun zonaBaseQuedaHaciaLaCamara() {
        val z = Geometria.zonaBase(Lado.ABAJO)
        assertTrue(z[1] > 0.4 && z[3] > 0.8) // mitad inferior de la casilla
    }

    /**
     * Grises medios de la posición inicial vista con a8 arriba a la izquierda: casillas claras 200,
     * oscuras 90; las piezas blancas suben el gris de su casilla y las negras lo bajan.
     */
    private fun inicialNormal() = DoubleArray(64) { i ->
        val f = i / 8
        val c = i % 8
        val base = if ((f + c) % 2 == 0) 200.0 else 90.0
        when (f) {
            0, 1 -> base * 0.5 + 20   // negras
            6, 7 -> base * 0.5 + 120  // blancas
            else -> base
        }
    }

    /** Gira el candidato 180° (cámara del lado de las negras). */
    private fun girar(m: DoubleArray) = DoubleArray(64) { m[63 - it] }

    /** Refleja izquierda-derecha (el detector puede entregar la cuadrícula espejada). */
    private fun espejar(m: DoubleArray) = DoubleArray(64) { m[(it / 8) * 8 + 7 - it % 8] }

    @Test fun orientacionNormal() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3), Geometria.orientar(inicialNormal()))
    }

    @Test fun orientacionGirada() {
        // a8 queda abajo-der (2), h8 abajo-izq (3), h1 arriba-izq (0), a1 arriba-der (1)
        assertArrayEquals(intArrayOf(2, 3, 0, 1), Geometria.orientar(girar(inicialNormal())))
    }

    @Test fun orientacionEspejada() {
        assertArrayEquals(intArrayOf(1, 0, 3, 2), Geometria.orientar(espejar(inicialNormal())))
    }

    @Test fun puntajeInicialPrefiereElCentroVacio() {
        val desv = DoubleArray(64) { i -> if (i / 8 in 2..5) 3.0 else 25.0 }
        val corrida = DoubleArray(64) { i -> if (i / 8 in 3..6) 3.0 else 25.0 } // candidato desplazado una fila
        assertTrue(Geometria.puntajeInicial(desv) > Geometria.puntajeInicial(corrida))
    }
}
