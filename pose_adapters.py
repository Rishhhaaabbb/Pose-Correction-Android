"""
Pose Model Adapter - MediaPipe BlazePose (PoseLandmarker Tasks API)

Maps MediaPipe 33 landmarks → COCO-17 format so all downstream code
(PoseFeatureExtractor, StateEngine, PoseGraph) remains unchanged.
"""

import cv2
import numpy as np
from typing import Optional
import os
import urllib.request
import time

# COCO-17 skeleton connections for visualization
_SKELETON = [
    (0, 1), (0, 2),
    (1, 3), (2, 4),
    (0, 5), (0, 6),
    (5, 6),
    (5, 7), (7, 9),
    (6, 8), (8, 10),
    (5, 11), (6, 12),
    (11, 12),
    (11, 13), (13, 15),
    (12, 14), (14, 16),
]

# MediaPipe landmark index → COCO-17 index
_MP_TO_COCO = {
    0: 0,   # nose
    2: 1,   # left_eye (inner)
    5: 2,   # right_eye (inner)
    7: 3,   # left_ear
    8: 4,   # right_ear
    11: 5,  # left_shoulder
    12: 6,  # right_shoulder
    13: 7,  # left_elbow
    14: 8,  # right_elbow
    15: 9,  # left_wrist
    16: 10, # right_wrist
    23: 11, # left_hip
    24: 12, # right_hip
    25: 13, # left_knee
    26: 14, # right_knee
    27: 15, # left_ankle
    28: 16, # right_ankle
}

_MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
    "pose_landmarker_full/float16/1/pose_landmarker_full.task"
)
_MODEL_PATH = "pose_landmarker_full.task"


class MediaPipePoseAdapter:
    """
    MediaPipe PoseLandmarker adapter (Tasks API).

    Outputs COCO-17 keypoints: shape (17, 3) → [x_px, y_px, confidence].
    """

    def __init__(
        self,
        model_path: str = _MODEL_PATH,
        confidence_threshold: float = 0.5,
    ):
        try:
            import mediapipe as mp
            from mediapipe.tasks import python
            from mediapipe.tasks.python import vision
        except ImportError:
            raise ImportError(
                "mediapipe not installed. Run: pip install mediapipe"
            )

        if not os.path.exists(model_path):
            print(f"[Adapter] Downloading MediaPipe model to {model_path} ...")
            urllib.request.urlretrieve(_MODEL_URL, model_path)
            print("[Adapter] Download complete.")

        base_options = python.BaseOptions(model_asset_path=model_path)
        options = vision.PoseLandmarkerOptions(
            base_options=base_options,
            running_mode=vision.RunningMode.VIDEO,
            min_pose_detection_confidence=confidence_threshold,
            min_pose_presence_confidence=confidence_threshold,
            min_tracking_confidence=confidence_threshold,
        )
        
        self._landmarker = vision.PoseLandmarker.create_from_options(options)
        self._mp = mp
        print(f"[Adapter] MediaPipe PoseLandmarker loaded: {model_path}")

    def detect(self, frame: np.ndarray) -> Optional[np.ndarray]:
        """
        Detect pose in a BGR frame.

        Returns:
            np.ndarray of shape (17, 3) — [x_px, y_px, confidence]
            or None if no pose detected.
        """
        h, w = frame.shape[:2]
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = self._mp.Image(
            image_format=self._mp.ImageFormat.SRGB, data=rgb
        )
        timestamp_ms = int(time.time() * 1000)
        result = self._landmarker.detect_for_video(mp_image, timestamp_ms)
        if not result.pose_landmarks or len(result.pose_landmarks) == 0:
            return None

        landmarks = result.pose_landmarks[0]
        keypoints = np.zeros((17, 3), dtype=np.float32)

        for mp_idx, coco_idx in _MP_TO_COCO.items():
            lm = landmarks[mp_idx]
            keypoints[coco_idx] = [
                lm.x * w,
                lm.y * h,
                float(np.clip(lm.visibility, 0.0, 1.0)),
            ]

        return keypoints

    def draw_skeleton(
        self, frame: np.ndarray, keypoints: Optional[np.ndarray]
    ) -> np.ndarray:
        """Draw COCO-17 skeleton on frame (in-place)."""
        if keypoints is None:
            return frame

        conf_threshold = 0.3

        for start_idx, end_idx in _SKELETON:
            if (
                keypoints[start_idx][2] > conf_threshold
                and keypoints[end_idx][2] > conf_threshold
            ):
                p1 = (int(keypoints[start_idx][0]), int(keypoints[start_idx][1]))
                p2 = (int(keypoints[end_idx][0]), int(keypoints[end_idx][1]))
                cv2.line(frame, p1, p2, (0, 255, 0), 2)

        for i in range(17):
            if keypoints[i][2] > conf_threshold:
                cv2.circle(
                    frame,
                    (int(keypoints[i][0]), int(keypoints[i][1])),
                    4,
                    (0, 0, 255),
                    -1,
                )

        return frame


def create_pose_adapter(model_path: str = _MODEL_PATH, **kwargs) -> MediaPipePoseAdapter:
    """
    Factory function — returns a MediaPipePoseAdapter.

    Args:
        model_path: Path to pose_landmarker_full.task
        **kwargs: confidence_threshold (float)
    """
    return MediaPipePoseAdapter(
        model_path=model_path,
        confidence_threshold=kwargs.get("confidence_threshold", 0.5),
    )