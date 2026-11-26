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

/** Partidas en curso; las partidas se crean desde el panel web. */
class PartidasActivity : ComponentActivity() {

    private val ids = mutableListOf<Long>()
    private lateinit var adaptador: ArrayAdapter<String>

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        adaptador = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val lista = ListView(this).apply {
            adapter = adaptador
            setPadding(16, 48, 16, 16)
            setOnItemClickListener { _, _, pos, _ ->
                startActivity(Intent(this@PartidasActivity, CamaraActivity::class.java).putExtra("id", ids[pos]))
            }
        }
        setContentView(lista)
    }

    override fun onResume() {
        super.onResume()
        val api = Api(this)
        lifecycleScope.launch {
            try {
                val ps = withContext(Dispatchers.IO) { api.partidasEnCurso() }
                ids.clear()
                adaptador.clear()
                for (i in 0 until ps.length()) {
                    val p = ps.getJSONObject(i)
                    ids += p.getLong("id")
                    adaptador.add("${p.getString("blancas")} – ${p.getString("negras")}  ·  ${p.optString("torneo", "")}")
                }
                if (ps.length() == 0) adaptador.add("No hay partidas en curso. Créelas en el panel web.")
            } catch (e: ErrorApi) {
                Toast.makeText(this@PartidasActivity, e.message, Toast.LENGTH_LONG).show()
                if (api.token == null) {
                    startActivity(Intent(this@PartidasActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }
}
