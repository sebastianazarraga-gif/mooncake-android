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
    private float currentX, currentY;

    private final Runnable dynamicMouseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPressed() || !_isDynamicMode || !isMouseMapping() && !isCombinedMapping()) {
                return;
            }
            
            ControllerHandler ch = virtualController.getControllerHandler();
            if (ch == null) return;

            float totalSense = _sensitivity * _globalSensitivity;
            // Continuous velocity-based movement for Dynamic Mouse Mode
            short dx = (short) (currentX * 20 * totalSense);
            short dy = (short) (-currentY * 20 * totalSense);
            
            if (dx != 0 || dy != 0) {
                ch.reportVirtualMouseMove(dx, dy);
            }
            
            virtualController.getHandler().postDelayed(this, 16);
        }
    };

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
        currentX = x;
        currentY = y;
        
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;

        if (_isDynamicMode) {
            // Dynamic Mode Logic
            if (isMouseMapping() || isCombinedMapping()) {
                // For mouse, we use the runnable for continuous velocity-based movement
                virtualController.getHandler().removeCallbacks(dynamicMouseRunnable);
                if (isPressed() && (x != 0 || y != 0)) {
                    virtualController.getHandler().post(dynamicMouseRunnable);
                }
            }
            
            if (!isKeyboardMapping() && !isMouseMapping() || isCombinedMapping()) {
                // For controller, we set the axes directly in the context
                VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
                if (_dynamicStickType == 0) { // Left Stick
                    inputContext.leftStickX = (short) (x * 0x7FFE);
                    inputContext.leftStickY = (short) (y * 0x7FFE);
                } else { // Right Stick
                    inputContext.rightStickX = (short) (x * 0x7FFE);
                    inputContext.rightStickY = (short) (y * 0x7FFE);
                }
                virtualController.sendControllerInputContext();
            }
            
            // In Dynamic Mode, we skip the static directional mappings (keys/flags)
            // but we must release any currently held static bindings if we just switched or released
            if (!isPressed()) {
                releaseStaticBindings(ch);
            }
            return;
        }

        // Standard mapping: Physically UP stick (pos y in logic) results in newU=true
        boolean newU = y > 0.3f;
        boolean newD = y < -0.3f;
        boolean newL = x < -0.3f;
        boolean newR = x > 0.3f;

        if (isMouseMapping() || isCombinedMapping()) {
            if (_mouseAction == MouseAction.None) {
                // Continuous axis-based movement (non-dynamic version sends deltas only on change)
                ch.reportVirtualMouseMove((short) (x * 20 * _sensitivity * _globalSensitivity), (short) (-y * 20 * _sensitivity * _globalSensitivity));
            } else {
                // Whole-stick action (like scroll)
                handleDirMouseInternal(_mouseAction, ch);
            }
        }
        
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

    private void releaseStaticBindings(ControllerHandler ch) {
        if (uDown && _mappedKeyUp != 0) ch.reportVirtualKeyboardInput(_mappedKeyUp, false);
        if (dDown && _mappedKeyDown != 0) ch.reportVirtualKeyboardInput(_mappedKeyDown, false);
        if (lDown && _mappedKeyLeft != 0) ch.reportVirtualKeyboardInput(_mappedKeyLeft, false);
        if (rDown && _mappedKeyRight != 0) ch.reportVirtualKeyboardInput(_mappedKeyRight, false);
        
        VirtualController.ControllerInputContext ctx = virtualController.getControllerInputContext();
        if (uDown && _mappedDirUpGamepadFlag != 0) ctx.inputMap &= ~_mappedDirUpGamepadFlag;
        if (dDown && _mappedDirDownGamepadFlag != 0) ctx.inputMap &= ~_mappedDirDownGamepadFlag;
        if (lDown && _mappedDirLeftGamepadFlag != 0) ctx.inputMap &= ~_mappedDirLeftGamepadFlag;
        if (rDown && _mappedDirRightGamepadFlag != 0) ctx.inputMap &= ~_mappedDirRightGamepadFlag;
        
        handleDirMouse(false, uDown, _mappedDirUpMouseAction, ch);
        handleDirMouse(false, dDown, _mappedDirDownMouseAction, ch);
        handleDirMouse(false, lDown, _mappedDirLeftMouseAction, ch);
        handleDirMouse(false, rDown, _mappedDirRightMouseAction, ch);
        
        uDown = dDown = lDown = rDown = false;
        virtualController.sendControllerInputContext();
        virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
        virtualController.getHandler().removeCallbacks(dynamicMouseRunnable);
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
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            if (activePointerId != -1 && event.getPointerId(actionIndex) == activePointerId) {
                activePointerId = -1;
                setPressed(false);
                stick_state = STICK_STATE.NO_MOVEMENT;
                notifyOnMovement(0, 0);
                updateDirectionalKeys(0, 0);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (activePointerId != -1) {
                activePointerId = -1;
                setPressed(false);
                stick_state = STICK_STATE.NO_MOVEMENT;
                notifyOnMovement(0, 0);
                updateDirectionalKeys(0, 0);
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

                float nX = 0;
                float nY = 0;
                if (stick_state == STICK_STATE.MOVED_ACTIVE) {
                    nX = corX / (radius_complete - radius_analog_stick);
                    nY = -corY / (radius_complete - radius_analog_stick);
                }

                notifyOnMovement(nX, nY);
                updateDirectionalKeys(nX, nY);
            }
        }
        invalidate();
        return true;
    }

    private void notifyOnMovement(float x, float y) { for (AnalogStickListener l : listeners) l.onMovement(x, y); }
    public interface AnalogStickListener { void onMovement(float x, float y); void onClick(); void onDoubleClick(); void onRevoke(); }
    public void addAnalogStickListener(AnalogStickListener listener) { listeners.add(listener); }
}
