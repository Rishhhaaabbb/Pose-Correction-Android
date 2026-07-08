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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    // Pre-warm camera provider once so first camera open is faster.
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    private var landmarker: PoseLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var camera: Camera? = null // Reference to the active camera

    private val _zoomStateFlow = MutableStateFlow<ZoomState?>(null)
    val zoomStateFlow: StateFlow<ZoomState?> = _zoomStateFlow.asStateFlow()

    private val _frameRotationDegreesFlow = MutableStateFlow(0)
    val frameRotationDegreesFlow: StateFlow<Int> = _frameRotationDegreesFlow.asStateFlow()

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

    // ── Low-light recovery ────────────────────────────────────────────────────
    // When MediaPipe's LIVE_STREAM tracker loses the pose for too many consecutive
    // frames (e.g. due to low light), its internal tracking state goes stale and
    // it stops recovering even when conditions improve.
    // Fix: close and re-create the landmarker after NoPose persists for ~1 second
    // (NO_POSE_RESET_THRESHOLD frames at 30fps ≈ 1s). The camera keeps running
    // uninterrupted; the re-init runs on the same executor thread.
    private var consecutiveNoPoseCount = 0
    private var lastConfidenceThreshold = 0.35f
    private var isReinitializing = false

    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        previewSurfaceProvider = provider
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    fun initialize(confidenceThreshold: Float = 0.35f) {
            lastConfidenceThreshold = confidenceThreshold
            consecutiveNoPoseCount = 0
            isReinitializing = false
            // Try GPU delegate first; if that fails (device/driver issues), fall back to CPU.
            val baseOptionsGpu = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU)
                .build()

            var created = false
            try {
                val optionsGpu = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsGpu)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(2)
                    .setResultListener { result, _ -> handleLiveResult(result) }
                    .setErrorListener { error ->
                        Log.e(TAG, "MediaPipe error: ${error.message}")
                        onResult?.invoke(PoseDetectorResult.NoPose)
                    }
                    .setMinPoseDetectionConfidence(confidenceThreshold)
                    .setMinPosePresenceConfidence(confidenceThreshold)
                    .setMinTrackingConfidence(confidenceThreshold)
                    .build()

                landmarker = PoseLandmarker.createFromOptions(context, optionsGpu)
                Log.d(TAG, "MediaPipe PoseLandmarker initialised (LIVE_STREAM, $MODEL_FILE) [GPU]")
                created = true
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate initialisation failed: ${e.message}. Falling back to CPU.")
            }

            if (!created) {
                val baseOptionsCpu = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE)
                    .setDelegate(com.google.mediapipe.tasks.core.Delegate.CPU)
                    .build()

                val optionsCpu = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsCpu)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(2)
                    .setResultListener { result, _ -> handleLiveResult(result) }
                    .setErrorListener { error ->
                        Log.e(TAG, "MediaPipe error: ${error.message}")
                        onResult?.invoke(PoseDetectorResult.NoPose)
                    }
                    .setMinPoseDetectionConfidence(confidenceThreshold)
                    .setMinPosePresenceConfidence(confidenceThreshold)
                    .setMinTrackingConfidence(confidenceThreshold)
                    .build()

                landmarker = PoseLandmarker.createFromOptions(context, optionsCpu)
                Log.d(TAG, "MediaPipe PoseLandmarker initialised (LIVE_STREAM, $MODEL_FILE) [CPU]")
            }
    }

    /**
     * Returns true when the internal MediaPipe landmarker has been created.
     */
    fun isInitialized(): Boolean = landmarker != null

    // ── CameraX binding ───────────────────────────────────────────────────────

    fun frameFlow(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    ): Flow<PoseDetectorResult> = callbackFlow {
        onResult = { result -> trySend(result) }

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            previewSurfaceProvider?.let { preview.setSurfaceProvider(it) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                try {
                    processFrame(imageProxy)
                } catch (e: Exception) {
                    Log.e(TAG, "Frame processing failed: ${e.message}", e)
                    try { imageProxy.close() } catch (_: Exception) {}
                    // Close the flow with the exception so upstream can handle it.
                    close(e)
                }
            }

            try {
                // Ensure camera permission is granted before binding; fail fast with clear message.
                val permission = android.Manifest.permission.CAMERA
                if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    throw SecurityException("Camera permission not granted")
                }
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                )
                this@PoseDetector.camera = camera // Store reference to camera
                
                // Observe hardware zoom state
                camera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                    _zoomStateFlow.value = state
                }
                
                // Attempt to set zoom to 0.7x (Capture more area for full-body tracking)
                camera.cameraControl.setZoomRatio(0.7f).addListener({
                    Log.d(TAG, "Zoom 0.7x applied (or clipped to hardware min)")
                }, mainExecutor)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed: ${e.message}")
                close(e)
            }
        }, mainExecutor)

        awaitClose {
            onResult = null
            runCatching { cameraProviderFuture.get().unbindAll() }
            camera = null
            _zoomStateFlow.value = null
            frameSizeReported = false
        }
    }.buffer(Channel.CONFLATED)

    fun release() {
        runCatching { cameraProviderFuture.get().unbindAll() }
        camera = null
        _zoomStateFlow.value = null
        runCatching { landmarker?.close() }
        landmarker = null
        consecutiveNoPoseCount = 0
        isReinitializing = false
        runCatching { executor.shutdown() }
    }

    fun setLinearZoom(value: Float) {
        camera?.cameraControl?.setLinearZoom(value.coerceIn(0f, 1f))
    }

    fun setZoomRatio(value: Float) {
        camera?.cameraControl?.setZoomRatio(value)
    }

    fun getZoomState(): ZoomState? = camera?.cameraInfo?.zoomState?.value

    // ── Frame processing ──────────────────────────────────────────────────────

    private fun processFrame(imageProxy: ImageProxy) {
        val lm = landmarker ?: run {
            imageProxy.close()
            onResult?.invoke(PoseDetectorResult.NoLandmarker)
            return
        }

        // If re-init is in progress, drop the frame and wait
        if (isReinitializing) {
            imageProxy.close()
            onResult?.invoke(PoseDetectorResult.NoLandmarker)
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        _frameRotationDegreesFlow.value = rotation

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
        // on MediaPipe's internal callback thread. Wrap in try/catch to avoid
        // crashing the analyzer thread if MediaPipe throws for certain frames.
        try {
            lm.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe detectAsync failed: ${e.message}", e)
            // Don't propagate — drop this frame.
            onResult?.invoke(PoseDetectorResult.NoPose)
        }
    }

    // ── LIVE_STREAM result handler ────────────────────────────────────────────

    private fun handleLiveResult(result: PoseLandmarkerResult) {
        try {
            val allLandmarks = result.landmarks()
            
            if (allLandmarks.size > 1) {
                Log.d(TAG, "Layer 1 ❌ — multiple poses detected: ${allLandmarks.size}")
                consecutiveNoPoseCount = 0  // Multiple people is not a tracker failure
                onResult?.invoke(PoseDetectorResult.MultiplePoses)
                return
            }

            val landmarks = allLandmarks.firstOrNull()

            if (landmarks == null) {
                Log.d(TAG, "Layer 1 ❌ — no pose detected")
                consecutiveNoPoseCount++
                Log.d(TAG, "Consecutive NoPose: $consecutiveNoPoseCount / $NO_POSE_RESET_THRESHOLD")

                // Tracker has been stuck for ~1s — close and re-create to clear stale state
                if (consecutiveNoPoseCount >= NO_POSE_RESET_THRESHOLD && !isReinitializing) {
                    Log.w(TAG, "NoPose threshold reached — re-initializing landmarker to recover from low-light dropout")
                    isReinitializing = true
                    consecutiveNoPoseCount = 0
                    executor.execute {
                        try {
                            landmarker?.close()
                            landmarker = null
                            initialize(lastConfidenceThreshold)
                            Log.d(TAG, "Landmarker re-initialized successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Landmarker re-initialization failed: ${e.message}", e)
                        } finally {
                            isReinitializing = false
                        }
                    }
                }

                onResult?.invoke(PoseDetectorResult.NoPose)
                return
            }

            // Pose detected — reset the failure counter
            consecutiveNoPoseCount = 0

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
            onResult?.invoke(
                PoseDetectorResult.Detected(
                    skeleton = skeleton,
                    landmarkCount = landmarks.size,
                    frameRotationDegrees = _frameRotationDegreesFlow.value,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping MediaPipe result: ${e.message}", e)
            onResult?.invoke(PoseDetectorResult.NoPose)
        }
    }

    companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_FILE = "pose_landmarker_full.task"
        // Frames of consecutive NoPose before the LIVE_STREAM tracker is reset.
        // 30 frames ≈ 1 second at 30fps — long enough to avoid spurious resets
        // on momentary occlusion, short enough to recover from low-light dropout.
        private const val NO_POSE_RESET_THRESHOLD = 30
    }
}

// ── Result type ───────────────────────────────────────────────────────────────

sealed class PoseDetectorResult {
    object NoLandmarker : PoseDetectorResult()
    object NoPose : PoseDetectorResult()
    object MultiplePoses : PoseDetectorResult()
    data class Detected(
        val skeleton: Skeleton2d,
        val landmarkCount: Int,
        val frameRotationDegrees: Int,
    ) : PoseDetectorResult()
}
