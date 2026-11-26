package bo.utb.ajedrez.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        val api = Api(this)
        if (api.token != null) return abrirPartidas()

        setContentView(R.layout.login)
        val servidor = findViewById<EditText>(R.id.servidor).apply { setText(api.servidor) }
        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val error = findViewById<TextView>(R.id.error)
        val entrar = findViewById<Button>(R.id.entrar)

        entrar.setOnClickListener {
            api.servidor = servidor.text.toString().trim()
            entrar.isEnabled = false
            error.visibility = View.GONE
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { api.login(email.text.toString().trim(), password.text.toString()) }
                    abrirPartidas()
                } catch (e: ErrorApi) {
                    error.text = e.message
                    error.visibility = View.VISIBLE
                    entrar.isEnabled = true
                }
            }
        }
    }

    private fun abrirPartidas() {
        startActivity(Intent(this, PartidasActivity::class.java))
        finish()
    }
}
