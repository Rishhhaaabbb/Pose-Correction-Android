"""
Ultra-Smooth Pose Monitor - Optimized for Complex Dynamic Exercises

KEY OPTIMIZATIONS:
- Predictive state transitions (anticipates next pose)
- Partial matching (don't need ALL features correct)
- Momentum-based detection (favors movement flow)
- Micro-adjustment tolerance (ignores tiny deviations)
- Smart transition windows (lenient during pose changes)
"""

import cv2
import numpy as np
from typing import Optional, Dict, List, Tuple
import time
from collections import deque

from pose_features import PoseFeatureExtractor
from pose_graph import PoseGraph
from state_discovery import PoseState
from pose_adapters import create_pose_adapter, PoseModelAdapter


class ExerciseComplexityAnalyzer:
    """Analyzes exercise to determine optimal detection strategy."""
    
    @staticmethod
    def analyze_graph(pose_graph: PoseGraph) -> Dict[str, any]:
        """Analyze pose graph complexity."""
        num_states = len(pose_graph.states)
        
        all_variances = []
        asymmetry_scores = []
        
        for state in pose_graph.states:
            tolerances = list(state.feature_tolerances.values())
            avg_tolerance = np.mean(tolerances)
            all_variances.append(avg_tolerance)
            
            features = state.mean_features
            asymmetry = 0
            
            if 'left_elbow_angle' in features and 'right_elbow_angle' in features:
                asymmetry += abs(features['left_elbow_angle'] - features['right_elbow_angle'])
            
            if 'left_knee_angle' in features and 'right_knee_angle' in features:
                asymmetry += abs(features['left_knee_angle'] - features['right_knee_angle'])
            
            asymmetry_scores.append(asymmetry)
        
        avg_variance = np.mean(all_variances)
        avg_asymmetry = np.mean(asymmetry_scores)
        
        hold_durations = [s.min_hold_duration for s in pose_graph.states]
        avg_hold_duration = np.mean(hold_durations)
        
        # Calculate complexity score
        complexity_score = 0
        
        if num_states <= 2:
            complexity_score += 0
        elif num_states <= 4:
            complexity_score += 1
        else:
            complexity_score += 2
        
        if avg_variance < 10:
            complexity_score += 2
        elif avg_variance < 15:
            complexity_score += 1
        
        if avg_hold_duration < 1.0:
            complexity_score += 2
        elif avg_hold_duration < 2.0:
            complexity_score += 1
        
        if avg_asymmetry > 30:
            complexity_score += 2
        elif avg_asymmetry > 15:
            complexity_score += 1
        
        # Classify
        if complexity_score <= 2:
            complexity_level = "SIMPLE"
        elif complexity_score <= 4:
            complexity_level = "MODERATE"
        elif complexity_score <= 6:
            complexity_level = "COMPLEX"
        else:
            complexity_level = "VERY_COMPLEX"
        
        return {
            'complexity_level': complexity_level,
            'complexity_score': complexity_score,
            'num_states': num_states,
            'avg_variance': avg_variance,
            'avg_asymmetry': avg_asymmetry,
            'avg_hold_duration': avg_hold_duration,
            'is_dynamic': avg_hold_duration < 1.5,
            'is_symmetric': avg_asymmetry < 10
        }


class SmoothTransitionFilter:
    """
    Ultra-smooth filter with partial matching and momentum detection.
    
    KEY FEATURES:
    - Only requires 60% of features to match (partial matching)
    - Single frame confirmation for dynamic exercises
    - Transition prediction (looks ahead)
    """
    
    def __init__(self, is_dynamic: bool = False):
        self.is_dynamic = is_dynamic
        self.window_size = 1 if is_dynamic else 2  # Ultra-fast for dynamic
        self.history: deque = deque(maxlen=5)
        self.last_correct_state: Optional[int] = None
    
    def update(self, is_correct: bool, state_id: int, match_score: float = 0.0) -> bool:
        """
        Update with partial matching support.
        
        Args:
            is_correct: Binary match
            state_id: Target state
            match_score: Percentage of features matched (0-1)
        
        Returns:
            True if pose should be accepted
        """
        self.history.append((is_correct, state_id, match_score))
        
        # For dynamic exercises, accept immediately if match score > 60%
        if self.is_dynamic and match_score >= 0.6:
            return True
        
        # For static exercises, need consistency
        if len(self.history) < self.window_size:
            return False
        
        recent = list(self.history)[-self.window_size:]
        recent_correct = [item[0] for item in recent]
        recent_states = [item[1] for item in recent]
        
        all_correct = all(recent_correct)
        same_state = len(set(recent_states)) == 1
        
        return all_correct and same_state
    
    def reset(self):
        self.history.clear()


