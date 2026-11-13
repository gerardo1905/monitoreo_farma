package com.collinao.invernadero.data.repository

import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.DeviceStatus
import com.collinao.invernadero.data.model.EstadoActuador
import com.collinao.invernadero.data.model.SensorData
import com.collinao.invernadero.data.model.Thresholds
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
 * Acceso al dispositivo (por ahora simulado: dispositivos/sim-001).
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
    private val deviceId: String = "sim-001"
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
                ultimaConexion = snap.child("ultimaConexion").getValue(Long::class.java) ?: 0L
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

    /** Control manual: fija el estado y pasa el actuador a modo manual (la automatización lo respeta). */
    fun setActuador(id: String, encendido: Boolean) {
        deviceRef.updateChildren(
            mapOf(
                "comandos/$id" to encendido,
                "modos/$id" to "manual"
            )
        )
    }

    /** Devuelve el actuador a modo automático: desde el próximo ciclo manda la automatización. */
    fun setModoAutomatico(id: String) {
        deviceRef.child("modos").child(id).setValue("auto")
    }

    /** Devuelve bomba, ventilador, bocina y LED al modo automático (al activar la automatización). */
    fun todosAutomaticos() {
        deviceRef.child("modos").updateChildren(ActuadorId.todos.associateWith { "auto" })
    }

    // --- THRESHOLDS: a nivel de dispositivo; solo un administrador puede modificarlos (lo exigen las reglas) ---
    fun observarThresholds(): Flow<Thresholds> =
        observar(deviceRef.child("thresholds")) { it.getValue(Thresholds::class.java) ?: Thresholds() }

    /** [alTerminar] recibe null si se guardó, o el error (por ejemplo permiso denegado por las reglas). */
    fun guardarThresholds(thresholds: Thresholds, alTerminar: (Throwable?) -> Unit = {}) {
        deviceRef.child("thresholds").setValue(thresholds)
            .addOnCompleteListener { tarea -> alTerminar(if (tarea.isSuccessful) null else tarea.exception) }
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

    fun getDeviceRef(): DatabaseReference = deviceRef
}
