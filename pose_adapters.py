"""
Pose Model Adapter Interface
Provides a unified interface for multiple pose estimation models.

Each adapter maps model-specific outputs to a canonical COCO-17 skeleton format,
ensuring the pose graph and downstream logic remain model-agnostic.
"""

from abc import ABC, abstractmethod
import numpy as np
from typing import Tuple, Optional, List
import cv2


class PoseModelAdapter(ABC):
    """
    Abstract interface for pose estimation models.
    
    All adapters must output keypoints in the canonical COCO-17 format:
    [nose, left_eye, right_eye, left_ear, right_ear,
     left_shoulder, right_shoulder, left_elbow, right_elbow,
     left_wrist, right_wrist, left_hip, right_hip,
     left_knee, right_knee, left_ankle, right_ankle]
    
    This ensures the pose graph and all downstream logic work
    identically regardless of the underlying model.
    """
    
    @abstractmethod
    def detect(self, frame: np.ndarray) -> Optional[np.ndarray]:
        """
        Detect pose keypoints in frame.
        
        Args:
            frame: BGR image from camera
        
        Returns:
            Keypoints array of shape (17, 3) in format [x, y, confidence]
            or None if no pose detected.
            
            Coordinates are in pixel space relative to frame dimensions.
            Confidence is in range [0, 1].
        """
        pass
    def draw_skeleton(self, frame: np.ndarray, keypoints: Optional[np.ndarray]) -> np.ndarray:
        """
        Draw skeleton visualization on frame.
        
        Args:
            frame: BGR image
            keypoints: Keypoints array of shape (17, 3) in format [x, y, confidence]
        
        Returns:
            Annotated frame
        """
        if keypoints is None:
            return frame
        
        # COCO-17 skeleton connections (bone pairs)
        skeleton = [
            (0, 1), (0, 2),      # nose to eyes
            (1, 3), (2, 4),      # eyes to ears
            (0, 5), (0, 6),      # nose to shoulders
            (5, 6),              # shoulders
            (5, 7), (7, 9),      # left arm
            (6, 8), (8, 10),     # right arm
            (5, 11), (6, 12),    # shoulders to hips
            (11, 12),            # hips
            (11, 13), (13, 15),  # left leg
            (12, 14), (14, 16),  # right leg
        ]
        
        # Lower threshold for better detection of leg joints (important for MoveNet)
        conf_threshold = 0.15
        
        # Draw bones (lines between joints)
        for start_idx, end_idx in skeleton:
            if (keypoints[start_idx][2] > conf_threshold and keypoints[end_idx][2] > conf_threshold):
                start_point = (int(keypoints[start_idx][0]), int(keypoints[start_idx][1]))
                end_point = (int(keypoints[end_idx][0]), int(keypoints[end_idx][1]))
                cv2.line(frame, start_point, end_point, (0, 255, 0), 2)
        
        # Draw joints (circles at keypoints)
        for i in range(17):
            if keypoints[i][2] > conf_threshold:
                x, y = int(keypoints[i][0]), int(keypoints[i][1])
                cv2.circle(frame, (x, y), 4, (0, 0, 255), -1)
        
        return frame


