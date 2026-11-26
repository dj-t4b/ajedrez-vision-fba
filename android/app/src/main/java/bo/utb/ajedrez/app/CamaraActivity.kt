package bo.utb.ajedrez.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

/**
 * Pantalla del árbitro: el celular queda fijo sobre el tablero. Se calibra una vez marcando
 * las 4 esquinas; después de cada jugada se toca "Registrar jugada" y la app deduce cuál fue.
 */
class CamaraActivity : ComponentActivity() {

    private lateinit var api: Api
    private lateinit var vista: PreviewView
    private lateinit var marcas: Marcas
    private lateinit var estado: TextView
    private var partidaId = 0L
    private var fen = ""
    private var referencia: Mat? = null          // tablero enderezado de la última posición confirmada
    private var referenciaAnterior: Mat? = null  // para volver atrás si se deshace una jugada
    private var calibrando = false

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamara() else estado.text = "Sin permiso de cámara no se pueden leer jugadas."
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(guardado: Bundle?) {
        super.onCreate(guardado)
        setContentView(R.layout.camara)
        api = Api(this)
        partidaId = intent.getLongExtra("id", 0)
        vista = findViewById(R.id.vista)
        marcas = findViewById(R.id.marcas)
        estado = findViewById(R.id.estado)

        if (!OpenCVLoader.initLocal()) estado.text = "No se pudo cargar OpenCV."

        findViewById<Button>(R.id.calibrar).setOnClickListener {
            calibrando = true
            marcas.limpiar()
            referencia = null
            estado.text = "Toque las 4 esquinas del tablero en orden: a8, h8, h1, a1."
        }
        findViewById<Button>(R.id.jugada).setOnClickListener { leerJugada(elegirSiempre = false) }
        findViewById<Button>(R.id.jugada).setOnLongClickListener { leerJugada(elegirSiempre = true); true }
        findViewById<Button>(R.id.deshacer).setOnClickListener { deshacer() }

        marcas.setOnTouchListener { _, e ->
            if (calibrando && e.action == MotionEvent.ACTION_UP) {
                marcas.esquinas += PointF(e.x, e.y)
                marcas.invalidate()
                if (marcas.esquinas.size == 4) {
                    calibrando = false
                    referencia = capturar()
                    estado.text = if (referencia == null) "No se pudo capturar la imagen; calibre de nuevo."
                    else "Listo. Después de cada jugada toque «Registrar jugada»."
                }
            }
            true
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) iniciarCamara()
        else pedirCamara.launch(Manifest.permission.CAMERA)
        cargarPartida()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val proveedor = futuro.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            proveedor.unbindAll()
            proveedor.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, mainExecutor)
    }

    private fun cargarPartida() = lifecycleScope.launch {
        try {
            mostrar(withContext(Dispatchers.IO) { api.partida(partidaId) })
            if (referencia == null) estado.text = "${estado.text}\nToque «Calibrar» con el celular fijo sobre el tablero."
        } catch (e: ErrorApi) {
            estado.text = e.message
        }
    }

    private fun capturar(): Mat? {
        val foto = vista.bitmap ?: return null
        return Vision.enderezar(foto, marcas.esquinas)
    }

    private fun leerJugada(elegirSiempre: Boolean) {
        val ref = referencia ?: run { estado.text = "Primero calibre el tablero."; return }
        val actual = capturar() ?: run { estado.text = "La cámara todavía no tiene imagen."; return }
        val cambios = Vision.cambios(ref, actual)
        val r = Deduccion.deducir(fen, cambios)
        Log.i("Ajedrez", "candidatas=${r.candidatas} confianza=${r.confianza}")

        val mejor = r.mejor
        if (mejor == null) {
            estado.text = "No se ve ningún cambio en el tablero. ¿Se movió una pieza?"
            actual.release()
            return
        }
        if (!elegirSiempre && r.confianza >= 0.5) {
            enviar(mejor.uci, r.confianza, actual)
            return
        }
        // Confianza baja: el árbitro elige entre las jugadas más probables
        val opciones = r.candidatas.map { it.uci }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("¿Cuál fue la jugada? (confianza ${(r.confianza * 100).toInt()}%)")
            .setItems(opciones) { _, i -> enviar(opciones[i], r.confianza, actual) }
            .setNegativeButton("Ninguna") { _, _ -> actual.release() }
            .show()
    }

    private fun enviar(uci: String, confianza: Double, actual: Mat) = lifecycleScope.launch {
        try {
            mostrar(withContext(Dispatchers.IO) { api.registrarJugada(partidaId, uci, confianza) })
            referenciaAnterior?.release()
            referenciaAnterior = referencia
            referencia = actual
        } catch (e: ErrorApi) {
            estado.text = "No se registró $uci: ${e.message}"
            actual.release()
        }
    }

    private fun deshacer() = lifecycleScope.launch {
        try {
            mostrar(withContext(Dispatchers.IO) { api.deshacer(partidaId) })
            referenciaAnterior?.let { referencia?.release(); referencia = it; referenciaAnterior = null }
            estado.text = "${estado.text}\nJugada deshecha. Toque largo en «Registrar jugada» para elegirla a mano."
        } catch (e: ErrorApi) {
            estado.text = e.message
        }
    }

    private fun mostrar(detalle: JSONObject) {
        val p = detalle.getJSONObject("partida")
        fen = p.getString("fenActual")
        val jugadas = detalle.getJSONArray("jugadas")
        val ultima = if (jugadas.length() > 0) jugadas.getJSONObject(jugadas.length() - 1).getString("san") else "—"
        val turno = if (fen.split(" ")[1] == "w") "blancas" else "negras"
        estado.text = if (p.getString("estado") == "FINALIZADA") {
            "${p.getString("blancas")} – ${p.getString("negras")}: partida terminada ${p.getString("resultado")}"
        } else {
            "${p.getString("blancas")} – ${p.getString("negras")} · última: $ultima · juegan $turno"
        }
    }

    override fun onDestroy() {
        referencia?.release()
        referenciaAnterior?.release()
        super.onDestroy()
    }
}
