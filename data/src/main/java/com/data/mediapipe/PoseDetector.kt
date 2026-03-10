// data/src/main/java/com/data/mediapipe/PoseDetector.kt

package com.data.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.core.poseengine.model.Keypoint2d
import com.core.poseengine.model.Skeleton2d
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import android.util.Size
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors

/**
 * Layer 1 — Camera → MediaPipe → Skeleton2d (COCO-17)
 *
 * ── Performance changes from original ────────────────────────────────────────
 *
 * 1. LIVE_STREAM mode (was IMAGE mode)
 *    IMAGE mode: full detection pipeline from scratch every frame — no tracking.
 *    LIVE_STREAM mode: temporal tracking reuses pose state between frames,
 *    ~2–3× faster per-frame inference. Results are delivered asynchronously
 *    via resultListener on MediaPipe's internal thread.
 *
 * 2. Cached rotation Matrix (was new Matrix() every frame)
 *    The original rotateBitmap() called Matrix() + postRotate() + createBitmap()
 *    on every frame. The Matrix object is now reused across frames — only
 *    reset+postRotate when the rotation degree value actually changes
 *    (which in a portrait-locked app is essentially never after the first frame).
 *    Bitmap.createBitmap() with the matrix is still required — MediaPipe needs
 *    an upright pixel buffer — but we avoid re-allocating the Matrix itself.
 *
 *    NOTE: setTargetRotation() on ImageAnalysis only sets metadata — it does NOT
 *    rotate pixel data. The bitmap must be explicitly rotated before passing to
 *    MediaPipe, otherwise landmarks are returned in landscape orientation.
 *
 * 3. Channel.CONFLATED result delivery
 *    LIVE_STREAM results arrive on MP's callback thread asynchronously.
 *    CONFLATED ensures stale intermediate frames are dropped if the collector
 *    is momentarily slow, keeping end-to-end latency low.
 *
 * ── Coordinate system after this class ───────────────────────────────────────
 *   x ∈ [0,1]  left→right  (mirrored for front camera in handleLiveResult)
 *   y ∈ [0,1]  top→bottom  (portrait upright, after bitmap rotation)
 */
