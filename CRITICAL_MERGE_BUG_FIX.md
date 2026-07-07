# CRITICAL BUG FIX: State Merging Issue

## The Problem

**Symptoms:**
- Python generates 3 states (correct)
- Android generates 5 states (WRONG - duplicates)
- State 0 and State 4 are the same pose
- State 1 and State 3 are the same pose

## Root Cause

### Python Code (from `state_discovery.py`):
```python
def _find_matching_state(self, segment_features, canonical_states):
    for state_id, state_mean_features, _, _ in canonical_states:
        distance = self._compute_feature_distance(segment_features, state_mean_features)
        
        # Use stability threshold as merge threshold
        if distance <= self.stability_threshold:  # ← Uses SAME threshold
            return state_id
    return None
```

**Python uses `stabilityThreshold` for BOTH:**
1. Detecting stable segments
2. Merging similar segments into states

### Android Code (BEFORE FIX):
```kotlin
class StateDiscovery(
    val stabilityThreshold: Double = 0.12,
    mergeThreshold: Double? = null,
) {
    // BUG: Defaults to 0.5× instead of 1.0×
    val mergeThreshold: Double = mergeThreshold ?: stabilityThreshold * 0.5  // ← WRONG!
```

**Android was using DIFFERENT thresholds:**
- Segment detection: `stabilityThreshold = 0.12`
- State merging: `mergeThreshold = 0.06` (0.5× smaller!)

## The Impact

With `mergeThreshold = 0.06`:
- Two segments with distance = 0.08 would NOT merge
- But in Python, with threshold = 0.12, they WOULD merge
- Result: **Duplicate states** for poses that should be the same

### Example:
```
Segment A (standing): features = {...}
Segment B (standing): features = {...}
Distance(A, B) = 0.08

Python (threshold=0.12):
  0.08 ≤ 0.12 → MERGE ✓
  Result: 1 state

Android (threshold=0.06):
  0.08 > 0.06 → NO MERGE ✗
  Result: 2 states (duplicate!)
```

## The Fix

### Android Code (AFTER FIX):
```kotlin
class StateDiscovery(
    val stabilityThreshold: Double = 0.12,
    mergeThreshold: Double? = null,
) {
    // FIX: Use SAME threshold as Python
    val mergeThreshold: Double = mergeThreshold ?: stabilityThreshold  // ← CORRECT!
```

Now both use `stabilityThreshold = 0.12` for merging, **exactly matching Python**.

## Expected Result

After rebuilding and reinstalling:

### Before:
```
States: 5
- State 0 (standing) 
- State 1 (arms up)
- State 2 (transition)
- State 3 (arms up) ← DUPLICATE of State 1
- State 4 (standing) ← DUPLICATE of State 0

Temporal: S0→S1→S2→S3→S4
```

### After:
```
States: 3
- State 0 (standing)
- State 1 (arms up)  
- State 2 (transition)

Temporal: S0→S1→S2→S0 (proper cycle)
```

## Why This Bug Existed

Looking at the code comments in `GeneratorConfig.kt`, there's a note:

```kotlin
 * Previous Kotlin default was stabilityThreshold × 0.5 — this caused
 * states that Python merges (distance ≤ 0.12) to remain separate in Kotlin
 * (distance > 0.06)...
```

The developers **knew about this issue** in `GeneratorConfig` and fixed it there, but **forgot to fix it in `StateDiscovery.kt`**!

## Files Changed

**`core/poseengine/src/main/java/com/core/poseengine/StateDiscovery.kt`**
```diff
- val mergeThreshold: Double = mergeThreshold ?: stabilityThreshold * 0.5
+ val mergeThreshold: Double = mergeThreshold ?: stabilityThreshold
```

## Verification

To verify the fix works:

1. **Uninstall old app**
2. **Install new APK** 
3. **Generate graph with same video**
4. **Check results:**
   - Should have 3 states (not 5)
   - No duplicate poses
   - Proper cyclic transitions

## Technical Details

### Merge Logic Flow:

```
For each new segment:
  1. Try asymmetry veto (checks left/right side consistency)
  2. Compute distance to each existing canonical state
  3. If distance ≤ mergeThreshold:
     → Assign segment to that state (MERGE)
  4. Else:
     → Create new canonical state (NO MERGE)
```

**The bug:** Step 3 was using `0.06` instead of `0.12`, causing too many "NO MERGE" decisions.

### Why 0.5× was chosen (incorrectly):

The original developer likely thought:
- "Segment detection needs looser threshold (0.12)"
- "State merging should be stricter (0.06)"
- "To avoid over-merging different poses"

But this is **wrong** because:
1. Python uses the SAME threshold for both
2. The asymmetry veto already prevents opposite-side merging
3. Using 0.5× creates artificial duplicates

## Apology & Lesson

I apologize for not catching this immediately. I should have:

1. ✅ Checked the algorithm logic (did this - it matched)
2. ✅ Checked the parameter defaults (did this - found mismatch)
3. ❌ **Checked the derived parameters** (`mergeThreshold`) - MISSED THIS

The bug was in a **computed property** that derived its value from a parameter, not in the parameter itself. This is a subtle bug that's easy to miss.

## Final State

Now the Android implementation:
- ✅ Uses same algorithm as Python
- ✅ Uses same parameter defaults as Python  
- ✅ Uses same merge threshold as Python
- ✅ Should produce identical graphs as Python

This was the **actual root cause** of your graph generation issues.
