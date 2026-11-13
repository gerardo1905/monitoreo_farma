package com.collinao.invernadero.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SessionViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    // Escucha cuándo cambia la sesión (login/logout), no solo el UID al crear el ViewModel
    private val uidActual: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** false cuando la sesión se cierra por cualquier motivo (botón, inactividad, cuenta deshabilitada). */
    val sesionIniciada: StateFlow<Boolean> = uidActual
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser != null)

    /** Para mostrar u ocultar la pestaña de Registros en la barra inferior. */
    val esAdmin: StateFlow<Boolean> = uidActual
        .flatMapLatest { uid ->
            if (uid != null) userRepository.observarEsAdmin(uid).catch { emit(false) }
            else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cuentaActiva: StateFlow<Boolean> = uidActual
        .flatMapLatest { uid ->
            if (uid != null) userRepository.observarPerfilPropio(uid).map { it.activo }.catch { emit(true) }
            else flowOf(true)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
}
