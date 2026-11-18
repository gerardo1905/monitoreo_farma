package com.collinao.invernadero

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.StringRes
import com.collinao.invernadero.alarm.AlarmMonitor
import com.collinao.invernadero.alarm.MonitorService
import com.collinao.invernadero.data.DeviceConfig
import com.collinao.invernadero.data.local.BitacoraLocal
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.repository.DeviceRepository
import com.collinao.invernadero.data.simulator.SimulatorEngine
import com.collinao.invernadero.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class InvernaderoApp : Application() {

    companion object {
        private lateinit var instancia: InvernaderoApp

        /** 10 MB de caché local: alcanza para 24 h de lecturas y la cola de cambios pendientes. */
        private const val CACHE_LOCAL_BYTES = 10L * 1024 * 1024

        /**
         * Texto de strings.xml desde clases sin contexto (ViewModels, repositorios).
         * Mantiene todos los mensajes al usuario en un solo archivo de recursos.
         */
        fun texto(@StringRes id: Int, vararg argumentos: Any): String =
            instancia.getString(id, *argumentos)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var simulator: SimulatorEngine
        private set

    lateinit var alarmMonitor: AlarmMonitor
        private set

    /** true mientras alguna pantalla de la app está visible. */
    var enPrimerPlano: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instancia = this

        // 3.1.2.7 Almacenamiento temporal en el teléfono: Firebase guarda en disco los datos
        // leídos y las escrituras pendientes (comandos, ajustes, auditoría). Sin conexión, la app
        // sigue funcionando con lo guardado y, al reconectar, sincroniza sola con el servidor.
        // Debe llamarse ANTES de cualquier otro uso de la base de datos.
        Firebase.database.setPersistenceEnabled(true)
        Firebase.database.setPersistenceCacheSizeBytes(CACHE_LOCAL_BYTES)

        // 3.1.2.7: base de datos local (SQLite) con las acciones del usuario y su estado de sincronización
        BitacoraLocal.iniciar(this)

        NotificationHelper.crearCanal(this)
        val deviceRef = Firebase.database.reference.child("dispositivos/${DeviceConfig.DEVICE_ID}")
        simulator = SimulatorEngine(deviceRef, appScope, applicationContext)
        alarmMonitor = AlarmMonitor(applicationContext, appScope)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var visibles = 0
            override fun onActivityStarted(activity: Activity) {
                visibles++
                enPrimerPlano = true
            }

            override fun onActivityStopped(activity: Activity) {
                visibles--
                enPrimerPlano = visibles > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Arranca el vigilante de alarma y el servicio que lo mantiene vivo en segundo plano.
     * El simulador solo arranca si DeviceConfig.USAR_SIMULADOR = true (con el ESP32 real no corre).
     */
    fun iniciarMonitoreo() {
        // 3.1.2.7: con sesión iniciada (las reglas exigen cuenta activa) se mantiene la copia local
        DeviceRepository().mantenerSincronizado(true)
        vigilarSincronizacion()
        simulator.start()
        alarmMonitor.start()
        MonitorService.iniciar(this)
    }

    private var sincronizacionJob: Job? = null

    /**
     * 3.1.2.7 / 3.1.1.3: SINCRONIZACIÓN AL RECONECTAR.
     * Escucha la conexión con el servidor. Al volver, deja un registro SINCRONIZACION_LOCAL en el
     * historial de acciones. Firebase procesa las escrituras en orden, así que cuando ese registro se
     * confirma, todas las acciones hechas sin conexión ya llegaron: se marcan como SINCRONIZADO en la
     * base local (incluidas las que quedaron pendientes de una sesión anterior de la app).
     */
    private fun vigilarSincronizacion() {
        if (sincronizacionJob != null) return
        sincronizacionJob = appScope.launch {
            DeviceRepository().observarConexionServidor()
                .catch { }
                .collect { conectado ->
                    BitacoraLocal.servidorConectado = conectado
                    val bitacora = BitacoraLocal.get()
                    val pendientes = bitacora.contarPendientes()
                    if (conectado && pendientes > 0) {
                        val hastaId = bitacora.ultimoIdPendiente()
                        AuditRepository().registrarConConfirmacion(
                            AuditCategoria.DISPOSITIVOS, "SINCRONIZACION_LOCAL",
                            "Conexión restablecida: se sincronizaron $pendientes acciones realizadas sin conexión en este teléfono"
                        ) { bitacora.marcarSincronizadasHasta(hastaId) }
                    }
                }
        }
    }

    fun detenerMonitoreo() {
        sincronizacionJob?.cancel(); sincronizacionJob = null
        DeviceRepository().mantenerSincronizado(false)
        alarmMonitor.stop()
        simulator.stop()
        MonitorService.detener(this)
    }

    /**
     * Cierre de sesión ordenado: primero se deja el registro y se apaga todo (mientras aún hay
     * sesión para poder escribir en la base) y al final se cierra la sesión de Firebase.
     */
    suspend fun cerrarSesion(tipo: String = "LOGOUT", detalle: String = "Cierre de sesión") {
        AuditRepository().registrarYEsperar(AuditCategoria.SESIONES, tipo, detalle)
        sincronizacionJob?.cancel(); sincronizacionJob = null
        DeviceRepository().mantenerSincronizado(false)
        alarmMonitor.stop()
        simulator.detenerYEsperar()
        MonitorService.detener(this)
        Firebase.auth.signOut()
    }
}
