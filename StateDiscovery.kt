package com.core.poseengine

import com.core.poseengine.model.*

/**
 * Discovers pose states from a sequence of extracted features.
 *
 * ── Algorithm overview ────────────────────────────────────────────────────────
 * 1. Smooth the raw feature sequence with a rolling window average.
 * 2. Scan frame-by-frame; emit a SegmentInfo whenever the pose has been
 *    stable for at least [minHoldFrames] frames and then changes.
 * 3. Merge segments that are close in feature space into a single canonical
 *    PoseState (using [mergeThreshold], not [stabilityThreshold]).
 * 4. Return a [DiscoveryResult] containing the final states AND the temporal
 *    sequence (which state ID appeared at which segment position).
 *
 * ── API change from Python ────────────────────────────────────────────────────
 * Python required two calls:
 *   states = discovery.discover_states(...)
 *   seq    = discovery.get_temporal_sequence()    # reads instance state
 *
 * Kotlin replaces this with a single call returning [DiscoveryResult] which
 * bundles both. There is no mutable instance state after construction.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * Stateless after construction. [discoverStates] can be called from any thread.
 * Do NOT share a single instance across concurrent discovery runs.
 *
 * @param stabilityThreshold  Maximum mean normalized distance for a frame to
 *   be considered "still in the same segment" as the segment reference.
 *   Default 0.15 matches Python default.
 * @param minHoldFrames       A segment is only emitted if it spans at least
 *   this many frames. Default 15 matches Python default.
 * @param smoothingWindow     Rolling average window size. Default 5.
 * @param transitionBuffer    Frames skipped after a segment boundary to ignore
 *   transition motion. Default 3.
 * @param toleranceMultiplier Per-feature tolerance = std × multiplier, subject
 *   to a minimum floor. Default 2.0 matches Python default.
 * @param mergeThreshold      Two segments are merged into one state if their
 *   mean feature distance is ≤ this value. Defaults to stabilityThreshold × 0.5.
 * @param asymmetryVetoMinMagnitude  Asymmetry values below this magnitude are
 *   treated as symmetric and do not trigger the veto gate. Default 5.0.
 */
