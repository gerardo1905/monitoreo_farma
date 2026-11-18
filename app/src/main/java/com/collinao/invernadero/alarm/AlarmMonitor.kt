package com.collinao.invernadero.alarm

import android.content.Context
import android.content.Intent
import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.R
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.SensorData
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.data.repository.DeviceRepository
import com.collinao.invernadero.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Vigila los sensores en tiempo real en CADA teléfono con sesión abierta y dispara la alarma
 * cuando un valor sale de los límites de alarma.
 *
 * Ciclo de una emergencia:
 *  - Al cruzar el límite suena la alarma. Desde ese momento el usuario tiene 2 minutos para
 *    activar el equipo de emergencia (bomba si es temperatura, ventilador si es humedad) desde la
 *    pantalla de alarma. Si no lo hace, el simulador/dispositivo lo enciende solo.
 *  - Si pasados 2 minutos los valores siguen fuera de límites, la alarma VUELVE A SONAR, y así
 *    cada 2 minutos hasta que el problema se solucione (aunque el usuario la haya silenciado).
 *  - Ignora lecturas viejas (más de 60 s): si el dispositivo está apagado no suena por un dato antiguo.
 */
class AlarmMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        /**
         * FALLA SEGURA (fail-safe): una lectura de más de 60 s se considera vieja y NO dispara la
         * alarma. Si el ESP32 se apaga o pierde conexión, no se actúa con un dato antiguo.
         */
        const val LECTURA_VIGENTE_MS = 60_000L

        /** Tiempo para atender la alarma y también intervalo entre alertas repetidas. */
        const val REALERTA_MS = 120_000L
    }

    private var job: Job? = null

    // Estado del episodio actual (0 = no hay emergencia)
    private var episodioInicio = 0L
    private var ultimaAlerta = 0L

    /** Resultado de evaluar los sensores: el motivo y el equipo de emergencia (null si no hay uno que ayude). */
    private data class Evaluacion(val motivo: String, val equipoEmergencia: String?)

    fun start() {
        if (job != null) return
        val repo = DeviceRepository()

        // Un "reloj" que obliga a reevaluar cada 5 s, para repetir la alerta aunque no lleguen datos nuevos
        val reloj: Flow<Long> = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(5000)
            }
        }

        job = scope.launch {
            combine(
                repo.observarSensores(),
                repo.observarThresholds(),
                repo.observarOffsetServidor(),
                reloj
            ) { sensor, umbrales, offset, ahora ->
                val edad = ahora + offset - sensor.timestamp
                if (sensor.timestamp == 0L || edad > LECTURA_VIGENTE_MS) null else evaluar(sensor, umbrales)
            }
                // FALLA SEGURA (fail-safe): un error de red o de permisos no cierra la app
                .catch { /* sin permiso o sin red: se reintenta al próximo inicio de sesión */ }
                .collect { evaluacion -> procesar(evaluacion) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        episodioInicio = 0L
        ultimaAlerta = 0L
        AlarmPlayer.detener(context)
        NotificationHelper.cancelarAlarma(context)
    }

    private fun procesar(evaluacion: Evaluacion?) {
        val ahora = System.currentTimeMillis()

        if (evaluacion == null) {
            // Todo volvió a la normalidad: se cierra el episodio
            episodioInicio = 0L
            ultimaAlerta = 0L
            return
        }

        if (episodioInicio == 0L) {
            // Primera alerta del episodio
            episodioInicio = ahora
            ultimaAlerta = ahora
            disparar(evaluacion)
        } else if (ahora - ultimaAlerta >= REALERTA_MS) {
            // Sigue sin resolverse: nueva alerta (aunque la anterior se haya silenciado)
            ultimaAlerta = ahora
            disparar(evaluacion)
        }
    }

    private fun disparar(e: Evaluacion) {
        // Notificación de alarma (funciona también con la app en segundo plano y el teléfono bloqueado)
        NotificationHelper.mostrarAlarma(context, e.motivo)

        // Si la app está abierta, se muestra directamente la pantalla de alarma
        val app = context.applicationContext as? InvernaderoApp
        if (app?.enPrimerPlano == true) {
            val intent = Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(AlarmActivity.EXTRA_MOTIVO, e.motivo)
                putExtra(AlarmActivity.EXTRA_INICIO, episodioInicio)
                putExtra(AlarmActivity.EXTRA_EQUIPO, e.equipoEmergencia)
            }
            try {
                context.startActivity(intent)
            } catch (ex: Exception) {
                // La notificación ya está activa
            }
        }
    }

    private fun evaluar(s: SensorData, u: Thresholds): Evaluacion? = when {
        s.temperatura > u.alertaTemperaturaMax -> Evaluacion(
            context.getString(R.string.alarma_temp_muy_alta, s.temperatura, u.alertaTemperaturaMax), ActuadorId.BOMBA
        )
        s.temperatura < u.alertaTemperaturaMin -> Evaluacion(
            context.getString(R.string.alarma_temp_muy_baja, s.temperatura, u.alertaTemperaturaMin), null
        )
        s.humedadAire > u.alertaHumedadMax -> Evaluacion(
            context.getString(R.string.alarma_hum_muy_alta, s.humedadAire, u.alertaHumedadMax), ActuadorId.VENTILADOR
        )
        s.humedadAire < u.alertaHumedadMin -> Evaluacion(
            context.getString(R.string.alarma_hum_muy_baja, s.humedadAire, u.alertaHumedadMin), null
        )
        else -> null
    }
}
