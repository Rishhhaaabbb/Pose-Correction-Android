# Graph Generation Testing Report
**Testing Date:** _____________  
**Tester Name:** _____________  
**Device Model:** _____________  
**Android Version:** _____________  
**App Version:** _____________

---

## Task G1: Video Selection

### Test Case G1.1: Open File Picker
**Steps to Reproduce:**
1. Launch the app
2. Navigate to Graph Generation screen
3. Tap "Choose Video" button

**Expected Behavior:**
- File picker opens successfully
- Shows video files in device storage
- Interface is responsive

**Actual Behavior:**
File picker opens successfully. Interface is responsive and shows available options.

**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G1.2: Select Valid MP4 Video
**Steps to Reproduce:**
1. Open Graph Generation screen
2. Tap "Choose Video"
3. Select a valid MP4 video file (e.g., 10-20 seconds long)
4. Observe response

**Expected Behavior:**
- Video is accepted
- Video filename/path is displayed
- Settings screen appears with parameter fields
- No error messages

**Actual Behavior:**
Video is accepted successfully. Video filename is displayed and settings screen appears with all parameter fields. No errors encountered.

**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G1.3: Select Non-Video File
**Steps to Reproduce:**
1. Open Graph Generation screen
2. Tap "Choose Video"
3. Select a non-video file (PDF, image, text file, etc.)
4. Observe response

**Expected Behavior:**
- Appropriate error message is displayed
- App does not crash
- User can retry with a different file
- Error message clearly indicates video format is required

