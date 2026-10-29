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
 * Reads no notifications: Android only lets an app with an enabled NotificationListenerService see other
 * apps' media sessions. Enabled over ADB with
 * `cmd notification allow_listener dev.robertovillegas.inicio/dev.robertovillegas.inicio.MediaListener`.
 * Android reconnects it after every boot, which is what wakes HomeGuard.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() = HomeGuard.check(this)
}

data class Song(val title: String, val artist: String, val cover: Bitmap?, val app: String)

/** What's playing right now (Spotify, SmartTube...), if anything. */
class NowPlaying(private val ctx: Context, private val onChange: (Song?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = ctx.getSystemService(MediaSessionManager::class.java)
    private var controllers: List<MediaController> = emptyList()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        follow(list.orEmpty())
    }
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    fun start() {
        val listener = ComponentName(ctx, MediaListener::class.java)
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            follow(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            Log.i("NowPlaying", "Not allowed to read media sessions; see install-inicio.sh")
        }
    }

    fun stop() {
        sessions.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = emptyList()
    }

    private fun follow(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list
        controllers.forEach { it.registerCallback(controllerCallback, main) }
        publish()
    }

    private fun publish() {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val meta = playing?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (playing == null || meta == null || title.isEmpty()) {
            onChange(null)
            return
        }
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val cover = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val app = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(playing.packageName, 0)).toString()
        } catch (e: Exception) {
            ""
        }
        onChange(Song(title, artist, cover, app))
    }
}
