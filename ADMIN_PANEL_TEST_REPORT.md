# Admin Panel Testing Report
**Testing Date:** _____________  
**Tester Name:** _____________  
**Device Model:** _____________  
**Android Version:** _____________  
**App Version:** _____________

---

## Admin Access

### Test Case A0.1: Admin Login
**Steps to Reproduce:**
1. Open the app
2. Navigate to login screen
3. Enter email: `admin` or `admin@poseapp.com`
4. Enter password: `admin`
5. Tap "Login"

**Expected Behavior:**
- Login succeeds
- User is redirected to Admin Home screen
- Admin-specific navigation appears

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

## Task A1: Graph Management

### Test Case A1.1: Admin Home Screen - Verify Options
**Steps to Reproduce:**
1. Login as admin
2. View Admin Home screen
3. Check available options/cards

**Expected Behavior:**
Admin Home displays three main options:
- ☐ "Generate Graph" card with "Choose Video" button
- ☐ "Start Monitoring" card with "Open Camera" button
- ☐ "View Graphs" card with "View Graphs" button
- ☐ Admin home title and subtitle displayed
- ☐ Logout button available

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.2: Generate Graph Navigation
**Steps to Reproduce:**
1. From Admin Home
2. Tap "Choose Video" button on "Generate Graph" card
3. Observe navigation

**Expected Behavior:**
- Navigates to Graph Generation Activity
- Graph generation screen opens
- Can select video and generate graph
- No crash

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.3: Start Monitoring Navigation
**Steps to Reproduce:**
1. From Admin Home
2. Tap "Open Camera" button on "Start Monitoring" card
3. Observe navigation

**Expected Behavior:**
- Navigates to Main Activity (monitoring/camera screen)
- Camera permission requested if needed
- Can select exercise and start monitoring
- No crash

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.4: View Graphs List - Empty State
**Steps to Reproduce:**
1. From Admin Home (with no generated graphs)
2. Tap "View Graphs" button
3. Observe graph list screen

**Expected Behavior:**
- Navigates to Graph List screen
- Shows empty state with:
  - 📂 icon
  - "No graphs yet" message
  - "Generate your first graph to get started" message
- Back button available
- No crash

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.5: View Graphs List - With Data
**Steps to Reproduce:**
1. Generate at least one graph (if not already done)
2. From Admin Home, tap "View Graphs"
3. Check graph list display

**Expected Behavior:**
- Graph list screen displays
- Shows both asset graphs (📦 icon, "Bundled" label) and generated graphs (📊 icon, "Generated" label)
- Each graph card shows:
  - Icon (📦 or 📊)
  - Graph name (without .json extension)
  - Type label ("Bundled" or "Generated")
  - Chevron arrow (›)
- Graphs are clickable
- Back button works

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.6: View Individual Graph
**Steps to Reproduce:**
1. From Graph List screen
2. Tap on any graph entry
3. Observe graph viewer

**Expected Behavior:**
- Opens Graph View screen
- Shows graph visualization
- Top bar displays:
  - Back button (arrow)
  - Graph name
- Visualization is readable and interactive
- No loading errors
- Back button returns to graph list

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.7: View Asset Graph
**Steps to Reproduce:**
1. From Graph List
2. Select a bundled graph (📦 icon)
3. Check visualization loads

**Expected Behavior:**
- Asset graph loads from app's assets folder
- Visualization displays correctly
- No file not found errors
- Can navigate back

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.8: View Generated Graph
**Steps to Reproduce:**
1. From Graph List
2. Select a generated graph (📊 icon)
3. Check visualization loads

**Expected Behavior:**
- Generated graph loads from filesDir/graphs/
- Visualization displays correctly
- Shows same visualization as asset graphs
- No file not found errors
- Can navigate back

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.9: Failed Graph Load Handling
**Steps to Reproduce:**
1. If possible, corrupt a graph JSON file or delete it
2. Try to view the corrupted/missing graph
3. Observe error handling

**Expected Behavior:**
- Shows error state with:
  - ⚠️ icon
  - "Failed to load graph" message
  - File path displayed
- Does not crash
- Can navigate back
- Error is clear and actionable

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail | ☐ N/A (could not test)  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.10: Rename Graph Feature
**Steps to Reproduce:**
1. Navigate to Graph List
2. Long-press on a generated graph
3. Look for rename option
4. If available, rename the graph
5. Verify name updates