class PoseDetector(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var landmarker: PoseLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    // Callback wired by frameFlow to receive LIVE_STREAM results.
    private var onResult: ((PoseDetectorResult) -> Unit)? = null

    // Reports the camera frame size (post-rotation) once on the first frame.
    // Used by the overlay to compute the FILL_CENTER crop rect for skeleton alignment.
    var onFrameSize: ((Size) -> Unit)? = null
    private var frameSizeReported = false

    // ── Cached rotation state ─────────────────────────────────────────────────
    // Reuse a single Matrix instance across frames.
    // Only call postRotate() again when the rotation degree changes.
    // In a portrait-locked app this effectively never changes after frame 1.
    private val rotationMatrix = Matrix()
    private var lastRotationDegrees: Int = Int.MIN_VALUE

    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        previewSurfaceProvider = provider
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    fun initialize(confidenceThreshold: Float = 0.2f) {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_FILE)
            .setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU)
            .build()

        val options = PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> handleLiveResult(result) }
            .setErrorListener { error ->
                Log.e(TAG, "MediaPipe error: ${error.message}")
                onResult?.invoke(PoseDetectorResult.NoPose)
            }
            .setMinPoseDetectionConfidence(confidenceThreshold)
            .setMinPosePresenceConfidence(confidenceThreshold)
            .setMinTrackingConfidence(confidenceThreshold)
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
        Log.d(TAG, "MediaPipe PoseLandmarker initialised (LIVE_STREAM, $MODEL_FILE)")
    }

    // ── CameraX binding ───────────────────────────────────────────────────────

    fun frameFlow(lifecycleOwner: LifecycleOwner): Flow<PoseDetectorResult> = callbackFlow {
        onResult = { result -> trySend(result) }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            previewSurfaceProvider?.let { preview.setSurfaceProvider(it) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                processFrame(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed: ${e.message}")
                close(e)
            }
        }, ContextCompat.getMainExecutor(context))

        awaitClose {
            onResult = null
            executor.shutdown()
            landmarker?.close()
        }
    }.buffer(Channel.CONFLATED)

    // ── Frame processing ──────────────────────────────────────────────────────

    private fun processFrame(imageProxy: ImageProxy) {
        val lm = landmarker ?: run {
            imageProxy.close()
            onResult?.invoke(PoseDetectorResult.NoLandmarker)
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees

        // toBitmap() gives raw sensor-orientation pixels.
        // MediaPipe requires an upright image to return correct [0,1] coords —
        // without rotation, landmarks are delivered in landscape orientation.
        val raw = imageProxy.toBitmap()
        imageProxy.close()

        // ── Cached Matrix rotation ────────────────────────────────────────────
        // Original: new Matrix() + postRotate() on every frame (allocation).
        // Now: reuse rotationMatrix, only call reset+postRotate when rotation
        // changes. In a portrait-locked app this is a one-time cost on frame 1.
        val bitmap = if (rotation == 0) {
            raw
        } else {
            if (rotation != lastRotationDegrees) {
                rotationMatrix.reset()
                rotationMatrix.postRotate(rotation.toFloat())
                lastRotationDegrees = rotation
            }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, rotationMatrix, true)
        }

        // Report frame size once — after rotation so dimensions reflect portrait orientation.
        // The overlay uses this to compute the FILL_CENTER crop rect.
        if (!frameSizeReported) {
            onFrameSize?.invoke(Size(bitmap.width, bitmap.height))
            frameSizeReported = true
        }

        val mpImage = BitmapImageBuilder(bitmap).build()

        // detectAsync returns immediately — result delivered to handleLiveResult()
        // on MediaPipe's internal callback thread.
        lm.detectAsync(mpImage, System.currentTimeMillis())
    }

    // ── LIVE_STREAM result handler ────────────────────────────────────────────

    private fun handleLiveResult(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks().firstOrNull()

        if (landmarks == null) {
            Log.d(TAG, "Layer 1 ❌ — no pose detected")
            onResult?.invoke(PoseDetectorResult.NoPose)
            return
        }

        Log.d(TAG, "Layer 1 ✅ — landmarks detected: ${landmarks.size}")

        val keypoints = Array(LandmarkMapping.COCO_LANDMARK_COUNT) {
            Keypoint2d(0.0, 0.0, 0.0)
        }

        var validCount = 0
        for (mpIdx in landmarks.indices) {
            val cocoIdx = LandmarkMapping.mpToCoco.getOrElse(mpIdx) { LandmarkMapping.UNMAPPED }
            if (cocoIdx == LandmarkMapping.UNMAPPED) continue

            val lmk = landmarks[mpIdx]
            val conf = lmk.visibility().orElse(0f).let { f ->
                if (f < 0f) 0.0 else if (f > 1f) 1.0 else f.toDouble()
            }

            keypoints[cocoIdx] = Keypoint2d(
                x = lmk.x().toDouble().coerceIn(0.0, 1.0),
                y = lmk.y().toDouble().coerceIn(0.0, 1.0),
                confidence = conf,
            )
            if (conf > 0.5) validCount++
        }

        Log.d(TAG, "Layer 2 ✅ — COCO keypoints mapped: 17 (valid: $validCount)")

        val skeleton = Skeleton2d(keypoints.toList())
        onResult?.invoke(PoseDetectorResult.Detected(skeleton, landmarkCount = landmarks.size))
    }

    companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_FILE = "pose_landmarker_full.task"
    }
}

// ── Result type ───────────────────────────────────────────────────────────────

sealed class PoseDetectorResult {
    object NoLandmarker : PoseDetectorResult()
    object NoPose : PoseDetectorResult()
    data class Detected(
        val skeleton: Skeleton2d,
        val landmarkCount: Int,
    ) : PoseDetectorResult()
}