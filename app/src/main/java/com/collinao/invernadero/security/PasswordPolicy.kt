package com.collinao.invernadero.security

import com.collinao.invernadero.R

/** Un requisito de la política de contraseña y si se cumple. */
data class Requisito(
    val textoId: Int,
    val cumplido: Boolean,
    val argumentos: List<Any> = emptyList()
)

/**
 * Política de contraseñas y validación de correo.
 * Se usa en el registro (y sirve de referencia para la política de Firebase Authentication).
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    private val correoRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private val comunes = setOf(
        "12345678", "123456789", "1234567890", "password", "password1", "password123",
        "qwerty123", "qwertyui", "contrasena", "contraseña", "admin123", "iloveyou",
        "abc12345", "welcome1", "letmein1", "invernadero", "invernadero1"
    )

    fun correoValido(correo: String): Boolean =
        correo.length <= 100 && correoRegex.matches(correo.trim())

    fun requisitos(password: String, email: String = ""): List<Requisito> {
        val parteLocal = email.substringBefore('@').lowercase()
        val contieneCorreo = parteLocal.length >= 3 && password.lowercase().contains(parteLocal)
        return listOf(
            Requisito(R.string.req_longitud, password.length in MIN_LENGTH..MAX_LENGTH, listOf(MIN_LENGTH, MAX_LENGTH)),
            Requisito(R.string.req_mayuscula, password.any { it.isUpperCase() }),
            Requisito(R.string.req_minuscula, password.any { it.isLowerCase() }),
            Requisito(R.string.req_numero, password.any { it.isDigit() }),
            Requisito(R.string.req_simbolo, password.any { !it.isLetterOrDigit() && !it.isWhitespace() }),
            Requisito(R.string.req_sin_espacios, password.none { it.isWhitespace() }),
            Requisito(R.string.req_no_comun, password.lowercase() !in comunes),
            Requisito(R.string.req_sin_correo, !contieneCorreo)
        )
    }

    fun esValida(password: String, email: String = ""): Boolean =
        requisitos(password, email).all { it.cumplido }

    /** Fortaleza de 0 (vacía) a 4 (muy fuerte), solo para el indicador visual. */
    fun fortaleza(password: String, email: String = ""): Int {
        if (password.isEmpty()) return 0
        val cumplidos = requisitos(password, email).count { it.cumplido }
        val base = when {
            cumplidos <= 3 -> 1
            cumplidos <= 5 -> 2
            cumplidos <= 7 -> 3
            else -> 4
        }
        return if (password.length >= 12 && base >= 3) 4 else base
    }

    /** Texto (recurso de strings.xml) que describe el nivel de fortaleza. */
    fun etiquetaFortaleza(nivel: Int): Int = when (nivel) {
        0 -> R.string.fortaleza_vacia
        1 -> R.string.fortaleza_muy_debil
        2 -> R.string.fortaleza_debil
        3 -> R.string.fortaleza_buena
        else -> R.string.fortaleza_fuerte
    }
}
