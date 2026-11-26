package bo.utb.ajedrez.app

import android.graphics.Bitmap
import bo.utb.ajedrez.app.Geometria.P
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Visión por computadora en el dispositivo con OpenCV. */
object Vision {

    private const val LADO = 480          // tablero enderezado de 480x480 px: casillas de 60 px
    private const val CASILLA = LADO / 8
    private const val ANCHO_BUSQUEDA = 800.0 // la búsqueda del tablero se hace sobre una imagen reducida

    /**
     * Ubica un tablero estándar en la posición inicial, sin marcadores:
     * 1. busca el cuadriculado vacío del centro (7x3 cruces entre las filas 3 a 6);
     * 2. lo extiende a las 8 filas probando cada desplazamiento posible y se queda con el que deja
     *    piezas arriba y abajo y el centro vacío;
     * 3. decide de qué lado están las blancas y dónde está la columna a ([Geometria.orientar]).
     * @return esquinas a8, h8, h1, a1 en coordenadas de la foto, o null si no se encontró
     */
    fun buscarTablero(foto: Bitmap): List<P>? {
        val gris = aGris(foto)
        val escala = ANCHO_BUSQUEDA / gris.cols()
        val chico = Mat()
        Imgproc.resize(gris, chico, Size(), escala, escala, Imgproc.INTER_AREA)

        val cruces = Mat()
        val hallado = Calib3d.findChessboardCornersSB(chico, Size(7.0, 3.0), cruces,
            Calib3d.CALIB_CB_EXHAUSTIVE or Calib3d.CALIB_CB_ACCURACY)
        if (!hallado || cruces.rows() != 21) {
            gris.release(); chico.release(); cruces.release()
            return null
        }
        // Cruce (c, r) del patrón = punto (c + 1, r) de la cuadrícula del tablero, en la foto original
        val enFoto = MatOfPoint2f(*Array(21) { i -> cruces.get(i, 0).let { Point(it[0] / escala, it[1] / escala) } })
        val enPatron = MatOfPoint2f(*Array(21) { i -> Point((i % 7 + 1).toDouble(), (i / 7).toDouble()) })
        val h = Calib3d.findHomography(enPatron, enFoto)
        chico.release(); cruces.release(); enFoto.release(); enPatron.release()

        var mejor: Pair<List<P>, DoubleArray>? = null
        var mejorPuntaje = Double.NEGATIVE_INFINITY
        for (desplazamiento in 1..5) {
            val o = desplazamiento.toDouble()
            val esquinas = proyectar(h, listOf(Point(0.0, -o), Point(8.0, -o), Point(8.0, 8 - o), Point(0.0, 8 - o)))
            if (esquinas.any { it.x < 0 || it.y < 0 || it.x > gris.cols() || it.y > gris.rows() }) continue
            val plano = enderezarGris(gris, esquinas)
            val (medias, desviaciones) = estadisticas(plano)
            plano.release()
            val p = Geometria.puntajeInicial(desviaciones)
            if (p > mejorPuntaje) {
                mejorPuntaje = p
                mejor = esquinas to medias
            }
        }
        gris.release(); h.release()
        val (esquinas, medias) = mejor ?: return null
        if (mejorPuntaje <= 0) return null // no parece la posición inicial
        return Geometria.orientar(medias).map { esquinas[it] }
    }

    /** Endereza el tablero a un cuadrado visto desde arriba a partir de sus esquinas a8, h8, h1, a1. */
    fun enderezar(foto: Bitmap, esquinas: List<P>): Mat {
        require(esquinas.size == 4)
        val gris = aGris(foto)
        return enderezarGris(gris, esquinas).also { gris.release() }
    }

    /**
     * Diferencia media de gris (0..255) por casilla entre dos tableros enderezados; índice 0 = a8.
     * Solo se mide la franja de la casilla donde apoya la base de la pieza (hacia la cámara).
     */
    fun cambios(antes: Mat, despues: Mat, lado: Geometria.Lado): DoubleArray {
        val dif = Mat()
        Core.absdiff(antes, despues, dif)
        val (x0, y0, x1, y1) = Geometria.zonaBase(lado).map { (it * CASILLA).toInt() }
        val res = DoubleArray(64) { i ->
            val zona = dif.submat(Rect((i % 8) * CASILLA + x0, (i / 8) * CASILLA + y0, x1 - x0, y1 - y0))
            Core.mean(zona).`val`[0].also { zona.release() }
        }
        dif.release()
        return res
    }

    /** Proyecta los puntos de la cuadrícula 9x9 del tablero enderezado a la imagen (para dibujarla). */
    fun cuadricula(esquinas: List<P>): List<P> {
        val h = Imgproc.getPerspectiveTransform(cuadrado(8.0), puntos(esquinas))
        return proyectar(h, List(81) { Point((it % 9).toDouble(), (it / 9).toDouble()) }).also { h.release() }
    }

    private fun aGris(foto: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(foto, rgba)
        val gris = Mat()
        Imgproc.cvtColor(rgba, gris, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gris
    }

    private fun enderezarGris(gris: Mat, esquinas: List<P>): Mat {
        val l = LADO.toDouble()
        val h = Imgproc.getPerspectiveTransform(puntos(esquinas), cuadrado(l))
        val plano = Mat()
        Imgproc.warpPerspective(gris, plano, h, Size(l, l))
        Imgproc.GaussianBlur(plano, plano, Size(5.0, 5.0), 0.0) // reduce ruido del sensor
        h.release()
        return plano
    }

    /** Gris medio y desviación de la parte central de cada casilla de un tablero enderezado. */
    private fun estadisticas(plano: Mat): Pair<DoubleArray, DoubleArray> {
        val m = CASILLA / 5
        val medias = DoubleArray(64)
        val desv = DoubleArray(64)
        val media = MatOfDouble()
        val sd = MatOfDouble()
        for (i in 0 until 64) {
            val zona = plano.submat(Rect((i % 8) * CASILLA + m, (i / 8) * CASILLA + m, CASILLA - 2 * m, CASILLA - 2 * m))
            Core.meanStdDev(zona, media, sd)
            medias[i] = media.get(0, 0)[0]
            desv[i] = sd.get(0, 0)[0]
            zona.release()
        }
        media.release(); sd.release()
        return medias to desv
    }

    private fun proyectar(h: Mat, ps: List<Point>): List<P> {
        val origen = MatOfPoint2f(*ps.toTypedArray())
        val destino = MatOfPoint2f()
        Core.perspectiveTransform(origen, destino, h)
        return destino.toArray().map { P(it.x, it.y) }.also { origen.release(); destino.release() }
    }

    private fun puntos(ps: List<P>) = MatOfPoint2f(*ps.map { Point(it.x, it.y) }.toTypedArray())

    private fun cuadrado(l: Double) = MatOfPoint2f(Point(0.0, 0.0), Point(l, 0.0), Point(l, l), Point(0.0, l))
}
