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
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;

        // Standard mapping: Physically UP stick (pos y in logic) results in newU=true
        boolean newU = y > 0.3f;
        boolean newD = y < -0.3f;
        boolean newL = x < -0.3f;
        boolean newR = x > 0.3f;

        if (isMouseMapping() || isCombinedMapping()) {
            if (_mouseAction == MouseAction.None) {
                // Continuous axis-based movement
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
        float relX = -(getWidth() / 2 - event.getX());
        float relY = -(getHeight() / 2 - event.getY());
        movement_radius = (float) Math.sqrt(relX * relX + relY * relY);
        movement_angle = Math.atan2(relY, relX);

        if (movement_radius > radius_complete && !isPressed()) return false;
        if (movement_radius > (radius_complete - radius_analog_stick)) movement_radius = radius_complete - radius_analog_stick;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                updateGlobalSensitivity();
                stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;
                setPressed(true);
                timeLastClick = event.getEventTime();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                setPressed(false);
                stick_state = STICK_STATE.NO_MOVEMENT;
                notifyOnMovement(0, 0);
                updateDirectionalKeys(0, 0);
                break;
        }

        if (isPressed()) {
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
        invalidate();
        return true;
    }

    private void notifyOnMovement(float x, float y) { for (AnalogStickListener l : listeners) l.onMovement(x, y); }
    public interface AnalogStickListener { void onMovement(float x, float y); void onClick(); void onDoubleClick(); void onRevoke(); }
    public void addAnalogStickListener(AnalogStickListener listener) { listeners.add(listener); }
}
