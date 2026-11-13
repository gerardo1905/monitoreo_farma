package com.collinao.invernadero.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.collinao.invernadero.R
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.DeviceRepository
import com.collinao.invernadero.notifications.NotificationHelper
import com.collinao.invernadero.ui.components.nombreActuador
import com.collinao.invernadero.ui.theme.AlarmaFondoAbajo
import com.collinao.invernadero.ui.theme.AlarmaFondoArriba
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Pantalla de alarma: se abre sobre la pantalla de bloqueo, enciende la pantalla y suena
 * en bucle hasta que el usuario pulsa "Silenciar alarma".
 *
 * Cuando la causa es un valor ALTO muestra además la cuenta atrás de 2 minutos: el usuario puede
 * activar el equipo de emergencia (bomba o ventilador) con un botón y, si no lo hace, el sistema lo enciende solo.
 */
class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MOTIVO = "motivo"
        /** Momento (ms, reloj del teléfono) en que empezó la emergencia. */
        const val EXTRA_INICIO = "inicio"
        /** Equipo que ayuda en esta emergencia (bomba o ventilador); sin valor si ninguno ayuda. */
        const val EXTRA_EQUIPO = "equipo"
    }

    private var motivo by mutableStateOf("")
    private var inicio by mutableLongStateOf(0L)
    private var equipo by mutableStateOf<String?>(null)
    private var equipoEncendido by mutableStateOf(false)

    private val deviceRepository = DeviceRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrar sobre el bloqueo y encender la pantalla (minSdk 28, estos métodos existen desde la 27)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        leerDatos(intent)

        // Desde aquí el sonido lo reproduce la pantalla; se quita la notificación insistente
        AlarmPlayer.iniciar(this)
        NotificationHelper.cancelarAlarma(this)

        // Estado del ventilador en tiempo real (para el botón y el mensaje)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                deviceRepository.observarActuadores()
                    .catch { }
                    .collect { equipoEncendido = equipo?.let { id -> it[id]?.encendido } == true }
            }
        }

        setContent {
            PantallaAlarma(
                motivo = motivo,
                inicio = inicio,
                equipo = equipo,
                equipoEncendido = equipoEncendido,
                alActivarEquipo = ::activarEquipo,
                alSilenciar = ::silenciar
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leerDatos(intent)
        // Alerta repetida: si se había silenciado, vuelve a sonar
        AlarmPlayer.iniciar(this)
        NotificationHelper.cancelarAlarma(this)
    }

    private fun leerDatos(i: Intent) {
        motivo = i.getStringExtra(EXTRA_MOTIVO) ?: getString(R.string.alarma_motivo_defecto)
        inicio = i.getLongExtra(EXTRA_INICIO, System.currentTimeMillis())
        equipo = i.getStringExtra(EXTRA_EQUIPO)
    }

    override fun onStop() {
        super.onStop()
        // Si el usuario sale sin silenciar, la alarma sigue; se deja la notificación para volver a ella
        if (AlarmPlayer.sonando && !isFinishing) {
            NotificationHelper.mostrarAlarma(this, motivo, silenciosa = true)
        }
    }

    private fun activarEquipo() {
        val id = equipo ?: return
        deviceRepository.setActuador(id, true)
        AuditRepository().registrar(
            AuditCategoria.DISPOSITIVOS, "${id.uppercase()}_ON",
            getString(R.string.alarma_equipo_activado_detalle, ActuadorId.nombre(id))
        )
    }

    private fun silenciar() {
        AlarmPlayer.detener(this)
        NotificationHelper.cancelarAlarma(this)
        AuditRepository().registrar(
            AuditCategoria.ALERTAS, "ALARMA_SILENCIADA", getString(R.string.alarma_silenciada_detalle, motivo)
        )
        finish()
    }
}

private const val ESPERA_EMERGENCIA_MS = AlarmMonitor.REALERTA_MS

@Composable
private fun PantallaAlarma(
    motivo: String,
    inicio: Long,
    equipo: String?,
    equipoEncendido: Boolean,
    alActivarEquipo: () -> Unit,
    alSilenciar: () -> Unit
) {
    // Reloj para la cuenta atrás
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            ahora = System.currentTimeMillis()
            delay(1000)
        }
    }
    val restanteMs = (ESPERA_EMERGENCIA_MS - (ahora - inicio)).coerceAtLeast(0)
    val cuenta = "%d:%02d".format(restanteMs / 60000, (restanteMs / 1000) % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AlarmaFondoArriba, AlarmaFondoAbajo)))
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Signo de exclamación dentro de un círculo translúcido
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "!", color = Color.White, fontSize = 66.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.alarma_titulo),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = motivo,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 19.sp,
            textAlign = TextAlign.Center
        )

        if (equipo != null) {
            val nombreEquipo = nombreActuador(equipo)
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.alarma_emergencia_titulo, nombreEquipo),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        equipoEncendido -> stringResource(R.string.alarma_equipo_activo, nombreEquipo)
                        restanteMs > 0 -> stringResource(R.string.alarma_equipo_cuenta, nombreEquipo, cuenta)
                        else -> stringResource(R.string.alarma_equipo_activando, nombreEquipo)
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                if (!equipoEncendido) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = alActivarEquipo,
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(stringResource(R.string.alarma_activar_equipo, nombreEquipo), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.alarma_instruccion),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = alSilenciar,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlarmaFondoAbajo),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
        ) {
            Text(stringResource(R.string.alarma_silenciar), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.alarma_se_repite),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
