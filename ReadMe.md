# Real-Time Pose Correction System (MediaPipe Edition)

A production-ready real-time pose correction platform for yoga and exercise guidance using **MediaPipe BlazePose (PoseLandmarker Full)**.

The system automatically generates **pose-state graphs from reference videos** and provides **live accuracy, biomechanical feedback, and repetition tracking** during user sessions.

---

## 📌 Overview

The system operates in two phases:

### 1️⃣ Offline Phase  
Builds a **pose graph (JSON)** from a reference video.

### 2️⃣ Online Phase  
Uses the pose graph to guide users through correct pose sequences using live camera input.

Both phases use **MediaPipe PoseLandmarker (BlazePose Full model)**.

---

## 🚀 Key Features

- Automatic pose-state discovery (no manual labeling)
- Deterministic state-machine execution
- Real-time accuracy tracking
- Rep counting
- Biomechanical feedback
- Adaptive tolerances
- Smooth state transitions
- Lightweight CPU execution
- Android-ready architecture

---

## 🏗 Architecture


Architecture
Reference Video → Graph Generator → pose_graph.json
                                           ↓
Camera → MediaPipe Pose → Feature Extraction → State Machine → Feedback
                                                      ↓
                                                 Visualization

---

## 📂 Core Components

### Offline

- `offline_graph_generator.py` — Generates pose graphs (JSON)
- `state_discovery.py` — Pose segmentation and clustering
- `pose_graph.py` — Graph data structure

### Online

- `online_monitor_final.py` — Real-time monitoring engine
- `pose_adapters.py` — MediaPipe PoseLandmarker adapter
- `pose_features.py` — Biomechanical feature extraction

---

## 🧠 Model Used

### MediaPipe BlazePose — PoseLandmarker Full

Model file:

pose_landmarker_full.task


This model:

- Runs fully on CPU
- Handles occlusion well
- Provides stable landmark tracking
- Optimized for mobile devices

---

## 🛠 Installation

### 1️⃣ Create Virtual Environment

```bash
python -m venv pose-env
pose-env\Scripts\activate
2️⃣ Install Requirements
```
Create requirements.txt:
```bash
mediapipe>=0.10.9
opencv-python>=4.8.0
numpy>=1.23.0
```
Install:
```bash
pip install -r requirements.txt
Project Structure
pose-correction-system/
│
├── offline_graph_generator.py
├── online_monitor_final.py
├── pose_graph.py
├── state_discovery.py
├── pose_features.py
├── pose_adapters.py
│
├── pose_landmarker_full.task
│
├── reference_videos/
│
└── pose_graphs/
    └── exercise_graph.json
  ```
Usage
1️⃣ Generate Pose Graph (Offline)
```bash
python offline_graph_generator.py reference_videos/triangle.mp4 --output pose_graphs/triangle.json
```
Notes:

Output is JSON

Graph stores state sequence and tolerances

Generated graph is reusable

2️⃣ Run Online Monitoring
```bash
python online_monitor_final.py --graph pose_graphs/triangle.json --camera 0 --max-reps 6
```
Optional Flags
| Flag            | Description                        |
| --------------- | ---------------------------------- |
| `--max-reps`    | Stop after N repetitions           |
| `--camera`      | Camera index                       |
| `--no-adaptive` | Disable adaptive tolerance scaling |
| `--speed`       | `fast`, `normal`, `strict`         |
| `--no-preview`  | Disable next-pose preview panel    |

Controls (During Session)
| Key | Action            |
| --- | ----------------- |
| `q` | Quit              |
| `s` | Skip current pose |
| `f` | Toggle fullscreen |
| `p` | Toggle preview    |


Accuracy is computed as:
```bash
Accuracy = (Correct Frames / Evaluated Frames) × 100
```
Starts after first correct match

Paused during transitions

Reset per state

Aggregated per rep

State Logic

Each exercise consists of:

A fixed temporal sequence of states

Minimum hold duration per state

Per-feature tolerances

Transitions occur when:
```bash
hold_time >= min_hold_duration
```
Rep is counted when:
sequence_index completes full cycle
Performance Notes

Single inference per frame

CPU optimized

Stable at 30 FPS on modern laptops

Android deployment ready via MediaPipe Tasks SDK

Android Deployment

For Android:

Use:

MediaPipe Tasks SDK (PoseLandmarker)

CameraX for capture

Same pose_graph JSON

Same state machine logic

Python implementation serves as reference prototype.

License
MIT License

Status

Version: 2.0.0
Model: MediaPipe BlazePose (Full)
Architecture: Single-model stable release
Deployment Target: Desktop + Android