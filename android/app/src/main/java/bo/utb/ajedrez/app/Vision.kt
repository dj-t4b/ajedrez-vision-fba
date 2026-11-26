package bo.utb.ajedrez.app

import android.graphics.Bitmap
import bo.utb.ajedrez.app.Geometria.P
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
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
     * 1. contorno exterior: el cuadrilátero convexo más grande de la imagen (el marco contra la mesa);
     * 2. se endereza ese contorno y se busca el ancho del marco a cada lado con el que 9 líneas
     *    igualmente espaciadas calzan con los bordes de las casillas;
     * 3. con las piezas se decide en qué eje están las filas, de qué lado están las blancas y
     *    dónde está la columna a ([Geometria.orientar]).
     * @return esquinas a8, h8, h1, a1 en coordenadas de la foto, o null si no se encontró
     */
    fun buscarTablero(foto: Bitmap): List<P>? {
        val gris = aGris(foto)
        val escala = ANCHO_BUSQUEDA / gris.cols()
        val chico = Mat()
        Imgproc.resize(gris, chico, Size(), escala, escala, Imgproc.INTER_AREA)
        try {
            val exterior = contornoExterior(chico) ?: return null
            val juego = areaDeJuego(chico, exterior).map { P(it.x / escala, it.y / escala) }

            // Filas en un eje u otro: el candidato girado 90° es la misma lista de esquinas rotada una posición
            var esquinas = juego
            var (medias, desv) = estadisticas(enderezarGris(gris, esquinas), liberar = true)
            val girado = juego.drop(1) + juego.first()
            val (mediasG, desvG) = estadisticas(enderezarGris(gris, girado), liberar = true)
            if (Geometria.puntajeInicial(desvG) > Geometria.puntajeInicial(desv)) {
                esquinas = girado; medias = mediasG; desv = desvG
            }
            if (Geometria.puntajeInicial(desv) <= 0) return null // no parece la posición inicial
            return Geometria.orientar(medias).map { esquinas[it] }
        } finally {
            gris.release(); chico.release()
        }
    }

    /** El cuadrilátero convexo más grande (al menos 8 % de la imagen), en orden TL, TR, BR, BL. */
    private fun contornoExterior(gris: Mat): List<P>? {
        val bordes = Mat()
        Imgproc.GaussianBlur(gris, bordes, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(bordes, bordes, 50.0, 150.0)
        Imgproc.dilate(bordes, bordes, Mat.ones(3, 3, CvType.CV_8U))
        val contornos = mutableListOf<MatOfPoint>()
        Imgproc.findContours(bordes, contornos, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        bordes.release()
        val minimo = 0.08 * gris.rows() * gris.cols()
        val res = contornos.sortedByDescending { Imgproc.contourArea(it) }.take(5).firstNotNullOfOrNull { c ->
            val c2 = MatOfPoint2f(*c.toArray())
            val aprox = MatOfPoint2f()
            Imgproc.approxPolyDP(c2, aprox, 0.02 * Imgproc.arcLength(c2, true), true)
            val pts = aprox.toArray()
            val ok = pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts)) && Imgproc.contourArea(aprox) > minimo
            c2.release(); aprox.release()
            if (ok) Geometria.ordenar(pts.map { P(it.x, it.y) }) else null
        }
        contornos.forEach { it.release() }
        return res
    }

    /** Dentro del contorno exterior, las 4 esquinas de las 64 casillas (sin el marco), en orden TL, TR, BR, BL. */
    private fun areaDeJuego(gris: Mat, exterior: List<P>): List<P> {
        val w = 640.0
        val h = Imgproc.getPerspectiveTransform(puntos(exterior), cuadrado(w))
        val plano = Mat()
        Imgproc.warpPerspective(gris, plano, h, Size(w, w))
        val perfilX = perfil(plano, dx = 1, dy = 0, eje = 0) // bordes verticales, sumados por columna
        val perfilY = perfil(plano, dx = 0, dy = 1, eje = 1) // bordes horizontales, sumados por fila
        plano.release()
        val maxMargen = (0.15 * w).toInt()
        val (izq, der) = Geometria.ajustarLineas(perfilX, maxMargen)
        val (arr, aba) = Geometria.ajustarLineas(perfilY, maxMargen)
        val n = perfilX.size - 1.0
        val inversa = h.inv()
        h.release()
        return proyectar(inversa, listOf(Point(izq.toDouble(), arr.toDouble()), Point(n - der, arr.toDouble()),
            Point(n - der, n - aba), Point(izq.toDouble(), n - aba))).also { inversa.release() }
    }

    private fun perfil(plano: Mat, dx: Int, dy: Int, eje: Int): DoubleArray {
        val g = Mat()
        Imgproc.Sobel(plano, g, CvType.CV_32F, dx, dy, 3)
        Core.absdiff(g, Scalar(0.0), g)
        val suma = Mat()
        Core.reduce(g, suma, eje, Core.REDUCE_SUM, CvType.CV_64F)
        val n = (suma.total()).toInt()
        val res = DoubleArray(n) { i -> if (eje == 0) suma.get(0, i)[0] else suma.get(i, 0)[0] }
        g.release(); suma.release()
        return res
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
    private fun estadisticas(plano: Mat, liberar: Boolean = false): Pair<DoubleArray, DoubleArray> {
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
        if (liberar) plano.release()
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
