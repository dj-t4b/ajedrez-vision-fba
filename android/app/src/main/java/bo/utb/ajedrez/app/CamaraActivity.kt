package bo.utb.ajedrez.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
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
 * Pantalla del celular de una mesa: queda fijo (inclinado) frente al tablero. Muestra su código y su
 * mesa, toma sola la partida que el árbitro principal carga para esa mesa, ubica el tablero en la
 * posición inicial y registra cada jugada cuando la imagen queda quieta.
 */
class CamaraActivity : ComponentActivity() {

    private lateinit var api: Api
    private lateinit var vista: PreviewView
    private lateinit var marcas: Marcas
    private lateinit var identidad: TextView
    private lateinit var estado: TextView
    private lateinit var aviso: TextView
    private var camara: Camera? = null

    private var partidaId: Long? = null
    private var fen = ""
    private var referencia: Mat? = null          // tablero enderezado de la última posición confirmada
    private var referenciaAnterior: Mat? = null  // para volver atrás si se deshace una jugada
    private var calibrando = false
    private var buscando = false
    private var ocupado = false                 // diálogo abierto o jugada enviándose

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamara() else estado.text = "Sin permiso de cámara no se pueden leer jugadas."
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(guardado: Bundle?) {
        super.onCreate(guardado)
        setContentView(R.layout.camara)
        api = Api(this)
        vista = findViewById(R.id.vista)
        marcas = findViewById(R.id.marcas)
        identidad = findViewById(R.id.identidad)
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
            reiniciarDeteccion("Buscando el tablero en posición inicial…")
            true
        }
        findViewById<Button>(R.id.deshacer).setOnClickListener { deshacer() }
        findViewById<Button>(R.id.terminar).setOnClickListener { terminar() }

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
        seguirEstado()
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
     * Cada 2,5 s pregunta al servidor su mesa y la partida en curso en ella. Así toma sola la partida
     * nueva de la mesa y se entera si un árbitro corrigió una jugada desde la web.
     */
    private fun seguirEstado() = lifecycleScope.launch {
        while (isActive) {
            try {
                val e = withContext(Dispatchers.IO) { api.estado() }
                identidad.text = "${e.getString("codigo")} · Mesa ${e.getInt("mesa")}"
                if (!ocupado) aplicarEstado(e.optJSONObject("partida"), e.getInt("mesa"))
            } catch (ex: ErrorApi) {
                if (ex.desvinculado) return@launch volverAVincular()
                estado.text = ex.message
            }
            delay(2500)
        }
    }

    private fun aplicarEstado(detalle: JSONObject?, mesa: Int) {
        if (detalle == null) {
            if (partidaId != null) reiniciarDeteccion("")
            partidaId = null
            estado.text = "Esperando que el árbitro principal cargue la partida de la mesa $mesa."
            return
        }
        val p = detalle.getJSONObject("partida")
        val id = p.getLong("id")
        if (id != partidaId) {
            // Partida nueva en esta mesa: empieza en la posición inicial, se vuelve a ubicar el tablero
            partidaId = id
            fen = p.getString("fenActual")
            reiniciarDeteccion("")
        } else if (p.getString("fenActual") != fen) {
            // Un árbitro corrigió desde la web: la foto actual pasa a ser la posición de referencia
            Log.i("Ajedrez", "posición corregida desde la web")
            fen = p.getString("fenActual")
            capturar()?.let { nueva -> soltarReferencias(); referencia = nueva }
        }
        mostrar(detalle)
    }

    private fun volverAVincular() {
        startActivity(Intent(this, VincularActivity::class.java))
        finish()
    }

    private fun reiniciarDeteccion(mensaje: String) {
        calibrando = false
        marcas.limpiar()
        soltarReferencias()
        if (mensaje.isNotEmpty()) estado.text = mensaje
    }

