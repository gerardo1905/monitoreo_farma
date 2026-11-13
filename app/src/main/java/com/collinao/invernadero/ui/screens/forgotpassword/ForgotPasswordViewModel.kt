package com.collinao.invernadero.ui.screens.forgotpassword

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
import com.collinao.invernadero.data.repository.LoginAttemptRepository
import com.collinao.invernadero.security.PasswordPolicy
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val lockRepository: LoginAttemptRepository = LoginAttemptRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    private companion object {
        const val ESPERA_ENTRE_ENVIOS_MS = 60_000L
    }

    private var ultimoEnvio = 0L

    var email by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
        successMessage = null
    }

    fun sendResetEmail() {
        val correo = email.trim().lowercase()
        if (correo.isBlank()) {
            errorMessage = InvernaderoApp.texto(R.string.recuperar_error_vacio)
            return
        }
        if (!PasswordPolicy.correoValido(correo)) {
            errorMessage = InvernaderoApp.texto(R.string.error_correo_invalido)
            return
        }
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoEnvio < ESPERA_ENTRE_ENVIOS_MS) {
            errorMessage = InvernaderoApp.texto(R.string.recuperar_error_espera)
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                ultimoEnvio = ahora
                authRepository.resetPassword(correo)
                // Si la cuenta estaba bloqueada, esto habilita un nuevo intento de acceso
                lockRepository.marcarResetSolicitado(correo)
                auditRepository.registrar(
                    AuditCategoria.SESIONES, "RECUPERACION_SOLICITADA",
                    "Solicitud de recuperación de contraseña", usuario = correo
                )
                // Mismo mensaje exista o no la cuenta (no se revela qué correos están registrados)
                successMessage = InvernaderoApp.texto(R.string.recuperar_exito)
            } catch (e: Exception) {
                errorMessage = InvernaderoApp.texto(R.string.recuperar_error_envio)
            } finally {
                isLoading = false
            }
        }
    }
}
