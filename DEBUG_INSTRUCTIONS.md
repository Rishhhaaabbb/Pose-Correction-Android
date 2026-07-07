# Debug Instructions - Find the Root Cause

## The Problem
Your app generates 5 duplicate states instead of 4 unique states. The code is now identical to the working reference, so the issue must be in **runtime behavior**.

## What I've Done
1. ✅ Copied exact working `StateDiscovery.kt` from reference
2. ✅ Verified `GeneratorConfig` defaults are correct (0.15, 15, 5)
3. ✅ Confirmed `OfflineGraphGeneratorImpl` passes config correctly
4. ✅ Added logging to show distance values during merging

## The Real Question
**Are the segment distances actually <= 0.15 but not merging, OR are they > 0.15?**

If distances are > 0.15, then the poses genuinely ARE different (maybe different video, different MediaPipe version, different feature extraction).

If distances are <= 0.15 but not merging, then there's a logic bug.

## What You Need To Do

### Step 1: Build the App
The gradle daemon keeps hanging. Do this:
1. Close Android Studio
2. Open Task Manager → End all `java.exe` processes
3. Delete `.gradle` folder in project root
4. Reopen Android Studio
5. Build → Rebuild Project

### Step 2: Run Graph Generation
1. Launch the app
2. Go to Graph Generation screen
3. Upload the **EXACT same video** that produces 4 states in the working version
4. Use **DEFAULT parameters** (don't change anything)
5. Click Generate

### Step 3: Capture Logs  
While generation is running, open terminal and run:
```bash
adb logcat | findstr "StateDiscovery"
```

Or in Android Studio Logcat, filter by "StateDiscovery"

### Step 4: Send Me These Logs
The logs will show:
```
StateDiscovery: Distance to State 0: 0.0823 (threshold: 0.15)
StateDiscovery: Distance to State 1: 0.1876 (threshold: 0.15)
StateDiscovery: No match found, creating new state
```

**This will tell us WHY states aren't merging.**

## Possible Causes

1. **Different Video**: You're using a different video than the working reference
2. **Different MediaPipe Version**: Feature extraction produces different values
3. **Different Device/Android Version**: Floating point differences
4. **UI Override**: You changed parameters in the settings UI

## The Fix Depends On The Logs
- If distances are **0.16-0.20**: Slightly increase `stabilityThreshold` to 0.20
- If distances are **> 0.30**: The video/extraction is fundamentally different
- If distances are **< 0.15**: There's a logic bug I need to fix

**I cannot fix this without seeing the actual distance values being computed at runtime.**

Please build, run, and send me the "StateDiscovery" logs showing the distance values.
