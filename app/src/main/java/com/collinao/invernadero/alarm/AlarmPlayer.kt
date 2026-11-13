package com.collinao.invernadero.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Reproduce el sonido de alarma en bucle + vibración hasta que alguien la silencia.
 * Usa el canal de audio de ALARMA: suena aunque el timbre esté en silencio o vibración.
 * Es un singleton para poder detenerla desde cualquier pantalla.
 */
object AlarmPlayer {
    private var player: MediaPlayer? = null
    private var vibrador: Vibrator? = null
    private var volumenPrevio: Int? = null

    val sonando: Boolean get() = player != null

    fun iniciar(context: Context) {
        if (player != null) return
        val app = context.applicationContext

        try {
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            volumenPrevio = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        } catch (e: Exception) {
            // Si el sistema no deja subir el volumen, suena con el que tenga
        }

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(app, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player?.release()
            player = null
        }

        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
            vibrador = vib
        } catch (e: Exception) {
        }
    }

    fun detener(context: Context) {
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null

        vibrador?.cancel()
        vibrador = null

        volumenPrevio?.let { previo ->
            try {
                val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.setStreamVolume(AudioManager.STREAM_ALARM, previo, 0)
            } catch (e: Exception) {
            }
        }
        volumenPrevio = null
    }
}
