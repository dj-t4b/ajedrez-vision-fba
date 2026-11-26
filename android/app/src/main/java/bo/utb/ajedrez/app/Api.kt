package bo.utb.ajedrez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ErrorApi(mensaje: String) : Exception(mensaje)

/** Cliente mínimo de la API REST (la misma que usa el panel web). Llamar fuera del hilo principal. */
class Api(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    var servidor: String
        get() = prefs.getString("servidor", "http://192.168.1.10:8080/ajedrez")!!
        set(v) = prefs.edit().putString("servidor", v.trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    fun login(email: String, password: String): JSONObject {
        val r = JSONObject(pedir("POST", "/api/auth/login", JSONObject().put("email", email).put("password", password)))
        if (r.getString("rol") == "JUGADOR") throw ErrorApi("Solo árbitros y administradores registran jugadas")
        token = r.getString("token")
        return r
    }

    fun partidasEnCurso() = JSONArray(pedir("GET", "/api/partidas?estado=EN_CURSO"))

    fun partida(id: Long) = JSONObject(pedir("GET", "/api/partidas/$id"))

    fun registrarJugada(id: Long, uci: String, confianza: Double?) = JSONObject(
        pedir("POST", "/api/partidas/$id/jugadas", JSONObject()
            .put("uci", uci)
            .put("origen", if (confianza == null) "MANUAL" else "VISION")
            .put("confianza", confianza ?: JSONObject.NULL))
    )

    fun deshacer(id: Long) = JSONObject(pedir("DELETE", "/api/partidas/$id/jugadas/ultima"))

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
            token?.let { con.setRequestProperty("Authorization", "Bearer $it") }
            if (cuerpo != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json")
                con.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
            }
            val codigo = con.responseCode
            val texto = (if (codigo < 400) con.inputStream else con.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (codigo == 401 && ruta != "/api/auth/login") token = null
            if (codigo >= 400) {
                val detalle = runCatching { JSONObject(texto).optString("detail") }.getOrNull()
                throw ErrorApi(if (detalle.isNullOrBlank()) "Error $codigo del servidor" else detalle)
            }
            return texto
        } catch (e: IOException) {
            throw ErrorApi("No hay conexión con $servidor. ¿El celular está en la misma red que el PC?")
        } finally {
            con.disconnect()
        }
    }
}
