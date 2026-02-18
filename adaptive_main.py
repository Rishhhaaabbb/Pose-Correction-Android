"""
Ultra-Smooth Pose Monitoring - Seamless Detection for Complex Exercises

Usage:
    python ultra_smooth_main.py --graph jumpingjacks_graph.json --max-reps 5
"""

import cv2
import argparse
from pose_graph import PoseGraph
from adaptive_pose_monitor import UltraSmoothPoseMonitor


def main():
    parser = argparse.ArgumentParser(description='Ultra-Smooth Pose Monitoring')
    
    parser.add_argument('--graph', type=str, required=True, help='Pose graph JSON')
    parser.add_argument('--model', type=str, default='yolo', choices=['yolo', 'mediapipe', 'movenet'])
    parser.add_argument('--max-reps', type=int, default=None)
    parser.add_argument('--camera', type=int, default=0)
    parser.add_argument('--smoothness', type=str, default='auto', 
                        choices=['auto', 'ultra', 'balanced', 'strict'],
                        help='Detection smoothness (auto recommended)')
    
    args = parser.parse_args()
    
    # Load graph
    print(f"\n{'='*60}")
    print("ULTRA-SMOOTH POSE MONITORING")
    print(f"{'='*60}\n")
    print(f"Loading: {args.graph}")
    
    try:
        graph = PoseGraph.load_json(args.graph)
        print("✓ Graph loaded\n")
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return 1
    
    # Create monitor
    try:
        monitor = UltraSmoothPoseMonitor(
            pose_graph=graph,
            pose_model=args.model,
            smoothness=args.smoothness,
            max_reps=args.max_reps
        )
    except Exception as e:
        print(f"❌ ERROR: {e}")
        import traceback
        traceback.print_exc()
        return 1
    
    # Open camera
    print(f"\nOpening camera {args.camera}...")
    cap = cv2.VideoCapture(args.camera)
    
    if not cap.isOpened():
        print(f"❌ Camera {args.camera} failed")
        return 1
    
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    print("✓ Camera ready\n")
    
    print(f"{'='*60}")
    print("READY TO START")
    print(f"{'='*60}")
    print("\n💡 Tips:")
    print("  • Full body visible")
    print("  • Move naturally, don't over-hold")
    print("  • System is very lenient")
    print("  • Press 'q' to quit\n")
    
    if args.max_reps:
        print(f"🎯 Goal: {args.max_reps} reps\n")
    
    print("Starting in 3 seconds...\n")
    cv2.waitKey(3000)
    
    monitor.start_session()
    
    # Main loop
    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            annotated_frame, status = monitor.process_frame(frame)
            cv2.imshow('Ultra-Smooth Monitor', annotated_frame)
            
            if cv2.waitKey(1) & 0xFF == ord('q'):
                print("\n⏹️  Stopped by user")
                break
            
            if status.get('session_complete', False):
                print(f"\n🎉 {monitor.total_reps} reps complete!")
                cv2.waitKey(2000)
                break
    
    except KeyboardInterrupt:
        print("\n⏹️  Interrupted")
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        cap.release()
        cv2.destroyAllWindows()
        print("\nExiting...\n")
    
    return 0


if __name__ == '__main__':
    exit(main())