**Expected Behavior:**
- Long-press shows context menu or dialog
- Rename option is available
- Can enter new name
- Graph name updates in list
- File is renamed successfully
- No data loss

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail | ☐ N/A (feature not implemented in admin panel)  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.11: Delete Graph Feature
**Steps to Reproduce:**
1. Navigate to Graph List
2. Long-press or swipe on a generated graph
3. Look for delete option
4. If available, attempt to delete
5. Check for confirmation dialog

**Expected Behavior:**
- Delete option is available for generated graphs
- Confirmation dialog appears: "Are you sure?"
- If confirmed, graph is removed from list
- File is deleted from storage
- Cannot delete bundled/asset graphs
- List updates immediately

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail | ☐ N/A (feature not implemented)  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A1.12: Back Navigation Flow
**Steps to Reproduce:**
1. Admin Home → View Graphs → View specific graph
2. Press back button from graph view
3. Should return to graph list
4. Press back again
5. Should return to Admin Home

**Expected Behavior:**
- Back button navigation works correctly at each level
- No unexpected exits or crashes
- State is preserved (e.g., scroll position in list)
- Smooth transitions

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

## Task A2: Admin Stats

### Test Case A2.1: Admin Stats Navigation
**Steps to Reproduce:**
1. Login as admin
2. Check bottom navigation bar
3. Tap on "Stats" or analytics icon
4. Observe navigation

**Expected Behavior:**
- Bottom nav shows admin-specific items including Stats
- Tapping Stats navigates to Admin Stats Dashboard
- Screen loads without crash
- No blank screen or infinite loading

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.2: Stats Dashboard - Loading State
**Steps to Reproduce:**
1. Navigate to Admin Stats screen
2. Observe initial loading state

**Expected Behavior:**
- Shows loading indicator (CircularProgressIndicator)
- Loading indicator is centered and visible
- Screen doesn't freeze
- Transitions to data display after loading

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.3: Stats Dashboard - Data Display
**Steps to Reproduce:**
1. Navigate to Admin Stats screen (with existing user data)
2. Wait for data to load
3. Check all displayed statistics

**Expected Behavior:**
Dashboard displays:
- ☐ "Namaste 🙏" greeting header
- ☐ "Dashboard" title with back button
- ☐ Total Users card (with count)
- ☐ New Members card (with count and badge)
- ☐ Live Users card (with count and "Live now" badge)
- ☐ Community Growth card with graph
- ☐ Week/Month toggle on growth card
- ☐ All numbers are accurate
- ☐ No "0" for all values (if data exists)

**Actual Behavior:**
_____________________________________________

**Total Users:** _______  
**New Members:** _______  
**Live Users:** _______  
**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.4: Stats Card Icons and Styling
**Steps to Reproduce:**
1. Navigate to Admin Stats screen
2. Check visual design of stat cards

**Expected Behavior:**
Each stat card displays:
- ☐ Icon with colored background (Group, PersonAdd, FlashOn)
- ☐ Label text (small, gray)
- ☐ Large value number (bold, dark)
- ☐ Badge/chip (if applicable)
- ☐ Proper spacing and alignment
- ☐ Consistent styling across all cards

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.5: Community Growth Graph
**Steps to Reproduce:**
1. Navigate to Admin Stats screen
2. Scroll to Community Growth card
3. Observe graph visualization

**Expected Behavior:**
- Graph renders smoothly with curved line
- Shows 7 data points (Monday-Sunday)
- Day labels shown below graph (Mon, Tue, Wed, etc.)
- Graph has gradient fill below line
- Line color matches theme (teal)
- Data points are visible
- No rendering glitches

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.6: Week/Month Toggle
**Steps to Reproduce:**
1. Navigate to Admin Stats screen
2. On Community Growth card, tap "Week" toggle
3. Observe graph
4. Tap "Month" toggle
5. Observe graph changes

**Expected Behavior:**
- Week is selected by default
- Tapping Week/Month changes toggle state visually
- Selected option has dark background and white text
- Unselected option has transparent background and gray text
- Graph data updates when toggle changes (if different data)
- No crash or lag

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.7: Stats with Zero Data (Empty State)
**Steps to Reproduce:**
1. Test with fresh database or no user sessions
2. Navigate to Admin Stats screen
3. Check how zero data is displayed

**Expected Behavior:**
- Stats cards show "0" for counts
- No crash or error messages
- Graph shows flat line or empty state
- Layout remains intact
- User understands there's no data (not an error)
- Dashboard is still navigable

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.8: Stats Accuracy Verification
**Steps to Reproduce:**
1. Create/login with 2-3 test user accounts
2. Complete some workout sessions with each
3. Navigate to Admin Stats
4. Manually verify counts

