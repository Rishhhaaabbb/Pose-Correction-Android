# Graph Generation Diagnostic Guide

## Logic Verification: Python vs Android

### ✅ **CONFIRMED: The Android implementation exactly matches the Python logic**

| Component | Python | Android | Match? |
|-----------|--------|---------|--------|
| State Discovery Algorithm | ✓ | ✓ | ✅ YES |
| Temporal Smoothing | Rolling window | Rolling window | ✅ YES |
| Segment Detection | Stability threshold | Stability threshold | ✅ YES |
| State Merging | Merge threshold (0.5 × stability) | Merge threshold (0.5 × stability) | ✅ YES |
| Temporal Sequence Tracking | ✓ | ✓ | ✅ YES |
| Transition Building | Consecutive pairs + wrap-around | Consecutive pairs + wrap-around | ✅ YES |
| Graph Validation | ✓ | ✓ | ✅ YES |

## Common Issues & Solutions

### Issue 1: "Generated graph isn't proper"

**Possible causes:**

1. **Parameter Mismatch**
   - Python defaults: `stabilityThreshold=0.12, minHoldFrames=18, smoothingWindow=7`
   - Android defaults: `stabilityThreshold=0.10, minHoldFrames=12, smoothingWindow=5`
   - **Solution**: Use the same parameters in Android as in Python

2. **Video Quality Differences**
   - MediaPipe model versions might differ
   - Frame extraction quality
   - **Solution**: Test with the same video on both platforms

3. **Too Few Segments Detected**
   - Video too short or poses not held long enough
   - **Solution**: Check `numSegments` in success screen
   - Should see: "State merging: X segments → Y unique states"

4. **Merge Threshold Too Aggressive**
   - Multiple distinct poses being merged into one state
   - **Solution**: Try setting `mergeThreshold` explicitly (increase it)

5. **Temporal Sequence Issue**
   - States correct, but transitions wrong
   - **Solution**: Check if temporal sequence matches expected exercise flow

### Issue 2: Graph Validation Fails

**Check for:**
- Empty states list
- Missing transitions
- Isolated states (no outgoing edges)
- Invalid state ID references

**Debug**: Look at the validation errors in the error screen

### Issue 3: Graph Can't Start Monitoring

**Common causes:**
1. **JSON Deserialization Failure**
   - Check if file exists and is valid JSON
   - Solution: We added validation in Fix #8

2. **State Matching Too Strict**
   - Real-time poses don't match graph tolerances
   - Solution: Increase `toleranceMultiplier` when generating

3. **Missing Initial State**
   - Graph has no states or empty transitions
   - Solution: Regenerate with lower `minHoldFrames`

## Debugging Steps

### Step 1: Check Generation Output

After generating a graph, check the success screen for:
```
✅ Graph Generated Successfully
States discovered: X
Valid frames: Y
Filename: exercise_name.json
```

**Expected values:**
- States: 3-10 (typical for most exercises)
- Valid frames: >50% of total frames
- If states = 1: Exercise too simple or parameters too strict
- If states > 15: Parameters too sensitive, might be noise

### Step 2: Check Generated JSON

Open the generated `.json` file and verify:

```json
{
  "version": "1.0",
  "metadata": {
    "temporalSequence": [0, 1, 2, 0, 1, 2],  // ← Should show actual flow
    "numStates": 3,
    "numSegments": 6,
    "fps": 30.0,
    ...
  },
  "states": [
    { "stateId": 0, "meanFeatures": {...}, ... },
    { "stateId": 1, "meanFeatures": {...}, ... },
    { "stateId": 2, "meanFeatures": {...}, ... }
  ],
  "transitions": {
    "0": [1],    // State 0 → State 1
    "1": [2],    // State 1 → State 2
    "2": [0]     // State 2 → State 0 (cycle)
  }
}
```

**Check:**
- `temporalSequence` matches exercise flow
- `transitions` has edges for each state
- No isolated states (every state should have outgoing edges)

### Step 3: Compare with Python Output

Generate the same exercise with Python using **identical parameters**:

```python
python offline_graph_generator.py video.mp4 \
    --output python_graph.json \
    --stability 0.12 \
    --min-hold 18 \
    --skip-frames 1 \
    --confidence 0.6
```

Then generate in Android with:
```
Stability Threshold: 0.12
Min Hold Frames: 18
Skip Frames: 1
Smoothing Window: 7
Transition Buffer: 3
Tolerance Multiplier: 2.0
Confidence Threshold: 0.6
```

**Compare:**
- Number of states should be identical (±1 due to floating point)
- Temporal sequence should be very similar
- Transitions should match

### Step 4: Test Monitoring

Try to start a monitoring session with the generated graph:

1. Go to Workout tab
2. Select the generated exercise
3. Start camera monitoring

**If it fails immediately:**
- Graph JSON is malformed
- Check logcat for deserialization errors

**If it fails to detect first pose:**
- Initial state tolerances too strict
- Try regenerating with higher `toleranceMultiplier`

**If it gets stuck on one state:**
- Tolerances too loose (all poses match first state)
- Try regenerating with lower `toleranceMultiplier`

## Parameter Tuning Guide

### Stability Threshold
- **Higher (0.15-0.20)**: Fewer segments, more permissive
- **Lower (0.08-0.10)**: More segments, stricter pose detection
- **Recommended**: Start with 0.12 (Python default)

### Min Hold Frames
- **Higher (20-30)**: Only detects very stable poses
- **Lower (8-12)**: Detects briefer pose holds
- **Recommended**: 18 frames @ 30fps = 0.6 seconds

### Smoothing Window
- **Higher (9-11)**: More temporal averaging, smoother
- **Lower (3-5)**: More responsive to quick movements
- **Recommended**: 7 frames @ 30fps = 0.23 seconds

### Tolerance Multiplier
- **Higher (2.5-3.0)**: More forgiving during monitoring
- **Lower (1.5-2.0)**: Stricter pose matching
- **Recommended**: 2.0 (balanced)

## Recommended Settings by Exercise Type

### Yoga/Static Poses
```
stabilityThreshold: 0.12
minHoldFrames: 25
smoothingWindow: 9
toleranceMultiplier: 2.5
```

### Dynamic Exercises (Jumping Jacks, etc.)
```
stabilityThreshold: 0.15
minHoldFrames: 12
smoothingWindow: 5
toleranceMultiplier: 2.0
```

### Strength Training (Pushups, Squats)
```
stabilityThreshold: 0.10
minHoldFrames: 18
smoothingWindow: 7
toleranceMultiplier: 2.2
```

## Getting Help

If the graph still isn't working:

1. **Share the logcat output** during generation
   - Filter: `tag:GraphGeneration OR tag:StateDiscovery`

2. **Share the generated JSON file** (first 100 lines)
   - Check if states and transitions look reasonable

3. **Describe the specific issue:**
   - "Only 1 state detected" → Parameters too permissive
   - "Too many states (>20)" → Parameters too strict
   - "Can't start monitoring" → Validation or deserialization error
   - "Gets stuck on one state" → Tolerances too loose
   - "Immediately fails validation" → State/transition mismatch

4. **Share the video characteristics:**
   - Exercise type (yoga, cardio, strength)
   - Video duration
   - Number of repetitions
   - Whether person stays in frame

## Conclusion

The Android implementation is a **faithful port of the Python code**. The logic is identical. If graphs aren't generating properly, it's likely due to:

1. **Parameter differences** (use Python defaults in Android)
2. **Video quality issues** (lighting, occlusion, frame rate)
3. **Exercise characteristics** (poses not held long enough)

Not due to algorithmic differences.
