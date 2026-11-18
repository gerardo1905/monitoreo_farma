package com.collinao.invernadero.data.simulator

import android.content.Context
import com.collinao.invernadero.data.DeviceConfig
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.model.SensorData
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.DeviceRepository
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Simula el dispositivo (sensor DHT11 + bomba + ventilador + bocina + LED) escribiendo en Firebase.
 *
 * - Solo UN teléfono a la vez hace de "dispositivo simulado" (liderazgo en estado/simulador).
 * - Respeta el modo de cada actuador: si está en "manual", la automatización no lo toca.
 * - La alarma sonora del teléfono NO se dispara aquí sino en AlarmMonitor (suena en todos los teléfonos).
 */
class SimulatorEngine(
    private val deviceRef: DatabaseReference,
    private val scope: CoroutineScope,
    @Suppress("unused") private val context: Context
) {
    private companion object {
        const val LIDERAZGO_VIGENCIA_MS = 12_000L
        const val INTERVALO_LECTURA_MS = 60_000L
        const val LECTURAS_POR_DIA = 1440

        /** Tiempo que se le da al usuario para atender una alarma crítica antes de actuar solos. */
        const val EMERGENCIA_ESPERA_MS = 120_000L

        /** Histéresis: el equipo se apaga un poco DEBAJO del umbral para no prender/apagar sin parar. */
        const val MARGEN_BOMBA = 1.0
        const val MARGEN_VENTILADOR = 5.0
    }

    private val audit = AuditRepository()
    private val deviceId = deviceRef.key ?: DeviceConfig.DEVICE_ID
    private val lecturasRef = deviceRef.child("lecturas")
    private val instanciaId = UUID.randomUUID().toString()

    private var esLider = false
    private var offsetServidor = 0L
    private var ultimaLecturaMs = 0L

    private var job: Job? = null
    private var offsetJob: Job? = null
    private var running = false

    private var temperatura = 5.0
    private var humedadAire = 50.0
    private var alertaCriticaActiva = false

    // Emergencia: cuándo empezó el estado crítico por valores ALTOS (por equipo) y qué equipos están en emergencia
    private val criticoDesde = mutableMapOf<String, Long>()
    private val equiposForzados = mutableSetOf<String>()

    fun start() {
        // Con el ESP32 real el simulador no corre: el dispositivo físico escribe sensores,
        // estado y lecturas, y ejecuta la automatización (ver sketch de Arduino).
        if (!DeviceConfig.USAR_SIMULADOR) return
        if (running) return
        running = true

        offsetJob = scope.launch {
            DeviceRepository(deviceId).observarOffsetServidor()
                .catch { }
                .collect { offsetServidor = it }
        }

        job = scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                    // Falla momentánea de red: no matamos el loop
                }
                delay(3000)
            }
        }
    }

    private fun ahoraServidor(): Long = System.currentTimeMillis() + offsetServidor

    /**
     * Con varios teléfonos con sesión abierta, solo UNO hace de "dispositivo simulado" a la vez.
     * El líder renueva el contrato (estado/simulador) en cada ciclo; si otro lo tiene vigente,
     * este teléfono no simula y solo muestra datos. Si el líder se cae, otro toma el relevo.
     */
    private suspend fun obtenerLiderazgo(): Boolean {
        val ref = deviceRef.child("estado").child("simulador")
        val snap = ref.get().await()
        val duenio = snap.child("id").getValue(String::class.java)
        val ultimo = snap.child("ts").getValue(Long::class.java) ?: 0L
        val ahora = ahoraServidor()

        if (duenio != null && duenio != instanciaId && ahora - ultimo < LIDERAZGO_VIGENCIA_MS) {
            esLider = false
            return false
        }

        if (!esLider) {
            // Toma el relevo continuando desde los últimos valores publicados
            val sensores = deviceRef.child("sensores").get().await().getValue(SensorData::class.java)
            if (sensores != null && sensores.timestamp != 0L) {
                temperatura = sensores.temperatura
                humedadAire = sensores.humedadAire
            }
            esLider = true
            deviceRef.child("estado").child("online").setValue(true)
            registrarEvento(AuditCategoria.DISPOSITIVOS, "DISPOSITIVO_EN_LINEA", "El dispositivo simulado se conectó")
        }
        ref.setValue(mapOf("id" to instanciaId, "ts" to ahora))
        return true
    }

    private suspend fun tick() {
        if (!obtenerLiderazgo()) return

        // Se leen los nodos hijos (las reglas dan lectura por nodo, no sobre todo el dispositivo)
        val comandos = deviceRef.child("comandos").get().await()
        val modos = deviceRef.child("modos").get().await()
        val thresholds = deviceRef.child("thresholds").get().await()
            .getValue(Thresholds::class.java) ?: Thresholds()

        val estados = ActuadorId.todos.associateWith {
            comandos.child(it).getValue(Boolean::class.java) ?: false
        }.toMutableMap()

        // La emergencia actúa aunque la automatización esté desactivada: es una medida de seguridad
        manejarEmergencia(thresholds, estados)

        if (thresholds.automatizacionActiva) {
            for ((id, deseado) in estadosDeseados(thresholds, estados)) {
                // Durante una emergencia el equipo lo controla el protocolo de emergencia
                if (id in equiposForzados) continue
                val manual = modos.child(id).getValue(String::class.java) == "manual"
                if (!manual && estados[id] != deseado) {
                    estados[id] = deseado
                    deviceRef.child("comandos").child(id).setValue(deseado)
                    registrarEvento(
                        AuditCategoria.DISPOSITIVOS,
                        "${id.uppercase()}_${if (deseado) "ON" else "OFF"}",
                        "${ActuadorId.nombre(id)} ${if (deseado) "activado" else "desactivado"} " +
                                "automáticamente (${motivoAutomatico(id, thresholds)})"
                    )
                }
            }
        }

        val bombaOn = estados[ActuadorId.BOMBA] == true
        val ventiladorOn = estados[ActuadorId.VENTILADOR] == true

        // Temperatura: sube sola y SOLO la bomba de agua la enfría
        temperatura += if (bombaOn) -randomIn(0.3, 0.6) else randomIn(0.1, 0.3)
        temperatura = temperatura.coerceIn(-5.0, 20.0)

        // Humedad: el ambiente la va subiendo (siempre, aunque la automatización esté apagada)
        // y SOLO el ventilador la baja
        humedadAire += if (ventiladorOn) -randomIn(0.5, 1.0) else randomIn(0.2, 0.6)
        humedadAire = humedadAire.coerceIn(0.0, 100.0)

        verificarAlertaCritica(thresholds)

        // La hora la pone el servidor de Firebase (no depende del reloj del teléfono)
        deviceRef.child("sensores").setValue(
            mapOf(
                "temperatura" to temperatura,
                "humedadAire" to humedadAire,
                "timestamp" to ServerValue.TIMESTAMP
            )
        )
        deviceRef.child("estado").child("ultimaConexion").setValue(ServerValue.TIMESTAMP)

        guardarLecturaSiToca()
    }

    /**
     * Qué debería hacer cada actuador según los valores actuales:
     *  - bomba: temperatura sobre su máximo.
     *  - ventilador: humedad sobre su máximo.
     *  - LED: advertencia, cualquier valor fuera de su rango normal.
     *  - bocina: zona crítica, cualquier valor fuera de los umbrales de alarma.
     */
    private fun estadosDeseados(t: Thresholds, actuales: Map<String, Boolean>): Map<String, Boolean> = mapOf(
        // Con histéresis: una vez encendidos, siguen hasta bajar un margen por debajo del umbral
        ActuadorId.BOMBA to
                (temperatura > t.temperaturaMax - if (actuales[ActuadorId.BOMBA] == true) MARGEN_BOMBA else 0.0),
        ActuadorId.VENTILADOR to
                (humedadAire > t.humedadAireMax - if (actuales[ActuadorId.VENTILADOR] == true) MARGEN_VENTILADOR else 0.0),
        ActuadorId.LED to (temperatura > t.temperaturaMax || temperatura < t.temperaturaMin ||
                humedadAire > t.humedadAireMax || humedadAire < t.humedadAireMin),
        ActuadorId.BOCINA to enZonaCritica(t)
    )

    /**
     * Protocolo de emergencia (valores críticos ALTOS). Cada causa tiene su equipo:
     *  - temperatura crítica alta -> bomba de agua (enfría)
     *  - humedad crítica alta -> ventilador (seca)
     *
     *  1. Suena la alarma en los teléfonos y se le dan 2 minutos al usuario para activar ese equipo
     *     desde la propia alarma.
     *  2. Si pasan 2 minutos y sigue apagado, el sistema lo enciende solo.
     *  3. Se mantiene encendido hasta que el valor vuelve al rango normal.
     */
    private fun manejarEmergencia(t: Thresholds, estados: MutableMap<String, Boolean>) {
        emergenciaDeEquipo(
            ActuadorId.BOMBA,
            critico = temperatura > t.alertaTemperaturaMax,
            normalizado = temperatura <= t.temperaturaMax,
            descripcion = "temp %.1f°C".format(temperatura),
            estados = estados
        )
        emergenciaDeEquipo(
            ActuadorId.VENTILADOR,
            critico = humedadAire > t.alertaHumedadMax,
            normalizado = humedadAire <= t.humedadAireMax,
            descripcion = "humedad %.0f%%".format(humedadAire),
            estados = estados
        )
    }

    private fun emergenciaDeEquipo(
        equipo: String,
        critico: Boolean,
        normalizado: Boolean,
        descripcion: String,
        estados: MutableMap<String, Boolean>
    ) {
        val ahora = ahoraServidor()
        val forzado = equipo in equiposForzados

        if (critico) {
            val desde = criticoDesde.getOrPut(equipo) { ahora }
            val encendido = estados[equipo] == true
            if (!forzado && encendido) {
                // Ya lo activó el usuario (desde la alarma o a mano): el protocolo lo apagará al normalizarse
                equiposForzados.add(equipo)
            } else if (!forzado && ahora - desde >= EMERGENCIA_ESPERA_MS) {
                equiposForzados.add(equipo)
                registrarEvento(
                    AuditCategoria.ALERTAS, "EMERGENCIA_AUTOMATICA",
                    "La alarma no se atendió en 2 minutos: ${ActuadorId.nombre(equipo)} activado automáticamente ($descripcion)"
                )
            }
        } else {
            criticoDesde.remove(equipo)
        }

        if (equipo in equiposForzados) {
            if (!critico && normalizado) {
                equiposForzados.remove(equipo)
                estados[equipo] = false
                deviceRef.child("comandos").child(equipo).setValue(false)
                // Se devuelve el control a la automatización
                deviceRef.child("modos").child(equipo).setValue("auto")
                registrarEvento(
                    AuditCategoria.ALERTAS, "EMERGENCIA_RESUELTA",
                    "Valores normalizados: se apagó ${ActuadorId.nombre(equipo)} de emergencia"
                )
            } else if (estados[equipo] != true) {
                estados[equipo] = true
                deviceRef.child("comandos").child(equipo).setValue(true)
            }
        }
    }

    private fun enZonaCritica(t: Thresholds): Boolean =
        temperatura > t.alertaTemperaturaMax || temperatura < t.alertaTemperaturaMin ||
                humedadAire > t.alertaHumedadMax || humedadAire < t.alertaHumedadMin

    private fun motivoAutomatico(id: String, t: Thresholds): String = when (id) {
        ActuadorId.BOMBA -> "temp %.1f°C, máx %.1f°C".format(temperatura, t.temperaturaMax)
        ActuadorId.VENTILADOR -> "humedad %.0f%%, máx %.0f%%".format(humedadAire, t.humedadAireMax)
        ActuadorId.LED -> "valores fuera del rango normal"
        else -> "valores en zona crítica"
    }

    // Deja registro solo al CRUZAR el umbral (no se repite cada 3 segundos mientras siga crítico)
    private fun verificarAlertaCritica(thresholds: Thresholds) {
        val fueraDeRango = enZonaCritica(thresholds)

        if (fueraDeRango && !alertaCriticaActiva) {
            alertaCriticaActiva = true
            val mensaje = "Temp: %.1f°C · Humedad: %.0f%%".format(temperatura, humedadAire)
            registrarEvento(AuditCategoria.ALERTAS, "ALERTA_CRITICA", "Alerta crítica disparada: $mensaje")
        } else if (!fueraDeRango && alertaCriticaActiva) {
            alertaCriticaActiva = false
            registrarEvento(AuditCategoria.ALERTAS, "ALERTA_NORMALIZADA", "Valores de vuelta a rango normal")
        }
    }

    /**
     * Guarda una lectura por minuto en un buffer circular de 1440 posiciones (24 h): la posición
     * es (minuto del día mod 1440), así la lectura nueva reemplaza a la de hace 24 h y no hace
     * falta borrar nada.
     */
    private fun guardarLecturaSiToca() {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimaLecturaMs < INTERVALO_LECTURA_MS) return
        ultimaLecturaMs = ahora

        val posicion = ((ahora / 60_000L) % LECTURAS_POR_DIA).toString()
        lecturasRef.child(posicion).setValue(
            mapOf(
                "temperatura" to temperatura,
                "humedadAire" to humedadAire,
                "timestamp" to ServerValue.TIMESTAMP
            )
        )
    }

    private fun registrarEvento(categoria: String, tipo: String, detalle: String) {
        audit.registrar(categoria, tipo, detalle, usuario = "Sistema")
    }

    fun stop() {
        if (!running) return
        job?.cancel()
        offsetJob?.cancel()
        running = false
        // Solo el líder marca el dispositivo como desconectado y libera el contrato
        if (esLider) {
            esLider = false
            deviceRef.child("estado").child("online").setValue(false)
            deviceRef.child("estado").child("simulador").removeValue()
            registrarEvento(AuditCategoria.DISPOSITIVOS, "DISPOSITIVO_FUERA_DE_LINEA", "El dispositivo simulado se desconectó")
        }
    }

    /** Detiene y espera a que Firebase confirme "offline" (se usa antes de cerrar sesión). */
    suspend fun detenerYEsperar() {
        if (!running) return
        job?.cancel()
        offsetJob?.cancel()
        running = false
        if (!esLider) return
        esLider = false
        try {
            withTimeoutOrNull(3000) {
                deviceRef.child("estado").child("online").setValue(false).await()
                deviceRef.child("estado").child("simulador").removeValue().await()
            }
            audit.registrarYEsperar(
                AuditCategoria.DISPOSITIVOS, "DISPOSITIVO_FUERA_DE_LINEA",
                "El dispositivo simulado se desconectó", usuario = "Sistema"
            )
        } catch (e: Exception) {
            // sin red o sin permiso: no bloquea el cierre de sesión
        }
    }

    private fun randomIn(min: Double, max: Double): Double = min + Math.random() * (max - min)
}
