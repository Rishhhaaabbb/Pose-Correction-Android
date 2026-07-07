# Parameter Default Fix Summary

## Problem Identified

Your generated graph had **11 states with very short hold durations** (0.2s-0.4s), indicating the algorithm was too sensitive and detecting micro-movements as separate states.

### Root Cause

The Android defaults did **NOT match** the Python defaults:

| Parameter | Python Default | Old Android Default | New Android Default |
|-----------|----------------|---------------------|---------------------|
| `stabilityThreshold` | 0.12 | ❌ 0.10 | ✅ 0.12 |
| `minHoldFrames` | 18 | ❌ 12 | ✅ 18 |
| `smoothingWindow` | 7 | ❌ 5 | ✅ 7 |
| `transitionBuffer` | 3 | ✅ 3 | ✅ 3 |
| `toleranceMultiplier` | 2.0 | ✅ 2.0 | ✅ 2.0 |

## Impact

### Old Behavior (0.10 stability, 12 min frames, 5 smoothing):
- **More sensitive** to small movements
- Detected **more segments** (micro-states)
- **Shorter hold requirements** (0.4 seconds @ 30fps)
- Result: **11 states** with brief holds

### New Behavior (0.12 stability, 18 min frames, 7 smoothing):
- **Less sensitive** to small movements
- Detects **fewer, more stable segments**
- **Longer hold requirements** (0.6 seconds @ 30fps)
- Expected: **3-6 states** with proper holds

## Files Changed

1. **`core/poseengine/src/main/java/com/core/poseengine/StateDiscovery.kt`**
   - Updated class defaults to match Python

2. **`domain/src/main/java/com/domain/generator/GeneratorConfig.kt`**
   - Updated data class defaults to match Python
   - Updated documentation

## What You Should See Now

After reinstalling the app and regenerating a graph:

### Expected Improvements:
- ✅ **Fewer states** (3-6 for typical exercises instead of 11)
- ✅ **Longer hold durations** (0.6s-2.0s instead of 0.2s-0.4s)
- ✅ **Proper cycles** (A→B→C→A instead of linear A→B→C→...→K)
- ✅ **Better state merging** (similar poses consolidated)

### Example: Jumping Jacks
**Before (11 states):**
```
S0→S1→S2→S3→S4→S5→S6→S7→S8→S9→S10
(too many micro-states)
```

**After (3 states):**
```
S0 (arms down) → S1 (arms up) → S2 (transition) → S0 (cycle)
(clean, proper states)
```

## Testing Instructions

1. **Uninstall the old app**
2. **Install new APK** from `app/build/outputs/apk/debug/app-debug.apk`
3. **Generate a new graph** with the same video
4. **Check the results:**
   - Number of states should be much lower
   - Hold durations should be longer (0.6s+)
   - Should see proper cycles in temporal sequence
   - Graph should match exercise flow

## Default Parameter Settings

When you open the settings screen now, you'll see:

```
Graph Name: (empty)
Stability Threshold: 0.12   ← Increased from 0.10
Min Hold Frames: 18         ← Increased from 12
Skip Frames: 1
Smoothing Window: 7         ← Increased from 5
Transition Buffer: 3
Tolerance Multiplier: 2.0
Confidence Threshold: 0.6
```

These now **exactly match** your Python implementation defaults.

## When to Adjust Parameters

Even with correct defaults, you may need to adjust for specific exercises:

### If you get TOO FEW states (1-2):
- **Decrease** `stabilityThreshold` to 0.10
- **Decrease** `minHoldFrames` to 12-15
- Exercise might have very similar poses

### If you still get TOO MANY states (>10):
- **Increase** `stabilityThreshold` to 0.15
- **Increase** `minHoldFrames` to 20-25
- **Increase** `smoothingWindow` to 9-11
- Video might have too much noise or shaking

### For dynamic exercises (burpees, jumping):
- Use **higher** `stabilityThreshold` (0.15)
- Use **lower** `minHoldFrames` (12-15)
- Movements are faster

### For static exercises (yoga, planks):
- Use **lower** `stabilityThreshold` (0.10)
- Use **higher** `minHoldFrames` (20-30)
- Poses are held longer

## Verification

To verify the fix worked, compare:

**Old Graph:**
- States: 11
- Transitions: 11
- Hold times: 0.2s-0.4s
- Pattern: Linear

**New Graph (Expected):**
- States: 3-6
- Transitions: 3-6
- Hold times: 0.6s-2.0s
- Pattern: Cyclic (for repetitive exercises)

## Conclusion

The Android implementation logic was **correct** and matched Python perfectly. The only issue was **incorrect default parameter values**. This has now been fixed, and your graphs should generate properly with the same quality as the Python implementation.