class StateDiscovery(
    val stabilityThreshold: Double = 0.15,
    val minHoldFrames: Int = 15,
    val smoothingWindow: Int = 5,
    val transitionBuffer: Int = 3,
    val toleranceMultiplier: Double = 2.0,
    mergeThreshold: Double? = null,
    val asymmetryVetoMinMagnitude: Double = 5.0,
) {
    val mergeThreshold: Double = mergeThreshold ?: stabilityThreshold

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Run the full discovery pipeline.
     *
     * Matches Python: StateDiscovery.discover_states() + get_temporal_sequence()
     *
     * @param featureSequence  One [RawFeatureMap] per processed frame.
     *   Empty maps represent frames where pose extraction failed.
     * @param fps              Effective frames-per-second of [featureSequence]
     *   (accounts for skip_frames in the offline generator).
     * @param keypointSequence Optional parallel list of [Skeleton2d] per frame.
     *   Used only for computing [PoseState.meanKeypoints] for visualization.
     *   Must have the same length as [featureSequence] when provided.
     *
     * @return [DiscoveryResult] with discovered states and temporal sequence,
     *   or [DiscoveryResult.EMPTY] if [featureSequence] is shorter than
     *   [minHoldFrames].
     */
    fun discoverStates(
        featureSequence: List<RawFeatureMap>,
        fps: Double,
        keypointSequence: List<Skeleton2d?>? = null,
    ): DiscoveryResult {
        if (featureSequence.size < minHoldFrames) return DiscoveryResult.EMPTY

        // Step 1 — temporal smoothing
        val smoothed = smoothFeatures(featureSequence)

        // Step 2 — stable segment detection
        val segments = detectStableSegments(smoothed)

        // Step 3 — state merging and PoseState construction
        return createStatesWithMerging(segments, smoothed, fps, keypointSequence)
    }

    // ── Step 1: Temporal smoothing ────────────────────────────────────────────

    /**
     * Applies a causal rolling-average to each feature independently.
     *
     * Matches Python: StateDiscovery._smooth_features()
     *
     * CAUSAL: only past frames contribute to the average (window slides forward).
     * Empty frames (failed detection) are passed through as-is and do NOT
     * contribute to the running window.
     *
     * The window is seeded with whatever is available (partial window at start).
     * This matches Python's deque(maxlen=N) behaviour: the deque starts empty
     * and fills up, so early frames have fewer contributors.
     */
    internal fun smoothFeatures(featureSequence: List<RawFeatureMap>): List<RawFeatureMap> {
        if (featureSequence.size < smoothingWindow) return featureSequence

        // Collect all feature keys that appear anywhere in the sequence
        val allKeys: Set<FeatureKey> = buildSet {
            featureSequence.forEach { frame -> addAll(frame.map.keys) }
        }

        // Sliding window — ArrayDeque used as FIFO with max capacity
        val window = ArrayDeque<RawFeatureMap>(smoothingWindow)
        val result = ArrayList<RawFeatureMap>(featureSequence.size)

        for (frame in featureSequence) {
            if (frame.isEmpty) {
                // Matches Python: if not features: smoothed.append({}); continue
                result.add(RawFeatureMap.EMPTY)
                continue
            }

            // Evict oldest entry when window is full
            // Matches Python: deque(maxlen=self.smoothing_window) auto-eviction
            if (window.size == smoothingWindow) window.removeFirst()
            window.addLast(frame)

            // Compute rolling mean for each key
            val smoothedFrame = buildMap<FeatureKey, Double> {
                for (key in allKeys) {
                    // Collect values from current window (only frames that have this key)
                    // Matches Python: values = [f[fname] for f in window if fname in f]
                    var sum = 0.0
                    var count = 0
                    for (windowFrame in window) {
                        val v = windowFrame[key]
                        if (v != null) { sum += v; count++ }
                    }
                    if (count > 0) put(key, sum / count)
                }
            }
            result.add(RawFeatureMap(smoothedFrame))
        }

        return result
    }

    // ── Step 2: Stable segment detection ─────────────────────────────────────

    /**
     * Scans the smoothed sequence and emits a [SegmentInfo] for each contiguous
     * block of frames that remained stable relative to the block's first frame.
     *
     * Matches Python: StateDiscovery._detect_stable_segments_with_tracking()
     *
     * Stability criterion: distance(current_frame, reference_frame) ≤ stabilityThreshold
     * where reference_frame is reset to the FIRST frame of the current block.
     *
     * Asymmetry defaults:
     * Python injects `hip_asymmetry_delta` and `knee_asymmetry_delta` as 0.0
     * via setdefault() into each segment's mean features. We replicate this by
     * ensuring these keys appear in the output map (absent = 0.0 equivalent for
     * the veto gate, which only fires when |value| >= veto_min).
     * Rather than injecting magic 0.0 values, we model absence as "no asymmetry"
     * directly in [asymmetryVeto], which produces identical behavior.
     */
    internal fun detectStableSegments(
        featureSequence: List<RawFeatureMap>,
    ): List<SegmentInfo> {
        val segments = mutableListOf<SegmentInfo>()
        var segmentIndex = 0
        var currentStart = 0
        var currentReference: RawFeatureMap? = null
        var i = 0

        while (i < featureSequence.size) {
            val frame = featureSequence[i]

            if (frame.isEmpty) {
                // Invalid frame — flush current segment if long enough
                if (currentReference != null) {
                    val length = i - currentStart
                    if (length >= minHoldFrames) {
                        segments.add(
                            buildSegment(
                                startFrame = currentStart,
                                endFrame = i - 1,
                                features = featureSequence.subList(currentStart, i),
                                segmentIndex = segmentIndex++,
                            )
                        )
                    }
                }
                currentReference = null
                currentStart = i + 1
                i++
                continue
            }

            if (currentReference == null) {
                // Start a new segment — this frame becomes the reference
                currentReference = frame
                currentStart = i
                i++
                continue
            }

            val distance = DistanceCalculator.compute(frame, currentReference)

            if (distance <= stabilityThreshold) {
                // Still stable — stay in this segment
                i++
            } else {
                // Pose changed — flush current segment if long enough
                val length = i - currentStart
                if (length >= minHoldFrames) {
                    segments.add(
                        buildSegment(
                            startFrame = currentStart,
                            endFrame = i - 1,
                            features = featureSequence.subList(currentStart, i),
                            segmentIndex = segmentIndex++,
                        )
                    )
                }
                // Skip transition buffer, reset reference
                // Matches Python: current_reference = None; i += self.transition_buffer
                currentReference = null
                i += transitionBuffer
                currentStart = i
            }
        }

        // Flush final segment
        if (currentReference != null) {
            val length = featureSequence.size - currentStart
            if (length >= minHoldFrames) {
                segments.add(
                    buildSegment(
                        startFrame = currentStart,
                        endFrame = featureSequence.size - 1,
                        features = featureSequence.subList(currentStart, featureSequence.size),
                        segmentIndex = segmentIndex,
                    )
                )
            }
        }

        return segments
    }

    /** Constructs a [SegmentInfo] from a contiguous slice of the feature sequence. */
    private fun buildSegment(
        startFrame: Int,
        endFrame: Int,
        features: List<RawFeatureMap>,
        segmentIndex: Int,
    ): SegmentInfo = SegmentInfo(
        startFrame = startFrame,
        endFrame = endFrame,
        meanFeatures = computeMeanFeatures(features),
        segmentIndex = segmentIndex,
    )

    // ── Step 3: State merging ─────────────────────────────────────────────────

    /**
     * Merges segments into canonical states and builds [PoseState] objects.
     *
     * Matches Python: StateDiscovery._create_states_from_segments_with_merging()
     *
     * MERGE LOGIC:
     *   For each segment (temporal order):
     *     1. Run asymmetry veto gate (immediately rejects opposite-side asymmetry)
     *     2. Compute distance to each existing canonical state
     *     3. If distance ≤ mergeThreshold → assign to that state
     *     4. Otherwise → create a new canonical state
     *
     * After all segments are assigned:
     *   - Recompute mean features across ALL frames in each canonical state
     *   - Compute per-feature tolerances from variance
     *   - Compute min hold duration from median segment duration × 0.6
     *   - Optionally compute mean keypoints for visualization
     */
    private fun createStatesWithMerging(
        segments: List<SegmentInfo>,
        featureSequence: List<RawFeatureMap>,
        fps: Double,
        keypointSequence: List<Skeleton2d?>?,
    ): DiscoveryResult {
        if (segments.isEmpty()) return DiscoveryResult.EMPTY

        val canonicalStates = mutableListOf<CanonicalState>()

        // segment index → canonical state ID
        val segmentToStateId = HashMap<Int, Int>(segments.size)

        var nextStateId = 0

        // ── Assign each segment to a canonical state ──────────────────────────
        for (segment in segments) {
            val matchedId = findMatchingState(segment.meanFeatures, canonicalStates)

            if (matchedId != null) {
                // Merge: add frame range to existing canonical state
                segmentToStateId[segment.segmentIndex] = matchedId
                canonicalStates.first { it.stateId == matchedId }
                    .addRange(segment.startFrame..segment.endFrame)
            } else {
                // New canonical state
                val newId = nextStateId++
                segmentToStateId[segment.segmentIndex] = newId
                canonicalStates.add(
                    CanonicalState(
                        stateId = newId,
                        meanFeatures = segment.meanFeatures,
                        frameRanges = mutableListOf(segment.startFrame..segment.endFrame),
                        kptRanges = mutableListOf(segment.startFrame..segment.endFrame),
                    )
                )
            }
        }

        // ── Build final PoseState objects ─────────────────────────────────────
        val finalStates = canonicalStates.map { canonical ->
            buildPoseState(
                canonical = canonical,
                featureSequence = featureSequence,
                fps = fps,
                keypointSequence = keypointSequence,
                originalSegments = segments,
            )
        }.sortedBy { it.stateId }   // Matches Python: final_states.sort(key=lambda s: s.state_id)

        // ── Build temporal sequence ───────────────────────────────────────────
        // Matches Python: get_temporal_sequence()
        val temporalSequence = segments.map { seg ->
            segmentToStateId[seg.segmentIndex]
                ?: error("Segment ${seg.segmentIndex} has no assigned state — this is a bug")
        }

        return DiscoveryResult(states = finalStates, temporalSequence = temporalSequence)
    }

    /** Constructs a single [PoseState] from a [CanonicalState] and the full data. */
    private fun buildPoseState(
        canonical: CanonicalState,
        featureSequence: List<RawFeatureMap>,
        fps: Double,
        keypointSequence: List<Skeleton2d?>?,
        originalSegments: List<SegmentInfo>,
    ): PoseState {
        // Recompute mean features across ALL frames assigned to this canonical state
        val allFrameFeatures = buildList {
            for (range in canonical.frameRanges) {
                for (idx in range) {
                    val f = featureSequence.getOrNull(idx)
                    if (f != null && !f.isEmpty) add(f)
                }
            }
        }

        val mergedMean = computeMeanFeatures(allFrameFeatures)

        val tolerances = computeFeatureTolerances(
            frameRanges = canonical.frameRanges,
            featureSequence = featureSequence,
            meanFeatures = mergedMean,
        )

        // Median hold duration × 0.6
        // Matches Python: np.median(durations) * 0.6
        val durations = canonical.frameRanges.map { it.count().toDouble() / fps }
        val minHoldDuration = median(durations) * 0.6

        val meanKeypoints = keypointSequence?.let {
            computeMeanKeypoints(canonical.kptRanges, it)
        }

        return PoseState(
            stateId = canonical.stateId,
            meanFeatures = mergedMean,
            featureTolerances = tolerances,
            minHoldDuration = minHoldDuration,
            meanKeypoints = meanKeypoints,
        )
    }

    // ── State matching ────────────────────────────────────────────────────────

    /**
     * Matches Python: StateDiscovery._find_matching_state()
     *
     * Two-stage gate:
     *   1. Asymmetry veto — immediately rejects opposite-side asymmetry
     *   2. Distance check — accepts if mean distance ≤ mergeThreshold
     *
     * Returns the first matching state ID, or null if none match.
     */
    internal fun findMatchingState(
        segmentFeatures: RawFeatureMap,
        canonicalStates: List<CanonicalState>,
    ): Int? {
        for (canonical in canonicalStates) {
            // Stage 1: asymmetry veto

            // Stage 2: distance check
            val distance = DistanceCalculator.compute(segmentFeatures, canonical.meanFeatures)
            if (distance <= mergeThreshold) return canonical.stateId
        }
        return null
    }

    /**
     * Returns true if the merge should be vetoed due to opposite-side asymmetry.
     *
     * Matches Python: StateDiscovery._asymmetry_veto()
     *
     * Veto fires when:
     *   - Both values have magnitude ≥ [asymmetryVetoMinMagnitude]
     *   - AND they have opposite signs (product < 0)
     *
     * Absence of an asymmetry key is treated as 0.0, which is below the
     * magnitude threshold, so the veto does NOT fire — matching Python's
     * setdefault(key, 0.0) behavior.
     */


    // ── Statistical helpers ───────────────────────────────────────────────────

    /**
     * Compute mean value for each feature across a list of frames.
     *
     * Matches Python: StateDiscovery._compute_mean_features()
     *
     * Features absent from a frame are skipped (not zero-filled).
     * An empty [featureList] returns [RawFeatureMap.EMPTY].
     */
    internal fun computeMeanFeatures(featureList: List<RawFeatureMap>): RawFeatureMap {
        if (featureList.isEmpty()) return RawFeatureMap.EMPTY

        // Collect all keys that appear in any frame
        val allKeys: Set<FeatureKey> = buildSet {
            featureList.forEach { addAll(it.map.keys) }
        }

        val result = buildMap<FeatureKey, Double> {
            for (key in allKeys) {
                var sum = 0.0
                var count = 0
                for (frame in featureList) {
                    val v = frame[key]
                    if (v != null) { sum += v; count++ }
                }
                // Matches Python: values = [f[fname] for f in feature_list if fname in f]
                //                 if values: mean_features[fname] = float(np.mean(values))
                if (count > 0) put(key, sum / count)
            }
        }
        return RawFeatureMap(result)
    }

    /**
     * Compute per-feature tolerances from the variance across all assigned frames.
     *
     * Matches Python: StateDiscovery._compute_feature_tolerances_from_frames()
     *
     * CRITICAL — ddof=0 (population std):
     *   Python uses np.std() which defaults to ddof=0.
     *   Kotlin's built-in standard deviation functions use ddof=1 (sample).
     *   We implement population std manually to match Python exactly.
     *
     * Minimum tolerance floors (match Python):
     *   Angle features:       max(std × multiplier, 10.0)
     *   Non-angle features:   max(std × multiplier, 0.1)
     *
     * Single-value fallback (match Python):
     *   Angle features:       15.0
     *   Non-angle features:   0.15
     */
    internal fun computeFeatureTolerances(
        frameRanges: List<IntRange>,
        featureSequence: List<RawFeatureMap>,
        meanFeatures: RawFeatureMap,
    ): RawFeatureMap {
        // Collect all feature frames within all ranges
        val allFrames = buildList<RawFeatureMap> {
            for (range in frameRanges) {
                for (idx in range) {
                    featureSequence.getOrNull(idx)?.let { add(it) }
                }
            }
        }

        val result = buildMap<FeatureKey, Double> {
            for ((key, meanVal) in meanFeatures.map) {
                val values = allFrames.mapNotNull { it[key] }
                val tolerance = when {
                    values.size > 1 -> {
                        // Population std (ddof=0) — matches np.std()
                        val std = populationStd(values, meanVal)
                        if (key.isAngle) {
                            maxOf(std * toleranceMultiplier, 10.0)
                        } else {
                            maxOf(std * toleranceMultiplier, 0.1)
                        }
                    }
                    // Single observation — use Python's default fallback
                    key.isAngle -> 15.0
                    else -> 0.15
                }
                put(key, tolerance)
            }
        }
        return RawFeatureMap(result)
    }

    /**
     * Compute mean keypoints across all frames in the given ranges.
     *
     * Matches Python: StateDiscovery._compute_mean_keypoints()
     *
     * Returns null when no valid keypoints are found in any frame.
     * Result is a list of 17 (x, y) pairs.
     */
    internal fun computeMeanKeypoints(
        kptRanges: List<IntRange>,
        keypointSequence: List<Skeleton2d?>,
    ): List<Pair<Double, Double>>? {
        // Collect all valid skeletons
        val allSkeletons = buildList<Skeleton2d> {
            for (range in kptRanges) {
                for (idx in range) {
                    keypointSequence.getOrNull(idx)?.let { add(it) }
                }
            }
        }
        if (allSkeletons.isEmpty()) return null

        // Compute per-keypoint mean x and y
        // Matches Python: np.mean(keypoints_array, axis=0) on shape (N, 17, 3)
        return List(KeypointIndex.COUNT) { kptIdx ->
            var sumX = 0.0
            var sumY = 0.0
            var count = 0
            for (skeleton in allSkeletons) {
                val kpt = skeleton.keypoints[kptIdx]
                sumX += kpt.x
                sumY += kpt.y
                count++
            }
            (sumX / count) to (sumY / count)
        }
    }

    // ── Math primitives ───────────────────────────────────────────────────────

    /**
     * Population standard deviation (ddof=0).
     *
     * MUST match Python np.std() default behavior (ddof=0).
     * Kotlin's built-in statistics use ddof=1 — do NOT use them here.
     *
     * Formula: sqrt( sum((xi - mean)²) / N )
     *
     * [precomputedMean] is passed in to avoid recomputing it when the mean
     * is already known from [computeMeanFeatures].
     */
    internal fun populationStd(values: List<Double>, precomputedMean: Double): Double {
        if (values.size <= 1) return 0.0
        var sumSq = 0.0
        for (v in values) {
            val diff = v - precomputedMean
            sumSq += diff * diff
        }
        return kotlin.math.sqrt(sumSq / values.size)   // ddof=0
    }

    /**
     * Median of a list of doubles.
     *
     * Matches Python: np.median(durations)
     *
     * For even-length lists, returns the average of the two middle values —
     * identical to NumPy's behavior.
     */
    internal fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}

// ── Discovery result ──────────────────────────────────────────────────────────

/**
 * Output of [StateDiscovery.discoverStates].
 *
 * Bundles both the [PoseState] list and the temporal sequence in a single
 * return value, eliminating Python's two-call pattern:
 *   states = discovery.discover_states(...)
 *   seq    = discovery.get_temporal_sequence()
 *
 * @param states           Final discovered [PoseState] objects, sorted by ID.
 * @param temporalSequence State IDs in the order their segments appeared in
 *   the video. May contain repeats when the same state appears multiple times.
 *   E.g. [0, 1, 2, 0, 1] means state 0 was seen first, then 1, then 2,
 *   then 0 again, then 1 again.
 */
data class DiscoveryResult(
    val states: List<PoseState>,
    val temporalSequence: List<Int>,
) {
    val isEmpty: Boolean get() = states.isEmpty()

    companion object {
        val EMPTY = DiscoveryResult(states = emptyList(), temporalSequence = emptyList())
    }
}