class YOLOv8PoseAdapter(PoseModelAdapter):
    """
    Adapter for YOLOv8 Pose model.
    
    YOLOv8 already outputs COCO-17 format, so this is mostly a thin wrapper
    that extracts the primary person and ensures consistent output format.
    """
    
    def __init__(self, model_name: str = 'yolov8n-pose.pt'):
        """
        Args:
            model_name: YOLOv8 pose model name
        """
        from ultralytics import YOLO
        self.model = YOLO(model_name)
        print(f"  [ADAPTER] Loaded YOLOv8 Pose: {model_name}")
    
    def detect(self, frame: np.ndarray) -> Optional[np.ndarray]:
        """
        Detect pose using YOLOv8.
        
        YOLOv8 outputs COCO-17 format directly, so we just need to:
        1. Run inference
        2. Select primary person (largest bbox)
        3. Return keypoints in canonical format
        """
        results = self.model(frame, verbose=False)
        
        if results[0].keypoints is None or len(results[0].keypoints) == 0:
            return None
        
        if results[0].boxes is None or len(results[0].boxes) == 0:
            return None
        
        # Select primary person (largest bounding box)
        num_detections = len(results[0].keypoints)
        
        if num_detections == 0:
            return None
        
        if num_detections == 1:
            keypoints = results[0].keypoints.data[0].cpu().numpy()
            return keypoints  # Already in COCO-17 format [17, 3]
        
        # Multiple people - select largest bbox
        boxes = results[0].boxes.data.cpu().numpy()
        max_area = 0
        primary_idx = 0
        
        for i, box in enumerate(boxes):
            x1, y1, x2, y2 = box[:4]
            area = (x2 - x1) * (y2 - y1)
            if area > max_area:
                max_area = area
                primary_idx = i
        
        keypoints = results[0].keypoints.data[primary_idx].cpu().numpy()
        return keypoints  # Shape: (17, 3)
    def draw_skeleton(self, frame: np.ndarray, keypoints: Optional[np.ndarray]) -> np.ndarray:
        """
        Draw COCO-17 skeleton on frame.
        
        Uses cached keypoints (no inference).
        Draws circles for joints and lines for bones.
        """
        if keypoints is None:
            return frame
        
        # COCO-17 skeleton connections (bone pairs)
        skeleton = [
            (0, 1), (0, 2),      # nose to eyes
            (1, 3), (2, 4),      # eyes to ears
            (0, 5), (0, 6),      # nose to shoulders
            (5, 6),              # shoulders
            (5, 7), (7, 9),      # left arm
            (6, 8), (8, 10),     # right arm
            (5, 11), (6, 12),    # shoulders to hips
            (11, 12),            # hips
            (11, 13), (13, 15),  # left leg
            (12, 14), (14, 16),  # right leg
        ]
        
        # Draw bones (lines between joints)
        for start_idx, end_idx in skeleton:
            if (keypoints[start_idx][2] > 0.5 and keypoints[end_idx][2] > 0.5):
                start_point = (int(keypoints[start_idx][0]), int(keypoints[start_idx][1]))
                end_point = (int(keypoints[end_idx][0]), int(keypoints[end_idx][1]))
                cv2.line(frame, start_point, end_point, (0, 255, 0), 2)
        
        # Draw joints (circles at keypoints)
        for i in range(17):
            if keypoints[i][2] > 0.5:  # Only draw if confidence > 0.5
                x, y = int(keypoints[i][0]), int(keypoints[i][1])
                cv2.circle(frame, (x, y), 4, (0, 0, 255), -1)
        
        return frame


