package com.collinao.invernadero.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.collinao.invernadero.R
import com.collinao.invernadero.alarm.AlarmActivity

/**
 * Notificación de alarma crítica: prioridad máxima, sonido de ALARMA (se oye aunque el teléfono esté
 * en silencio de timbre), vibración, se muestra en la pantalla de bloqueo y abre la pantalla de alarma.
 */
object NotificationHelper {
    // El canal anterior ("alertas_criticas") no se puede modificar una vez creado, por eso uno nuevo.
    private const val CHANNEL_ALARMA = "alarma_critica_v2"
    private const val CHANNEL_ANTIGUO = "alertas_criticas"
    private const val ID_ALARMA = 1001

    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(CHANNEL_ANTIGUO)

        val sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val canal = NotificationChannel(
            CHANNEL_ALARMA,
            context.getString(R.string.canal_alarma_nombre),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.canal_alarma_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 500, 800)
            setSound(sonido, atributos)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(canal)
    }

    /**
     * @param silenciosa true para volver a mostrar la notificación sin sonido (cuando el sonido ya
     * lo está reproduciendo la pantalla de alarma).
     */
    fun mostrarAlarma(context: Context, motivo: String, silenciosa: Boolean = false) {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmActivity.EXTRA_MOTIVO, motivo)
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARMA)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.alarma_titulo))
            .setContentText(motivo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(motivo))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pending)
            .setSilent(silenciosa)

        // Pantalla completa solo la primera vez (si el teléfono está bloqueado o apagado)
        if (!silenciosa) builder.setFullScreenIntent(pending, true)

        val notificacion = builder.build()
        // INSISTENT: el sonido se repite hasta que la alarma se silencie
        if (!silenciosa) notificacion.flags = notificacion.flags or Notification.FLAG_INSISTENT

        try {
            NotificationManagerCompat.from(context).notify(ID_ALARMA, notificacion)
        } catch (e: SecurityException) {
            // El usuario no concedió el permiso de notificaciones; la pantalla de alarma igual funciona
        }
    }

    fun cancelarAlarma(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ALARMA)
    }
}
