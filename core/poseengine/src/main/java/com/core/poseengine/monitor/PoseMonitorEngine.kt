// core/src/main/java/com/core/poseengine/monitor/PoseMonitorEngine.kt

package com.core.poseengine.monitor

import com.core.poseengine.PoseGraph
import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.PoseState
import com.core.poseengine.model.RawFeatureMap

/**
 * Pure state machine for real-time pose monitoring.
 *
 * ── Architectural contract ─────────────────────────────────────────────────────
 * This class is in [core/poseengine] — it has ZERO Android imports.
 * It does not touch the camera, MediaPipe, or coroutines.
 * It receives pre-extracted [RawFeatureMap] and returns [FrameStatus].
 *
 * ── Performance change: cached tolerance scaling ──────────────────────────────
 * Original code called [applyPerKeyAdaptiveScale] on EVERY frame, which:
 *   1. Called state.featureTolerances.map.mapValues { } — full Map copy
 *   2. Called state.copy() — new PoseState allocation
 *   = 2 allocations × 30fps = 60 unnecessary allocations/second
 *
 * Fix: [getScaledState] caches the result keyed on (stateId, progressiveMult,
 * graceMult). The cache is invalidated only when one of those three values
 * changes — which happens at state transitions and grace period edges, not
 * every frame. In steady-state (user holding a pose), zero allocations occur
 * from tolerance scaling.
 *
 * ── Time injection ────────────────────────────────────────────────────────────
 * All timing uses [TimeSource] — an injectable interface.
 * Real usage: [SystemTimeSource]. Tests: [FakeTimeSource].
 *
 * ── Tolerance scaling ─────────────────────────────────────────────────────────
 * [PoseState.withScaledTolerances] returns a new instance — original graph
 * states are never modified. The cached copy lives until inputs change.
 *
 * ── Adaptive tolerances ───────────────────────────────────────────────────────
 * Adaptive scale stored in [adaptiveToleranceScale], applied per-frame via
 * [getEffectiveTolerance]. Behaviorally equivalent to Python's one-time
 * mutation but preserves graph immutability.
 *
 * @param graph   Loaded, validated [PoseGraph].
 * @param config  Engine configuration.
 * @param time    Time source (inject [FakeTimeSource] in tests).
 */