class MediaPipePoseAdapter(PoseModelAdapter):
    """
    Adapter for MediaPipe Pose model.
    
    MediaPipe outputs 33 landmarks, but we only need the COCO-17 subset.
    Maps MediaPipe landmark indices to canonical COCO-17 format.
    
    Updated for MediaPipe Tasks API (0.10.30+).
    """
    
    # MediaPipe landmark indices mapping to COCO-17
    # https://google.github.io/mediapipe/solutions/pose.html
    MEDIAPIPE_TO_COCO = {
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
    
    # Model download URL
    MODEL_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
    MODEL_PATH = "pose_landmarker.task"
    
    def __init__(self, confidence_threshold: float = 0.5):
        """
        Args:
            confidence_threshold: Minimum detection confidence
        """
        try:
            import mediapipe as mp
            from mediapipe.tasks import python
            from mediapipe.tasks.python import vision
            
            self.mp = mp
            
            # Download model if not present
            import os
            if not os.path.exists(self.MODEL_PATH):
                print(f"  [ADAPTER] Downloading MediaPipe Pose model...")
                import urllib.request
                urllib.request.urlretrieve(self.MODEL_URL, self.MODEL_PATH)
                print(f"  [ADAPTER] Model downloaded: {self.MODEL_PATH}")
            
            # Create PoseLandmarker with new Tasks API
            base_options = python.BaseOptions(model_asset_path=self.MODEL_PATH)
            options = vision.PoseLandmarkerOptions(
                base_options=base_options,
                running_mode=vision.RunningMode.IMAGE,
                min_pose_detection_confidence=confidence_threshold,
                min_tracking_confidence=confidence_threshold
            )
            self.pose = vision.PoseLandmarker.create_from_options(options)
            print(f"  [ADAPTER] Loaded MediaPipe Pose (Tasks API)")
        except ImportError:
            raise ImportError(
                "MediaPipe not installed. Install with: pip install mediapipe"
            )
    
    def detect(self, frame: np.ndarray) -> Optional[np.ndarray]:
        """
        Detect pose using MediaPipe Tasks API.
        
        MediaPipe workflow:
        1. Convert BGR to RGB
        2. Create MediaPipe Image
        3. Run inference
        4. Extract 33 landmarks
        5. Map to COCO-17 subset
        6. Convert visibility to confidence
        7. Denormalize coordinates to pixel space
        """
        # MediaPipe requires RGB
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        # Create MediaPipe Image
        mp_image = self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=rgb_frame)
        
        # Detect pose
        results = self.pose.detect(mp_image)
        
        if not results.pose_landmarks or len(results.pose_landmarks) == 0:
            return None
        
        h, w = frame.shape[:2]
        # Use first detected pose
        landmarks = results.pose_landmarks[0]
        
        # Initialize COCO-17 keypoints array
        keypoints = np.zeros((17, 3), dtype=np.float32)
        
        # Map MediaPipe landmarks to COCO-17
        for mp_idx, coco_idx in self.MEDIAPIPE_TO_COCO.items():
            lm = landmarks[mp_idx]
            
            # Denormalize coordinates to pixel space
            x = lm.x * w
            y = lm.y * h
            
            # MediaPipe provides 'visibility' (0-1), use as confidence
            # Clamp to [0, 1] in case of numerical issues
            confidence = np.clip(lm.visibility, 0.0, 1.0)
            
            keypoints[coco_idx] = [x, y, confidence]
        
        return keypoints  # Shape: (17, 3)


