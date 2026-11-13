package com.collinao.invernadero

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.StringRes
import com.collinao.invernadero.alarm.AlarmMonitor
import com.collinao.invernadero.alarm.MonitorService
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.repository.AuditRepository
import com.collinao.invernadero.data.simulator.SimulatorEngine
import com.collinao.invernadero.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class InvernaderoApp : Application() {

    companion object {
        private lateinit var instancia: InvernaderoApp

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
        NotificationHelper.crearCanal(this)
        val deviceRef = Firebase.database.reference.child("dispositivos/sim-001")
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

    /** Arranca simulador, vigilante de alarma y el servicio que los mantiene vivos en segundo plano. */
    fun iniciarMonitoreo() {
        simulator.start()
        alarmMonitor.start()
        MonitorService.iniciar(this)
    }

    fun detenerMonitoreo() {
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
        alarmMonitor.stop()
        simulator.detenerYEsperar()
        MonitorService.detener(this)
        Firebase.auth.signOut()
    }
}
