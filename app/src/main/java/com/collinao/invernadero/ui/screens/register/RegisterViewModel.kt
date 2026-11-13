package com.collinao.invernadero.ui.screens.register

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
import com.collinao.invernadero.data.repository.UserRepository
import com.collinao.invernadero.security.PasswordPolicy
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun onConfirmPasswordChange(value: String) {
        confirmPassword = value
        errorMessage = null
    }

    fun register(onSuccess: () -> Unit) {
        val correo = email.trim().lowercase()
        if (correo.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            errorMessage = InvernaderoApp.texto(R.string.error_campos)
            return
        }
        if (!PasswordPolicy.correoValido(correo)) {
            errorMessage = InvernaderoApp.texto(R.string.error_correo_invalido)
            return
        }
        if (!PasswordPolicy.esValida(password, correo)) {
            errorMessage = InvernaderoApp.texto(R.string.error_password_requisitos)
            return
        }
        if (password != confirmPassword) {
            errorMessage = InvernaderoApp.texto(R.string.error_password_no_coinciden)
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val resultado = authRepository.register(correo, password)
                if (resultado.isSuccess) {
                    val user = authRepository.currentUser
                    if (user != null) {
                        userRepository.asegurarPerfil(user.uid, correo)
                        auditRepository.registrar(
                            AuditCategoria.CUENTAS, "CUENTA_CREADA", "Nueva cuenta registrada: $correo"
                        )
                        // Correo de verificación (no bloquea el registro si falla)
                        authRepository.enviarVerificacion()
                    }
                    password = ""
                    confirmPassword = ""
                    onSuccess()
                } else {
                    errorMessage = resultado.exceptionOrNull()?.message ?: InvernaderoApp.texto(R.string.error_registro)
                }
            } catch (e: Exception) {
                errorMessage = InvernaderoApp.texto(R.string.error_registro)
            } finally {
                isLoading = false
            }
        }
    }
}
