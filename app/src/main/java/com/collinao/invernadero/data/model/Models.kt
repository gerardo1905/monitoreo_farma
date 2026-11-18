package com.collinao.invernadero.data.model

data class SensorData(
    val temperatura: Double = 0.0,
    val humedadAire: Double = 0.0,
    val timestamp: Long = 0L
)

data class DeviceStatus(
    val online: Boolean = false,
    val bombaActiva: Boolean = false,
    val ultimaConexion: Long = 0L,
    /** Lecturas que el ESP32 guardó sin conexión y aún no sube (3.1.1.4). */
    val pendientes: Int = 0,
    /** Señal WiFi del ESP32 en dBm (0 = sin dato). */
    val rssi: Int = 0
)

/**
 * Umbrales por defecto: almacenamiento de medicamentos a temperatura ambiente controlada
 * (15–25 °C, humedad 30–60 %), dentro del rango que mide el sensor DHT11 (0–50 °C, 20–90 %).
 * - temperaturaMax: sobre este valor se enciende la BOMBA de agua (enfría).
 * - humedadAireMax: sobre este valor se enciende el VENTILADOR (baja la humedad).
 * - alerta*: zona crítica, suena la alarma y el buzzer.
 */
data class Thresholds(
    val temperaturaMax: Float = 25f,
    val temperaturaMin: Float = 15f,
    val humedadAireMax: Float = 60f,
    val humedadAireMin: Float = 30f,
    val automatizacionActiva: Boolean = true,
    val alertaTemperaturaMax: Float = 30f,
    val alertaTemperaturaMin: Float = 10f,
    val alertaHumedadMax: Float = 80f,
    val alertaHumedadMin: Float = 20f
)

/**
 * Actuadores del invernadero. Cada uno tiene en Firebase:
 *  - dispositivos/<id>/comandos/<actuador>  -> true/false (encendido)
 *  - dispositivos/<id>/modos/<actuador>     -> "auto" | "manual"
 * El ESP32 real deberá leer y obedecer exactamente estos mismos nodos.
 */
object ActuadorId {
    const val BOMBA = "bomba"
    const val VENTILADOR = "ventilador"
    const val BOCINA = "bocina"
    const val LED = "led"

    val todos = listOf(BOMBA, VENTILADOR, BOCINA, LED)

    fun nombre(id: String): String = when (id) {
        BOMBA -> "Bomba de agua"
        VENTILADOR -> "Ventilador"
        BOCINA -> "Bocina"
        LED -> "LED indicador"
        else -> id
    }
}

data class EstadoActuador(
    val encendido: Boolean = false,
    val automatico: Boolean = true
)

data class UserProfile(
    val email: String = "",
    val rol: String = "usuario",
    val activo: Boolean = true
)

/**
 * Categorías de la bitácora de auditoría. Cada una vive en su propio nodo:
 * registros/<categoria>/<idPush>
 */
object AuditCategoria {
    const val SESIONES = "sesiones"
    const val CUENTAS = "cuentas"
    const val DISPOSITIVOS = "dispositivos"
    const val CONFIGURACION = "configuracion"
    const val ALERTAS = "alertas"

    val todas = listOf(SESIONES, CUENTAS, DISPOSITIVOS, CONFIGURACION, ALERTAS)

    fun titulo(categoria: String): String = when (categoria) {
        SESIONES -> "Sesiones"
        CUENTAS -> "Cuentas"
        DISPOSITIVOS -> "Dispositivos"
        CONFIGURACION -> "Configuración"
        ALERTAS -> "Alertas"
        else -> categoria
    }
}

/** Un registro de la bitácora. Solo se lee en la app (se escribe como Map con hora del servidor). */
data class AuditEvent(
    val id: String = "",
    val tipo: String = "",
    val detalle: String = "",
    val usuario: String = "Sistema",
    val uid: String = "",
    val timestamp: Long = 0L
)

/** Estado de bloqueo por intentos fallidos de inicio de sesión (nodo bloqueos/<correo>). */
data class EstadoBloqueo(
    val intentos: Int = 0,
    val bloqueado: Boolean = false,
    val resetSolicitado: Boolean = false
)
