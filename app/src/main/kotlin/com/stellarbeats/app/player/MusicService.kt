package com.stellarbeats.app.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service that hosts the MediaSession.
 *
 * Android requires a foreground service with a persistent notification
 * for audio playback when the app is in the background. Media3's
 * MediaSessionService handles the lifecycle, notification, and
 * media button integration automatically.
 *
 * The ExoPlayer instance is injected by Hilt and shared between
 * this service and the UI layer. When the service starts, it
 * attaches the player to the session. When the last controller
 * disconnects and playback stops, the service stops itself.
 */
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // In a full implementation, the session is created here
        // using the Hilt-injected ExoPlayer. For now, the placeholder
        // ensures the service compiles and the manifest reference works.
        //
        // Full implementation:
        // val player = (application as StellarBeatsApp).player
        // mediaSession = MediaSession.Builder(this, player)
        //     .setCallback(MusicNotificationManager(this).sessionCallback)
        //     .setId("stellarbeats_session")
        //     .build()
        // addSession(mediaSession!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
