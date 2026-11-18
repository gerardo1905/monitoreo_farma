package com.collinao.invernadero.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Una acción del usuario guardada en la base local del teléfono. */
data class AccionLocal(
    val id: Long,
    val fecha: Long,
    val tipo: String,
    val detalle: String,
    val estado: String,
    val sincronizadoEn: Long?
)

/**
 * 3.1.2.7  ALMACENAMIENTO TEMPORAL DE DATOS EN LA APLICACIÓN MÓVIL
 *
 * Base de datos SQLite local (bitacora_local.db, tabla "acciones") con cada acción que el usuario
 * envía al dispositivo: comandos de los equipos, cambios de modo y de umbrales.
 *
 *  1. La acción se guarda PRIMERO aquí, con estado PENDIENTE (funciona sin Internet).
 *  2. Se envía a Firebase. Sin conexión, Firebase la deja en cola en el teléfono.
 *  3. Cuando el servidor confirma la escritura (al volver la conexión), pasa a SINCRONIZADO;
 *     si el servidor la rechaza (permisos/validación), queda como RECHAZADO.
 *
 * Se ve en Android Studio > App Inspection > Database Inspector > bitacora_local.db > acciones.
 * 3.1.4.12: no guarda correos, contraseñas ni datos personales; vive en el almacenamiento privado
 * de la app y no se respalda (allowBackup=false).
 */
class BitacoraLocal private constructor(context: Context) :
    SQLiteOpenHelper(context, "bitacora_local.db", null, 1) {

    companion object {
        const val PENDIENTE = "PENDIENTE"
        const val SINCRONIZADO = "SINCRONIZADO"
        const val RECHAZADO = "RECHAZADO"

        /** Se conservan las últimas 500 acciones (el resto se borra para no llenar el teléfono). */
        private const val MAXIMO_REGISTROS = 500

        @Volatile
        private var instancia: BitacoraLocal? = null

        fun iniciar(context: Context) {
            if (instancia == null) {
                synchronized(this) {
                    if (instancia == null) instancia = BitacoraLocal(context.applicationContext)
                }
            }
        }

        fun get(): BitacoraLocal = instancia ?: error("BitacoraLocal.iniciar() no fue llamado")

        /** true si el teléfono está conectado al servidor de Firebase (lo actualiza InvernaderoApp). */
        @Volatile
        var servidorConectado: Boolean = true
    }

    /** Cambia cada vez que se modifica la tabla, para refrescar la pantalla. */
    private val version = MutableStateFlow(0L)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE acciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha INTEGER NOT NULL,
                tipo TEXT NOT NULL,
                detalle TEXT NOT NULL,
                estado TEXT NOT NULL,
                sincronizado_en INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun avisarCambio() {
        version.value = System.nanoTime()
    }

    /** Guarda una acción nueva como PENDIENTE y devuelve su id. */
    fun registrar(tipo: String, detalle: String): Long {
        val valores = ContentValues().apply {
            put("fecha", System.currentTimeMillis())
            put("tipo", tipo.take(40))
            put("detalle", detalle.take(300))
            put("estado", PENDIENTE)
        }
        val id = writableDatabase.insert("acciones", null, valores)
        writableDatabase.execSQL(
            "DELETE FROM acciones WHERE id NOT IN (SELECT id FROM acciones ORDER BY id DESC LIMIT $MAXIMO_REGISTROS)"
        )
        avisarCambio()
        return id
    }

    /** Marca una acción como SINCRONIZADO o RECHAZADO cuando Firebase responde. */
    fun marcar(id: Long, estado: String) {
        val valores = ContentValues().apply {
            put("estado", estado)
            put("sincronizado_en", System.currentTimeMillis())
        }
        writableDatabase.update("acciones", valores, "id = ? AND estado = ?", arrayOf(id.toString(), PENDIENTE))
        avisarCambio()
    }

    /** Marca como SINCRONIZADO todas las pendientes hasta [hastaId] (se usa al reconectar). */
    fun marcarSincronizadasHasta(hastaId: Long): Int {
        val valores = ContentValues().apply {
            put("estado", SINCRONIZADO)
            put("sincronizado_en", System.currentTimeMillis())
        }
        val n = writableDatabase.update(
            "acciones", valores, "id <= ? AND estado = ?", arrayOf(hastaId.toString(), PENDIENTE)
        )
        avisarCambio()
        return n
    }

    fun contarPendientes(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM acciones WHERE estado = ?", arrayOf(PENDIENTE)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun ultimoIdPendiente(): Long =
        readableDatabase.rawQuery("SELECT MAX(id) FROM acciones WHERE estado = ?", arrayOf(PENDIENTE)).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
        }

    fun ultimas(limite: Int = 10): List<AccionLocal> =
        readableDatabase.rawQuery(
            "SELECT id, fecha, tipo, detalle, estado, sincronizado_en FROM acciones ORDER BY id DESC LIMIT $limite",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        AccionLocal(
                            id = c.getLong(0),
                            fecha = c.getLong(1),
                            tipo = c.getString(2),
                            detalle = c.getString(3),
                            estado = c.getString(4),
                            sincronizadoEn = if (c.isNull(5)) null else c.getLong(5)
                        )
                    )
                }
            }
        }

    /** Cantidad de acciones PENDIENTES de sincronizar, en vivo. */
    fun observarPendientes(): Flow<Int> = version.map { contarPendientes() }.flowOn(Dispatchers.IO)

    /** Últimas acciones, actualizadas en vivo cada vez que cambia la tabla. */
    fun observarUltimas(limite: Int = 10): Flow<List<AccionLocal>> =
        version.map { ultimas(limite) }.flowOn(Dispatchers.IO)
}
