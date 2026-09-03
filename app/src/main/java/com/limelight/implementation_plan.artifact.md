# Implementation Plan - Fix PcView Landscape Crash

The `PcView` activity crashes when the device is in landscape orientation because the landscape layout (`res/layout-land/activity_pc_view.xml`) is missing the `grid_toggle` button, which the Java code attempts to use without a null check.

## Proposed Changes

### [PcView](file:///C:/Users/Admin/StudioProjects/mooncake/app/src/main/java/com/limelight/PcView.java)

#### [MODIFY] [PcView.java](file:///C:/Users/Admin/StudioProjects/mooncake/app/src/main/java/com/limelight/PcView.java)
- Add a null check for `gridToggle` in `initializeViews()` before setting the click listener.

## Verification Plan

### Automated Tests
- N/A (UI crash fix)

### Manual Verification
1. Launch the app in portrait mode.
2. Rotate the device to landscape.
3. Verify the app does not crash.
