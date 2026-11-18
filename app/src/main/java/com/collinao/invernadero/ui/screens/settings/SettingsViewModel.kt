package com.collinao.invernadero.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.R
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.model.EstadoActuador
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.DeviceRepository
import com.collinao.invernadero.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Cada umbral editable con los límites que aceptan las reglas de Firebase
 * (si se cambian aquí, hay que cambiarlos también en database.rules.json).
 */
enum class Umbral(val minimo: Float, val maximo: Float, val esTemperatura: Boolean) {
    // Temperatura: rango de medición del sensor DHT11 (0–50 °C)
    TEMP_MIN(0f, 50f, true),
    TEMP_MAX(0f, 50f, true),
    HUM_MIN(0f, 100f, false),
    HUM_MAX(0f, 100f, false),
    ALARMA_TEMP_MIN(-10f, 50f, true),
    ALARMA_TEMP_MAX(0f, 60f, true),
    ALARMA_HUM_MIN(0f, 100f, false),
    ALARMA_HUM_MAX(0f, 100f, false)
}

/**
 * Los umbrales los puede modificar SOLO un administrador (la app lo valida y, además,
 * las reglas de Firebase lo hacen cumplir). Los demás usuarios solo los ven.
 *
 * Los valores se escriben como números y se guardan al pulsar "Guardar cambios".
 */