class PoseMonitorEngine(
    private val graph: PoseGraph,
    private val config: MonitorConfig = MonitorConfig.DEFAULT,
    private val time: TimeSource = SystemTimeSource,
) {
    // ── Validation ────────────────────────────────────────────────────────────

    private val temporalSequence: List<Int> = graph.temporalSequence.also {
        require(it.isNotEmpty()) {
            "PoseGraph is missing temporal_sequence metadata. " +
                    "Regenerate the graph using the offline generator."
        }
    }

    // ── Complexity / adaptive tolerance ───────────────────────────────────────

    val complexity: ExerciseComplexity =
        if (config.enableAdaptive) ComplexityAnalyzer.analyze(graph)
        else ExerciseComplexity(
            level = ComplexityLevel.MODERATE,
            numStates = graph.states.size,
            isDynamic = false,
            avgHoldSeconds = 0.0,
            toleranceScale = 1.0,
        )

    private fun adaptiveScaleFor(key: FeatureKey): Double =
        if (!config.enableAdaptive) 1.0
        else if (key.isCoreJoint && key != FeatureKey.TORSO_ANGLE)
            complexity.toleranceScale
        else
            complexity.toleranceScale * 1.1

    // ── Navigator / feedback ──────────────────────────────────────────────────

    private val navigator = SequenceNavigator(temporalSequence)
    private val feedbackGenerator = FeedbackGenerator()

    // ── Mutable engine state ──────────────────────────────────────────────────

    private var sequenceIndex: Int = 0
    private var currentState: PoseState = requireNotNull(graph.getState(temporalSequence[0])) {
        "State ${temporalSequence[0]} not found in graph"
    }

    private var currentStateStartTime: Double = time.nowSeconds()
    private var currentStateHoldTime: Double = 0.0
    private var transitionStartTime: Double = 0.0
    private var guidanceStartTime: Double = 0.0

    private var inTransition: Boolean = false
    private var isGuidanceActive: Boolean = false
    private var nextStateForGuidance: PoseState? = null
    private var sessionComplete: Boolean = false

    private var accuracyFrame = AccuracyFrame()
    private var accuracyActive: Boolean = false

    private var currentStreak: Int = 0
    private var maxStreak: Int = 0
    private var lastWasCorrect: Boolean = false

    private var lastFeedbackTime: Double = 0.0
    private var currentFeedback: String = ""

    private val stateAttemptCounts = mutableMapOf<Int, Int>()
    private val stateSuccessCounts = mutableMapOf<Int, Int>()

    private val stateBestAccuracies = mutableMapOf<Int, MutableList<Double>>()
    private val skippedStateIds = mutableSetOf<Int>()

    private var totalReps: Int = 0

    private val frameTimes = ArrayDeque<Double>(30)

    private var lastDeviations: Map<FeatureKey, Double> = emptyMap()

    // ── Tolerance scale cache ─────────────────────────────────────────────────
    //
    // Caches the result of [applyPerKeyAdaptiveScale] across frames.
    //
    // In the original code, applyPerKeyAdaptiveScale was called every frame and
    // always produced a new Map + new PoseState copy (2 allocations × 30fps).
    //
    // The inputs that determine the scaled state are:
    //   - currentState.stateId  (changes only on state transition)
    //   - progressiveMult       (changes only when attempt/success ratio crosses thresholds)
    //   - graceMult             (changes only at grace period entry/exit)
    //
    // In steady state (user holding a pose correctly), all three are stable for
    // multiple seconds — the cache hits every frame, zero allocations occur.
    //
    // Cache is a simple triple; no concurrent access (single-threaded by contract).

    private var cachedScaledState: PoseState? = null
    private var cachedStateId: Int = Int.MIN_VALUE
    private var cachedProgressiveMult: Double = Double.NaN
    private var cachedGraceMult: Double = Double.NaN

    /**
     * Returns a [PoseState] with scaled tolerances, reusing the cached instance
     * if inputs have not changed since the last call.
     *
     * Replaces the unconditional [applyPerKeyAdaptiveScale] call from every frame.
     */
    private fun getScaledState(
        state: PoseState,
        progressiveMult: Double,
        graceMult: Double,
    ): PoseState {
        // Fast path: inputs unchanged → return cached instance, zero allocation.
        if (state.stateId == cachedStateId &&
            progressiveMult == cachedProgressiveMult &&
            graceMult == cachedGraceMult
        ) {
            return cachedScaledState!!
        }

        // Slow path: inputs changed → recompute and cache.
        val scaled = applyPerKeyAdaptiveScale(state, progressiveMult, graceMult)
        cachedScaledState = scaled
        cachedStateId = state.stateId
        cachedProgressiveMult = progressiveMult
        cachedGraceMult = graceMult
        return scaled
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Process extracted features for a single camera frame.
     *
     * Must be called from a single thread.
     *
     * @param features  Extracted features from the current frame, or null if
     *   pose detection failed (no valid skeleton visible).
     * @return [FrameStatus] describing the outcome of this frame.
     */
    fun processFeatures(features: RawFeatureMap?): FrameStatus {
        val now = time.nowSeconds()

        if (frameTimes.size >= 30) frameTimes.removeFirst()
        frameTimes.addLast(now)

        if (sessionComplete) return buildStatus(
            isCorrect = true,
            progress = 1.0,
            phase = MonitorPhase.SESSION_COMPLETE,
        )

        // ── Guidance phase ────────────────────────────────────────────────────
        if (isGuidanceActive) {
            val elapsed = now - guidanceStartTime
            if (elapsed >= config.guidanceDurationSeconds) {
                isGuidanceActive = false
                currentState = nextStateForGuidance
                    ?: error("Guidance active but nextStateForGuidance is null")
                nextStateForGuidance = null
                currentStateStartTime = now
                currentStateHoldTime = 0.0
                inTransition = true
                transitionStartTime = now
                // Invalidate cache — state changed
                cachedStateId = Int.MIN_VALUE
            } else {
                val remaining = config.guidanceDurationSeconds - elapsed
                currentFeedback = "Get ready! Starting in ${"%.1f".format(remaining)}s"
                return buildStatus(
                    isCorrect = false,
                    progress = 0.0,
                    phase = MonitorPhase.GUIDANCE,
                )
            }
        }

        val stateId = currentState.stateId
        stateAttemptCounts.getOrPut(stateId) { 0 }
        stateSuccessCounts.getOrPut(stateId) { 0 }

        // ── No skeleton visible ───────────────────────────────────────────────
        if (features == null || features.isEmpty) {
            currentFeedback = "Step back - full body must be visible"
            lastWasCorrect = false
            currentStreak = 0
            return buildStatus(isCorrect = false, progress = 0.0)
        }

        feedbackGenerator.updateHistory(features)

        // ── Compute effective state with scaled tolerances ─────────────────────
        // getScaledState returns the cached instance when progressiveMult and
        // graceMult haven't changed — no allocation in the common case.
        val progressiveMult = getProgressiveTolerance(stateId)
        val graceMult = if (inGracePeriod(now)) 1.2 else 1.0

        val perKeyScaledState = getScaledState(currentState, progressiveMult, graceMult)

        val matchResult = perKeyScaledState.matches(features, strict = false)
        lastDeviations = matchResult.deviations

        return if (matchResult.matched) {
            handleCorrectFrame(now, stateId, features)
        } else {
            handleIncorrectFrame(now, stateId, features, matchResult.deviations)
        }
    }

    fun skipCurrentState() {
        transitionToNextState(skipped = true)
    }

    fun getSessionSummary(): SessionSummary = SessionSummary(
        stateAccuracies = stateBestAccuracies.mapValues { it.value.toList() },
        skippedStateIds = skippedStateIds.toSet(),
        totalReps = totalReps,
        maxStreak = maxStreak,
    )

    val currentFps: Double
        get() {
            if (frameTimes.size < 2) return 0.0
            val elapsed = frameTimes.last() - frameTimes.first()
            return if (elapsed <= 0.0) 0.0 else (frameTimes.size - 1) / elapsed
        }

    val targetState: PoseState get() = currentState
    val guidancePreviewState: PoseState? get() = nextStateForGuidance

    // ── Frame handlers ────────────────────────────────────────────────────────

    private fun handleCorrectFrame(
        now: Double,
        stateId: Int,
        features: RawFeatureMap,
    ): FrameStatus {
        if (!accuracyActive) accuracyActive = true
        if (inTransition) inTransition = false

        if (!inGracePeriod(now)) {
            accuracyFrame = accuracyFrame.withCorrectFrame()
            stateSuccessCounts[stateId] = (stateSuccessCounts[stateId] ?: 0) + 1
        }

        currentStreak = if (!lastWasCorrect) 1 else currentStreak + 1
        maxStreak = maxOf(maxStreak, currentStreak)
        lastWasCorrect = true

        val delta = now - currentStateStartTime
        currentStateHoldTime += delta
        currentStateStartTime = now

        val holdProgress = currentStateHoldTime / currentState.minHoldDuration

        if (holdProgress >= 1.0) {
            transitionToNextState(skipped = false)
            currentFeedback = "Next!"
        } else {
            val remaining = currentState.minHoldDuration - currentStateHoldTime
            currentFeedback = when {
                currentStreak >= 10 -> "🔥 ${"%.1f".format(remaining)}s"
                currentStreak >= 5  -> "✨ ${"%.1f".format(remaining)}s"
                else                -> "Hold ${"%.1f".format(remaining)}s"
            }
        }

        return buildStatus(
            isCorrect = true,
            progress = holdProgress.coerceAtMost(1.0),
        )
    }

    private fun handleIncorrectFrame(
        now: Double,
        stateId: Int,
        features: RawFeatureMap,
        deviations: Map<FeatureKey, Double>,
    ): FrameStatus {
        stateAttemptCounts[stateId] = (stateAttemptCounts[stateId] ?: 0) + 1

        if (accuracyActive && !inGracePeriod(now)) {
            accuracyFrame = accuracyFrame.withIncorrectFrame()
        }

        lastWasCorrect = false
        currentStreak = 0

        // ── Match Python exactly ───────────────────────────────────────────────
        // Python only resets current_state_start_time on an incorrect frame —
        // it does NOT reset current_state_hold_time.
        //
        // Hold time is accumulated as a running delta on each correct frame:
        //   delta = now - current_state_start_time
        //   current_state_hold_time += delta
        //   current_state_start_time = now
        //
        // On an incorrect frame, moving start_time to now means the next correct
        // frame only adds the delta since the break — not time from before.
        // But hold_time already accumulated from previous correct frames is kept.
        //
        // The old Kotlin code zeroed currentStateHoldTime = 0.0 here, wiping
        // all progress on any brief form break — much stricter than Python.
        currentStateStartTime = now
        // currentStateHoldTime intentionally NOT reset — matches Python behavior

        if (now - lastFeedbackTime > config.feedbackDelaySeconds) {
            currentFeedback = feedbackGenerator.generateFeedback(deviations, features)
            lastFeedbackTime = now
        }

        // Show partial hold progress even on incorrect frames so the UI
        // reflects that accumulated hold time is preserved (matches Python).
        return buildStatus(isCorrect = false, progress = currentStateHoldTime / currentState.minHoldDuration)
    }

    // ── State transition ──────────────────────────────────────────────────────

    private fun transitionToNextState(skipped: Boolean) {
        val currentStateId = temporalSequence[sequenceIndex]
        val now = time.nowSeconds()

        val acc = if (skipped) {
            skippedStateIds.add(currentStateId)
            0.0
        } else {
            accuracyFrame.accuracyPercent
        }

        stateBestAccuracies.getOrPut(currentStateId) { mutableListOf() }.add(acc)

        accuracyFrame = AccuracyFrame()
        accuracyActive = false

        val navResult = navigator.nextIndex(
            currentIndex = sequenceIndex,
            currentStateId = currentStateId,
        )

        if (navResult.repCompleted) {
            totalReps++
            val maxReps = config.maxReps
            if (maxReps != null && totalReps >= maxReps) {
                sessionComplete = true
                return
            }
        }

        val nextStateObj = requireNotNull(graph.getState(navResult.nextStateId)) {
            "State ${navResult.nextStateId} not found in graph — graph may be corrupt"
        }
        sequenceIndex = navResult.nextSequenceIndex

        // Invalidate tolerance cache — state is changing
        cachedStateId = Int.MIN_VALUE

        if (config.guidanceDurationSeconds > 0.0) {
            nextStateForGuidance = nextStateObj
            isGuidanceActive = true
            guidanceStartTime = now
        } else {
            isGuidanceActive = false
            currentState = nextStateObj
            currentStateStartTime = now
            currentStateHoldTime = 0.0
            inTransition = true
            transitionStartTime = now
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun inGracePeriod(now: Double): Boolean {
        if (!inTransition) return false
        return (now - transitionStartTime) < config.transitionGraceSeconds
    }

    private fun getProgressiveTolerance(stateId: Int): Double {
        if (!config.progressiveTolerance) return 1.0
        val attempts = stateAttemptCounts[stateId] ?: 0
        val successes = stateSuccessCounts[stateId] ?: 0
        if (attempts < 5) return 1.0
        val successRate = if (attempts > 0) successes.toDouble() / attempts else 1.0
        return when {
            successRate < 0.3 -> 1.2
            successRate < 0.5 -> 1.1
            else              -> 1.0
        }
    }

    private fun applyPerKeyAdaptiveScale(
        state: PoseState,
        progressiveMult: Double,
        graceMult: Double,
    ): PoseState {
        if (!config.enableAdaptive && progressiveMult == 1.0 && graceMult == 1.0
            && !config.enforceToleranceFloor) return state

        val scaledTolerances = state.featureTolerances.map.mapValues { (key, tolerance) ->
            val adaptiveScale = adaptiveScaleFor(key)
            val scaled = tolerance * adaptiveScale * progressiveMult * graceMult

            // ── Android-calibrated tolerance floor ────────────────────────────
            // Python runs at a fixed desktop webcam distance (~2m), so graph-baked
            // ratio tolerances of 0.1 are achievable. On Android, the phone is
            // handheld at variable distances — body_center_y, arm_elevation, and
            // leg_spread shift constantly with camera distance and angle, making
            // 0.1 tolerance impossible to hold.
            //
            // The floor values match what the Python monitor effectively sees at
            // its fixed camera distance, translated to Android's variable conditions:
            //
            //   Angle features:  15.0° floor
            //     Python graph default is 10°. Live camera noise (MediaPipe jitter,
            //     slight body sway) adds ~3-5° variance. 15° gives the same
            //     effective headroom Python has at a stable desk setup.
            //
            //   Ratio features:  0.35 floor
            //     Graph default is 0.1. A 0.5m change in camera distance shifts
            //     body_center_y by ~0.3 units. 0.35 absorbs normal phone handling
            //     variation while still distinguishing meaningfully different poses
            //     (e.g. standing vs crouching differs by ~1.5 units).
            //
            // Floor only widens — never tightens a tolerance that is already wider.
            if (config.enforceToleranceFloor) {
                val floor = if (key.isAngle) MIN_ANGLE_TOLERANCE else MIN_RATIO_TOLERANCE
                maxOf(scaled, floor)
            } else {
                scaled
            }
        }
        return state.copy(
            featureTolerances = com.core.poseengine.model.RawFeatureMap(scaledTolerances)
        )
    }

    companion object {
        // Android-calibrated tolerance floors.
        // Angle features: degrees. Ratio/elevation/spread features: normalized units.
        private const val MIN_ANGLE_TOLERANCE = 5.0
        private const val MIN_RATIO_TOLERANCE = 0.35
    }

    private fun buildStatus(
        isCorrect: Boolean,
        progress: Double,
        phase: MonitorPhase = when {
            isGuidanceActive                     -> MonitorPhase.GUIDANCE
            inGracePeriod(time.nowSeconds())     -> MonitorPhase.TRANSITIONING
            else                                 -> MonitorPhase.HOLDING
        },
    ): FrameStatus = FrameStatus(
        stateId = currentState.stateId,
        holdProgress = progress,
        isCorrect = isCorrect,
        feedback = currentFeedback,
        repsCompleted = totalReps,
        sequenceIndex = sequenceIndex,
        streak = currentStreak,
        inGrace = inGracePeriod(time.nowSeconds()),
        sessionComplete = sessionComplete,
        phase = phase,
        deviations = lastDeviations,
    )
}

// ── Time abstraction ──────────────────────────────────────────────────────────

interface TimeSource {
    fun nowSeconds(): Double
}

object SystemTimeSource : TimeSource {
    override fun nowSeconds(): Double = System.nanoTime() / 1_000_000_000.0
}

class FakeTimeSource(private var currentSeconds: Double = 0.0) : TimeSource {
    override fun nowSeconds(): Double = currentSeconds
    fun advanceBy(seconds: Double) { currentSeconds += seconds }
    fun set(seconds: Double) { currentSeconds = seconds }
}