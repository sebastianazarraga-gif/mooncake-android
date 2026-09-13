# Implementation Plan - Joystick Input and Dynamic Mode Fixes

This plan addresses several bugs related to joystick input, focusing on correctly separating static and dynamic mapping modes, fixing Dynamic Return behavior, and implementing continuous Dynamic Mouse movement.

## User Review Required

> [!IMPORTANT]
> The fix assumes that "Dynamic Mode" is the intended way to enable continuous analog stick/mouse output. In "Static Mode" (Dynamic Mode OFF), only directional bindings will be active.

## Proposed Changes

### [Input Architecture]

#### [MODIFY] [LeftAnalogStick.java](file:///C:/Users/Admin/StudioProjects/mooncake-android-VERISON-1.1.1/app/src/main/java/com/limelight/binding/input/virtual_controller/LeftAnalogStick.java)
- Remove the analog stick output (`leftStickX`, `leftStickY`) from the movement listener. This prevents "leakage" where static directional binds also trigger analog stick movement. Analog output will now be handled exclusively by the base `AnalogStick` class when `isDynamicMode()` is ON.

#### [MODIFY] [RightAnalogStick.java](file:///C:/Users/Admin/StudioProjects/mooncake-android-VERISON-1.1.1/app/src/main/java/com/limelight/binding/input/virtual_controller/RightAnalogStick.java)
- Similarly, remove the analog stick output (`rightStickX`, `rightStickY`) from the movement listener.

#### [MODIFY] [AnalogStick.java](file:///C:/Users/Admin/StudioProjects/mooncake-android-VERISON-1.1.1/app/src/main/java/com/limelight/binding/input/virtual_controller/AnalogStick.java)
- **Dynamic Update Loop:** Rename `dynamicReturnRunnable` to `dynamicUpdateRunnable` and enhance it to handle both Dynamic Return and continuous Dynamic Mouse movement.
- **Smoothness:** Use time deltas (`System.currentTimeMillis()`) for frame-consistent and smooth return interpolation.
- **Dynamic Mouse:** Implement a repeating update in the runnable that continues to report mouse movement as long as the stick is held away from the center while in Dynamic Mode.
- **Touch Lifecycle:** Correct the trigger logic for Dynamic Return. It will now be cancelled on `ACTION_DOWN` and started only on `ACTION_UP` or `ACTION_CANCEL`.
- **Bug Fixes:**
    - Fix the "fighting" between the return runnable and active touch events.
    - Ensure `notifyOnMovement` is called correctly during the return phase to update the runtime output state.
    - Ensure exact `(0,0)` rest position after return.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :app:assembleDebug`.

### Manual Verification Matrix
- **Test 1 & 2 (Static Controller):** Verify that directional binds do NOT trigger Left/Right Stick analog movement when Dynamic Mode is OFF.
- **Test 3 (Add Bind):** Verify adding a bind doesn't accidentally enable Dynamic Mode.
- **Test 4 & 5 (Dynamic Controller):** Verify Left/Right Stick Dynamic Mode still works correctly.
- **Test 6 (Dynamic Mouse):** Verify continuous mouse movement when Dynamic Mode is ON and Mouse/Combined is selected.
- **Test 7 & 8 (Dynamic Return):** Verify smooth return to center upon release, reaching exact `(0,0)`.
- **Test 9 (Dynamic Mouse Return):** Verify return works for Dynamic Mouse too.
- **Test 11 (All None):** Verify `None` bindings result in no input.
