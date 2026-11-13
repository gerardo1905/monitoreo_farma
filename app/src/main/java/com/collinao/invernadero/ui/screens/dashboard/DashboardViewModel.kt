package com.collinao.invernadero.ui.screens.dashboard

import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.model.DeviceStatus
import com.collinao.invernadero.data.model.EstadoActuador
import com.collinao.invernadero.data.model.SensorData
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.DeviceRepository
import com.collinao.invernadero.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val deviceRepository: DeviceRepository = DeviceRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    private companion object {
        /** Si no llega señal en este tiempo, el dispositivo se considera desconectado. */
        const val SIN_SENAL_MS = 15_000L
    }

    private val inicio = SharingStarted.WhileSubscribed(5000)

    val sensorData: StateFlow<SensorData> = deviceRepository.observarSensores()
        .catch { }
        .stateIn(viewModelScope, inicio, SensorData())

    val deviceStatus: StateFlow<DeviceStatus> = deviceRepository.observarEstado()
        .catch { }
        .stateIn(viewModelScope, inicio, DeviceStatus())

    val actuadores: StateFlow<Map<String, EstadoActuador>> = deviceRepository.observarActuadores()
        .catch { }
        .stateIn(viewModelScope, inicio, ActuadorId.todos.associateWith { EstadoActuador() })

    val umbrales: StateFlow<Thresholds> = deviceRepository.observarThresholds()
        .catch { }
        .stateIn(viewModelScope, inicio, Thresholds())

    // --- Estado de conexión real: se compara la última señal con la hora del SERVIDOR ---
    private val offsetServidor: StateFlow<Long> = deviceRepository.observarOffsetServidor()
        .catch { }
        .stateIn(viewModelScope, inicio, 0L)

    private val reloj: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(2000)
        }
    }

    /** Segundos desde la última señal del dispositivo (null si nunca ha enviado). */
    val segundosDesdeSenal: StateFlow<Long?> =
        combine(deviceStatus, offsetServidor, reloj) { estado, offset, ahora ->
            if (estado.ultimaConexion == 0L) null
            else ((ahora + offset - estado.ultimaConexion) / 1000).coerceAtLeast(0)
        }.stateIn(viewModelScope, inicio, null)

    val enLinea: StateFlow<Boolean> =
        combine(deviceStatus, segundosDesdeSenal) { estado, segundos ->
            estado.online && segundos != null && segundos * 1000 < SIN_SENAL_MS
        }.stateIn(viewModelScope, inicio, false)

    /** Segundos desde la última lectura de sensores (null si aún no hay). */
    val segundosDesdeLectura: StateFlow<Long?> =
        combine(sensorData, offsetServidor, reloj) { sensor, offset, ahora ->
            if (sensor.timestamp == 0L) null
            else ((ahora + offset - sensor.timestamp) / 1000).coerceAtLeast(0)
        }.stateIn(viewModelScope, inicio, null)

    /** Solo un administrador ve el botón de registros de auditoría. */
    val esAdmin: StateFlow<Boolean> = run {
        val uid = Firebase.auth.currentUser?.uid
        val flujo: Flow<Boolean> = if (uid != null) userRepository.observarEsAdmin(uid) else emptyFlow()
        flujo.catch { }.stateIn(viewModelScope, inicio, false)
    }

    val alertaMensaje: StateFlow<String?> =
        combine(sensorData, umbrales) { sensor, thresholds ->
            if (sensor.timestamp == 0L) null
            else when {
                sensor.temperatura > thresholds.temperaturaMax ->
                    InvernaderoApp.texto(R.string.alerta_temp_alta, sensor.temperatura, thresholds.temperaturaMax)
                sensor.temperatura < thresholds.temperaturaMin ->
                    InvernaderoApp.texto(R.string.alerta_temp_baja, sensor.temperatura, thresholds.temperaturaMin)
                sensor.humedadAire > thresholds.humedadAireMax ->
                    InvernaderoApp.texto(R.string.alerta_hum_alta, sensor.humedadAire, thresholds.humedadAireMax)
                sensor.humedadAire < thresholds.humedadAireMin ->
                    InvernaderoApp.texto(R.string.alerta_hum_baja, sensor.humedadAire, thresholds.humedadAireMin)
                else -> null
            }
        }.stateIn(viewModelScope, inicio, null)

    /** Encender/apagar a mano: el actuador pasa a modo manual y la automatización lo respeta. */
    fun cambiarEstado(id: String, encendido: Boolean) {
        deviceRepository.setActuador(id, encendido)
        auditRepository.registrar(
            AuditCategoria.DISPOSITIVOS,
            "${id.uppercase()}_${if (encendido) "ON" else "OFF"}",
            "${ActuadorId.nombre(id)} ${if (encendido) "encendido" else "apagado"} manualmente"
        )
    }
}
