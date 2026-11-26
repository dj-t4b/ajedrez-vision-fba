package bo.utb.ajedrez.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import bo.utb.ajedrez.app.Geometria.P

/** Capa sobre la cámara: esquinas marcadas a mano y, cuando el tablero está ubicado, su cuadrícula 8x8. */
class Marcas(contexto: Context, attrs: AttributeSet?) : View(contexto, attrs) {

    val esquinas = mutableListOf<P>()
    private var cuadricula: List<P> = emptyList()
    private val nombres = listOf("a8", "h8", "h1", "a1")

    private val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 220, 150); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private val punto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 220, 150) }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f; setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    fun limpiar() {
        esquinas.clear()
        cuadricula = emptyList()
        invalidate()
    }

    fun ubicar(nuevas: List<P>) {
        esquinas.clear()
        esquinas += nuevas
        cuadricula = Vision.cuadricula(nuevas)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (cuadricula.size == 81) {
            for (i in 0..8) {
                val f = { col: Int -> cuadricula[i * 9 + col] }
                val k = { fila: Int -> cuadricula[fila * 9 + i] }
                c.drawLine(f(0).x.toFloat(), f(0).y.toFloat(), f(8).x.toFloat(), f(8).y.toFloat(), linea)
                c.drawLine(k(0).x.toFloat(), k(0).y.toFloat(), k(8).x.toFloat(), k(8).y.toFloat(), linea)
            }
        }
        esquinas.forEachIndexed { i, p ->
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), 12f, punto)
            c.drawText(nombres[i], p.x.toFloat() + 18, p.y.toFloat() - 18, texto)
            if (cuadricula.isEmpty() && i > 0) {
                c.drawLine(esquinas[i - 1].x.toFloat(), esquinas[i - 1].y.toFloat(), p.x.toFloat(), p.y.toFloat(), linea)
            }
        }
    }
}
