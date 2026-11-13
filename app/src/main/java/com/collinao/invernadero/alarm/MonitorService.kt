package com.collinao.invernadero.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.MainActivity
import com.collinao.invernadero.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Servicio en primer plano ("Monitoreo activo"). Mantiene viva la app con la pantalla apagada o en
 * segundo plano para que el vigilante de la alarma siga escuchando los sensores y la alarma suene.
 * Si el sistema lo reinicia, retoma el monitoreo por su cuenta mientras haya una sesión abierta.
 */
class MonitorService : Service() {

    companion object {
        private const val CANAL = "monitoreo_activo"
        private const val ID_NOTIFICACION = 3001

        fun iniciar(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            } catch (e: Exception) {
                // Si el sistema no lo permite en este momento, la app sigue funcionando en primer plano
            }
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanal()
        val notificacion = construirNotificacion()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_NOTIFICACION, notificacion)
        }

        if (Firebase.auth.currentUser == null) {
            // Reinicio del sistema sin sesión abierta: no hay nada que vigilar
            stopSelf()
            return START_NOT_STICKY
        }

        (application as InvernaderoApp).also {
            it.simulator.start()
            it.alarmMonitor.start()
        }
        return START_STICKY
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canal = NotificationChannel(CANAL, getString(R.string.canal_monitoreo_nombre), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.canal_monitoreo_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(canal)
    }

    private fun construirNotificacion(): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.monitoreo_titulo))
            .setContentText(getString(R.string.monitoreo_texto))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(abrir)
            .build()
    }
}
