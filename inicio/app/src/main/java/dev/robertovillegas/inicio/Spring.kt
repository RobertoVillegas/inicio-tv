package dev.robertovillegas.inicio

import android.view.Choreographer
import kotlin.math.abs

/**
 * A spring that moves a value toward a target, like the Apple TV app's scrolling: mass 1, stiffness 150,
 * damping 30. Fast start, soft stop, no bounce. If the target changes midway (a held d-pad key), it
 * carries on from where it is instead of restarting.
 */
class Spring(private val apply: (Float) -> Unit) : Choreographer.FrameCallback {
    var target = 0f
        private set
    private var value = 0f
    private var velocity = 0f
    private var lastFrame = 0L
    private var running = false

    fun animateTo(to: Float, from: Float) {
        target = to
        if (running) return
        value = from
        velocity = 0f
        lastFrame = 0L
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        // When the TV drops a frame, step the real elapsed time (capped) in small increments.
        var dt = if (lastFrame == 0L) 1 / 60f else ((frameTimeNanos - lastFrame) / 1e9f).coerceAtMost(1 / 15f)
        lastFrame = frameTimeNanos
        while (dt > 0f) {
            val h = minOf(dt, 1 / 240f)
            velocity += (-STIFFNESS * (value - target) - DAMPING * velocity) * h
            value += velocity * h
            dt -= h
        }
        if (abs(value - target) < 0.5f && abs(velocity) < 10f) {
            value = target
            running = false
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
        apply(value)
    }

    private companion object {
        const val STIFFNESS = 150f
        const val DAMPING = 30f
    }
}
