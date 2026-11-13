package com.collinao.invernadero

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.collinao.invernadero.ui.navigation.NavGraph
import com.collinao.invernadero.ui.navigation.Routes
import com.collinao.invernadero.ui.theme.InvernaderoTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        /** Sin tocar la pantalla durante este tiempo (con la app abierta) se cierra la sesión. */
        const val INACTIVIDAD_MS = 10 * 60 * 1000L
    }

    private var ultimaInteraccion = SystemClock.elapsedRealtime()

    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* resultado ignorado */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fondo claro siempre: los iconos de las barras del sistema deben ser oscuros
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val concedido = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!concedido) {
                permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        pedirPermisoAlarmaPantallaCompleta()

        val app = application as InvernaderoApp
        val haySesion = Firebase.auth.currentUser != null
        if (haySesion) app.iniciarMonitoreo()

        vigilarInactividad(app)

        setContent {
            InvernaderoTheme {
                NavGraph(
                    startDestination = if (haySesion) Routes.DASHBOARD else Routes.LOGIN,
                    onSesionIniciada = { app.iniciarMonitoreo() },
                    onSesionCerrada = { app.detenerMonitoreo() }
                )
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        ultimaInteraccion = SystemClock.elapsedRealtime()
    }

    /**
     * Cierra la sesión si el usuario deja la app abierta y sin tocarla. Solo cuenta mientras la app
     * está en pantalla: si pasa a segundo plano, el monitoreo y la alarma siguen funcionando.
     */
    private fun vigilarInactividad(app: InvernaderoApp) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ultimaInteraccion = SystemClock.elapsedRealtime()
                while (true) {
                    delay(15_000)
                    val inactivo = SystemClock.elapsedRealtime() - ultimaInteraccion > INACTIVIDAD_MS
                    if (inactivo && Firebase.auth.currentUser != null) {
                        app.cerrarSesion("LOGOUT_INACTIVIDAD", "Sesión cerrada por inactividad")
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.sesion_cerrada_inactividad),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Android 14+ exige que el usuario permita las notificaciones a pantalla completa para que la
     * alarma pueda abrirse sobre la pantalla de bloqueo. Se pide una sola vez.
     */
    private fun pedirPermisoAlarmaPantallaCompleta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.canUseFullScreenIntent()) return

        val prefs = getSharedPreferences("ajustes_alarma", MODE_PRIVATE)
        if (prefs.getBoolean("permiso_pantalla_completa_pedido", false)) return
        prefs.edit().putBoolean("permiso_pantalla_completa_pedido", true).apply()

        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            // Sin pantalla de ajustes disponible: la alarma sigue funcionando como notificación
        }
    }
}
