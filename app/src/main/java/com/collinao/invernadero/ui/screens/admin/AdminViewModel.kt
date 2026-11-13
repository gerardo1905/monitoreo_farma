package com.collinao.invernadero.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.model.UserProfile
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AdminViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) : ViewModel() {

    /** uid del admin que está usando el panel (para no dejarle bloquearse a sí mismo). */
    val miUid: String? = Firebase.auth.currentUser?.uid

    val esAdmin: StateFlow<Boolean?> = run {
        val flujo: Flow<Boolean?> =
            if (miUid != null) userRepository.observarEsAdmin(miUid) else emptyFlow()
        flujo.catch { emit(false) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    val usuarios: StateFlow<List<Pair<String, UserProfile>>> = userRepository.observarUsuarios()
        .map { mapa -> mapa.toList().sortedBy { it.second.email } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cambiarRol(uid: String, email: String, esAdmin: Boolean) {
        if (uid == miUid) return // un admin no puede quitarse el rol a sí mismo
        userRepository.cambiarRol(uid, if (esAdmin) "admin" else "usuario")
        auditRepository.registrar(
            AuditCategoria.CUENTAS, "ROL_CAMBIADO",
            "$email ahora es ${if (esAdmin) "administrador" else "usuario"}"
        )
    }

    fun cambiarEstado(uid: String, email: String, activo: Boolean) {
        if (uid == miUid) return // un admin no puede deshabilitarse a sí mismo
        userRepository.cambiarEstadoActivo(uid, activo)
        auditRepository.registrar(
            AuditCategoria.CUENTAS,
            if (activo) "CUENTA_HABILITADA" else "CUENTA_DESHABILITADA",
            if (activo) "Se habilitó la cuenta de $email" else "Se deshabilitó la cuenta de $email"
        )
    }
}
