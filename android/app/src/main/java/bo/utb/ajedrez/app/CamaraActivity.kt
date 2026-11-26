package bo.utb.ajedrez.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
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
import org.opencv.imgcodecs.Imgcodecs
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
    private var ocupado = false                 // diálogo abierto o jugada enviándose
    private lateinit var aviso: TextView

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
        aviso = findViewById(R.id.aviso)

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
        vigilar()
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

    /** Botón «Registrar jugada» (respaldo del registro automático). */
    private fun leerJugada(elegirSiempre: Boolean) {
        if (referencia == null) { estado.text = "Todavía no se ubicó el tablero."; return }
        if (ocupado) return
        val actual = capturar() ?: run { estado.text = "La cámara todavía no tiene imagen."; return }
        evaluar(actual, elegirSiempre, automatico = false)
    }

    /**
     * Registro automático: compara cada cuadro con el anterior. Cuando hubo movimiento (la mano sobre
     * el tablero) y después la imagen queda quieta ~1 s, evalúa la jugada contra la posición anterior.
     */
    private fun vigilar() = lifecycleScope.launch {
        var previo: Mat? = null
        var quietos = 0
        var huboMovimiento = false
        while (isActive) {
            delay(350)
            if (referencia == null || calibrando || ocupado) {
                previo?.release(); previo = null; quietos = 0; huboMovimiento = false
                continue
            }
            val actual = capturar() ?: continue
            val movimiento = previo?.let { Vision.diferenciaMedia(it, actual) } ?: 0.0
            previo?.release()
            previo = actual.clone()
            if (movimiento > MOVIMIENTO_CUADRO) {
                huboMovimiento = true
                quietos = 0
                actual.release()
                continue
            }
            quietos++
            if (huboMovimiento && quietos >= CUADROS_QUIETOS) {
                // Si la mano sigue tapando el tablero (muchas casillas distintas), se sigue esperando
                if (evaluar(actual, elegirSiempre = false, automatico = true)) huboMovimiento = false
            } else {
                actual.release()
            }
        }
        previo?.release()
    }

    /**
     * Deduce la jugada de [actual] contra la referencia y la registra o pregunta. Se queda con [actual].
     * @return false si parece que algo todavía tapa el tablero (solo en modo automático)
     */
    private fun evaluar(actual: Mat, elegirSiempre: Boolean, automatico: Boolean): Boolean {
        val ref = referencia ?: run { actual.release(); return true }
        val cambios = Vision.cambios(ref, actual, Geometria.ladoCercano(marcas.esquinas))
        if (automatico && cambios.count { it > Deduccion.UMBRAL_MOVIMIENTO } > MAX_CASILLAS_CAMBIADAS) {
            actual.release()
            return false
        }
        val r = Deduccion.deducir(fen, cambios)
        Log.i("Ajedrez", "${if (automatico) "auto" else "botón"} candidatas=${r.candidatas} confianza=${r.confianza}")
        Log.i("Ajedrez", "cambios a8..h1:\n" + cambios.toList().chunked(8).joinToString("\n") { f -> f.joinToString(" ") { "%3.0f".format(it) } })
        // Tableros enderezados antes/después, para revisar la lectura desde el PC con adb pull
        getExternalFilesDir(null)?.let { dir ->
            Imgcodecs.imwrite(File(dir, "antes.png").path, ref)
            Imgcodecs.imwrite(File(dir, "despues.png").path, actual)
        }

        val mejor = r.mejor
        if (mejor == null) {
            // En automático, la mano pasó sin mover nada: no se avisa
            if (!automatico) estado.text = "No se ve ningún cambio en el tablero. ¿Se movió una pieza?"
            actual.release()
            return true
        }
        if (!elegirSiempre && r.confianza >= 0.5) {
            enviar(mejor, r.confianza, actual)
            return true
        }
        // Confianza baja: el árbitro elige entre las jugadas más probables
        ocupado = true
        val etiquetas = r.candidatas.mapIndexed { i, c -> if (i == 0) "${c.san}   (más probable)" else c.san }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("¿Cuál fue la jugada? (confianza ${(r.confianza * 100).toInt()}%)")
            .setItems(etiquetas) { _, i -> enviar(r.candidatas[i], r.confianza, actual) }
            .setNegativeButton("Ninguna") { _, _ -> actual.release(); ocupado = false }
            .setOnCancelListener { actual.release(); ocupado = false }
            .show()
        return true
    }

    private fun enviar(jugada: Deduccion.Candidata, confianza: Double, actual: Mat) = lifecycleScope.launch {
        ocupado = true
        try {
            mostrar(withContext(Dispatchers.IO) { api.registrarJugada(partidaId, jugada.uci, confianza) })
            referenciaAnterior?.release()
            referenciaAnterior = referencia
            referencia = actual
            avisar(jugada.san)
        } catch (e: ErrorApi) {
            estado.text = "No se registró ${jugada.san}: ${e.message}"
            actual.release()
        } finally {
            ocupado = false
        }
    }

    /** Muestra la jugada registrada grande y en verde en el centro de la pantalla, sin sonido. */
    private fun avisar(san: String) {
        aviso.animate().cancel()
        aviso.text = san
        aviso.alpha = 1f
        aviso.visibility = View.VISIBLE
        aviso.animate().alpha(0f).setStartDelay(1800).setDuration(500)
            .withEndAction { aviso.visibility = View.GONE }.start()
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

    private companion object {
        const val MOVIMIENTO_CUADRO = 3.5      // diferencia media de gris entre cuadros que indica movimiento (mano)
        const val CUADROS_QUIETOS = 3          // ~1 s quieto (cuadros cada 350 ms) para evaluar
        const val MAX_CASILLAS_CAMBIADAS = 8   // más casillas distintas = algo sigue tapando el tablero
    }

    override fun onDestroy() {
        soltarReferencias()
        super.onDestroy()
    }
}
