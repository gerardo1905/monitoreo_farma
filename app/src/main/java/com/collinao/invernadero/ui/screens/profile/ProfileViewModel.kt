package com.collinao.invernadero.ui.screens.profile

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

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    val email: String = authRepository.currentUser?.email ?: ""

    var esAdmin by mutableStateOf(false)
        private set

    var correoVerificado by mutableStateOf(authRepository.correoVerificado)
        private set

    /** Mensaje informativo de la pantalla (verificación enviada, contraseña cambiada...). */
    var mensaje by mutableStateOf<String?>(null)
        private set

    // --- Cambio de contraseña ---
    var mostrarCambioPassword by mutableStateOf(false)
        private set
    var passActual by mutableStateOf("")
        private set
    var passNueva by mutableStateOf("")
        private set
    var passConfirmar by mutableStateOf("")
        private set
    var errorPassword by mutableStateOf<String?>(null)
        private set
    var cambiandoPassword by mutableStateOf(false)
        private set

    init {
        val uid = authRepository.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    val perfil = userRepository.obtenerPerfil(uid)
                    esAdmin = perfil.rol == "admin" && perfil.activo
                } catch (e: Exception) {
                    esAdmin = false
                }
            }
        }
        viewModelScope.launch {
            authRepository.recargarUsuario()
            correoVerificado = authRepository.correoVerificado
        }
    }

    fun reenviarVerificacion() {
        viewModelScope.launch {
            val resultado = authRepository.enviarVerificacion()
            mensaje = if (resultado.isSuccess) {
                InvernaderoApp.texto(R.string.perfil_verificacion_enviada)
            } else {
                resultado.exceptionOrNull()?.message
            }
        }
    }

    fun abrirCambioPassword() {
        passActual = ""
        passNueva = ""
        passConfirmar = ""
        errorPassword = null
        mostrarCambioPassword = true
    }

    fun cerrarCambioPassword() {
        mostrarCambioPassword = false
        passActual = ""
        passNueva = ""
        passConfirmar = ""
        errorPassword = null
    }

    fun onPassActualChange(v: String) { passActual = v; errorPassword = null }
    fun onPassNuevaChange(v: String) { passNueva = v; errorPassword = null }
    fun onPassConfirmarChange(v: String) { passConfirmar = v; errorPassword = null }

    fun cambiarPassword() {
        when {
            passActual.isBlank() || passNueva.isBlank() || passConfirmar.isBlank() ->
                errorPassword = InvernaderoApp.texto(R.string.error_campos)
            !PasswordPolicy.esValida(passNueva, email) ->
                errorPassword = InvernaderoApp.texto(R.string.error_password_requisitos)
            passNueva != passConfirmar ->
                errorPassword = InvernaderoApp.texto(R.string.error_password_no_coinciden)
            passNueva == passActual ->
                errorPassword = InvernaderoApp.texto(R.string.perfil_error_misma_password)
            else -> viewModelScope.launch {
                cambiandoPassword = true
                val resultado = authRepository.cambiarPassword(passActual, passNueva)
                cambiandoPassword = false
                if (resultado.isSuccess) {
                    auditRepository.registrar(
                        AuditCategoria.CUENTAS, "PASSWORD_CAMBIADA", "El usuario cambió su contraseña"
                    )
                    cerrarCambioPassword()
                    mensaje = InvernaderoApp.texto(R.string.perfil_password_cambiada)
                } else {
                    errorPassword = resultado.exceptionOrNull()?.message
                }
            }
        }
    }
}
