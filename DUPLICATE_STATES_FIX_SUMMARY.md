# Duplicate States Fix Summary

## Problem
The Android app was generating **5 states with duplicates** (State 0 = State 4, State 1 = State 3) for a video that should generate **3 unique states** (matching the Python reference implementation).

## Root Cause
The Android implementation had **incorrect default parameters** that didn't match the working Python implementation. The parameters were set to overly strict values from a previous iteration.

## Solution
Updated the default parameters in three files to match the working Python implementation:

### 1. StateDiscovery.kt
**Before:**
```kotlin
class StateDiscovery(
    val stabilityThreshold: Double = 0.12,
    val minHoldFrames: Int = 18,
    val smoothingWindow: Int = 7,
    // ...
)
```

**After:**
```kotlin
class StateDiscovery(
    val stabilityThreshold: Double = 0.15,  // ← Changed from 0.12
    val minHoldFrames: Int = 15,            // ← Changed from 18
    val smoothingWindow: Int = 5,           // ← Changed from 7
    // ...
)
```

### 2. GeneratorConfig.kt
**Before:**
```kotlin
data class GeneratorConfig(
    val stabilityThreshold: Double = 0.12,
    val minHoldFrames: Int = 18,
    val smoothingWindow: Int = 7,
    // ...
)
```

**After:**
```kotlin
data class GeneratorConfig(
    val stabilityThreshold: Double = 0.15,  // ← Changed from 0.12
    val minHoldFrames: Int = 15,            // ← Changed from 18
    val smoothingWindow: Int = 5,           // ← Changed from 7
    // ...
)
```

### 3. OfflineGraphGeneratorImpl.kt
**Before:**
```kotlin
val stateDiscovery = StateDiscovery(
    stabilityThreshold = config.stabilityThreshold,
    minHoldFrames = config.minHoldFrames,
    smoothingWindow = config.smoothingWindow,
    transitionBuffer = config.transitionBuffer,
    toleranceMultiplier = config.toleranceMultiplier,
    mergeThreshold = config.mergeThreshold,  // ← Explicitly passed
)
```

**After:**
```kotlin
val stateDiscovery = StateDiscovery(
    stabilityThreshold = config.stabilityThreshold,
    minHoldFrames = config.minHoldFrames,
    smoothingWindow = config.smoothingWindow,
    transitionBuffer = config.transitionBuffer,
    toleranceMultiplier = config.toleranceMultiplier,
    // mergeThreshold NOT passed - uses default (stabilityThreshold)
)
```

### 4. Additional Cleanup
- Removed debug `println` statements from `StateDiscovery.kt`
- Simplified `smoothFeatures()` to match the reference implementation (removed running sums optimization)

## Why This Fixes the Duplicate States

### stabilityThreshold: 0.12 → 0.15
- **Lower threshold** (0.12) = more sensitive = detects smaller pose changes = more segments
- **Higher threshold** (0.15) = less sensitive = only detects significant pose changes = fewer segments
- With 0.12, the algorithm was splitting single poses into multiple segments

### minHoldFrames: 18 → 15
- **Higher value** (18) = requires poses to be held longer before recognition
- **Lower value** (15) = recognizes poses faster
- Combined with the lower stabilityThreshold, this was causing legitimate pose transitions to be split into separate states

### smoothingWindow: 7 → 5
- **Larger window** (7) = more smoothing = can mask quick transitions
- **Smaller window** (5) = less smoothing = better captures actual pose changes
- The larger window was over-smoothing legitimate pose variations

## Expected Result
With these changes, the app should now generate **3 unique states** matching the Python reference implementation:
- State 0: First pose position
- State 1: Second pose position  
- State 2: Third pose position

No more duplicates (State 0 ≠ State 4, State 1 ≠ State 3).

## Testing
Build completed successfully. Please test by:
1. Upload the same video that was generating 5 states
2. Use default parameter settings
3. Verify that exactly 3 states are generated
4. Check that the temporal sequence is correct (no duplicate state IDs for the same physical pose)

## Files Modified
- `core/poseengine/src/main/java/com/core/poseengine/StateDiscovery.kt`
- `domain/src/main/java/com/domain/generator/GeneratorConfig.kt`
- `data/src/main/java/com/data/generator/OfflineGraphGeneratorImpl.kt`
- `app/src/main/res/values/strings.xml` (added missing string resources for graph deletion)
