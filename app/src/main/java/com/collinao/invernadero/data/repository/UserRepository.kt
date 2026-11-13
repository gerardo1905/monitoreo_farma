package com.collinao.invernadero.data.repository

import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.collinao.invernadero.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val usuariosRef: DatabaseReference = Firebase.database.reference.child("usuarios")

    suspend fun asegurarPerfil(uid: String, email: String): UserProfile {
        val ref = usuariosRef.child(uid).child("perfil")
        val snapshot = ref.get().await()
        val existente = snapshot.getValue(UserProfile::class.java)
        if (existente != null) return existente

        val nuevo = UserProfile(email = email, rol = "usuario", activo = true)
        ref.setValue(nuevo).await()
        return nuevo
    }

    suspend fun obtenerPerfil(uid: String): UserProfile {
        val snapshot = usuariosRef.child(uid).child("perfil").get().await()
        return snapshot.getValue(UserProfile::class.java) ?: UserProfile()
    }

    fun observarUsuarios(): Flow<Map<String, UserProfile>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val mapa = mutableMapOf<String, UserProfile>()
                for (child in snapshot.children) {
                    val perfil = child.child("perfil").getValue(UserProfile::class.java)
                    if (perfil != null) mapa[child.key ?: ""] = perfil
                }
                trySend(mapa)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        usuariosRef.addValueEventListener(listener)
        awaitClose { usuariosRef.removeEventListener(listener) }
    }

    fun cambiarRol(uid: String, nuevoRol: String) {
        usuariosRef.child(uid).child("perfil").child("rol").setValue(nuevoRol)
    }

    fun cambiarEstadoActivo(uid: String, activo: Boolean) {
        usuariosRef.child(uid).child("perfil").child("activo").setValue(activo)
    }

    /** true solo si el usuario es admin Y su cuenta está activa. Para mostrar/ocultar opciones de admin. */
    fun observarEsAdmin(uid: String): Flow<Boolean> =
        observarPerfilPropio(uid).map { it.rol == "admin" && it.activo }

    fun observarPerfilPropio(uid: String): Flow<UserProfile> = callbackFlow {
        val ref = usuariosRef.child(uid).child("perfil")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(UserProfile::class.java) ?: UserProfile())
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}