package bo.utb.ajedrez.app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Sin extra "torneoId": lista de torneos. Con "torneoId": las partidas en curso de ese torneo.
 * Torneos y partidas se crean desde el panel web.
 */
class PartidasActivity : ComponentActivity() {

    private val ids = mutableListOf<Long>()
    private lateinit var adaptador: ArrayAdapter<String>
    private val torneoId by lazy { intent.getLongExtra("torneoId", 0L) }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        title = intent.getStringExtra("titulo") ?: "Torneos"
        adaptador = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val lista = ListView(this).apply {
            adapter = adaptador
            setPadding(16, 48, 16, 16)
            setOnItemClickListener { _, _, pos, _ ->
                if (pos >= ids.size) return@setOnItemClickListener
                val siguiente = if (torneoId == 0L) {
                    Intent(this@PartidasActivity, PartidasActivity::class.java)
                        .putExtra("torneoId", ids[pos]).putExtra("titulo", adaptador.getItem(pos))
                } else {
                    Intent(this@PartidasActivity, CamaraActivity::class.java).putExtra("id", ids[pos])
                }
                startActivity(siguiente)
            }
        }
        setContentView(lista)
    }

    override fun onResume() {
        super.onResume()
        val api = Api(this)
        lifecycleScope.launch {
            try {
                val filas = withContext(Dispatchers.IO) {
                    if (torneoId == 0L) api.torneos().objetos().map { t ->
                        val cat = t.optString("categoria").takeIf { it.isNotBlank() && it != "null" }
                        t.getLong("id") to "${t.getString("nombre")}${cat?.let { " · $it" } ?: ""}  (${t.getLong("enCurso")} en curso)"
                    } else api.partidasEnCurso(torneoId).objetos().map { p ->
                        val ronda = if (p.isNull("ronda")) "" else "Ronda ${p.getInt("ronda")}: "
                        p.getLong("id") to "$ronda${p.getString("blancas")} – ${p.getString("negras")}"
                    }
                }
                ids.clear()
                adaptador.clear()
                filas.forEach { (id, texto) -> ids += id; adaptador.add(texto) }
                if (filas.isEmpty()) adaptador.add(
                    if (torneoId == 0L) "No hay torneos. Créelos en el panel web." else "No hay partidas en curso en este torneo."
                )
            } catch (e: ErrorApi) {
                Toast.makeText(this@PartidasActivity, e.message, Toast.LENGTH_LONG).show()
                if (api.token == null) {
                    startActivity(Intent(this@PartidasActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun org.json.JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