class SettingsViewModel(
    private val deviceRepository: DeviceRepository = DeviceRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    var esAdmin by mutableStateOf(false); private set
    var automatizacionActiva by mutableStateOf(true); private set

    /** Texto que escribe el usuario en cada campo, y el error de cada uno (si lo hay). */
    val textos = mutableStateMapOf<Umbral, String>()
    val errores = mutableStateMapOf<Umbral, String>()

    var guardadoReciente by mutableStateOf(false); private set
    /** Error al guardar en Firebase (permiso denegado, sin red...). */
    var errorGuardado by mutableStateOf<String?>(null); private set
    /** Rol real que detectó la app, para mostrarlo en pantalla. */
    var rolDetectado by mutableStateOf<String?>(null); private set

    /** Modo de cada equipo: true = automático, false = manual. */
    val automaticos = mutableStateMapOf<String, Boolean>()
    private var estadosEquipos: Map<String, EstadoActuador> = emptyMap()

    private var guardado = Thresholds()
    private var cargadoUnaVez = false

    init {
        deviceRepository.observarThresholds()
            .catch { }
            .onEach { t ->
                val habiaCambios = hayCambios
                guardado = t
                automatizacionActiva = t.automatizacionActiva
                // No pisa lo que el usuario está escribiendo
                if (!cargadoUnaVez || !habiaCambios) cargarTextos(t)
                cargadoUnaVez = true
            }.launchIn(viewModelScope)

        deviceRepository.observarActuadores()
            .catch { }
            .onEach { mapa ->
                estadosEquipos = mapa
                mapa.forEach { (id, e) -> automaticos[id] = e.automatico }
            }.launchIn(viewModelScope)

        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            userRepository.observarPerfilPropio(uid)
                .catch { rolDetectado = "error" }
                .onEach { perfil ->
                    esAdmin = perfil.rol == "admin" && perfil.activo
                    rolDetectado = perfil.rol + if (perfil.activo) "" else " (inactiva)"
                }
                .launchIn(viewModelScope)
        }
    }

    private fun valorGuardado(u: Umbral): Float = when (u) {
        Umbral.TEMP_MIN -> guardado.temperaturaMin
        Umbral.TEMP_MAX -> guardado.temperaturaMax
        Umbral.HUM_MIN -> guardado.humedadAireMin
        Umbral.HUM_MAX -> guardado.humedadAireMax
        Umbral.ALARMA_TEMP_MIN -> guardado.alertaTemperaturaMin
        Umbral.ALARMA_TEMP_MAX -> guardado.alertaTemperaturaMax
        Umbral.ALARMA_HUM_MIN -> guardado.alertaHumedadMin
        Umbral.ALARMA_HUM_MAX -> guardado.alertaHumedadMax
    }

    private fun formato(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v).replace(',', '.')

    private fun cargarTextos(t: Thresholds) {
        Umbral.entries.forEach { textos[it] = formato(valorGuardado(it)) }
        errores.clear()
    }

    private fun analizar(texto: String?): Float? = texto?.trim()?.replace(',', '.')?.toFloatOrNull()

    /** true si hay algo escrito distinto de lo que está guardado. */
    val hayCambios: Boolean
        get() = esAdmin && Umbral.entries.any { analizar(textos[it]) != valorGuardado(it) }

    fun onCambio(umbral: Umbral, nuevo: String) {
        if (!esAdmin) return
        // Solo dígitos, signo menos y un separador decimal
        val limpio = nuevo.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        textos[umbral] = limpio
        errores.remove(umbral)
        guardadoReciente = false
    }

    fun onAutomatizacionChange(v: Boolean) {
        if (!esAdmin) return
        val anterior = automatizacionActiva
        automatizacionActiva = v
        errorGuardado = null
        deviceRepository.guardarThresholds(guardado.copy(automatizacionActiva = v)) { error ->
            if (error == null) {
                // Al activar la automatización, ningún equipo debe quedar olvidado en modo manual
                if (v) deviceRepository.todosAutomaticos(estadosEquipos.mapValues { it.value.encendido })
                auditRepository.registrar(
                    AuditCategoria.CONFIGURACION, "AUTOMATIZACION_CAMBIADA",
                    "Automatización ${if (v) "activada" else "desactivada"}"
                )
            } else {
                automatizacionActiva = anterior
                errorGuardado = mensajeDeError(error)
            }
        }
    }

    /** Cambia el modo de un equipo (automático/manual). Solo el administrador. */
    fun onModoChange(id: String, automatico: Boolean) {
        if (!esAdmin) return
        automaticos[id] = automatico
        if (automatico) {
            deviceRepository.setModoAutomatico(id, estadosEquipos[id]?.encendido ?: false)
        } else {
            // Pasa a manual conservando su estado actual (encendido/apagado)
            deviceRepository.setActuador(id, estadosEquipos[id]?.encendido ?: false)
        }
        auditRepository.registrar(
            AuditCategoria.CONFIGURACION,
            if (automatico) "MODO_AUTOMATICO" else "MODO_MANUAL",
            "${ActuadorId.nombre(id)} pasa a modo ${if (automatico) "automático" else "manual"}"
        )
    }

    private fun mensajeDeError(e: Throwable): String =
        if (e.message?.contains("permission", ignoreCase = true) == true)
            InvernaderoApp.texto(R.string.ajustes_error_permiso)
        else InvernaderoApp.texto(R.string.ajustes_error_guardar)

    fun descartar() {
        cargarTextos(guardado)
    }

    /** Valida todos los campos. Devuelve los valores o null si hay errores (que se muestran en cada campo). */
    private fun validar(): Map<Umbral, Float>? {
        errores.clear()
        val valores = mutableMapOf<Umbral, Float>()
        Umbral.entries.forEach { u ->
            val v = analizar(textos[u])
            when {
                v == null -> errores[u] = InvernaderoApp.texto(R.string.ajustes_error_numero)
                v < u.minimo || v > u.maximo -> errores[u] = InvernaderoApp.texto(
                    R.string.ajustes_error_rango, formato(u.minimo), formato(u.maximo)
                )
                else -> valores[u] = v
            }
        }
        // Cada mínimo debe ser menor que su máximo
        fun par(min: Umbral, max: Umbral) {
            val a = valores[min]
            val b = valores[max]
            if (a != null && b != null && a >= b) errores[max] = InvernaderoApp.texto(R.string.ajustes_error_mayor_que_minimo)
        }
        par(Umbral.TEMP_MIN, Umbral.TEMP_MAX)
        par(Umbral.HUM_MIN, Umbral.HUM_MAX)
        par(Umbral.ALARMA_TEMP_MIN, Umbral.ALARMA_TEMP_MAX)
        par(Umbral.ALARMA_HUM_MIN, Umbral.ALARMA_HUM_MAX)
        return if (errores.isEmpty()) valores else null
    }

    fun guardar() {
        if (!esAdmin) return
        val v = validar() ?: return

        val nuevo = Thresholds(
            temperaturaMax = v.getValue(Umbral.TEMP_MAX),
            temperaturaMin = v.getValue(Umbral.TEMP_MIN),
            humedadAireMax = v.getValue(Umbral.HUM_MAX),
            humedadAireMin = v.getValue(Umbral.HUM_MIN),
            automatizacionActiva = automatizacionActiva,
            alertaTemperaturaMax = v.getValue(Umbral.ALARMA_TEMP_MAX),
            alertaTemperaturaMin = v.getValue(Umbral.ALARMA_TEMP_MIN),
            alertaHumedadMax = v.getValue(Umbral.ALARMA_HUM_MAX),
            alertaHumedadMin = v.getValue(Umbral.ALARMA_HUM_MIN)
        )
        errorGuardado = null
        val anterior = guardado
        deviceRepository.guardarThresholds(nuevo) { error ->
            if (error != null) {
                // Firebase lo rechazó: se vuelve a los valores anteriores y se explica el motivo
                errorGuardado = mensajeDeError(error)
                guardado = anterior
                cargarTextos(anterior)
                guardadoReciente = false
            }
        }
        guardado = nuevo
        cargarTextos(nuevo)

        auditRepository.registrar(
            AuditCategoria.CONFIGURACION, "UMBRALES_MODIFICADOS",
            "Auto: temp %.1f–%.1f °C, hum %.0f–%.0f %% · Alerta: temp %.1f–%.1f °C, hum %.0f–%.0f %% · Automatización: %s".format(
                nuevo.temperaturaMin, nuevo.temperaturaMax, nuevo.humedadAireMin, nuevo.humedadAireMax,
                nuevo.alertaTemperaturaMin, nuevo.alertaTemperaturaMax, nuevo.alertaHumedadMin, nuevo.alertaHumedadMax,
                if (automatizacionActiva) "activa" else "inactiva"
            )
        )

        guardadoReciente = true
        viewModelScope.launch {
            delay(2500)
            guardadoReciente = false
        }
    }
}
