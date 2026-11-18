package com.collinao.invernadero.data.repository

import com.collinao.invernadero.data.local.BitacoraLocal
import com.collinao.invernadero.data.model.AuditEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitácora de auditoría: registros/<categoria>/<idPush>.
 * Los registros solo se pueden AGREGAR (las reglas impiden editar o borrar) y solo el admin los lee.
 * La hora la pone el servidor de Firebase, no el teléfono, para que no se pueda falsear.
 */
class AuditRepository {
    private val registrosRef: DatabaseReference = Firebase.database.reference.child("registros")

    private fun construir(tipo: String, detalle: String, usuario: String?): Map<String, Any> {
        val user = Firebase.auth.currentUser
        // 3.1.2.7: si la acción se hace SIN conexión, el registro queda en cola en el teléfono y llega al
        // servidor al reconectar. Se anota la hora real en que se hizo, para el historial de acciones.
        val detalleFinal = if (BitacoraLocal.servidorConectado) detalle else {
            val hora = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
            "$detalle [realizada sin conexión el $hora]"
        }
        return mapOf(
            "tipo" to tipo,
            "detalle" to detalleFinal.take(300),
            "usuario" to (usuario ?: user?.email ?: "Anónimo").take(100),
            "uid" to (user?.uid ?: ""),
            "timestamp" to ServerValue.TIMESTAMP
        )
    }

    /** FALLA SEGURA (fail-safe): registra y sigue (no bloquea). Si falla por red o permisos, no rompe la app. */
    fun registrar(categoria: String, tipo: String, detalle: String, usuario: String? = null) {
        try {
            registrosRef.child(categoria).push().setValue(construir(tipo, detalle, usuario))
        } catch (e: Exception) {
            // La bitácora nunca debe tumbar la app
        }
    }

    /** Registra y avisa en [alConfirmar] cuando el servidor procesa el registro (aunque sea al reconectar). */
    fun registrarConConfirmacion(categoria: String, tipo: String, detalle: String, alConfirmar: (Boolean) -> Unit) {
        try {
            registrosRef.child(categoria).push().setValue(construir(tipo, detalle, null))
                .addOnCompleteListener { alConfirmar(it.isSuccessful) }
        } catch (e: Exception) {
            alConfirmar(false)
        }
    }

    /** Igual que registrar, pero espera la confirmación (máx. 3 s). Útil antes de cerrar sesión. */
    suspend fun registrarYEsperar(categoria: String, tipo: String, detalle: String, usuario: String? = null) {
        try {
            withTimeoutOrNull(3000) {
                registrosRef.child(categoria).push().setValue(construir(tipo, detalle, usuario)).await()
            }
        } catch (e: Exception) {
            // idem
        }
    }

    fun observar(categoria: String, limite: Int = 200): Flow<List<AuditEvent>> = callbackFlow {
        val consulta = registrosRef.child(categoria).limitToLast(limite)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = snapshot.children.mapNotNull { hijo ->
                    hijo.getValue(AuditEvent::class.java)?.copy(id = hijo.key ?: "")
                }
                trySend(lista.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        consulta.addValueEventListener(listener)
        awaitClose { consulta.removeEventListener(listener) }
    }
}
