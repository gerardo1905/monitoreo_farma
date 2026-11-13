package com.collinao.invernadero.data.repository

import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/**
 * Credenciales incorrectas. Se usa UN solo mensaje tanto si el correo no existe como si la
 * contraseña está mal, para no revelar qué correos tienen cuenta (enumeración de usuarios).
 */
class CredencialesInvalidasException : Exception("Credenciales incorrectas")

class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth
) {

    val currentUser get() = auth.currentUser

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(CredencialesInvalidasException())
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(CredencialesInvalidasException())
        } catch (e: FirebaseTooManyRequestsException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_demasiados_dispositivo)))
        } catch (e: FirebaseNetworkException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_sin_conexion)))
        } catch (e: Exception) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.error_login_generico)))
        }
    }

    suspend fun register(email: String, password: String): Result<Unit> {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_password_debil)))
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_correo_existente)))
        } catch (e: FirebaseNetworkException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_sin_conexion)))
        } catch (e: Exception) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.error_registro)))
        }
    }

    /** Siempre responde éxito: no se revela si el correo existe. */
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    fun logout() {
        auth.signOut()
    }

    // --- Verificación de correo ---

    val correoVerificado: Boolean get() = auth.currentUser?.isEmailVerified == true

    suspend fun enviarVerificacion(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception(InvernaderoApp.texto(R.string.auth_sin_sesion)))
        return try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: FirebaseTooManyRequestsException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_verificacion_espera)))
        } catch (e: Exception) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_verificacion_error)))
        }
    }

    /** Refresca los datos de la cuenta (por ejemplo, si el correo ya fue verificado desde el navegador). */
    suspend fun recargarUsuario() {
        try {
            auth.currentUser?.reload()?.await()
        } catch (e: Exception) {
            // sin red: se queda con lo último conocido
        }
    }

    // --- Cambio de contraseña (exige confirmar la contraseña actual) ---

    suspend fun cambiarPassword(actual: String, nueva: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception(InvernaderoApp.texto(R.string.auth_sin_sesion)))
        val correo = user.email ?: return Result.failure(Exception(InvernaderoApp.texto(R.string.auth_sin_correo)))
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(correo, actual)).await()
            user.updatePassword(nueva).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_password_actual_incorrecta)))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_password_debil)))
        } catch (e: FirebaseTooManyRequestsException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_demasiados)))
        } catch (e: FirebaseNetworkException) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_sin_conexion)))
        } catch (e: Exception) {
            Result.failure(Exception(InvernaderoApp.texto(R.string.auth_cambio_error)))
        }
    }
}