**Expected Behavior:**
- Total Users count matches actual registered users
- Session counts are accurate
- Accuracy percentages are calculated correctly
- Data is up-to-date (real-time or near real-time)
- No inflated or deflated numbers

**Actual Behavior:**
_____________________________________________

**Expected Total Users:** _______  
**Displayed Total Users:** _______  
**Match:** ☐ Yes | ☐ No  
**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.9: Stats Refresh on Data Change
**Steps to Reproduce:**
1. View Admin Stats screen (note current values)
2. Complete a new workout session or create new user
3. Return to Admin Stats screen or pull to refresh

**Expected Behavior:**
- Stats update to reflect new data
- Refresh mechanism exists (auto or manual)
- New session/user is counted
- Graph updates if within current time period
- No need to restart app to see updates

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.10: Stats Screen Scrolling
**Steps to Reproduce:**
1. Navigate to Admin Stats screen
2. Scroll up and down through all content
3. Check scroll behavior

**Expected Behavior:**
- Screen scrolls smoothly
- All cards are visible when scrolling
- No content cutoff
- Top bar remains visible or sticky
- Scroll performance is good (no lag)
- Can reach all content

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A2.11: Back Navigation from Stats
**Steps to Reproduce:**
1. Navigate to Admin Stats screen
2. Press back button
3. Observe navigation

**Expected Behavior:**
- Returns to previous screen (likely Admin Home or exercises)
- No crash
- Navigation is immediate
- Can navigate back to stats again

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

## Admin vs User View

### Test Case A3.1: Admin-Specific Navigation
**Steps to Reproduce:**
1. Login as admin
2. Check bottom navigation bar
3. Note available tabs

**Expected Behavior:**
Admin sees different navigation than regular users:
- Home
- Exercises  
- Stats (Admin Stats) - admin only
- Profile/Graphs
May have different layout or additional options

**Actual Behavior:**
_____________________________________________

**Admin Nav Items:** _____________  
**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A3.2: Admin Home vs User Home
**Steps to Reproduce:**
1. Login as admin - note Home screen
2. Logout
3. Login as regular user
4. Note Home screen differences

**Expected Behavior:**
- Admin Home shows graph management cards
- User Home shows workout/exercise options
- Different titles and descriptions
- Different available actions
- Appropriate permissions enforced

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

## Admin Logout

### Test Case A4.1: Logout Function
**Steps to Reproduce:**
1. Login as admin
2. From Admin Home, tap logout button (top right)
3. Observe behavior

**Expected Behavior:**
- User is logged out successfully
- Redirected to Login screen
- Session is cleared
- Cannot navigate back to admin screens
- Google sign-in session also cleared (shows account chooser on next login)

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

### Test Case A4.2: Re-login After Logout
**Steps to Reproduce:**
1. Logout from admin account
2. Login again with admin credentials
3. Verify admin access is restored

**Expected Behavior:**
- Can login again successfully
- Admin privileges are restored
- Admin Home and Stats are accessible
- Previously generated graphs are still visible
- No data loss

**Actual Behavior:**
_____________________________________________

**Status:** ☐ Pass | ☐ Fail  
**Severity:** ☐ Crash | ☐ Functional | ☐ UI | ☐ Performance  
**Screenshot/Recording:** _____________  
**Notes:** _____________

---

## Additional Observations

### Edge Cases Discovered
_____________________________________________
_____________________________________________
_____________________________________________

### Performance Notes
_____________________________________________
_____________________________________________
_____________________________________________

### UI/UX Issues
_____________________________________________
_____________________________________________
_____________________________________________

### Suggestions for Improvement
_____________________________________________
_____________________________________________
_____________________________________________

---

## Summary

**Total Test Cases Executed:** _______ / 26  
**Passed:** _______  
**Failed:** _______  
**Crashes Found:** _______  
**Functional Issues:** _______  
**UI Issues:** _______  
**Performance Issues:** _______

### Critical Issues (Must Fix)
1. _____________________________________________
2. _____________________________________________
3. _____________________________________________

### Major Issues (Should Fix)
1. _____________________________________________
2. _____________________________________________
3. _____________________________________________

### Minor Issues (Nice to Fix)
1. _____________________________________________
2. _____________________________________________
3. _____________________________________________

---

**Tester Signature:** _____________  
**Date Completed:** _____________
