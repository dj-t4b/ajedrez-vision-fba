package bo.utb.ajedrez.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

/** Capa sobre la cámara: dibuja las esquinas marcadas y el contorno del tablero. */
class Marcas(contexto: Context, attrs: AttributeSet?) : View(contexto, attrs) {

    val esquinas = mutableListOf<PointF>()
    private val nombres = listOf("a8", "h8", "h1", "a1")

    private val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 220, 150); strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val punto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 220, 150) }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f; setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    fun limpiar() {
        esquinas.clear()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        esquinas.forEachIndexed { i, p ->
            c.drawCircle(p.x, p.y, 14f, punto)
            c.drawText(nombres[i], p.x + 20, p.y - 20, texto)
            if (i > 0) c.drawLine(esquinas[i - 1].x, esquinas[i - 1].y, p.x, p.y, linea)
        }
        if (esquinas.size == 4) c.drawLine(esquinas[3].x, esquinas[3].y, esquinas[0].x, esquinas[0].y, linea)
    }
}