    /**
     * Mientras el tablero no esté ubicado y haya una partida, lo busca en el video cada 0,7 s.
     * Necesita la posición inicial; una vez ubicado, el celular debe quedar quieto.
     */
    private fun buscarTableroAuto() = lifecycleScope.launch {
        while (isActive) {
            delay(700)
            if (partidaId == null || calibrando || referencia != null || buscando) continue
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
            else "$motivo Las jugadas se registran solas."
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

    private fun capturar(): Mat? {
        val foto = vista.bitmap ?: return null
        if (marcas.esquinas.size != 4) return null
        return Vision.enderezar(foto, marcas.esquinas)
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
            if (referencia == null || partidaId == null || calibrando || ocupado) {
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
                if (evaluar(actual)) huboMovimiento = false
            } else {
                actual.release()
            }
        }
        previo?.release()
    }

    /**
     * Deduce la jugada de [actual] contra la referencia y registra siempre la más probable con su
     * confianza; las dudosas quedan marcadas en la web para que el árbitro las corrija. Se queda con [actual].
     * @return false si parece que algo todavía tapa el tablero
     */
    private fun evaluar(actual: Mat): Boolean {
        val ref = referencia ?: run { actual.release(); return true }
        val cambios = Vision.cambios(ref, actual, Geometria.ladoCercano(marcas.esquinas))
        if (cambios.count { it > Deduccion.UMBRAL_MOVIMIENTO } > MAX_CASILLAS_CAMBIADAS) {
            actual.release()
            return false
        }
        val r = Deduccion.deducir(fen, cambios)
        Log.i("Ajedrez", "candidatas=${r.candidatas} confianza=${r.confianza}")
        Log.i("Ajedrez", "cambios a8..h1:\n" + cambios.toList().chunked(8).joinToString("\n") { f -> f.joinToString(" ") { "%3.0f".format(it) } })
        // Tableros enderezados antes/después, para revisar la lectura desde el PC con adb pull
        getExternalFilesDir(null)?.let { dir ->
            Imgcodecs.imwrite(File(dir, "antes.png").path, ref)
            Imgcodecs.imwrite(File(dir, "despues.png").path, actual)
        }
        val mejor = r.mejor
        if (mejor == null) {
            actual.release() // la mano pasó sin mover nada
            return true
        }
        enviar(mejor, r.confianza, actual)
        return true
    }

    private fun enviar(jugada: Deduccion.Candidata, confianza: Double, actual: Mat) = lifecycleScope.launch {
        ocupado = true
        try {
            val d = withContext(Dispatchers.IO) { api.registrarJugada(jugada.uci, confianza) }
            fen = d.getJSONObject("partida").getString("fenActual")
            mostrar(d)
            referenciaAnterior?.release()
            referenciaAnterior = referencia
            referencia = actual
            avisar(jugada.san, dudosa = confianza < 0.5)
        } catch (e: ErrorApi) {
            estado.text = "No se registró ${jugada.san}: ${e.message}"
            actual.release()
        } finally {
            ocupado = false
        }
    }

    /** Muestra la jugada registrada grande en el centro, sin sonido: verde, o ámbar si quedó para revisar. */
    private fun avisar(san: String, dudosa: Boolean) {
        aviso.animate().cancel()
        aviso.text = if (dudosa) "$san ?" else san
        aviso.setTextColor(if (dudosa) 0xFFFBBF24.toInt() else 0xFF4ADE80.toInt())
        aviso.alpha = 1f
        aviso.visibility = View.VISIBLE
        aviso.animate().alpha(0f).setStartDelay(1800).setDuration(500)
            .withEndAction { aviso.visibility = View.GONE }.start()
    }

    private fun deshacer() = lifecycleScope.launch {
        ocupado = true
        try {
            val d = withContext(Dispatchers.IO) { api.deshacer() }
            fen = d.getJSONObject("partida").getString("fenActual")
            mostrar(d)
            referenciaAnterior?.let { referencia?.release(); referencia = it; referenciaAnterior = null }
        } catch (e: ErrorApi) {
            estado.text = e.message
        } finally {
            ocupado = false
        }
    }

    /** «Terminar partida»: elegir el resultado y confirmar, para no cerrar por un toque accidental. */
    private fun terminar() {
        if (partidaId == null) { estado.text = "No hay una partida en curso en esta mesa."; return }
        val resultados = arrayOf("1-0", "0-1", "1/2-1/2")
        val textos = arrayOf("1-0  (ganan blancas)", "0-1  (ganan negras)", "½-½  (tablas)")
        AlertDialog.Builder(this)
            .setTitle("Terminar la partida")
            .setItems(textos) { _, i ->
                AlertDialog.Builder(this)
                    .setMessage("¿Terminar la partida con ${textos[i].substringBefore("  ")}?")
                    .setPositiveButton("Terminar") { _, _ -> enviarResultado(resultados[i]) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarResultado(resultado: String) = lifecycleScope.launch {
        try {
            val d = withContext(Dispatchers.IO) { api.terminar(resultado) }
            mostrar(d)
            avisar(d.getJSONObject("partida").getString("resultado").replace("1/2-1/2", "½-½"), dudosa = false)
        } catch (e: ErrorApi) {
            estado.text = e.message
        }
    }

    private fun mostrar(detalle: JSONObject) {
        val p = detalle.getJSONObject("partida")
        val jugadas = detalle.getJSONArray("jugadas")
        val ultima = if (jugadas.length() > 0) jugadas.getJSONObject(jugadas.length() - 1).getString("san") else "—"
        val turno = if (p.getString("fenActual").split(" ")[1] == "w") "blancas" else "negras"
        val pareja = "${p.getString("blancas")} – ${p.getString("negras")}"
        estado.text = when {
            p.getString("estado") == "FINALIZADA" -> "$pareja: partida terminada ${p.getString("resultado")}"
            referencia == null && !calibrando -> "$pareja\nArme la posición inicial: el tablero se ubica solo."
            else -> "$pareja · última: $ultima · juegan $turno"
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
