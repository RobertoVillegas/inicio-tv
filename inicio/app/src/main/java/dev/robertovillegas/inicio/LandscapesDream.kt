package dev.robertovillegas.inicio

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import java.util.concurrent.Executors

/**
 * The screensaver: drifting landscapes and the time, which moves to another corner every few minutes so
 * no pixel shows the same thing for long.
 */
class LandscapesDream : DreamService() {
    private lateinit var scene: Scene
    private lateinit var nowPlaying: NowPlaying
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var corner = 0

    private val everyMinute = object : Runnable {
        override fun run() {
            // Night can fall while the TV is already in the screensaver.
            val night = Night.isNight()
            scene.setNight(night)
            worker.execute { Night.applySleepTimeout(this@LandscapesDream, night) }
            main.postDelayed(this, 60_000L)
        }
    }

    private val moveClock = object : Runnable {
        override fun run() {
            corner = (corner + 1) % 4
            scene.info.animate().alpha(0f).setDuration(1500).withEndAction {
                val m = scene.dp(56)
                val info = scene.info.layoutParams as FrameLayout.LayoutParams
                info.gravity = when (corner) {
                    0 -> Gravity.BOTTOM or Gravity.START
                    1 -> Gravity.TOP or Gravity.START
                    2 -> Gravity.TOP or Gravity.END
                    else -> Gravity.BOTTOM or Gravity.END
                }
                info.setMargins(m, m, m, m)
                // The photo credit goes to a corner away from the clock.
                val credit = scene.credit.layoutParams as FrameLayout.LayoutParams
                credit.gravity = when (corner) {
                    0, 1 -> Gravity.BOTTOM or Gravity.END
                    2 -> Gravity.BOTTOM or Gravity.START
                    else -> Gravity.TOP or Gravity.START
                }
                credit.setMargins(m, m, m, m)
                scene.info.requestLayout()
                scene.credit.requestLayout()
                scene.info.animate().alpha(1f).setDuration(1500).start()
            }.start()
            main.postDelayed(this, 4 * 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        scene = Scene(this)
        setContentView(scene)
        nowPlaying = NowPlaying(this) { scene.setSong(it) }
        val m = scene.dp(56)
        (scene.info.layoutParams as FrameLayout.LayoutParams).setMargins(m, m, m, m)
        (scene.credit.layoutParams as FrameLayout.LayoutParams).setMargins(m, m, m, m)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scene.start(75_000L)
        nowPlaying.start()
        main.post(everyMinute)
        worker.execute {
            val w = LocalWeather.fetch(this)
            main.post { scene.setWeather(w); scene.setNight(Night.isNight()) }
        }
        main.postDelayed(moveClock, 4 * 60_000L)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        main.removeCallbacksAndMessages(null)
        nowPlaying.stop()
        scene.stop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scene.release()
        worker.shutdownNow()
    }
}