class MoveNetPoseAdapter(PoseModelAdapter):
    """
    MoveNet Thunder adapter - simple, correct, production-ready.
    
    Key insight: MoveNet handles aspect ratio internally.
    Just resize to 256x256, run, map back. No cropping needed.
    
    Post-processing:
    - Aggressive confidence boosting (MoveNet scores are 3-5x lower than YOLO)
    - Exponential moving average for smooth tracking
    - Outlier rejection to prevent skeleton jumps  
    - Body structure estimation for missing keypoints
    - Symmetry mirroring for remaining gaps
    """
    
    def __init__(self, model_path: str = 'movenet_thunder.tflite'):
        try:
            import tensorflow as tf
            
            self.interpreter = tf.lite.Interpreter(model_path=model_path)
            self.interpreter.allocate_tensors()
            self.input_details = self.interpreter.get_input_details()
            self.output_details = self.interpreter.get_output_details()
            self.input_size = self.input_details[0]['shape'][1]
            
            # ---- Temporal state ----
            self.prev_keypoints = None
            self.frame_count = 0
            
            # ---- Smoothing ----
            self.alpha_fast = 0.6   # For fast movement
            self.alpha_slow = 0.25  # For slow/still 
            self.jump_threshold = 60  # pixels - outlier if bigger
            self.jump_accept_after = 3  # accept after N consecutive "outliers"
            self.consecutive_jumps = np.zeros(17, dtype=np.int32)
            
            print(f"  [MoveNet] Loaded: {model_path} ({self.input_size}x{self.input_size})")
            
        except ImportError:
            raise ImportError("TensorFlow not installed. Install with: pip install tensorflow")
        except Exception as e:
            raise RuntimeError(f"Failed to load MoveNet model: {e}")
    
    def detect(self, frame: np.ndarray) -> Optional[np.ndarray]:
        """
        Simple, correct MoveNet detection pipeline:
        1. Resize full frame to 256x256 (model handles aspect ratio)
        2. Run inference
        3. Map normalized coords -> pixel coords  
        4. Boost confidence
        5. Outlier rejection
        6. Temporal smoothing (EMA)
        7. Estimate missing keypoints
        """
        self.frame_count += 1
        h, w = frame.shape[:2]
        
        # ==== STEP 1: Simple resize (NO cropping) ====
        img = cv2.resize(frame, (self.input_size, self.input_size))
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        input_tensor = np.expand_dims(img, axis=0).astype(np.uint8)
        
        # ==== STEP 2: Inference ====
        self.interpreter.set_tensor(self.input_details[0]['index'], input_tensor)
        self.interpreter.invoke()
        raw = self.interpreter.get_tensor(self.output_details[0]['index'])[0, 0]
        
        # ==== STEP 3: Map to pixel coordinates ====
        keypoints = np.zeros((17, 3), dtype=np.float32)
        for i in range(17):
            y_norm, x_norm, score = raw[i]
            keypoints[i] = [x_norm * w, y_norm * h, score]
        
        # ==== STEP 4: Boost confidence ====
        # MoveNet scores are ~3-5x lower than YOLO/MediaPipe
        # Raw range: 0.01-0.5 -> Boosted range: 0.15-0.95
        for i in range(17):
            raw_s = keypoints[i, 2]
            # Aggressive non-linear boost: stretch low scores up
            boosted = np.clip(raw_s * 2.0 + 0.15, 0.0, 0.99)
            keypoints[i, 2] = boosted
        
        # ==== STEP 5: Outlier rejection ====
        if self.prev_keypoints is not None:
            for i in range(17):
                if keypoints[i, 2] < 0.15:
                    continue
                if self.prev_keypoints[i, 2] < 0.15:
                    self.consecutive_jumps[i] = 0
                    continue
                
                dist = np.linalg.norm(keypoints[i, :2] - self.prev_keypoints[i, :2])
                if dist > self.jump_threshold:
                    self.consecutive_jumps[i] += 1
                    if self.consecutive_jumps[i] < self.jump_accept_after:
                        # Reject outlier, use previous position
                        keypoints[i, :2] = self.prev_keypoints[i, :2]
                        keypoints[i, 2] = self.prev_keypoints[i, 2] * 0.9
                    else:
                        # Accept - it's real movement
                        self.consecutive_jumps[i] = 0
                else:
                    self.consecutive_jumps[i] = 0
        
        # ==== STEP 6: Temporal smoothing (EMA) ====
        if self.prev_keypoints is not None:
            for i in range(17):
                if keypoints[i, 2] > 0.15 and self.prev_keypoints[i, 2] > 0.15:
                    # Adaptive alpha based on movement speed
                    dist = np.linalg.norm(keypoints[i, :2] - self.prev_keypoints[i, :2])
                    alpha = self.alpha_fast if dist > 15 else self.alpha_slow
                    keypoints[i, :2] = alpha * keypoints[i, :2] + (1 - alpha) * self.prev_keypoints[i, :2]
                elif keypoints[i, 2] < 0.15 and self.prev_keypoints[i, 2] > 0.15:
                    # Current missing, carry forward with decay
                    keypoints[i, :2] = self.prev_keypoints[i, :2]
                    keypoints[i, 2] = self.prev_keypoints[i, 2] * 0.85
        
        # ==== STEP 7: Estimate missing keypoints ====
        keypoints = self._fill_missing(keypoints)
        
        # ==== Validation ====
        valid = np.sum(keypoints[:, 2] > 0.2)
        if valid < 4:
            if self.prev_keypoints is not None:
                return self.prev_keypoints.copy()
            return None
        
        self.prev_keypoints = keypoints.copy()
        return keypoints
    
    def _fill_missing(self, kp: np.ndarray) -> np.ndarray:
        """Fill in missing keypoints using body geometry."""
        MIN = 0.15
        
        # Body structure: if joint is missing, estimate from these pairs
        rules = [
            # (target, source1, source2, w1, w2)
            # Hips from shoulders (torso proportions)
            (11, 5, 6, lambda s1,s2: (s1[0]*0.9+s2[0]*0.1, s1[1]+abs(s2[1]-s1[1])*1.5)),
            (12, 6, 5, lambda s1,s2: (s1[0]*0.9+s2[0]*0.1, s1[1]+abs(s2[1]-s1[1])*1.5)),
        ]
        
        # Simple midpoint rules
        midpoint_rules = [
            (7, 5, 9),    # elbow = mid(shoulder, wrist)
            (8, 6, 10),   # elbow = mid(shoulder, wrist)
            (13, 11, 15), # knee = mid(hip, ankle)
            (14, 12, 16), # knee = mid(hip, ankle)
        ]
        
        for target, src1, src2 in midpoint_rules:
            if kp[target, 2] < MIN and kp[src1, 2] >= MIN and kp[src2, 2] >= MIN:
                kp[target, :2] = (kp[src1, :2] + kp[src2, :2]) / 2
                kp[target, 2] = min(kp[src1, 2], kp[src2, 2]) * 0.5
        
        # Extrapolation rules
        extrap_rules = [
            (9, 7, 5),    # wrist from elbow + shoulder direction
            (10, 8, 6),   # wrist from elbow + shoulder direction
            (15, 13, 11), # ankle from knee + hip direction
            (16, 14, 12), # ankle from knee + hip direction
        ]
        
        for target, src1, src2 in extrap_rules:
            if kp[target, 2] < MIN and kp[src1, 2] >= MIN and kp[src2, 2] >= MIN:
                # Extrapolate: target = src1 + (src1 - src2)
                kp[target, :2] = kp[src1, :2] + (kp[src1, :2] - kp[src2, :2])
                kp[target, 2] = min(kp[src1, 2], kp[src2, 2]) * 0.4
        
        # Symmetry: mirror one side to the other
        pairs = [(5,6), (7,8), (9,10), (11,12), (13,14), (15,16)]
        
        # Find center x from best available pair
        center_x = None
        for l, r in [(5,6), (11,12)]:
            if kp[l, 2] >= MIN and kp[r, 2] >= MIN:
                center_x = (kp[l, 0] + kp[r, 0]) / 2
                break
        
        if center_x is not None:
            for l, r in pairs:
                if kp[l, 2] >= MIN and kp[r, 2] < MIN:
                    kp[r, 0] = 2 * center_x - kp[l, 0]
                    kp[r, 1] = kp[l, 1]
                    kp[r, 2] = kp[l, 2] * 0.5
                elif kp[r, 2] >= MIN and kp[l, 2] < MIN:
                    kp[l, 0] = 2 * center_x - kp[r, 0]
                    kp[l, 1] = kp[r, 1]
                    kp[l, 2] = kp[r, 2] * 0.5
        
        return kp


def create_pose_adapter(model_type: str, **kwargs) -> PoseModelAdapter:
    """
    Factory function to create appropriate pose adapter.
    
    Args:
        model_type: One of 'yolo', 'mediapipe', 'movenet'
        **kwargs: Model-specific parameters
    
    Returns:
        Appropriate PoseModelAdapter instance
    """
    model_type = model_type.lower()
    
    if model_type == 'yolo' or model_type == 'yolov8':
        model_name = kwargs.get('model_name', 'yolov8n-pose.pt')
        return YOLOv8PoseAdapter(model_name=model_name)
    
    elif model_type == 'mediapipe':
        confidence = kwargs.get('confidence_threshold', 0.5)
        return MediaPipePoseAdapter(confidence_threshold=confidence)
    
    elif model_type == 'movenet':
        model_path = kwargs.get('model_path', 'movenet_thunder.tflite')
        return MoveNetPoseAdapter(model_path=model_path)
    
    else:
        raise ValueError(
            f"Unknown model type: {model_type}. "
            f"Choose from: 'yolo', 'mediapipe', 'movenet'"
        )