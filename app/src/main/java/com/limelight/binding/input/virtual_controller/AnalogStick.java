/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class AnalogStick extends VirtualControllerElement {
    public final static long timeoutDoubleClick = 350;
    public final static long timeoutDeadzone = 150;

    private enum STICK_STATE { NO_MOVEMENT, MOVED_IN_DEAD_ZONE, MOVED_ACTIVE }
    private enum CLICK_STATE { SINGLE, DOUBLE }

    private float radius_complete = 0;
    private float radius_analog_stick = 0;
    private float radius_dead_zone = 0;
    private float movement_radius = 0;
    private double movement_angle = 0;
    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private final Paint paint = new Paint();
    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;
    private List<AnalogStickListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;
    private boolean uDown, dDown, lDown, rDown;

    private float velNX = 0, velNY = 0;
    private float lastNX = 0, lastNY = 0;
    private long lastTouchTime = 0;

    private final Runnable mouseRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            ControllerHandler ch = virtualController.getControllerHandler();
            if (ch == null) return;
            
            if (uDown) handleDirMouseInternal(_mappedDirUpMouseAction, ch);
            if (dDown) handleDirMouseInternal(_mappedDirDownMouseAction, ch);
            if (lDown) handleDirMouseInternal(_mappedDirLeftMouseAction, ch);
            if (rDown) handleDirMouseInternal(_mappedDirRightMouseAction, ch);
            
            if (uDown || dDown || lDown || rDown) {
                virtualController.getHandler().postDelayed(this, 33);
            }
        }
    };

    private long lastUpdateTime = 0;
    private final Runnable dynamicUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            if (lastUpdateTime == 0) lastUpdateTime = currentTime;
            float deltaTime = (currentTime - lastUpdateTime) / 1000f;
            lastUpdateTime = currentTime;
            
            // Clamp deltaTime to avoid physics explosion after a long stall
            if (deltaTime > 0.1f) deltaTime = 0.1f;
            if (deltaTime <= 0) deltaTime = 0.01f;

            boolean continueLoop = false;

            if (isDynamicModeActive() && !isPressed() && _isDynamicReturn && (movement_radius > 0 || Math.abs(velNX) > 0.001f || Math.abs(velNY) > 0.001f)) {
                // Critically damped (or slightly overdamped) spring physics
                // Reduced omega for a slower, more deliberate return
                float omega = 3.0f + _dynamicReturnSpeed * 7.0f;
                float damping = 2.5f * omega; // Overdamped to eliminate "jiggles"

                // Current normalized position
                float range = radius_complete - radius_analog_stick;
                float curY = (float) (Math.sin(movement_angle) * movement_radius);
                float curX = (float) (Math.cos(movement_angle) * movement_radius);
                float nX = (range > 0) ? curX / range : 0;
                float nY = (range > 0) ? -curY / range : 0;

                // Apply physics step
                float accX = -omega * omega * nX - damping * velNX;
                float accY = -omega * omega * nY - damping * velNY;

                if (!Float.isNaN(accX) && !Float.isInfinite(accX)) velNX += accX * deltaTime;
                if (!Float.isNaN(accY) && !Float.isInfinite(accY)) velNY += accY * deltaTime;
                if (!Float.isNaN(velNX)) nX += velNX * deltaTime;
                if (!Float.isNaN(velNY)) nY += velNY * deltaTime;

                if (Float.isNaN(nX)) nX = 0;
                if (Float.isNaN(nY)) nY = 0;

                // Update visual state
                float newCorX = nX * range;
                float newCorY = -nY * range;
                movement_radius = (float) Math.sqrt(newCorX * newCorX + newCorY * newCorY);
                movement_angle = Math.atan2(newCorY, newCorX);

                if (movement_radius > range) {
                    movement_radius = range;
                }

                // Snap to center when sufficiently close and slow
                if (movement_radius < 0.001f && Math.abs(velNX) < 0.1f && Math.abs(velNY) < 0.1f) {
                    movement_radius = 0;
                    velNX = 0;
                    velNY = 0;
                    nX = 0;
                    nY = 0;
                }

                position_stick_x = getWidth() / 2.0f + (float) (Math.cos(movement_angle) * movement_radius);
                position_stick_y = getHeight() / 2.0f + (float) (Math.sin(movement_angle) * movement_radius);

                stick_state = (movement_radius > radius_dead_zone) ? STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;
                if (movement_radius == 0) stick_state = STICK_STATE.NO_MOVEMENT;

                // Output handling
                // When in Dynamic Mode, the deadzone is removed for output
                float outX, outY;
                if (isDynamicModeActive()) {
                    outX = nX;
                    outY = nY;
                } else {
                    outX = (stick_state == STICK_STATE.MOVED_ACTIVE) ? nX : 0;
                    outY = (stick_state == STICK_STATE.MOVED_ACTIVE) ? nY : 0;
                }

                notifyOnMovement(outX, outY);
                
                if (isDynamicMode() && (isMouseMapping() || isCombinedMapping())) {
                    ControllerHandler ch = virtualController.getControllerHandler();
                    if (ch != null && (outX != 0 || outY != 0)) {
                        ch.reportVirtualMouseMove((short) (outX * 15 * _sensitivity * _globalSensitivity), 
                                                (short) (-outY * 15 * _sensitivity * _globalSensitivity));
                    }
                }

                updateDirectionalKeys(outX, outY);
                invalidate();
                
                if (movement_radius > 0 || Math.abs(velNX) > 0.001f || Math.abs(velNY) > 0.001f) continueLoop = true;
            } else if (isDynamicModeActive() && isPressed() && (isMouseMapping() || isCombinedMapping())) {
                // Continuous mouse reporting while held
                float curY = (float) (Math.sin(movement_angle) * movement_radius);
                float curX = (float) (Math.cos(movement_angle) * movement_radius);
                float range = radius_complete - radius_analog_stick;
                float nX = (range > 0) ? curX / range : 0;
                float nY = (range > 0) ? -curY / range : 0;
                
                ControllerHandler ch = virtualController.getControllerHandler();
                if (ch != null && (nX != 0 || nY != 0)) {
                    ch.reportVirtualMouseMove((short) (nX * 15 * _sensitivity * _globalSensitivity), 
                                            (short) (-nY * 15 * _sensitivity * _globalSensitivity));
                }
                
                notifyOnMovement(nX, nY);
                continueLoop = true;
            }

            if (continueLoop) {
                virtualController.getHandler().postDelayed(this, 10);
            } else {
                lastUpdateTime = 0;
            }
        }
    };

    private void handleDirMouseInternal(MouseAction action, ControllerHandler ch) {
        float totalSense = _sensitivity * _globalSensitivity;

        if (action == MouseAction.MoveUp) ch.reportVirtualMouseMove((short)0, (short)(-20 * totalSense));
        else if (action == MouseAction.MoveDown) ch.reportVirtualMouseMove((short)0, (short)(20 * totalSense));
        else if (action == MouseAction.MoveLeft) ch.reportVirtualMouseMove((short)(-20 * totalSense), (short)0);
        else if (action == MouseAction.MoveRight) ch.reportVirtualMouseMove((short)(20 * totalSense), (short)0);
        else if (action == MouseAction.ScrollUp) ch.reportVirtualMouseScroll((byte)(2 * totalSense));
        else if (action == MouseAction.ScrollDown) ch.reportVirtualMouseScroll((byte)(-2 * totalSense));
    }

    public AnalogStick(VirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);
        // Do not set directional flags by default for analog sticks
        // as they should only report axis data unless specifically mapped.
        _mappedDirUpGamepadFlag = 0;
        _mappedDirDownGamepadFlag = 0;
        _mappedDirLeftGamepadFlag = 0;
        _mappedDirRightGamepadFlag = 0;
    }

    private void updateDirectionalKeys(float x, float y) {
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;

        if (isDynamicModeActive()) return;
        if (!hasAnyDirectionalBinding()) return;

        // Standard mapping: Physically UP stick (pos y in logic) results in newU=true
        boolean newU = y > 0.3f;
        boolean newD = y < -0.3f;
        boolean newL = x < -0.3f;
        boolean newR = x > 0.3f;

        // Always handle directional mouse binds regardless of main stick mode
        handleDirMouse(newU, uDown, _mappedDirUpMouseAction, ch);
        handleDirMouse(newD, dDown, _mappedDirDownMouseAction, ch);
        handleDirMouse(newL, lDown, _mappedDirLeftMouseAction, ch);
        handleDirMouse(newR, rDown, _mappedDirRightMouseAction, ch);

        // Always handle keyboard directional binds
        if (newU != uDown && _mappedKeyUp != 0) ch.reportVirtualKeyboardInput(_mappedKeyUp, newU);
        if (newD != dDown && _mappedKeyDown != 0) ch.reportVirtualKeyboardInput(_mappedKeyDown, newD);
        if (newL != lDown && _mappedKeyLeft != 0) ch.reportVirtualKeyboardInput(_mappedKeyLeft, newL);
        if (newR != rDown && _mappedKeyRight != 0) ch.reportVirtualKeyboardInput(_mappedKeyRight, newR);
        
        // Gamepad mapping
        if (!isKeyboardMapping() && !isMouseMapping() || isCombinedMapping()) {
             VirtualController.ControllerInputContext ctx = virtualController.getControllerInputContext();
             updateGpFlag(newU, uDown, _mappedDirUpGamepadFlag, ctx);
             updateGpFlag(newD, dDown, _mappedDirDownGamepadFlag, ctx);
             updateGpFlag(newL, lDown, _mappedDirLeftGamepadFlag, ctx);
             updateGpFlag(newR, rDown, _mappedDirRightGamepadFlag, ctx);

             if (newU != uDown || newD != dDown || newL != lDown || newR != rDown) {
                 virtualController.sendControllerInputContext();
             }
        }
        uDown = newU; dDown = newD; lDown = newL; rDown = newR;

        // Start repeating logic if we have mouse binds active
        if ((uDown || dDown || lDown || rDown)) {
            boolean hasMouseBinds = isMouseBind(_mappedDirUpMouseAction) || isMouseBind(_mappedDirDownMouseAction) ||
                                   isMouseBind(_mappedDirLeftMouseAction) || isMouseBind(_mappedDirRightMouseAction);

            if (hasMouseBinds) {
                virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
                virtualController.getHandler().post(mouseRepeatRunnable);
            } else {
                virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
            }
        } else {
            virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
        }
    }

    private boolean isMouseBind(MouseAction action) {
        return action == MouseAction.MoveUp || action == MouseAction.MoveDown || 
               action == MouseAction.MoveLeft || action == MouseAction.MoveRight ||
               action == MouseAction.ScrollUp || action == MouseAction.ScrollDown;
    }

    private void handleDirMouse(boolean down, boolean wasDown, MouseAction action, ControllerHandler ch) {
        if (down == wasDown || action == MouseAction.None) return;
        if (action == MouseAction.LeftClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_LEFT, down);
        else if (action == MouseAction.RightClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_RIGHT, down);
        else if (action == MouseAction.MiddleClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_MIDDLE, down);
    }

    private void updateGpFlag(boolean down, boolean wasDown, int flag, VirtualController.ControllerInputContext ctx) {
        if (down == wasDown || flag == 0) return;
        if (down) ctx.inputMap |= flag; else ctx.inputMap &= ~flag;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        radius_complete = getPercent(getCorrectWidth() / 2, 100) - 2 * getDefaultStrokeWidth();
        radius_dead_zone = getPercent(getCorrectWidth() / 2, 30);
        radius_analog_stick = getPercent(getCorrectWidth() / 2, 20);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        float cx = getWidth() / 2, cy = getHeight() / 2;
        int color = (!isPressed() || click_state == CLICK_STATE.SINGLE) ? getDefaultColor() : getPressedColor();
        
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color & 0x40FFFFFF);
        if (shape == Shape.Circle) canvas.drawCircle(cx, cy, radius_complete, paint);
        else canvas.drawRect(cx - radius_complete, cy - radius_complete, cx + radius_complete, cy + radius_complete, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        if (shape == Shape.Circle) canvas.drawCircle(cx, cy, radius_complete, paint);
        else canvas.drawRect(cx - radius_complete, cy - radius_complete, cx + radius_complete, cy + radius_complete, paint);

        paint.setColor(getDefaultColor());
        canvas.drawCircle(cx, cy, radius_dead_zone, paint);

        if (stick_state == STICK_STATE.NO_MOVEMENT) drawStick(canvas, cx, cy);
        else drawStick(canvas, position_stick_x, position_stick_y);
    }

    private void drawStick(Canvas canvas, float x, float y) {
        int color = (stick_state == STICK_STATE.MOVED_ACTIVE) ? getPressedColor() : getDefaultColor();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color & 0x80FFFFFF);
        canvas.drawCircle(x, y, radius_analog_stick, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        canvas.drawCircle(x, y, radius_analog_stick, paint);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (activePointerId == -1) {
                float pointerX = event.getX(actionIndex);
                float pointerY = event.getY(actionIndex);
                float relX = -(getWidth() / 2 - pointerX);
                float relY = -(getHeight() / 2 - pointerY);
                float radius = (float) Math.sqrt(relX * relX + relY * relY);

                if (radius <= radius_complete) {
                    activePointerId = event.getPointerId(actionIndex);
                    updateGlobalSensitivity();
                    stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;
                    setPressed(true);
                    timeLastClick = event.getEventTime();

                    velNX = 0; velNY = 0;
                    lastTouchTime = event.getEventTime();
                    lastNX = 0; lastNY = 0;

                    virtualController.getHandler().removeCallbacks(dynamicUpdateRunnable);
                    if (isDynamicModeActive() && (isMouseMapping() || isCombinedMapping())) {
                        virtualController.getHandler().post(dynamicUpdateRunnable);
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            if (activePointerId != -1 && event.getPointerId(actionIndex) == activePointerId) {
                activePointerId = -1;
                setPressed(false);
                if (!_isDynamicReturn) {
                    stick_state = STICK_STATE.NO_MOVEMENT;
                    movement_radius = 0;
                    notifyOnMovement(0, 0);
                    updateDirectionalKeys(0, 0);
                } else {
                    virtualController.getHandler().removeCallbacks(dynamicUpdateRunnable);
                    if (isDynamicModeActive()) {
                        velNX *= 0.5f;
                        velNY *= 0.5f;
                        virtualController.getHandler().post(dynamicUpdateRunnable);
                    } else {
                        stick_state = STICK_STATE.NO_MOVEMENT;
                        movement_radius = 0;
                        notifyOnMovement(0, 0);
                        updateDirectionalKeys(0, 0);
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (activePointerId != -1) {
                activePointerId = -1;
                setPressed(false);
                if (!_isDynamicReturn) {
                    stick_state = STICK_STATE.NO_MOVEMENT;
                    movement_radius = 0;
                    notifyOnMovement(0, 0);
                    updateDirectionalKeys(0, 0);
                } else {
                    virtualController.getHandler().removeCallbacks(dynamicUpdateRunnable);
                    if (isDynamicModeActive()) {
                        velNX *= 0.5f;
                        velNY *= 0.5f;
                        virtualController.getHandler().post(dynamicUpdateRunnable);
                    } else {
                        stick_state = STICK_STATE.NO_MOVEMENT;
                        movement_radius = 0;
                        notifyOnMovement(0, 0);
                        updateDirectionalKeys(0, 0);
                    }
                }
            }
        }

        if (isPressed() && activePointerId != -1) {
            int pointerIdx = event.findPointerIndex(activePointerId);
            if (pointerIdx != -1) {
                float relX = -(getWidth() / 2 - event.getX(pointerIdx));
                float relY = -(getHeight() / 2 - event.getY(pointerIdx));
                movement_radius = (float) Math.sqrt(relX * relX + relY * relY);
                movement_angle = Math.atan2(relY, relX);

                if (movement_radius > (radius_complete - radius_analog_stick)) {
                    movement_radius = radius_complete - radius_analog_stick;
                }

                float corY = (float) (Math.sin(movement_angle) * movement_radius);
                float corX = (float) (Math.cos(movement_angle) * movement_radius);
                position_stick_x = getWidth() / 2 + corX;
                position_stick_y = getHeight() / 2 + corY;

                stick_state = (movement_radius > radius_dead_zone) ? STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;

                float range = radius_complete - radius_analog_stick;
                float nX = (range > 0) ? corX / range : 0;
                float nY = (range > 0) ? -corY / range : 0;

                long time = event.getEventTime();
                if (lastTouchTime > 0 && time > lastTouchTime) {
                    float dt = (time - lastTouchTime) / 1000f;
                    if (dt > 0.001f && dt < 0.1f) {
                        float vX = (nX - lastNX) / dt;
                        float vY = (nY - lastNY) / dt;
                        if (!Float.isNaN(vX) && !Float.isInfinite(vX)) velNX = vX;
                        if (!Float.isNaN(vY) && !Float.isInfinite(vY)) velNY = vY;
                    }
                }
                lastNX = nX;
                lastNY = nY;
                lastTouchTime = time;

                // When in Dynamic Mode, the deadzone is removed for output
                float outX, outY;
                if (isDynamicModeActive()) {
                    outX = nX;
                    outY = nY;
                } else {
                    outX = (stick_state == STICK_STATE.MOVED_ACTIVE) ? nX : 0;
                    outY = (stick_state == STICK_STATE.MOVED_ACTIVE) ? nY : 0;
                }

                notifyOnMovement(outX, outY);
                updateDirectionalKeys(outX, outY);
            }
        }
        invalidate();
        return true;
    }

    private void notifyOnMovement(float x, float y) {
        if (isDynamicModeActive()) {
            if (!(isMouseMapping() || isCombinedMapping())) {
                VirtualController.ControllerInputContext ctx = virtualController.getControllerInputContext();
                
                float totalSense = _sensitivity * _globalSensitivity;
                float outX = x * totalSense;
                float outY = y * totalSense;
                
                // Clamp to -1.0 to 1.0 range
                if (outX > 1.0f) outX = 1.0f; else if (outX < -1.0f) outX = -1.0f;
                if (outY > 1.0f) outY = 1.0f; else if (outY < -1.0f) outY = -1.0f;

                if (_dynamicStickType == 0) { // Left Stick
                    ctx.leftStickX = (short) (outX * 0x7FFE);
                    ctx.leftStickY = (short) (outY * 0x7FFE);
                } else { // Right Stick
                    ctx.rightStickX = (short) (outX * 0x7FFE);
                    ctx.rightStickY = (short) (outY * 0x7FFE);
                }
                virtualController.sendControllerInputContext();
            }
        }

        if (!isDynamicMode() && !hasAnyDirectionalBinding()) return;

        for (AnalogStickListener l : listeners) l.onMovement(x, y);
    }

    @Override
    protected void onMappingModeChanged() {
        if (!isDynamicAllowed()) {
            virtualController.getHandler().removeCallbacks(dynamicUpdateRunnable);
        }
    }
    public interface AnalogStickListener { void onMovement(float x, float y); void onClick(); void onDoubleClick(); void onRevoke(); }
    public void addAnalogStickListener(AnalogStickListener listener) { listeners.add(listener); }
}
