package bo.utb.ajedrez.app

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Visión por computadora en el dispositivo con OpenCV. */
object Vision {

    private const val LADO = 480          // tablero enderezado de 480x480 px: casillas de 60 px
    private const val CASILLA = LADO / 8
    private const val MARGEN = 0.2        // se ignora el borde de cada casilla (piezas vecinas, líneas)

    /**
     * Endereza el tablero a un cuadrado visto desde arriba usando las 4 esquinas marcadas
     * en orden a8, h8, h1, a1 (coordenadas de la imagen).
     */
    fun enderezar(foto: Bitmap, esquinas: List<PointF>): Mat {
        require(esquinas.size == 4)
        val rgba = Mat()
        Utils.bitmapToMat(foto, rgba)
        val gris = Mat()
        Imgproc.cvtColor(rgba, gris, Imgproc.COLOR_RGBA2GRAY)
        val origen = MatOfPoint2f(*esquinas.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val l = LADO.toDouble()
        val destino = MatOfPoint2f(Point(0.0, 0.0), Point(l, 0.0), Point(l, l), Point(0.0, l))
        val h = Imgproc.getPerspectiveTransform(origen, destino)
        val plano = Mat()
        Imgproc.warpPerspective(gris, plano, h, Size(l, l))
        Imgproc.GaussianBlur(plano, plano, Size(5.0, 5.0), 0.0) // reduce ruido del sensor
        rgba.release(); gris.release(); h.release()
        return plano
    }

    /** Diferencia media de gris (0..255) por casilla entre dos tableros enderezados; índice 0 = a8. */
    fun cambios(antes: Mat, despues: Mat): DoubleArray {
        val dif = Mat()
        Core.absdiff(antes, despues, dif)
        val m = (CASILLA * MARGEN).toInt()
        val res = DoubleArray(64) { i ->
            val fila = i / 8
            val col = i % 8
            val zona = dif.submat(Rect(col * CASILLA + m, fila * CASILLA + m, CASILLA - 2 * m, CASILLA - 2 * m))
            Core.mean(zona).`val`[0].also { zona.release() }
        }
        dif.release()
        return res
    }
}
