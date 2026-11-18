package com.collinao.invernadero.data

/**
 * Configuración del dispositivo IoT que monitorea la app.
 *
 * Un solo lugar para elegir el dispositivo: el ESP32 real escribe en dispositivos/<DEVICE_ID>/
 * (el mismo ID debe ir en el sketch de Arduino: DEVICE_ID).
 */
object DeviceConfig {

    /** ID del ESP32 en Firebase: dispositivos/esp32-001/... */
    const val DEVICE_ID = "esp32-001"

    /**
     * false = se usa el ESP32 real (el teléfono solo muestra datos y manda comandos).
     * true  = vuelve el simulador de la Unidad 2 (el teléfono inventa las lecturas).
     *         Para usarlo, la cuenta del teléfono debe estar en dispositivosAutorizados/<DEVICE_ID>/.
     */
    const val USAR_SIMULADOR = false

    /** Nodos del dispositivo que se guardan en el teléfono para verlos sin conexión (3.1.2.7). */
    val NODOS_SINCRONIZADOS = listOf("sensores", "estado", "comandos", "modos", "thresholds", "lecturas")
}
