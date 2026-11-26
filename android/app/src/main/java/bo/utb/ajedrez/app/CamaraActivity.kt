package bo.utb.ajedrez.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import bo.utb.ajedrez.app.Geometria.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.File

/**
 * Pantalla del árbitro: el celular queda fijo (inclinado) frente al tablero. El tablero se ubica solo
 * en la posición inicial (sin marcadores); si no lo encuentra, se calibra tocando las 4 esquinas.
 * Después de cada jugada se toca "Registrar jugada" y la app deduce cuál fue.
 */
class CamaraActivity : ComponentActivity() {

    private lateinit var api: Api
    private lateinit var vista: PreviewView
    private lateinit var marcas: Marcas
    private lateinit var estado: TextView
    private var camara: Camera? = null
    private var partidaId = 0L
    private var fen = ""
    private var referencia: Mat? = null          // tablero enderezado de la última posición confirmada
    private var referenciaAnterior: Mat? = null  // para volver atrás si se deshace una jugada
    private var calibrando = false
    private var buscando = false

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
            soltarReferencias()
            estado.text = "Toque las 4 esquinas de las casillas en orden: a8, h8, h1, a1."
        }
        // Pulsación larga: volver a buscar el tablero automáticamente
        findViewById<Button>(R.id.calibrar).setOnLongClickListener {
            calibrando = false
            marcas.limpiar()
            soltarReferencias()
            estado.text = "Buscando el tablero en posición inicial…"
            true
        }
        findViewById<Button>(R.id.jugada).setOnClickListener { leerJugada(elegirSiempre = false) }
        findViewById<Button>(R.id.jugada).setOnLongClickListener { leerJugada(elegirSiempre = true); true }
        findViewById<Button>(R.id.deshacer).setOnClickListener { deshacer() }

        marcas.setOnTouchListener { _, e ->
            if (calibrando && e.action == MotionEvent.ACTION_UP) {
                marcas.esquinas += P(e.x.toDouble(), e.y.toDouble())
                marcas.invalidate()
                if (marcas.esquinas.size == 4) {
                    calibrando = false
                    marcas.ubicar(marcas.esquinas.toList())
                    tomarReferencia("Calibrado a mano.")
                }
            }
            true
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) iniciarCamara()
        else pedirCamara.launch(Manifest.permission.CAMERA)
        cargarPartida()
        buscarTableroAuto()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val proveedor = futuro.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            proveedor.unbindAll()
            camara = proveedor.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, mainExecutor)
    }

    /**
     * Mientras el tablero no esté ubicado, lo busca en el video cada 0,7 s. Necesita la posición inicial
     * (centro vacío); una vez ubicado, el celular debe quedar quieto.
     */
    private fun buscarTableroAuto() = lifecycleScope.launch {
        while (isActive) {
            delay(700)
            if (calibrando || referencia != null || buscando) continue
            val foto = vista.bitmap ?: continue
            buscando = true
            val encontradas = withContext(Dispatchers.Default) {
                guardar(foto, "intento.jpg")
                runCatching { Vision.buscarTablero(foto) }.onFailure { Log.e("Ajedrez", "detección", it) }.getOrNull()
            }
            Log.i("Ajedrez", "detección automática: $encontradas")
            // sigue "ocupado" hasta que se tome la referencia
            buscando = encontradas != null && !calibrando && referencia == null
            if (buscando) {
                marcas.ubicar(encontradas!!)
                tomarReferencia("Tablero detectado.")
            }
        }
    }

    /** Guarda la imagen que analiza la app (para revisar la detección desde el PC con adb pull). */
    private fun guardar(foto: Bitmap, nombre: String) = runCatching {
        File(getExternalFilesDir(null), nombre).outputStream().use { foto.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun tomarReferencia(motivo: String) {
        bloquearEnfoque()
        // Espera a que el enfoque y la exposición se asienten antes de la foto de referencia.
        lifecycleScope.launch {
            delay(800)
            soltarReferencias()
            referencia = capturar()
            buscando = false
            estado.text = if (referencia == null) "No se pudo capturar la imagen; calibre de nuevo."
            else "$motivo Después de cada jugada toque «Registrar jugada»."
        }
    }

    /** Fija enfoque y exposición en el centro del tablero para que no cambien entre una foto y otra. */
    private fun bloquearEnfoque() {
        val e = marcas.esquinas
        if (e.size != 4) return
        val punto = vista.meteringPointFactory.createPoint(e.map { it.x }.average().toFloat(), e.map { it.y }.average().toFloat())
        camara?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(punto, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .disableAutoCancel()
                .build()
        )
    }

    private fun cargarPartida() = lifecycleScope.launch {
        try {
            mostrar(withContext(Dispatchers.IO) { api.partida(partidaId) })
            if (referencia == null) estado.text = "${estado.text}\nBuscando el tablero en posición inicial… o toque «Calibrar»."
        } catch (e: ErrorApi) {
            estado.text = e.message
        }
    }

    private fun capturar(): Mat? {
        val foto = vista.bitmap ?: return null
        if (marcas.esquinas.size != 4) return null
        return Vision.enderezar(foto, marcas.esquinas)
    }

    private fun leerJugada(elegirSiempre: Boolean) {
        val ref = referencia ?: run { estado.text = "Todavía no se ubicó el tablero."; return }
        val actual = capturar() ?: run { estado.text = "La cámara todavía no tiene imagen."; return }
        val cambios = Vision.cambios(ref, actual, Geometria.ladoCercano(marcas.esquinas))
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
            estado.text = "${estado.text}\nDeshecha. Toque largo en «Registrar jugada» para elegirla."
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

    private fun soltarReferencias() {
        referencia?.release(); referencia = null
        referenciaAnterior?.release(); referenciaAnterior = null
    }

    override fun onDestroy() {
        soltarReferencias()
        super.onDestroy()
    }
}
