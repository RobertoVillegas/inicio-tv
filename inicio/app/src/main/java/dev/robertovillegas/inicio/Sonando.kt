package dev.robertovillegas.inicio

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * No lee notificaciones: Android solo deja leer las sesiones de música de otras apps
 * a quien tenga un NotificationListenerService habilitado. Se habilita por ADB con
 * `cmd notification allow_listener dev.robertovillegas.inicio/.Escucha`.
 * Como Android lo vuelve a conectar después de cada arranque, de paso despierta a Guardia.
 */
class Escucha : NotificationListenerService() {
    override fun onListenerConnected() = Guardia.revisar(this)
}

data class Cancion(val titulo: String, val artista: String, val portada: Bitmap?, val app: String)

/** Lo que está sonando ahora (Spotify, SmartTube, etc.), si algo está sonando. */
class Sonando(private val ctx: Context, private val alCambiar: (Cancion?) -> Unit) {
    private val principal = Handler(Looper.getMainLooper())
    private val sesiones = ctx.getSystemService(MediaSessionManager::class.java)
    private var controles: List<MediaController> = emptyList()

    private val alCambiarSesiones = MediaSessionManager.OnActiveSessionsChangedListener { lista ->
        seguir(lista.orEmpty())
    }
    private val alCambiarControl = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = avisar()
        override fun onMetadataChanged(metadata: MediaMetadata?) = avisar()
        override fun onSessionDestroyed() = avisar()
    }

    fun iniciar() {
        val escucha = ComponentName(ctx, Escucha::class.java)
        try {
            sesiones.addOnActiveSessionsChangedListener(alCambiarSesiones, escucha, principal)
            seguir(sesiones.getActiveSessions(escucha))
        } catch (e: SecurityException) {
            Log.i("Sonando", "Sin permiso para leer la música; ver instalar-inicio.sh")
        }
    }

    fun detener() {
        sesiones.removeOnActiveSessionsChangedListener(alCambiarSesiones)
        controles.forEach { it.unregisterCallback(alCambiarControl) }
        controles = emptyList()
    }

    private fun seguir(lista: List<MediaController>) {
        controles.forEach { it.unregisterCallback(alCambiarControl) }
        controles = lista
        controles.forEach { it.registerCallback(alCambiarControl, principal) }
        avisar()
    }

    private fun avisar() {
        val activo = controles.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val meta = activo?.metadata
        if (activo == null || meta == null) {
            alCambiar(null)
            return
        }
        val titulo = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (titulo.isEmpty()) {
            alCambiar(null)
            return
        }
        val artista = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val portada = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val app = try {
            ctx.packageManager.getApplicationLabel(
                ctx.packageManager.getApplicationInfo(activo.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            ""
        }
        alCambiar(Cancion(titulo, artista, portada, app))
    }
}
