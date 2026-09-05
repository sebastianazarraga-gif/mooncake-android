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
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class DigitalPad extends VirtualControllerElement {
    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;
    private final Paint paint = new Paint();
    private boolean uDown, dDown, lDown, rDown;

    private final Runnable mouseRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            ControllerHandler ch = virtualController.getControllerHandler();
            if (ch == null) return;
            
            if (uDown) handleDirMouseInternal(true, _mappedDirUpMouseAction, ch);
            if (dDown) handleDirMouseInternal(true, _mappedDirDownMouseAction, ch);
            if (lDown) handleDirMouseInternal(true, _mappedDirLeftMouseAction, ch);
            if (rDown) handleDirMouseInternal(true, _mappedDirRightMouseAction, ch);
            
            if (uDown || dDown || lDown || rDown) {
                virtualController.getHandler().postDelayed(this, 33);
            }
        }
    };

    private void handleDirMouseInternal(boolean down, MouseAction action, ControllerHandler ch) {
        if (!down || action == MouseAction.None) return;
        float totalSense = _sensitivity * _globalSensitivity;

        if (action == MouseAction.MoveUp) ch.reportVirtualMouseMove((short)0, (short)(-20 * totalSense));
        else if (action == MouseAction.MoveDown) ch.reportVirtualMouseMove((short)0, (short)(20 * totalSense));
        else if (action == MouseAction.MoveLeft) ch.reportVirtualMouseMove((short)(-20 * totalSense), (short)0);
        else if (action == MouseAction.MoveRight) ch.reportVirtualMouseMove((short)(20 * totalSense), (short)0);
        else if (action == MouseAction.ScrollUp) ch.reportVirtualMouseScroll((byte)(2 * totalSense));
        else if (action == MouseAction.ScrollDown) ch.reportVirtualMouseScroll((byte)(-2 * totalSense));
    }

    public DigitalPad(VirtualController controller, Context context) {
        this(controller, context, EID_DPAD);
    }

    public DigitalPad(VirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);
        // Default directional mapping flags
        _mappedDirUpGamepadFlag = ControllerPacket.UP_FLAG;
        _mappedDirDownGamepadFlag = ControllerPacket.DOWN_FLAG;
        _mappedDirLeftGamepadFlag = ControllerPacket.LEFT_FLAG;
        _mappedDirRightGamepadFlag = ControllerPacket.RIGHT_FLAG;
    }

    private void updateDirectionalMapping(int currentDirection) {
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;

        boolean isU = (currentDirection & DIGITAL_PAD_DIRECTION_UP) != 0;
        boolean isD = (currentDirection & DIGITAL_PAD_DIRECTION_DOWN) != 0;
        boolean isL = (currentDirection & DIGITAL_PAD_DIRECTION_LEFT) != 0;
        boolean isR = (currentDirection & DIGITAL_PAD_DIRECTION_RIGHT) != 0;

        // Always handle keyboard directional binds
        if (isU != uDown && _mappedKeyUp != 0) ch.reportVirtualKeyboardInput(_mappedKeyUp, isU);
        if (isD != dDown && _mappedKeyDown != 0) ch.reportVirtualKeyboardInput(_mappedKeyDown, isD);
        if (isL != lDown && _mappedKeyLeft != 0) ch.reportVirtualKeyboardInput(_mappedKeyLeft, isL);
        if (isR != rDown && _mappedKeyRight != 0) ch.reportVirtualKeyboardInput(_mappedKeyRight, isR);
        
        // Always handle mouse directional binds
        handleDirMouse(isU, uDown, _mappedDirUpMouseAction, ch);
        handleDirMouse(isD, dDown, _mappedDirDownMouseAction, ch);
        handleDirMouse(isL, lDown, _mappedDirLeftMouseAction, ch);
        handleDirMouse(isR, rDown, _mappedDirRightMouseAction, ch);

        // Gamepad mapping
        if (!isKeyboardMapping() && !isMouseMapping() || isCombinedMapping()) {
            VirtualController.ControllerInputContext ctx = virtualController.getControllerInputContext();
            
            updateGpFlag(isU, uDown, _mappedDirUpGamepadFlag, ctx);
            updateGpFlag(isD, dDown, _mappedDirDownGamepadFlag, ctx);
            updateGpFlag(isL, lDown, _mappedDirLeftGamepadFlag, ctx);
            updateGpFlag(isR, rDown, _mappedDirRightGamepadFlag, ctx);
            
            if (isU != uDown || isD != dDown || isL != lDown || isR != rDown) {
                virtualController.sendControllerInputContext();
            }
        }

        uDown = isU; dDown = isD; lDown = isL; rDown = isR;

        if (uDown || dDown || lDown || rDown) {
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
        if (down) ctx.inputMap |= flag;
        else ctx.inputMap &= ~flag;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        // Draw individual direction pads
        drawPadRect(canvas, DIGITAL_PAD_DIRECTION_LEFT, paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33), getPercent(getWidth(), 33), getPercent(getHeight(), 66));
        drawPadRect(canvas, DIGITAL_PAD_DIRECTION_UP, getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getWidth(), 66), getPercent(getHeight(), 33));
        drawPadRect(canvas, DIGITAL_PAD_DIRECTION_RIGHT, getPercent(getWidth(), 66), getPercent(getHeight(), 33), getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 66));
        drawPadRect(canvas, DIGITAL_PAD_DIRECTION_DOWN, getPercent(getWidth(), 33), getPercent(getHeight(), 66), getPercent(getWidth(), 66), getHeight() - (paint.getStrokeWidth()+DPAD_MARGIN));

        // Draw separating lines
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getDefaultColor());
        canvas.drawLine(paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33), getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN, paint);
        canvas.drawLine(getPercent(getWidth(), 66), paint.getStrokeWidth()+DPAD_MARGIN, getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 33), paint);
        canvas.drawLine(getWidth()-paint.getStrokeWidth(), getPercent(getHeight(), 66), getPercent(getWidth(), 66), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN), paint);
        canvas.drawLine(getPercent(getWidth(), 33), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN), paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 66), paint);
    }

    private void drawPadRect(Canvas canvas, int dir, float l, float t, float r, float b) {
        int color = (direction & dir) > 0 ? getPressedColor() : getDefaultColor();
        paint.setColor(color & 0x40FFFFFF);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(l, t, r, b, paint);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(l, t, r, b, paint);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (activePointerId == -1) {
                // Exclusive Touch check: block if another element is already exclusive and pressed
                if (virtualController.isAnyElementExclusivePressed()) {
                    return true;
                }

                float pointerX = event.getX(actionIndex);
                float pointerY = event.getY(actionIndex);
                if (pointerX >= 0 && pointerX <= getWidth() && pointerY >= 0 && pointerY <= getHeight()) {
                    activePointerId = event.getPointerId(actionIndex);
                    updateGlobalSensitivity();
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            if (activePointerId != -1 && event.getPointerId(actionIndex) == activePointerId) {
                activePointerId = -1;
                direction = 0;
                updateDirectionalMapping(direction);
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (activePointerId != -1) {
                activePointerId = -1;
                direction = 0;
                updateDirectionalMapping(direction);
                invalidate();
                return true;
            }
        }

        if (activePointerId != -1) {
            int pointerIdx = event.findPointerIndex(activePointerId);
            if (pointerIdx != -1) {
                direction = 0;
                float x = event.getX(pointerIdx);
                float y = event.getY(pointerIdx);

                if (x < getPercent(getWidth(), 33)) direction |= DIGITAL_PAD_DIRECTION_LEFT;
                if (x > getPercent(getWidth(), 66)) direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                if (y < getPercent(getHeight(), 33)) direction |= DIGITAL_PAD_DIRECTION_UP;
                if (y > getPercent(getHeight(), 66)) direction |= DIGITAL_PAD_DIRECTION_DOWN;

                updateDirectionalMapping(direction);
                invalidate();
            }
        }
        return true;
    }

    public interface DigitalPadListener { void onDirectionChange(int direction); }
    public void addDigitalPadListener(DigitalPadListener listener) { listeners.add(listener); }
}
