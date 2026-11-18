package com.collinao.invernadero.data.repository

import com.collinao.invernadero.data.DeviceConfig
import com.collinao.invernadero.data.local.BitacoraLocal
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.DeviceStatus
import com.collinao.invernadero.data.model.EstadoActuador
import com.collinao.invernadero.data.model.SensorData
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.security.IntegridadDatos
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * Acceso al dispositivo IoT real (ESP32): dispositivos/<DeviceConfig.DEVICE_ID>.
 *
 * Contrato de datos (el ESP32 real deberá usar los mismos nodos):
 *  dispositivos/<id>/sensores   {temperatura, humedadAire, timestamp}
 *  dispositivos/<id>/estado     {online, ultimaConexion, simulador{id, ts}}
 *  dispositivos/<id>/comandos   {bomba, ventilador, bocina, led}  (true/false)
 *  dispositivos/<id>/modos      {bomba, ventilador, bocina, led}  ("auto"/"manual")
 *  dispositivos/<id>/thresholds {...}  (solo el admin escribe)
 *  dispositivos/<id>/lecturas/<0..1439>  buffer circular de 24 h, una lectura por minuto
 */
class DeviceRepository(
    private val deviceId: String = DeviceConfig.DEVICE_ID
) {
    private val rootRef: DatabaseReference = Firebase.database.reference
    private val deviceRef: DatabaseReference = rootRef.child("dispositivos/$deviceId")

    /** Escucha un nodo y convierte cada cambio. Se cierra con error si Firebase deniega el acceso. */
    private fun <T> observar(ref: DatabaseReference, convertir: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(convertir(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observarSensores(): Flow<SensorData> =
        observar(deviceRef.child("sensores")) { it.getValue(SensorData::class.java) ?: SensorData() }

    fun observarEstado(): Flow<DeviceStatus> =
        observar(deviceRef.child("estado")) { snap ->
            DeviceStatus(
                online = snap.child("online").getValue(Boolean::class.java) ?: false,
                ultimaConexion = snap.child("ultimaConexion").getValue(Long::class.java) ?: 0L,
                pendientes = snap.child("pendientes").getValue(Int::class.java) ?: 0,
                rssi = snap.child("rssi").getValue(Int::class.java) ?: 0
            )
        }

    private fun observarMapa(hijo: String): Flow<Map<String, Any?>> =
        observar(deviceRef.child(hijo)) { snap -> snap.children.associate { (it.key ?: "") to it.value } }

    /** Estado (encendido/apagado) y modo (automático/manual) de bomba, ventilador, bocina y LED. */
    fun observarActuadores(): Flow<Map<String, EstadoActuador>> =
        combine(observarMapa("comandos"), observarMapa("modos")) { comandos, modos ->
            ActuadorId.todos.associateWith { id ->
                EstadoActuador(
                    encendido = comandos[id] == true,
                    automatico = modos[id] != "manual"
                )
            }
        }

    // ===== 3.1.1.1 / 3.1.1.2: todo lo que la app envía al dispositivo viaja con su firma HMAC-SHA256 =====
    // La firma se escribe en dispositivos/<id>/firmas/<dato> en la MISMA operación (updateChildren es
    // atómico): o se guardan el dato y su hash juntos, o no se guarda nada.

    /**
     * 3.1.2.7: toda acción se guarda PRIMERO en la base local (PENDIENTE) y luego se envía a Firebase.
     * Cuando el servidor confirma (de inmediato, o al volver la conexión) pasa a SINCRONIZADO.
     */
    private fun enviarConRespaldoLocal(
        tipo: String,
        detalle: String,
        cambios: Map<String, Any>,
        alTerminar: (Throwable?) -> Unit = {}
    ) {
        val bitacora = BitacoraLocal.get()
        val idLocal = bitacora.registrar(tipo, detalle)
        deviceRef.updateChildren(cambios).addOnCompleteListener { tarea ->
            bitacora.marcar(idLocal, if (tarea.isSuccessful) BitacoraLocal.SINCRONIZADO else BitacoraLocal.RECHAZADO)
            alTerminar(if (tarea.isSuccessful) null else tarea.exception)
        }
    }

    /** Control manual: fija el estado y pasa el actuador a modo manual (la automatización lo respeta). */
    fun setActuador(id: String, encendido: Boolean) {
        enviarConRespaldoLocal(
            tipo = "${id.uppercase()}_${if (encendido) "ON" else "OFF"}",
            detalle = "${ActuadorId.nombre(id)} ${if (encendido) "encendido" else "apagado"} manualmente",
            cambios = mapOf(
                "comandos/$id" to encendido,
                "modos/$id" to "manual",
                "firmas/$id" to IntegridadDatos.firmaComando(deviceId, id, encendido, "manual")
            )
        )
    }

    /** Devuelve el actuador a modo automático: desde el próximo ciclo manda la automatización. */
    fun setModoAutomatico(id: String, encendidoActual: Boolean) {
        enviarConRespaldoLocal(
            tipo = "MODO_AUTOMATICO",
            detalle = "${ActuadorId.nombre(id)} pasa a modo automático",
            cambios = mapOf(
                "modos/$id" to "auto",
                "firmas/$id" to IntegridadDatos.firmaComando(deviceId, id, encendidoActual, "auto")
            )
        )
    }

    /** Devuelve bomba, ventilador, bocina y LED al modo automático (al activar la automatización). */
    fun todosAutomaticos(estadosActuales: Map<String, Boolean>) {
        val cambios = mutableMapOf<String, Any>()
        ActuadorId.todos.forEach { id ->
            val encendido = estadosActuales[id] ?: false
            cambios["modos/$id"] = "auto"
            cambios["firmas/$id"] = IntegridadDatos.firmaComando(deviceId, id, encendido, "auto")
        }
        enviarConRespaldoLocal("MODOS_AUTOMATICOS", "Todos los equipos pasan a modo automático", cambios)
    }

    // --- THRESHOLDS: a nivel de dispositivo; solo un administrador puede modificarlos (lo exigen las reglas) ---
    fun observarThresholds(): Flow<Thresholds> =
        observar(deviceRef.child("thresholds")) { it.getValue(Thresholds::class.java) ?: Thresholds() }

    /** [alTerminar] recibe null si se guardó, o el error (por ejemplo permiso denegado por las reglas). */
    fun guardarThresholds(thresholds: Thresholds, alTerminar: (Throwable?) -> Unit = {}) {
        val valores = mapOf(
            "temperaturaMax" to thresholds.temperaturaMax,
            "temperaturaMin" to thresholds.temperaturaMin,
            "humedadAireMax" to thresholds.humedadAireMax,
            "humedadAireMin" to thresholds.humedadAireMin,
            "automatizacionActiva" to thresholds.automatizacionActiva,
            "alertaTemperaturaMax" to thresholds.alertaTemperaturaMax,
            "alertaTemperaturaMin" to thresholds.alertaTemperaturaMin,
            "alertaHumedadMax" to thresholds.alertaHumedadMax,
            "alertaHumedadMin" to thresholds.alertaHumedadMin
        )
        enviarConRespaldoLocal(
            tipo = "UMBRALES_MODIFICADOS",
            detalle = "Bomba > %.1f °C · Ventilador > %.0f %% · Alarma > %.1f °C / %.0f %% · Automatización %s".format(
                thresholds.temperaturaMax, thresholds.humedadAireMax,
                thresholds.alertaTemperaturaMax, thresholds.alertaHumedadMax,
                if (thresholds.automatizacionActiva) "activa" else "inactiva"
            ),
            cambios = mapOf(
                "thresholds" to valores,
                "firmas/thresholds" to IntegridadDatos.firmaUmbrales(deviceId, thresholds)
            ),
            alTerminar = alTerminar
        )
    }

    /**
     * Diferencia (ms) entre el reloj de este teléfono y el del servidor de Firebase.
     * Hora del servidor = System.currentTimeMillis() + offset. Así "en línea" y "hace X s"
     * no dependen de que los relojes de los teléfonos estén bien puestos.
     */
    fun observarOffsetServidor(): Flow<Long> =
        observar(Firebase.database.getReference(".info/serverTimeOffset")) {
            (it.value as? Number)?.toLong() ?: 0L
        }

    /** Lecturas guardadas (una por minuto, últimas 24 h), ordenadas de la más antigua a la más reciente. */
    fun observarLecturas(): Flow<List<SensorData>> =
        observar(deviceRef.child("lecturas")) { snap ->
            snap.children
                .mapNotNull { it.getValue(SensorData::class.java) }
                .filter { it.timestamp > 0L }
                .sortedBy { it.timestamp }
        }

    // ================= 3.1.1.3 / 3.1.2.7: conexión y almacenamiento local =================

    /**
     * RECONEXIÓN AUTOMÁTICA (3.1.1.3): true mientras el teléfono está conectado al servidor de Firebase.
     * ".info/connected" lo publica el propio SDK, que reconecta solo al volver la red; la app lo
     * escucha para avisar al usuario y saber cuándo se están sincronizando los datos pendientes.
     */
    fun observarConexionServidor(): Flow<Boolean> =
        observar(Firebase.database.getReference(".info/connected")) {
            it.getValue(Boolean::class.java) ?: false
        }

    /**
     * 3.1.2.7 Almacenamiento temporal: mantiene en el disco del teléfono una copia actualizada de
     * los nodos del dispositivo. Sin conexión, la app sigue mostrando los últimos datos guardados
     * y los comandos/ajustes que se hagan quedan en cola local hasta que vuelva la conexión.
     * (Se llama por nodo porque las reglas dan lectura por nodo, no sobre todo el dispositivo.)
     */
    fun mantenerSincronizado(activo: Boolean = true) {
        DeviceConfig.NODOS_SINCRONIZADOS.forEach { deviceRef.child(it).keepSynced(activo) }
    }

    fun getDeviceRef(): DatabaseReference = deviceRef
}
