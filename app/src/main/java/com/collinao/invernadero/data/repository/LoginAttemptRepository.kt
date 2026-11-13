package com.collinao.invernadero.data.repository

import com.collinao.invernadero.data.model.EstadoBloqueo
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import kotlinx.coroutines.tasks.await

/**
 * Control de intentos fallidos de inicio de sesión, guardado en Firebase (bloqueos/<correo>)
 * para que el bloqueo siga aunque se reinstale la app o se cambie de teléfono.
 *
 * Flujo:
 *  - Cada fallo suma 1. Al llegar a MAX_INTENTOS la cuenta queda bloqueada.
 *  - Bloqueada: no se permite intentar iniciar sesión hasta pedir "Recuperar contraseña".
 *  - Tras pedir la recuperación se permite UN intento; si falla, vuelve a bloquearse.
 *  - Un inicio de sesión correcto borra el registro de intentos.
 *
 * Las reglas de la base (database.rules.json) validan cada transición para que nadie
 * pueda poner el contador en cero o desbloquear por su cuenta.
 */
class LoginAttemptRepository {
    companion object {
        const val MAX_INTENTOS = 5

        /** Las claves de Firebase no admiten '.', así que se cambia por ','. */
        fun clave(email: String): String = email.trim().lowercase().replace('.', ',')
    }

    private val bloqueosRef: DatabaseReference = Firebase.database.reference.child("bloqueos")

    suspend fun obtener(email: String): EstadoBloqueo = try {
        bloqueosRef.child(clave(email)).get().await()
            .getValue(EstadoBloqueo::class.java) ?: EstadoBloqueo()
    } catch (e: Exception) {
        EstadoBloqueo()
    }

    /** Suma un fallo y devuelve el estado resultante. */
    suspend fun registrarFallo(email: String): EstadoBloqueo {
        val actual = obtener(email)
        val nuevo = if (actual.bloqueado) {
            // Falló el intento permitido tras la recuperación: se bloquea de nuevo
            actual.copy(resetSolicitado = false)
        } else {
            val intentos = actual.intentos + 1
            EstadoBloqueo(intentos = intentos, bloqueado = intentos >= MAX_INTENTOS, resetSolicitado = false)
        }
        try {
            bloqueosRef.child(clave(email)).setValue(nuevo).await()
        } catch (e: Exception) {
            // Sin red: Firebase Authentication igualmente limita los intentos por su lado
        }
        return nuevo
    }

    /** Se llama al pedir "Recuperar contraseña": si la cuenta está bloqueada, habilita un intento. */
    suspend fun marcarResetSolicitado(email: String) {
        val actual = obtener(email)
        if (!actual.bloqueado || actual.resetSolicitado) return
        try {
            bloqueosRef.child(clave(email)).setValue(actual.copy(resetSolicitado = true)).await()
        } catch (e: Exception) {
        }
    }

    /** Inicio de sesión correcto: se borra el contador (las reglas exigen estar autenticado como ese correo). */
    suspend fun limpiar(email: String) {
        try {
            bloqueosRef.child(clave(email)).removeValue().await()
        } catch (e: Exception) {
        }
    }
}
