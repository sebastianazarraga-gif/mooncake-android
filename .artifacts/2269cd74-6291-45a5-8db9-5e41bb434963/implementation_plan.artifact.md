# Definitive Rotation and Hitbox Fix for Mooncake

This plan addresses the rotation bug where virtual control elements have incorrect hitboxes (ghost hitboxes) and visual clipping. The fix involves unifying hit testing using the View's transformation matrix and ensuring the visual representation matches the interactive area.

## Proposed Changes

### [Virtual Controller Core]

#### [MODIFY] [VirtualControllerElement.java](file:///C:/Users/Admin/StudioProjects/mooncake-android-VERISON-1.1.1/app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualControllerElement.java)
- Add `containsPoint(float x, float y)` to perform hit testing using the inverse transformation matrix.
- Add `getParentCoordinates(float localX, float localY)` to correctly convert local touch points to parent space.
- Remove redundant `canvas.rotate(_rotation, ...)` from `onDraw()` to fix double rotation.
- Add `onSizeChanged()` to ensure the rotation pivot is always centered.
- Update `onTouchEvent()` to use `getParentCoordinates()` when identifying elements at a touch point in the editor.

#### [MODIFY] [VirtualController.java](file:///C:/Users/Admin/StudioProjects/mooncake-android-VERISON-1.1.1/app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualController.java)
- Set `clipChildren(false)` and `clipToPadding(false)` on the `frame_layout` to prevent visual clipping of rotated elements.
- Update `getElementsAt()` to use `element.containsPoint(x, y)` for accurate rotated hit testing.

#### [MODIFY] [DigitalButton.java](file:///C:/Users/Admin/StudioProjects/mooncake-android-VERISON-1.1.1/app/src/main/java/com/limelight/binding/input/virtual_controller/DigitalButton.java)
- Update `inRange()` to use the shared `containsPoint()` implementation.

## Verification Plan

### Automated Tests
- No specific automated tests exist for UI interaction, but we will verify the logic via manual testing on the device.

### Manual Verification
1.  **Mapping Editor**:
    - Rotate a button by 45°.
    - Verify it's not clipped by its bounding box.
    - Verify it can be selected by touching any visible part of it.
    - Verify areas outside the visible rotated button (ghost hitbox) are NOT clickable.
2.  **In-Stream**:
    - Start a stream with a rotated button.
    - Verify the entire visible button responds to touch.
    - Verify corners of the original unrotated rectangle do not respond.
3.  **Rotation Range**:
    - Test rotation at various angles (90°, 180°, 270°, etc.) and verify visual and hitbox alignment.
