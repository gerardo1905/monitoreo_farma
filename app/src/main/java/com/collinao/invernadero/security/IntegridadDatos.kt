package com.collinao.invernadero.security

import com.collinao.invernadero.data.model.Thresholds
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

/**
 * 3.1.1.1 / 3.1.1.2  Integridad y autenticidad de la información en tránsito (ISO 27001 A.8.24).
 *
 * Cada dato que la app envía al dispositivo (comandos de los equipos y umbrales) viaja junto a una
 * firma HMAC-SHA256: un hash calculado sobre el contenido con una CLAVE SECRETA que solo conocen
 * la app y el ESP32. La firma se guarda en dispositivos/<id>/firmas/<dato> y el ESP32 la recalcula
 * antes de obedecer: si alguien modifica un comando o un umbral directamente en la base de datos
 * (sin la clave), el hash no coincide y el ESP32 lo rechaza.
 *
 * - La clave está en ClaveHmac.kt, archivo excluido por .gitignore: nunca se sube al repositorio
 *   (en el repositorio solo está la plantilla app/ClaveHmac.kt.ejemplo, sin la clave real).
 * - El "ts" (hora de envío) entra en el hash: impide reutilizar una firma antigua (repetición).
 */
object IntegridadDatos {

    private const val ALGORITMO = "HmacSHA256"

    /** HMAC-SHA256 de [mensaje] en hexadecimal (64 caracteres). */
    fun hmacSha256(mensaje: String): String {
        val mac = Mac.getInstance(ALGORITMO)
        mac.init(SecretKeySpec(ClaveHmac.HMAC_KEY.toByteArray(Charsets.UTF_8), ALGORITMO))
        return mac.doFinal(mensaje.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Mensaje canónico de un comando: "<dispositivo>|<equipo>|<1/0>|<auto/manual>|<ts>".
     * El ESP32 arma exactamente el mismo texto para verificar la firma.
     */
    fun mensajeComando(deviceId: String, actuador: String, encendido: Boolean, modo: String, ts: String) =
        "$deviceId|$actuador|${if (encendido) 1 else 0}|$modo|$ts"

    /** Nodo firmas/<equipo> que acompaña a cada comando enviado desde la app. */
    fun firmaComando(deviceId: String, actuador: String, encendido: Boolean, modo: String): Map<String, Any> {
        val ts = System.currentTimeMillis().toString()
        return mapOf(
            "valor" to encendido,
            "modo" to modo,
            "ts" to ts,
            "origen" to "app",
            "hash" to hmacSha256(mensajeComando(deviceId, actuador, encendido, modo, ts))
        )
    }

    /** Los decimales se firman como enteros (valor x 100) para que la app y el ESP32 den el mismo texto. */
    private fun c(v: Float): Int = (v * 100f).roundToInt()

    /** Mensaje canónico de los umbrales (mismo orden y formato que en el ESP32). */
    fun mensajeUmbrales(deviceId: String, t: Thresholds, ts: String): String = String.format(
        Locale.US, "%s|thresholds|%d|%d|%d|%d|%d|%d|%d|%d|%d|%s",
        deviceId, c(t.temperaturaMax), c(t.temperaturaMin), c(t.humedadAireMax), c(t.humedadAireMin),
        if (t.automatizacionActiva) 1 else 0,
        c(t.alertaTemperaturaMax), c(t.alertaTemperaturaMin), c(t.alertaHumedadMax), c(t.alertaHumedadMin), ts
    )

    /** Nodo firmas/thresholds que acompaña a cada cambio de umbrales. */
    fun firmaUmbrales(deviceId: String, t: Thresholds): Map<String, Any> {
        val ts = System.currentTimeMillis().toString()
        return mapOf(
            "ts" to ts,
            "origen" to "app",
            "hash" to hmacSha256(mensajeUmbrales(deviceId, t, ts))
        )
    }
}
