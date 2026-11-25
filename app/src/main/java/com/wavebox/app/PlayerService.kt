package com.wavebox.app

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.IOException

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        // YT Music songs are queued as ytm://<videoId>; the real (short-lived) stream link is looked up
        // here, just before ExoPlayer opens the stream, and re-looked-up if the stream is reopened later.
        val resolver = ResolvingDataSource.Resolver { spec ->
            val u = spec.uri
            if (u.scheme == "ytm") {
                val vid = u.toString().removePrefix("ytm://")
                val a = try {
                    YtSource.resolveCached(vid, Prefs(applicationContext).ytPlayQuality)
                } catch (e: Exception) {
                    throw IOException("Couldn't get the YT Music stream: ${e.message}", e)
                }
                spec.withUri(Uri.parse(a.url))
            } else spec
        }
        val dataSources = ResolvingDataSource.Factory(DefaultDataSource.Factory(this), resolver)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
