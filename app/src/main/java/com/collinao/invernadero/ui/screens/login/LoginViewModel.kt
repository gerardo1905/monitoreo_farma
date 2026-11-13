package com.collinao.invernadero.ui.screens.login

import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.AuthRepository
import com.collinao.invernadero.data.repository.CredencialesInvalidasException
import com.collinao.invernadero.data.repository.LoginAttemptRepository
import com.collinao.invernadero.data.repository.UserRepository
import com.collinao.invernadero.security.PasswordPolicy
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val lockRepository: LoginAttemptRepository = LoginAttemptRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    /** true cuando la cuenta está bloqueada: la pantalla resalta "Recuperar contraseña". */
    var bloqueada by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
        bloqueada = false
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    private val mensajeBloqueo: String
        get() = InvernaderoApp.texto(R.string.login_error_bloqueada)

    fun login(onSuccess: () -> Unit) {
        val correo = email.trim().lowercase()
        if (correo.isBlank() || password.isBlank()) {
            errorMessage = InvernaderoApp.texto(R.string.login_error_campos)
            return
        }
        if (!PasswordPolicy.correoValido(correo)) {
            errorMessage = InvernaderoApp.texto(R.string.error_correo_invalido)
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                // 1) ¿Está bloqueada? Si lo está (y no ha pedido recuperación), ni siquiera se intenta.
                val estado = lockRepository.obtener(correo)
                if (estado.bloqueado && !estado.resetSolicitado) {
                    bloqueada = true
                    errorMessage = mensajeBloqueo
                    return@launch
                }

                // 2) Intento de inicio de sesión
                val resultado = authRepository.login(correo, password)
                if (resultado.isSuccess) {
                    alIniciarSesion(correo, onSuccess)
                } else {
                    alFallar(correo, resultado.exceptionOrNull())
                }
            } catch (e: Exception) {
                errorMessage = InvernaderoApp.texto(R.string.error_login_generico)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun alIniciarSesion(correo: String, onSuccess: () -> Unit) {
        val user = authRepository.currentUser
        if (user == null) {
            errorMessage = InvernaderoApp.texto(R.string.error_inesperado)
            return
        }

        lockRepository.limpiar(correo)

        val perfil = userRepository.asegurarPerfil(user.uid, user.email ?: correo)
        if (!perfil.activo) {
            auditRepository.registrarYEsperar(
                AuditCategoria.SESIONES, "LOGIN_RECHAZADO", "Intento de acceso con cuenta deshabilitada"
            )
            authRepository.logout()
            password = ""
            errorMessage = InvernaderoApp.texto(R.string.login_error_deshabilitada)
            return
        }

        auditRepository.registrar(AuditCategoria.SESIONES, "LOGIN_EXITOSO", "Inicio de sesión correcto")
        password = ""
        onSuccess()
    }

    private suspend fun alFallar(correo: String, error: Throwable?) {
        password = ""
        if (error !is CredencialesInvalidasException) {
            errorMessage = error?.message ?: InvernaderoApp.texto(R.string.error_login_generico)
            return
        }

        val nuevo = lockRepository.registrarFallo(correo)
        if (nuevo.bloqueado) {
            bloqueada = true
            errorMessage = mensajeBloqueo
            auditRepository.registrarYEsperar(
                AuditCategoria.SESIONES, "CUENTA_BLOQUEADA",
                "Cuenta bloqueada tras ${LoginAttemptRepository.MAX_INTENTOS} intentos fallidos",
                usuario = correo
            )
        } else {
            val restantes = LoginAttemptRepository.MAX_INTENTOS - nuevo.intentos
            errorMessage = InvernaderoApp.texto(R.string.login_error_credenciales, restantes)
            auditRepository.registrar(
                AuditCategoria.SESIONES, "LOGIN_FALLIDO",
                "Intento fallido ${nuevo.intentos} de ${LoginAttemptRepository.MAX_INTENTOS}",
                usuario = correo
            )
        }
    }
}
