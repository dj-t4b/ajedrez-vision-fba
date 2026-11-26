package bo.utb.ajedrez.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.objdetect.QRCodeDetector

/**
 * Primera pantalla: el celular no inicia sesión, se vincula a un torneo escaneando el QR de la web
 * (trae servidor y PIN) o escribiendo el PIN. Ya vinculado, va directo a la cámara.
 */
class VincularActivity : ComponentActivity() {

    private lateinit var api: Api
    private lateinit var vista: PreviewView
    private lateinit var servidor: EditText
    private lateinit var pin: EditText
    private lateinit var error: TextView
    private lateinit var boton: Button
    private var vinculando = false

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamara()
    }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        api = Api(this)
        if (api.token != null) return abrirCamara()

        setContentView(R.layout.vincular)
        vista = findViewById(R.id.vista)
        servidor = findViewById<EditText>(R.id.servidor).apply { setText(api.servidor) }
        pin = findViewById(R.id.pin)
        error = findViewById(R.id.error)
        boton = findViewById(R.id.vincular)
        boton.setOnClickListener { vincular(servidor.text.toString(), pin.text.toString()) }

        OpenCVLoader.initLocal()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) iniciarCamara()
        else pedirCamara.launch(Manifest.permission.CAMERA)
        buscarQr()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val preview = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            futuro.get().apply { unbindAll(); bindToLifecycle(this@VincularActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview) }
        }, mainExecutor)
    }

    /** Busca el QR en el video dos veces por segundo con el detector de OpenCV. */
    private fun buscarQr() = lifecycleScope.launch {
        val detector = QRCodeDetector()
        while (isActive) {
            delay(500)
            if (vinculando) continue
            val foto = vista.bitmap ?: continue
            val texto = withContext(Dispatchers.Default) {
                val m = Mat()
                Utils.bitmapToMat(foto, m)
                runCatching { detector.detectAndDecode(m) }.getOrNull().also { m.release() }
            }
            val qr = texto?.let { Api.leerQr(it) } ?: continue
            servidor.setText(qr.first)
            pin.setText(qr.second)
            vincular(qr.first, qr.second)
        }
    }

    private fun vincular(direccion: String, codigo: String) {
        if (vinculando) return
        vinculando = true
        boton.isEnabled = false
        error.visibility = View.GONE
        api.servidor = direccion
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { api.vincular(codigo) }
                abrirCamara()
            } catch (e: ErrorApi) {
                error.text = e.message
                error.visibility = View.VISIBLE
                boton.isEnabled = true
                delay(2000) // no reintentar el mismo QR en cada cuadro
                vinculando = false
            }
        }
    }

    private fun abrirCamara() {
        startActivity(Intent(this, CamaraActivity::class.java))
        finish()
    }
}
