package bo.utb.ajedrez.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ErrorApi(mensaje: String, val desvinculado: Boolean = false) : Exception(mensaje)

/**
 * Cliente del celular de una mesa. No hay cuenta de usuario: el celular se vincula a un torneo con el
 * QR o el PIN que muestra la web y desde ahí usa su propio token. Llamar fuera del hilo principal.
 */
class Api(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("dispositivo", Context.MODE_PRIVATE)

    var servidor: String
        get() = prefs.getString("servidor", "http://localhost:8080/ajedrez")!!
        set(v) = prefs.edit().putString("servidor", v.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    /** Canjea el PIN por un token de este celular. */
    fun vincular(pin: String): JSONObject {
        val r = JSONObject(pedir("POST", "/api/dispositivo/vincular", JSONObject().put("pin", pin.trim())))
        token = r.getString("token")
        return r
    }

    /** Código, mesa y la partida en curso de la mesa (o null). */
    fun estado() = JSONObject(pedir("GET", "/api/dispositivo/estado"))

    fun registrarJugada(uci: String, confianza: Double) =
        JSONObject(pedir("POST", "/api/dispositivo/jugadas", JSONObject().put("uci", uci).put("confianza", confianza)))

    fun deshacer() = JSONObject(pedir("DELETE", "/api/dispositivo/jugadas/ultima"))

    fun terminar(resultado: String) =
        JSONObject(pedir("POST", "/api/dispositivo/resultado", JSONObject().put("resultado", resultado)))

    private fun pedir(metodo: String, ruta: String, cuerpo: JSONObject? = null): String {
        val con = try {
            URL(servidor + ruta).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ErrorApi("Dirección del servidor inválida: $servidor")
        }
        try {
            con.requestMethod = metodo
            con.connectTimeout = 5000
            con.readTimeout = 10000
            token?.let { con.setRequestProperty("X-Dispositivo", it) }
            if (cuerpo != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json")
                con.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
            }
            val codigo = con.responseCode
            val texto = (if (codigo < 400) con.inputStream else con.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (codigo >= 400) {
                val detalle = runCatching { JSONObject(texto).optString("detail") }.getOrNull()
                val desvinculado = codigo == 401 && ruta != "/api/dispositivo/vincular"
                if (desvinculado) token = null
                throw ErrorApi(if (detalle.isNullOrBlank()) "Error $codigo del servidor" else detalle, desvinculado)
            }
            return texto
        } catch (e: IOException) {
            throw ErrorApi("No hay conexión con $servidor. ¿El celular está en la misma red que el servidor?")
        } finally {
            con.disconnect()
        }
    }

    companion object {
        /** El QR de la web: ajedrezfba://vincular?s=<servidor>&p=<pin>. Devuelve (servidor, pin) o null. */
        fun leerQr(texto: String): Pair<String, String>? {
            if (!texto.startsWith("ajedrezfba://vincular")) return null
            val uri = Uri.parse(texto)
            val s = uri.getQueryParameter("s") ?: return null
            val p = uri.getQueryParameter("p") ?: return null
            return s to p
        }
    }
}