**Actual Behavior:**
File picker is restricted to video files only. Non-video files cannot be selected - the picker filters and shows only video files (video/*).

**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** This is actually better UX - prevents user error by only showing compatible files.

---

### Test Case G1.4: Cancel Video Selection
**Steps to Reproduce:**
1. Open Graph Generation screen
2. Tap "Choose Video"
3. Cancel the file picker without selecting a file

**Expected Behavior:**
- Returns to idle screen gracefully
- No crash or error messages
- "Choose Video" button remains functional

**Actual Behavior:**
Returns to idle screen gracefully. No crash or errors. "Choose Video" button remains functional and can be tapped again.

**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G1.5: Record Video Using Camera (NEW - Bug Found)
**Steps to Reproduce:**
1. Open Graph Generation screen
2. Tap "Choose Video"
3. Select "Choose Camera (Record)" option or camera recording option
4. Observe response

**Expected Behavior:**
- Camera app opens for recording
- User can record a new video
- After recording, video is accepted for graph generation
- No crash or error

**Actual Behavior:**
App crashes immediately when attempting to select camera/record option.

**Status:** ☐ Pass | ☑ Fail  
**Severity:** ☑ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** CRITICAL BUG - App crashes when trying to record video directly. Users must use pre-recorded videos only. This may require checking camera permissions, activity result contract implementation, or URI handling for camera captures.

---

## Task G2: Generation Parameters

### Test Case G2.1: Verify All Parameter Fields Exist
**Steps to Reproduce:**
1. Select a valid video file
2. Check Settings screen for all parameter fields

**Expected Behavior:**
All the following fields should be visible and editable:
- ☐ Graph Name field
- ☐ Stability Threshold
- ☐ Min Hold Frames
- ☐ Skip Frames
- ☐ Smoothing Window
- ☐ Transition Buffer
- ☐ Tolerance Multiplier
- ☐ Confidence Threshold

**Actual Behavior:**
Settings screen with parameter fields is NOT visible after video selection. The app skips directly to processing screen, bypassing the parameter configuration screen entirely. Generation starts immediately with default parameters.

**Status:** ☐ Pass | ☑ Fail  
**Severity:** ☐ Crash | ☑ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** MAJOR BUG - Parameter settings screen is being skipped. Users cannot customize generation parameters or provide custom graph names. The app proceeds directly from video selection to processing. This means:
- All generations use default parameters only
- Users cannot name their graphs (defaults to video filename)
- Advanced configuration is impossible
- The SettingsContent UI exists in code but is not being shown, suggesting a logic issue in the screen flow (pendingVideoUri state may not be triggering the settings screen correctly)

---

### Test Case G2.2 - G2.9: Parameter Testing
**Status:** ☐ Pass | ☐ Fail | ☑ Blocked  
**Notes:** All remaining parameter tests (G2.2 through G2.9) are BLOCKED because the parameter settings screen is not accessible. Cannot test default values, validation, input handling, or custom graph names until the settings screen issue is resolved.

**Tests Blocked:**
- G2.2: Default Values Display
- G2.3: Input Validation - Zero Values
- G2.4: Input Validation - Extremely High Values
- G2.5: Input Validation - Negative Values
- G2.6: Input Validation - Non-Numeric Values
- G2.7: Confidence Threshold Range
- G2.8: Graph Name Input (Empty)
- G2.9: Custom Graph Name

---
**Steps to Reproduce:**
1. Select a valid video
2. Check default values in all fields

**Expected Behavior:**
Default values should be:
- Stability Threshold: 0.12
- Min Hold Frames: 18
- Skip Frames: 1
- Smoothing Window: 7
- Transition Buffer: 3
- Tolerance Multiplier: 2.0
- Confidence Threshold: 0.6

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.3: Input Validation - Zero Values
**Steps to Reproduce:**
1. Select a valid video
2. Set Stability Threshold to 0
3. Tap "Start Generation"

**Expected Behavior:**
- Validation error is displayed
- Error message indicates invalid value
- Generation does not start
- No crash occurs

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.4: Input Validation - Extremely High Values
**Steps to Reproduce:**
1. Select a valid video
2. Set the following extreme values:
   - Stability Threshold: 9999
   - Min Hold Frames: 10000
   - Smoothing Window: 5000
3. Tap "Start Generation"

**Expected Behavior:**
- Values are accepted OR appropriate warning is shown
- App does not crash
- Generation either proceeds or shows meaningful error
- No frozen/unresponsive state

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.5: Input Validation - Negative Values
**Steps to Reproduce:**
1. Select a valid video
2. Try to enter negative values in numeric fields (e.g., -5 for Min Hold Frames)
3. Tap "Start Generation"

**Expected Behavior:**
- Negative values are either rejected during input OR
- Validation error is displayed when starting generation
- No crash occurs

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.6: Input Validation - Non-Numeric Values
**Steps to Reproduce:**
1. Select a valid video
2. Try to enter text/special characters in numeric fields (e.g., "abc" in Stability Threshold)
3. Observe behavior

**Expected Behavior:**
- Non-numeric input is prevented OR
- Validation error is displayed
- Keyboard shows numeric input for number fields
- No crash occurs

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.7: Confidence Threshold Range
**Steps to Reproduce:**
1. Select a valid video
2. Set Confidence Threshold to 1.5 (above valid range 0.0-1.0)
3. Tap "Start Generation"

**Expected Behavior:**
- Validation error indicates value must be between 0.0 and 1.0
- Generation does not start
- No crash occurs

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.8: Graph Name Input
**Steps to Reproduce:**
1. Select a valid video
2. Leave Graph Name field empty
3. Start generation
4. Check resulting filename

**Expected Behavior:**
- Graph name defaults to video filename
- File is saved with appropriate name
- .json extension is added automatically

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case G2.9: Custom Graph Name
**Steps to Reproduce:**
1. Select a valid video
2. Enter custom name in Graph Name field (e.g., "my_custom_exercise")
3. Start generation
4. Verify saved filename

**Expected Behavior:**
- File is saved with custom name provided
- .json extension is added automatically
- File appears in graph list with custom name

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

## Task G3: Processing

### Test Case G3.1: Short Video Processing
**Steps to Reproduce:**
1. Select a short video (5-15 seconds)
2. Use default parameter values
3. Tap "Start Generation"
4. Observe processing progress

**Expected Behavior:**
- Processing starts immediately
- Progress indicator displays and updates smoothly
- Progress messages are shown:
  - "Reading video..."
  - "Extracting poses..."
  - "Analysing poses..."
  - "Building graph..."
  - "Validating graph..."
  - "Saving..."
- Processing completes successfully
- No crash or freeze

**Actual Behavior:**
Processing starts immediately after video selection. Progress indicator displays and updates smoothly. Progress messages are shown throughout the process. Processing completed successfully with no crashes or freezes. App remained responsive throughout.

**Processing Time:** 10-12 seconds for 10 second video  
**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Excellent performance - processing time roughly matches video length (1:1 ratio)

---

### Test Case G3.2: Processing Time Benchmark
**Steps to Reproduce:**
1. Select a 10-second video
2. Use default parameters
3. Start generation
4. Measure processing time from start to completion

**Expected Behavior:**
- 10-second video processes in reasonable time (< 2 minutes recommended)
- Progress updates continuously
- Device remains responsive
- No overheating issues

**Actual Behavior:**
10-second video processed in 10-12 seconds. Progress updated continuously throughout. Device remained responsive with no performance issues or overheating.

**Processing Time:** 10-12 seconds  
**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Excellent performance - processing is near real-time (1:1 ratio)

---

### Test Case G3.3: Long Video Processing
**Steps to Reproduce:**
1. Select a longer video (30-60 seconds)
2. Start generation
3. Monitor processing throughout

**Expected Behavior:**
- Processing continues without crash
- Progress bar updates incrementally
- Frame count updates regularly
- Device does not become unresponsive
- Processing completes successfully

**Actual Behavior:**
24-second video processed successfully in 30 seconds. Progress bar updated incrementally throughout. Device remained responsive. No crashes or performance issues.

**Processing Time:** 30 seconds for 24 second video  
**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Processing ratio approximately 1.25:1 (video length to processing time) - still very reasonable performance

---

### Test Case G3.4: Cancel Mid-Processing (if implemented)
**Steps to Reproduce:**
1. Start generation with any video
2. While processing is ongoing (at ~50% progress)
3. Press back button or cancel button (if available)

**Expected Behavior:**
- Processing stops gracefully
- Returns to previous screen or idle state
- No crash occurs
- No orphaned files or corrupted data
- App remains functional for new generation

**Actual Behavior:**
Processing cancels gracefully when back button is pressed. Returns to idle state without crashes. App remains functional and ready for new generation.

**Status:** ☑ Pass | ☐ Fail | ☐ N/A (no cancel option)  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Good error handling - graceful cancellation works as expected

---

### Test Case G3.5: Background Interruption
**Steps to Reproduce:**
1. Start generation with a video
2. During processing, minimize app or lock screen
3. Wait a few seconds
4. Return to app

**Expected Behavior:**
- Processing continues OR resumes gracefully
- Progress is maintained or shows appropriate status
- No crash or data loss
- User can see current state

**Actual Behavior:**
App handles background interruption well. Processing state is maintained when returning to the app. No crashes or data loss observed.

**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Good lifecycle management - handles app backgrounding correctly

---

### Test Case G3.6: Low Quality Video
**Steps to Reproduce:**
1. Select a low quality/resolution video (if available)
2. Start generation with default parameters
3. Observe processing

**Expected Behavior:**
- Processing completes (may take longer or show warnings)
- Appropriate message if video quality is too low
- No crash occurs
- Result indicates video quality issues if applicable

**Actual Behavior:**
Low quality exercise video: Handled successfully, processing completed without issues.
Random non-exercise video: Gave appropriate validation error, indicating the video content was not suitable for pose graph generation.

**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Excellent validation - app can differentiate between low quality exercise videos (processes them) and non-exercise content (rejects with validation error). This shows good pose detection and content validation.

---

## Task G4: Results

### Test Case G4.1: Verify Graph JSON Saved
**Steps to Reproduce:**
1. Complete a successful generation
2. Navigate to device file explorer or use app's file manager
3. Check `<app_data>/files/graphs/` directory

**Expected Behavior:**
- Generated .json file exists in graphs directory
- File has correct name (custom or default)
- File size is > 0 bytes
- File contains valid JSON structure

**Actual Behavior:**
Cannot directly verify file system (requires file explorer/ADB), but the success message "Graph saved to library" indicates file was saved. Generated graphs appear in the app's graph selector list, confirming successful save operation.

**File Path:** Assumed to be app_data/files/graphs/ (as per code)  
**File Size:** Not verified  
**Status:** ☑ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Indirect verification through app UI - graph appears in selector list after generation, confirming save was successful

---

### Test Case G4.2: Graph Statistics Display
**Steps to Reproduce:**
1. Complete a successful generation
2. Review the success screen

**Expected Behavior:**
Success screen displays:
- ☐ "✅" success icon
- ☐ Success title message
- ☐ "States Discovered" count (number shown)
- ☐ "Valid Frames" count (number shown)
- ☐ "Saved to" filename
- ☐ "View Graph" button
- ☐ "Generate Another" button

**Actual Behavior:**
Success screen shows:
- ☑ Checkmark/success icon
- ☑ Success message: "Graph saved to library"
- ☐ "States Discovered" count - NOT displayed
- ☐ "Valid Frames" count - NOT displayed
- ☐ "Saved to" filename - NOT displayed
- ☐ "View Graph" button - NOT available
- ☐ "Generate Another" button - NOT available
- ☑ "Done" button only

**States Discovered:** Not shown  
**Valid Frames:** Not shown  
**Status:** ☐ Pass | ☑ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☑ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** UI/UX Issue - Success screen is too minimal. Missing useful information:
- No statistics (states, frames) that would help user understand graph quality
- No filename shown (user doesn't know what it was named)
- No "View Graph" option to visualize the result
- No "Generate Another" button for quick workflow
The code has SuccessContent with all these features, but a simpler version is being shown instead.

---

### Test Case G4.3: Generate Same Exercise Twice
**Steps to Reproduce:**
1. Select a video and generate graph with name "test_exercise"
2. Return to graph generation
3. Select the SAME video
4. Generate again with name "test_exercise_2"
5. Check graphs directory

**Expected Behavior:**
- Both graphs are saved with unique names
- No file overwrite occurs
- Both files appear in graph list
- File sizes may differ slightly
- No error or warning about duplicate

**Actual Behavior:**
Test modified due to missing settings screen - cannot provide custom names.
Test 1: Uploaded same video twice (defaults to same filename)
- No error shown
- Appears to overwrite silently (only one graph in list)
Test 2: Uploaded different videos
- Both saved successfully
- Both appear in graph list

**First File:** Same video (default name)  
**Second File:** Same video (default name) - likely overwritten  
**Status:** ☐ Pass | ☑ Fail  
**Severity:** ☐ Crash | ☑ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Issue found - Silent overwrite without warning. When same video is uploaded twice, the second generation silently overwrites the first with no warning or confirmation dialog. Users lose previous graph without knowing. Better UX would be: (1) Show confirmation dialog, (2) Auto-append timestamp/number, or (3) Prevent duplicate generation.

---

### Test Case G4.4: Duplicate Name Handling
**Steps to Reproduce:**
1. Generate a graph with name "duplicate_test"
2. Generate another graph with the EXACT same name "duplicate_test"
3. Check what happens to files

**Expected Behavior:**
- System either:
  - Prevents duplicate names with error message, OR
  - Automatically appends timestamp/number to filename, OR
  - Overwrites with confirmation dialog
- No crash or data corruption
- User is informed of the action taken

**Actual Behavior:**
Cannot test with custom names due to missing settings screen. However, testing with same video twice (which defaults to same filename) shows silent overwrite behavior with no warning or confirmation.

**Status:** ☐ Pass | ☑ Fail  
**Severity:** ☐ Crash | ☑ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Related to G4.3 - Silent overwrite issue. Users are not warned when generating from the same video twice, resulting in loss of previous graph without user awareness.

---

### Test Case G4.5: View Graph Visualization
**Steps to Reproduce:**
1. Complete a successful generation
2. Tap "View Graph" button on success screen
3. Observe visualization

**Expected Behavior:**
- Graph visualization screen opens
- Visual representation of states and transitions is shown
- Back button returns to success screen
- No crash or loading errors
- Visualization is meaningful and legible

**Actual Behavior:**
"View Graph" button is not available on the success screen. Only "Done" button is present.

**Status:** ☐ Pass | ☑ Fail | ☐ N/A  
**Severity:** ☐ Crash | ☐ Functional | ☑ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** Feature missing from UI - Cannot view graph visualization immediately after generation. The SuccessContent in code includes this feature, but simplified version is being used instead.

---

### Test Case G4.6: Generate Another Exercise
**Steps to Reproduce:**
1. Complete a successful generation
2. Tap "Generate Another" button
3. Observe behavior

**Expected Behavior:**
- Returns to idle screen (Choose Video screen)
- Previous graph data is cleared
- Can start new generation immediately
- No crash or state issues

**Actual Behavior:**
No "Generate Another" button available. Only "Done" button present. Tapping "Done" likely returns to previous screen (needs confirmation of exact behavior).

**Status:** ☐ Pass | ☑ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☑ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** UX Issue - No quick workflow option. Users must navigate back manually to generate another graph. "Generate Another" button would improve workflow efficiency.

---

   