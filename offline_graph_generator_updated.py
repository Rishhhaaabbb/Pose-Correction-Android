"""
Offline Pose Graph Generator
Processes a reference video to automatically extract pose states
and build a pose graph for online monitoring.

Pipeline:
1. Load video and extract frames
2. Run MediaPipe PoseLandmarker on each frame
3. Select single primary subject (best visibility)
4. Extract normalized features from keypoints
5. Discover stable pose states using temporal analysis
6. Merge identical/similar states
7. Build directed graph from actual video transitions
8. Store temporal sequence in metadata for deterministic replay
9. Save graph to JSON
"""

import cv2
import numpy as np
from typing import List, Optional, Tuple
import os
from datetime import datetime

from pose_features import PoseFeatureExtractor
from state_discovery import StateDiscovery
from pose_graph import PoseGraph
from pose_adapters import MediaPipePoseAdapter


class OfflineGraphGenerator:
    """Processes reference video to generate a pose graph using MediaPipe."""

    def __init__(
        self,
        model_path: str = 'pose_landmarker_full.task',
        confidence_threshold: float = 0.6,
        stability_threshold: float = 0.12,
        min_hold_frames: int = 18,
        smoothing_window: int = 7,
        transition_buffer: int = 3,
        tolerance_multiplier: float = 2.0,
    ):
        print(f"Loading MediaPipe PoseLandmarker: {model_path}")
        self.pose_adapter = MediaPipePoseAdapter(
            model_path=model_path,
            confidence_threshold=confidence_threshold,
        )
        self.feature_extractor = PoseFeatureExtractor(
            confidence_threshold=confidence_threshold
        )
        self.state_discovery = StateDiscovery(
            stability_threshold=stability_threshold,
            min_hold_frames=min_hold_frames,
            smoothing_window=smoothing_window,
            transition_buffer=transition_buffer,
            tolerance_multiplier=tolerance_multiplier,
        )

    def generate_from_video(
        self,
        video_path: str,
        output_json_path: str,
        max_frames: Optional[int] = None,
        skip_frames: int = 1,
    ) -> PoseGraph:
        """
        Generate pose graph from reference video.

        Args:
            video_path: Path to reference video file
            output_json_path: Path for output JSON file
            max_frames: Maximum frames to process (None = all)
            skip_frames: Process every Nth frame

        Returns:
            Generated PoseGraph object
        """
        print(f"\n{'='*60}")
        print("GENERATING POSE GRAPH FROM VIDEO")
        print(f"{'='*60}")
        print(f"Video:  {video_path}")
        print(f"Output: {output_json_path}")

        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise ValueError(f"Cannot open video: {video_path}")

        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

        print(f"\nVideo Info:")
        print(f"  Resolution: {width}x{height}")
        print(f"  FPS: {fps}")
        print(f"  Total Frames: {total_frames}")
        print(f"  Duration: {total_frames/fps:.2f}s")

        effective_fps = fps / skip_frames

        print("\nExtracting pose features ...")
        feature_sequence, keypoint_sequence = self._extract_features_from_video(
            cap, max_frames, skip_frames
        )
        cap.release()

        valid_frames = sum(1 for f in feature_sequence if f)
        print(f"  Processed: {len(feature_sequence)} frames")
        print(f"  Valid poses: {valid_frames} ({valid_frames/len(feature_sequence)*100:.1f}%)")

        if valid_frames < 10:
            raise ValueError(
                "Too few valid poses detected. Check video quality or camera angle."
            )

        print("\nDiscovering pose states (with automatic merging) ...")
        states = self.state_discovery.discover_states(
            feature_sequence,
            fps=effective_fps,
            keypoint_sequence=keypoint_sequence,
        )
        print(f"  Discovered: {len(states)} distinct pose states")

        if not states:
            raise ValueError(
                "No pose states discovered. Try adjusting parameters or video."
            )

        temporal_sequence = self.state_discovery.get_temporal_sequence()
        print(f"\nTemporal sequence: {temporal_sequence}")
        print(f"  Length: {len(temporal_sequence)} state occurrences")

        num_segments = len(temporal_sequence)
        num_unique_states = len(states)
        if num_segments > num_unique_states:
            print(
                f"  ✓ State merging: {num_segments} segments → {num_unique_states} unique states"
            )

        print("\nBuilding pose graph from temporal transitions ...")
        graph = PoseGraph()
        graph.build_from_temporal_sequence(states, temporal_sequence)

        stats = graph.get_graph_statistics()
        print(f"  Graph type: {'Linear' if stats['is_linear'] else 'Non-Linear'}")
        print(f"  Has cycles: {'Yes' if stats['has_cycles'] else 'No'}")
        print(f"  Total transitions: {stats['num_transitions']}")

        graph.set_metadata('video_path', os.path.basename(video_path))
        graph.set_metadata('fps', float(effective_fps))
        graph.set_metadata('original_fps', float(fps))
        graph.set_metadata('skip_frames', skip_frames)
        graph.set_metadata('total_frames', len(feature_sequence))
        graph.set_metadata('valid_frames', valid_frames)
        graph.set_metadata('num_states', len(states))
        graph.set_metadata('num_segments', len(temporal_sequence))
        graph.set_metadata('created_at', datetime.now().isoformat())
        graph.set_metadata('stability_threshold', self.state_discovery.stability_threshold)
        graph.set_metadata('temporal_sequence', temporal_sequence)

        is_valid, errors = graph.validate()
        if not is_valid:
            print("\nWARNING: Graph validation failed:")
            for error in errors:
                print(f"  - {error}")
        else:
            print("  Graph validation: PASSED")

        graph.save_json(output_json_path)
        graph.print_summary()

        print(f"\n{'='*60}")
        print("GRAPH GENERATION COMPLETE")
        print(f"{'='*60}\n")

        return graph

    def _extract_features_from_video(
        self,
        cap: cv2.VideoCapture,
        max_frames: Optional[int],
        skip_frames: int,
    ) -> Tuple[List[dict], List[Optional[np.ndarray]]]:
        """Extract pose features and keypoints from all video frames."""
        feature_sequence = []
        keypoint_sequence = []
        frame_idx = 0
        processed_count = 0

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            if frame_idx % skip_frames != 0:
                frame_idx += 1
                continue

            if max_frames and processed_count >= max_frames:
                break

            keypoints = self.pose_adapter.detect(frame)

            if keypoints is not None:
                features = self.feature_extractor.extract_features_with_fallback(keypoints)
                feature_sequence.append(features if features else {})
            else:
                feature_sequence.append({})

            keypoint_sequence.append(keypoints)
            processed_count += 1

            if processed_count % 30 == 0:
                print(f"  Processed: {processed_count} frames", end='\r')

            frame_idx += 1

        print(f"  Processed: {processed_count} frames")
        return feature_sequence, keypoint_sequence


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description='Generate pose graph from reference video (MediaPipe)'
    )
    parser.add_argument('video_path', type=str, help='Path to reference video file')
    parser.add_argument('--output', type=str, default='pose_graph.json',
                        help='Output JSON file path (default: pose_graph.json)')
    parser.add_argument('--model', type=str, default='pose_landmarker_full.task',
                        help='MediaPipe PoseLandmarker model path')
    parser.add_argument('--max-frames', type=int, default=None,
                        help='Maximum frames to process (default: all)')
    parser.add_argument('--skip-frames', type=int, default=1,
                        help='Process every Nth frame (default: 1)')
    parser.add_argument('--stability', type=float, default=0.12,
                        help='Stability threshold for state detection and merging (default: 0.12)')
    parser.add_argument('--min-hold', type=int, default=18,
                        help='Minimum frames to hold pose (default: 18)')
    parser.add_argument('--confidence', type=float, default=0.6,
                        help='Keypoint confidence threshold (default: 0.6)')
    args = parser.parse_args()

    generator = OfflineGraphGenerator(
        model_path=args.model,
        confidence_threshold=args.confidence,
        stability_threshold=args.stability,
        min_hold_frames=args.min_hold,
    )

    try:
        generator.generate_from_video(
            video_path=args.video_path,
            output_json_path=args.output,
            max_frames=args.max_frames,
            skip_frames=args.skip_frames,
        )
        print("SUCCESS: Pose graph generated.")
    except Exception as e:
        print(f"\nERROR: {e}")
        import traceback
        traceback.print_exc()
        return 1

    return 0


if __name__ == '__main__':
    exit(main())