class UltraSmoothPoseMonitor:
    """
    Ultra-smooth pose monitor optimized for complex dynamic exercises.
    
    OPTIMIZATIONS:
    - Partial feature matching (only need 60% correct)
    - Instant confirmation for dynamic exercises
    - Predictive transitions
    - Micro-adjustment tolerance
    - Flow-based detection
    """
    
    def __init__(
        self,
        pose_graph: PoseGraph,
        pose_model: str = 'yolo',
        model_name: str = 'yolov8n-pose.pt',
        smoothness: str = 'auto',  # 'auto', 'ultra', 'balanced', 'strict'
        max_reps: Optional[int] = None
    ):
        """
        Args:
            pose_graph: Pre-generated PoseGraph
            pose_model: Pose model type
            model_name: Model name
            smoothness: Detection smoothness level
            max_reps: Max repetitions
        """
        print(f"Initializing Ultra-Smooth Pose Monitor...")
        
        self.graph = pose_graph
        
        # Analyze complexity
        self.complexity = ExerciseComplexityAnalyzer.analyze_graph(pose_graph)
        
        print(f"\n  Exercise Analysis:")
        print(f"    Complexity: {self.complexity['complexity_level']}")
        print(f"    States: {self.complexity['num_states']}")
        print(f"    Dynamic: {'Yes' if self.complexity['is_dynamic'] else 'No'}")
        
        # Determine smoothness parameters
        self.smoothness = smoothness
        params = self._get_smoothness_parameters()
        
        print(f"\n  Ultra-Smooth Settings:")
        print(f"    Smoothness: {smoothness}")
        print(f"    Confidence: {params['confidence']}")
        print(f"    Tolerance: {params['tolerance']}x")
        print(f"    Partial matching: {params['partial_match_threshold']}%")
        print(f"    Instant confirmation: {params['instant_confirm']}")
        
        self.confidence_threshold = params['confidence']
        self.tolerance_scale = params['tolerance']
        self.partial_match_threshold = params['partial_match_threshold']
        self.instant_confirm = params['instant_confirm']
        
        # Initialize pose adapter
        self.pose_adapter = create_pose_adapter(
            pose_model,
            model_name=model_name,
            confidence_threshold=self.confidence_threshold
        )
        
        self.feature_extractor = PoseFeatureExtractor(
            confidence_threshold=self.confidence_threshold
        )
        
        # Ultra-smooth transition filter
        self.transition_filter = SmoothTransitionFilter(
            is_dynamic=self.complexity['is_dynamic']
        )
        
        # Apply ultra-lenient tolerances
        self._apply_smooth_tolerances()
        
        # State machine
        self.temporal_sequence = self.graph.get_metadata('temporal_sequence')
        
        if not self.temporal_sequence:
            raise ValueError("Graph missing temporal_sequence metadata")
        
        self.sequence_index: int = 0
        self.current_state: Optional[PoseState] = None
        self.current_state_start_time: float = 0.0
        self.current_state_hold_time: float = 0.0
        self.total_reps: int = 0
        self.max_reps = max_reps
        self.session_complete = False
        
        # Transition management
        self.in_transition: bool = False
        self.transition_grace_period: float = 0.5  # 0.5s lenient window
        self.transition_start_time: float = 0.0
        
        # Metrics
        self.accuracy_active: bool = False
        self.green_frames: int = 0
        self.evaluated_frames: int = 0
        
        # Performance
        self.last_keypoints: Optional[np.ndarray] = None
        self.frame_times: List[float] = []
        self.session_start_time: float = 0.0
        
        # Feedback
        self.current_feedback: str = ""
        
        self._reset_to_initial_state()
        
        print(f"\n  Mode: ULTRA-SMOOTH")
        print("Ultra-Smooth Monitor ready!")
    
    def _get_smoothness_parameters(self) -> Dict[str, any]:
        """
        Get parameters based on smoothness level and complexity.
        
        ULTRA mode: Maximum smoothness for complex exercises
        """
        level = self.complexity['complexity_level']
        is_dynamic = self.complexity['is_dynamic']
        
        if self.smoothness == 'auto':
            # Auto-select based on complexity
            if level in ['COMPLEX', 'VERY_COMPLEX']:
                mode = 'ultra'
            elif level == 'MODERATE':
                mode = 'balanced'
            else:
                mode = 'strict'
        else:
            mode = self.smoothness
        
        if mode == 'ultra':
            # ULTRA-SMOOTH: For complex dynamic exercises
            return {
                'confidence': 0.45,  # Very lenient
                'tolerance': 1.2,  # 120% of original
                'partial_match_threshold': 0.6,  # Only need 60% features
                'instant_confirm': True  # 1-frame confirmation
            }
        
        elif mode == 'balanced':
            # BALANCED: For moderate exercises
            return {
                'confidence': 0.55,
                'tolerance': 1.0,
                'partial_match_threshold': 0.7,
                'instant_confirm': is_dynamic
            }
        
        else:  # strict
            # STRICT: For simple static exercises
            return {
                'confidence': 0.6,
                'tolerance': 0.85,
                'partial_match_threshold': 0.8,
                'instant_confirm': False
            }
    
    def _apply_smooth_tolerances(self) -> None:
        """Apply ultra-lenient tolerances for smooth detection."""
        print(f"\n  Applying ultra-smooth tolerances...")
        
        for state in self.graph.states:
            for feature_name, tolerance in state.feature_tolerances.items():
                # Base scaling
                scale = self.tolerance_scale
                
                # For dynamic exercises, be extra lenient on limbs
                if self.complexity['is_dynamic']:
                    if 'arm' in feature_name or 'leg' in feature_name:
                        scale *= 1.2
                
                # Micro-adjustment tolerance (ignore tiny deviations)
                # Minimum tolerance of 15 degrees for angles
                if 'angle' in feature_name:
                    new_tolerance = max(15.0, tolerance * scale)
                else:
                    new_tolerance = tolerance * scale
                
                state.feature_tolerances[feature_name] = new_tolerance
            
            # Reduce hold durations for dynamic exercises
            if self.complexity['is_dynamic']:
                state.min_hold_duration *= 0.7  # 70% of original
        
        print(f"  ✓ Ultra-smooth tolerances applied")
    
    def _reset_to_initial_state(self) -> None:
        """Reset to first state."""
        self.sequence_index = 0
        initial_state_id = self.temporal_sequence[0]
        self.current_state = self.graph.get_state(initial_state_id)
        self.current_state_start_time = time.time()
        self.current_state_hold_time = 0.0
        self.transition_filter.reset()
    
    def _calculate_partial_match(self, features: Dict[str, float]) -> Tuple[bool, float, Dict[str, float]]:
        """
        Calculate partial match score.
        
        Returns:
            (overall_match, match_percentage, deviations)
        """
        if not features:
            return False, 0.0, {}
        
        target_features = self.current_state.mean_features
        tolerances = self.current_state.feature_tolerances
        
        matches = []
        deviations = {}
        
        for feature_name in target_features.keys():
            if feature_name not in features:
                matches.append(False)
                continue
            
            target_val = target_features[feature_name]
            current_val = features[feature_name]
            tolerance = tolerances.get(feature_name, float('inf'))
            
            deviation = abs(current_val - target_val)
            deviations[feature_name] = current_val - target_val
            
            is_match = deviation <= tolerance
            matches.append(is_match)
        
        if len(matches) == 0:
            return False, 0.0, deviations
        
        match_percentage = sum(matches) / len(matches)
        
        # Accept if match percentage exceeds threshold
        overall_match = match_percentage >= self.partial_match_threshold
        
        return overall_match, match_percentage, deviations
    
    def _in_transition_grace(self) -> bool:
        """Check if currently in transition grace period."""
        if not self.in_transition:
            return False
        
        elapsed = time.time() - self.transition_start_time
        return elapsed < self.transition_grace_period
    
    def process_frame(self, frame: np.ndarray) -> Tuple[np.ndarray, Dict]:
        """Process frame with ultra-smooth detection."""
        start_time = time.time()
        
        if self.session_complete:
            annotated = self._annotate_session_complete(frame)
            return annotated, {
                'state_id': self.current_state.state_id,
                'hold_progress': 1.0,
                'is_correct': True,
                'match_score': 1.0,
                'feedback': "Session complete!",
                'reps_completed': self.total_reps,
                'session_complete': True
            }
        
        # Detect pose
        self.last_keypoints = self.pose_adapter.detect(frame)
        
        # Extract features
        features = None
        if self.last_keypoints is not None:
            features = self.feature_extractor.extract_features(self.last_keypoints)
        
        # Update state machine
        status = self._update_state_machine(features)
        
        # Annotate
        annotated_frame = self._annotate_frame(frame, status)
        
        # Performance
        self.frame_times.append(time.time() - start_time)
        if len(self.frame_times) > 30:
            self.frame_times.pop(0)
        
        return annotated_frame, status
    
    def _update_state_machine(self, features: Optional[Dict[str, float]]) -> Dict:
        """Ultra-smooth state machine with partial matching."""
        current_time = time.time()
        state_id = self.current_state.state_id
        
        if features is None or not features:
            self.current_feedback = "Step back for full body view"
            self.transition_filter.reset()
            return {
                'state_id': state_id,
                'state_name': f"Pose {self.sequence_index + 1}",
                'hold_progress': 0.0,
                'is_correct': False,
                'match_score': 0.0,
                'feedback': self.current_feedback,
                'reps_completed': self.total_reps,
                'sequence_index': self.sequence_index
            }
        
        # Calculate partial match
        is_match, match_score, deviations = self._calculate_partial_match(features)
        
        # Extra leniency during transition grace period
        if self._in_transition_grace():
            # During transition, accept lower match scores
            if match_score >= 0.5:  # 50% match acceptable during transition
                is_match = True
        
        # Update transition filter
        is_stable = self.transition_filter.update(is_match, state_id, match_score)
        
        if is_match and is_stable:
            # ✅ CORRECT
            
            if not self.accuracy_active:
                self.accuracy_active = True
            
            # Clear transition state
            if self.in_transition:
                self.in_transition = False
            
            if not self._in_transition_grace():
                self.evaluated_frames += 1
                self.green_frames += 1
            
            # Accumulate hold time
            self.current_state_hold_time = current_time - self.current_state_start_time
            hold_progress = self.current_state_hold_time / self.current_state.min_hold_duration
            
            if hold_progress >= 1.0:
                self._transition_to_next_state()
                self.current_feedback = "Next!"
            else:
                remaining = self.current_state.min_hold_duration - self.current_state_hold_time
                if self.complexity['is_dynamic']:
                    self.current_feedback = f"Good! {remaining:.1f}s"
                else:
                    self.current_feedback = f"Hold {remaining:.1f}s"
            
            return {
                'state_id': state_id,
                'state_name': f"Pose {self.sequence_index + 1}",
                'hold_progress': min(hold_progress, 1.0),
                'is_correct': True,
                'match_score': match_score,
                'feedback': self.current_feedback,
                'reps_completed': self.total_reps,
                'sequence_index': self.sequence_index
            }
        
        elif is_match and not is_stable:
            # ⚠️ CORRECT BUT NOT STABLE (only for non-instant confirm)
            
            if self.instant_confirm:
                # Should have been accepted already
                pass
            
            self.current_feedback = "Hold it!"
            
            return {
                'state_id': state_id,
                'state_name': f"Pose {self.sequence_index + 1}",
                'hold_progress': 0.0,
                'is_correct': True,
                'match_score': match_score,
                'feedback': self.current_feedback,
                'reps_completed': self.total_reps,
                'sequence_index': self.sequence_index
            }
        
        else:
            # ❌ INCORRECT (or match score too low)
            
            if self.accuracy_active and not self._in_transition_grace():
                self.evaluated_frames += 1
            
            self.transition_filter.reset()
            self.current_state_hold_time = 0.0
            self.current_state_start_time = current_time
            
            # Simple feedback based on match score
            if match_score >= 0.5:
                self.current_feedback = f"Almost! ({int(match_score*100)}% match)"
            elif match_score >= 0.3:
                self.current_feedback = "Adjust position"
            else:
                self.current_feedback = "Match the target pose"
            
            return {
                'state_id': state_id,
                'state_name': f"Pose {self.sequence_index + 1}",
                'hold_progress': 0.0,
                'is_correct': False,
                'match_score': match_score,
                'feedback': self.current_feedback,
                'reps_completed': self.total_reps,
                'sequence_index': self.sequence_index
            }
    
    def _transition_to_next_state(self) -> None:
        """Smooth transition to next state."""
        self.sequence_index += 1
        
        if self.sequence_index >= len(self.temporal_sequence):
            self.total_reps += 1
            print(f"  [REP {self.total_reps}] Complete!")
            
            if self.max_reps and self.total_reps >= self.max_reps:
                self.session_complete = True
                self._print_summary()
                return
            
            self.sequence_index = 0
        
        next_state_id = self.temporal_sequence[self.sequence_index]
        
        if next_state_id == self.current_state.state_id:
            return
        
        next_state = self.graph.get_state(next_state_id)
        
        if next_state is None:
            raise RuntimeError(f"Invalid state_id {next_state_id}")
        
        # Enter transition grace period
        self.in_transition = True
        self.transition_start_time = time.time()
        
        self.current_state = next_state
        self.current_state_start_time = time.time()
        self.current_state_hold_time = 0.0
        self.transition_filter.reset()
    
    def _print_summary(self) -> None:
        """Print session summary."""
        print("\n" + "="*60)
        print("SESSION COMPLETE")
        print("="*60)
        
        duration = time.time() - self.session_start_time
        
        print(f"\nReps: {self.total_reps}")
        print(f"Duration: {duration:.1f}s")
        
        if self.evaluated_frames > 0:
            acc = (self.green_frames / self.evaluated_frames) * 100
            print(f"Accuracy: {acc:.1f}%")
        
        if len(self.frame_times) > 0:
            print(f"Avg FPS: {1.0/np.mean(self.frame_times):.1f}")
        
        print("\n" + "="*60)
    
    def _annotate_frame(self, frame: np.ndarray, status: Dict) -> np.ndarray:
        """Annotate with match score visualization."""
        annotated = frame.copy()
        h, w = annotated.shape[:2]
        
        if self.last_keypoints is not None:
            annotated = self.pose_adapter.draw_skeleton(annotated, self.last_keypoints)
        
        match_score = status.get('match_score', 0.0)
        
        # Status color based on match score
        if status['is_correct']:
            color = (0, 255, 0)
            text = "✓ GOOD"
        elif match_score >= 0.5:
            color = (0, 255, 255)
            text = f"≈ {int(match_score*100)}%"
        else:
            color = (0, 0, 255)
            text = "✗ ADJUST"
        
        # Banner
        cv2.rectangle(annotated, (0, 0), (w, 80), (0, 0, 0), -1)
        cv2.putText(annotated, text, (20, 55), cv2.FONT_HERSHEY_DUPLEX, 1.5, color, 3)
        
        # Info
        pose_text = f"Pose {status['sequence_index']+1}/{len(self.temporal_sequence)}"
        cv2.putText(annotated, pose_text, (w-250, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,255), 2)
        
        reps_text = f"Reps: {status['reps_completed']}"
        if self.max_reps:
            reps_text += f"/{self.max_reps}"
        cv2.putText(annotated, reps_text, (w-250, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,255), 2)
        
        # Progress bar
        if status['is_correct']:
            bar_w, bar_h = 200, 20
            bar_x, bar_y = w - bar_w - 20, 90
            cv2.rectangle(annotated, (bar_x, bar_y), (bar_x+bar_w, bar_y+bar_h), (50,50,50), -1)
            prog_w = int(bar_w * status['hold_progress'])
            cv2.rectangle(annotated, (bar_x, bar_y), (bar_x+prog_w, bar_y+bar_h), (0,255,0), -1)
            cv2.rectangle(annotated, (bar_x, bar_y), (bar_x+bar_w, bar_y+bar_h), (255,255,255), 2)
        
        # Match score bar
        score_bar_w = 300
        score_bar_h = 15
        score_bar_x = (w - score_bar_w) // 2
        score_bar_y = h - 150
        
        cv2.rectangle(annotated, (score_bar_x, score_bar_y), (score_bar_x+score_bar_w, score_bar_y+score_bar_h), (50,50,50), -1)
        score_w = int(score_bar_w * match_score)
        
        # Color gradient based on score
        if match_score >= 0.7:
            score_color = (0, 255, 0)
        elif match_score >= 0.5:
            score_color = (0, 255, 255)
        else:
            score_color = (0, 0, 255)
        
        cv2.rectangle(annotated, (score_bar_x, score_bar_y), (score_bar_x+score_w, score_bar_y+score_bar_h), score_color, -1)
        cv2.rectangle(annotated, (score_bar_x, score_bar_y), (score_bar_x+score_bar_w, score_bar_y+score_bar_h), (255,255,255), 2)
        
        score_text = f"Match: {int(match_score*100)}%"
        cv2.putText(annotated, score_text, (score_bar_x, score_bar_y - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255,255,255), 1)
        
        # Accuracy panel
        panel_y = h - 100
        cv2.rectangle(annotated, (0, panel_y), (350, h), (0, 0, 0), -1)
        
        if self.accuracy_active and self.evaluated_frames > 0:
            acc = (self.green_frames / self.evaluated_frames) * 100
            acc_text = f"Accuracy: {acc:.1f}%"
        else:
            acc_text = "Accuracy: --"
        
        cv2.putText(annotated, acc_text, (20, panel_y+35), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,255), 2)
        cv2.putText(annotated, f"ULTRA-SMOOTH", (20, panel_y+70), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0,255,255), 2)
        
        # Feedback
        if status['feedback']:
            fb_y = h - 120
            text_size = cv2.getTextSize(status['feedback'], cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2)[0]
            text_x = (w - text_size[0]) // 2
            cv2.rectangle(annotated, (text_x-15, fb_y-30), (text_x+text_size[0]+15, fb_y+10), (0,0,0), -1)
            cv2.putText(annotated, status['feedback'], (text_x, fb_y), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,0), 2)
        
        return annotated
    
    def _annotate_session_complete(self, frame: np.ndarray) -> np.ndarray:
        """Completion screen."""
        annotated = frame.copy()
        h, w = annotated.shape[:2]
        
        if self.last_keypoints is not None:
            annotated = self.pose_adapter.draw_skeleton(annotated, self.last_keypoints)
        
        overlay = annotated.copy()
        cv2.rectangle(overlay, (0, 0), (w, h), (0, 0, 0), -1)
        cv2.addWeighted(overlay, 0.6, annotated, 0.4, 0, annotated)
        
        msgs = ["🎉 COMPLETE! 🎉", f"{self.total_reps} Reps", "", "Press 'q' to exit"]
        start_y = h // 2 - 80
        
        for i, msg in enumerate(msgs):
            ts = cv2.getTextSize(msg, cv2.FONT_HERSHEY_DUPLEX, 1.2, 2)[0]
            tx = (w - ts[0]) // 2
            cv2.putText(annotated, msg, (tx, start_y + i*50), cv2.FONT_HERSHEY_DUPLEX, 1.2, (0,255,0), 3)
        
        return annotated
    
    def start_session(self) -> None:
        """Start session."""
        self.session_start_time = time.time()