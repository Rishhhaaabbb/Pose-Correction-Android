package com.data.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions
import com.core.poseengine.model.Keypoint2d
import com.core.poseengine.model.Skeleton2d
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
/**
 * Production [MediaPipePoseProvider] backed by MediaPipe Tasks PoseLandmarker.
 *
 * Matches Python: MediaPipePoseAdapter (Tasks API variant).
 *
 * ── Model loading ─────────────────────────────────────────────────────────────
 * The `.task` model file must be present in `src/main/assets/`.
 * Python downloads it at runtime; we ship it in the APK.
 * This is enforced in build.gradle via:
 *   androidResources.noCompress += ["task", "tflite"]
 * (already set in the Gradle configuration from earlier in the project)
 *
 * ── Running mode ─────────────────────────────────────────────────────────────
 * Uses [RunningMode.VIDEO] — identical to Python's RunningMode.VIDEO.
 * VIDEO mode requires timestamps that are monotonically increasing.
 * We use [SystemClock.elapsedRealtime] (milliseconds since boot) as a
 * monotonic source. This matches the Python pattern of time.time() * 1000
 * but is more appropriate for Android since elapsedRealtime() never goes
 * backwards even across clock adjustments.
 *
 * ── Coordinate output ─────────────────────────────────────────────────────────
 * MediaPipe outputs normalized [0, 1] coordinates.
 * Python multiplies by frame width/height:
 *   keypoints[coco_idx] = [lm.x * w, lm.y * h, clip(lm.visibility, 0, 1)]
 * We replicate this exactly using the bitmap's pixel dimensions.
 *
 * ── Confidence clamp ─────────────────────────────────────────────────────────
 * Python: float(np.clip(lm.visibility, 0.0, 1.0))
 * Kotlin: lm.visibility().coerceIn(0f, 1f)
 * Numerically identical.
 *
 * ── Subject selection ─────────────────────────────────────────────────────────
 * Python selects the first detected person: result.pose_landmarks[0]
 * We do the same: result.poseLandmarks()[0]
 *
 * @param context              Application context for asset loading.
 * @param modelAssetName       Filename of the .task model in assets/.
 *   Default matches the Python default: "pose_landmarker_full.task"
 * @param confidenceThreshold  Minimum confidence for detection, presence,
 *   and tracking. Default 0.5 matches Python default.
 */
@Singleton
class MediaPipePoseProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelAssetName: String = DEFAULT_MODEL_ASSET,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE,
) : MediaPipePoseProvider {

    private val landmarker: PoseLandmarker = buildLandmarker()

    /**
     * Detect pose in [bitmap] and return [Skeleton2d] in COCO-17 format.
     *
     * Matches Python: detect(frame) -> Optional[np.ndarray shape (17,3)]
     *
     * Null is returned when:
     *   - No person detected (result.poseLandmarks().isEmpty())
     *   - Matches Python: if not result.pose_landmarks: return None
     */
    override fun detect(bitmap: Bitmap): Skeleton2d? {
        val w = bitmap.width
        val h = bitmap.height

        // Build MPImage from bitmap
        // Matches Python: mp_image = mp.Image(image_format=SRGB, data=rgb)
        // BitmapImageBuilder handles RGB/ARGB conversion internally
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Monotonic timestamp in milliseconds
        // Matches Python: timestamp_ms = int(time.time() * 1000)
        // Uses SystemClock.elapsedRealtime() for true monotonic behavior
        val timestampMs = SystemClock.elapsedRealtime()

        val result = landmarker.detectForVideo(mpImage, timestampMs)

// Check for detection
        if (result.landmarks().isEmpty()) return null

// Use first detected person
        val landmarks = result.landmarks()[0]

// Map MP33 → COCO17
        val keypoints = Array(LandmarkMapping.COCO_LANDMARK_COUNT) {
            Keypoint2d(x = 0.0, y = 0.0, confidence = 0.0)
        }

        for (mpIdx in LandmarkMapping.mpToCoco.indices) {
            val cocoIdx = LandmarkMapping.mpToCoco[mpIdx]
            if (cocoIdx == LandmarkMapping.UNMAPPED) continue

            val lm = landmarks[mpIdx]

            val vis = lm.visibility().orElse(0f).let { f ->
                if (f < 0f) 0f else if (f > 1f) 1f else f
            }

            keypoints[cocoIdx] = Keypoint2d(
                x = lm.x().toDouble() * w,
                y = lm.y().toDouble() * h,
                confidence = vis.toDouble(),
            )
        }

        return Skeleton2d(keypoints.toList())
    }

    override fun close() {
        landmarker.close()
    }

    private fun buildLandmarker(): PoseLandmarker {
        // Load model from Android assets
        // Matches Python: BaseOptions(model_asset_path=model_path)
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetName)
            .build()

        // Matches Python: PoseLandmarkerOptions(
        //   base_options=base_options,
        //   running_mode=VIDEO,
        //   min_pose_detection_confidence=threshold,
        //   min_pose_presence_confidence=threshold,
        //   min_tracking_confidence=threshold,
        // )
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setMinPoseDetectionConfidence(confidenceThreshold)
            .setMinPosePresenceConfidence(confidenceThreshold)
            .setMinTrackingConfidence(confidenceThreshold)
            .build()

        return PoseLandmarker.createFromOptions(context, options)
    }

    companion object {
        /** Matches Python default: 'pose_landmarker_full.task' */
        const val DEFAULT_MODEL_ASSET = "pose_landmarker_full.task"

        /** Matches Python default: confidence_threshold=0.5 */
        const val DEFAULT_CONFIDENCE = 0.5f
    }
}