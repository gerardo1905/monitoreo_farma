package com.collinao.invernadero.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.model.AuditEvent
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Registros de auditoría (solo administradores). Cada categoría se consulta por separado.
 * Estado de esAdmin: null = comprobando, true/false = resultado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val auditRepository: AuditRepository = AuditRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    val esAdmin: StateFlow<Boolean?> = run {
        val uid = Firebase.auth.currentUser?.uid
        val flujo: Flow<Boolean?> =
            if (uid != null) userRepository.observarEsAdmin(uid) else emptyFlow()
        flujo.catch { emit(false) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    private val _categoria = MutableStateFlow(AuditCategoria.SESIONES)
    val categoria: StateFlow<String> = _categoria

    fun seleccionarCategoria(valor: String) {
        _categoria.value = valor
    }

    val registros: StateFlow<List<AuditEvent>> = _categoria
        .flatMapLatest { cat ->
            auditRepository.observar(cat).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
