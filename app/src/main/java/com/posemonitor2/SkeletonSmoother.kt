package com.posemonitor2

import com.core.poseengine.model.Keypoint2d
import com.core.poseengine.model.Skeleton2d
import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter for 2-D pose keypoint smoothing.
 *
 * ── Why One Euro Filter? ─────────────────────────────────────────────────────
 * Standard low-pass (EMA) filters reduce noise but introduce lag — the skeleton
 * "trails" the body during fast movements. One Euro adapts dynamically:
 *
 *   • When the signal changes SLOWLY (static hold)   → strong smoothing (no jitter)
 *   • When the signal changes QUICKLY (movement)      → weak smoothing (no lag)
 *
 * The key insight is using the *speed* of the signal to modulate the cutoff
 * frequency of a low-pass filter in real time.
 *
 * ── Algorithm ────────────────────────────────────────────────────────────────
 * For each scalar signal x at time t:
 *   1. Estimate the derivative dx̂ using a fixed-cutoff low-pass filter
 *   2. Compute adaptive cutoff: fc = fcMin + β × |dx̂|
 *   3. Smooth x with the adaptive cutoff low-pass filter
 *
 * Reference: Casiez, G., et al. "1€ Filter: A Simple Speed-Based Low-pass
 *   Filter for Noisy Input in Interactive Systems." CHI 2012.
 *
 * ── Parameters (tuned for MediaPipe on Android) ──────────────────────────────
 * [minCutoff]  Minimum frequency (Hz) — controls smoothing at rest.
 *              Smaller → smoother but more lag on slow start.
 *              Default 1.5 Hz: removes MediaPipe's ~2-4% frame noise.
 *
 * [beta]       Speed coefficient — controls how fast filtering relaxes.
 *              Larger → less lag during fast moves.
 *              Default 0.07: good balance for exercise at ~30fps.
 *
 * [dCutoff]    Derivative low-pass cutoff (Hz). Usually 1.0.
 */
class SkeletonSmoother(
    private val minCutoff: Double = 1.5,
    private val beta: Double      = 0.07,
    private val dCutoff: Double   = 1.0,
) {
    // Each keypoint has x and y independently filtered.
    // 17 keypoints × 2 axes = 34 scalar filters each for signal and derivative.
    private val xFilters  = Array(17) { OneEuroScalar(minCutoff, beta, dCutoff) }
    private val yFilters  = Array(17) { OneEuroScalar(minCutoff, beta, dCutoff) }

    private var lastTimestampNs: Long = -1L

    /**
     * Smooth a skeleton frame. Returns a new [Skeleton2d] with filtered keypoint
     * positions. Confidence values are passed through unchanged.
     *
     * Call once per camera frame. The first call initialises the filter state
     * (no output delay — the first frame is returned as-is but primes the filter).
     */
    fun smooth(skeleton: Skeleton2d): Skeleton2d {
        val nowNs = System.nanoTime()
        val dt = if (lastTimestampNs < 0L) {
            1.0 / 30.0  // Assume 30fps on first frame
        } else {
            ((nowNs - lastTimestampNs) / 1_000_000_000.0).coerceIn(1.0 / 120.0, 1.0 / 5.0)
        }
        lastTimestampNs = nowNs

        val smoothed = skeleton.keypoints.mapIndexed { i, kpt ->
            if (kpt.confidence < 0.15) {
                // Low-confidence keypoint — reset filter state so stale history
                // doesn't corrupt the next valid frame. Pass through as-is.
                xFilters[i].reset()
                yFilters[i].reset()
                kpt
            } else {
                Keypoint2d(
                    x          = xFilters[i].filter(kpt.x, dt),
                    y          = yFilters[i].filter(kpt.y, dt),
                    confidence = kpt.confidence,
                )
            }
        }

        return Skeleton2d(smoothed)
    }

    /** Reset all filter state (call when camera is toggled or session restarts). */
    fun reset() {
        lastTimestampNs = -1L
        xFilters.forEach { it.reset() }
        yFilters.forEach { it.reset() }
    }
}

// ── One Euro scalar filter ────────────────────────────────────────────────────

/**
 * One Euro Filter for a single scalar signal.
 *
 * Internally maintains two exponential moving averages:
 *   - [xHat] for the signal itself (with adaptive cutoff)
 *   - [dxHat] for the derivative (with fixed [dCutoff])
 */
private class OneEuroScalar(
    private val minCutoff: Double,
    private val beta:      Double,
    private val dCutoff:   Double,
) {
    private var xHat:  Double? = null   // Filtered signal
    private var dxHat: Double? = null   // Filtered derivative

    fun filter(x: Double, dt: Double): Double {
        val prevX  = xHat
        val prevDx = dxHat

        // Estimate derivative
        val dx = if (prevX == null) 0.0 else (x - prevX) / dt

        // Smooth derivative with fixed cutoff
        val alphaDx = alpha(dt, dCutoff)
        val dxFiltered = if (prevDx == null) dx else prevDx + alphaDx * (dx - prevDx)
        dxHat = dxFiltered

        // Adaptive cutoff based on speed of change
        val cutoff = minCutoff + beta * abs(dxFiltered)
        val alphaX = alpha(dt, cutoff)

        // Smooth signal with adaptive cutoff
        val xFiltered = if (prevX == null) x else prevX + alphaX * (x - prevX)
        xHat = xFiltered

        return xFiltered
    }

    fun reset() {
        xHat  = null
        dxHat = null
    }

    /** Low-pass filter alpha for given dt and cutoff frequency (Hz). */
    private fun alpha(dt: Double, cutoff: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
}
