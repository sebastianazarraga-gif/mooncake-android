/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class DigitalButton extends VirtualControllerElement {

    public interface DigitalButtonListener {
        void onClick();
        void onLongClick();
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private boolean lastReportedState = false;
    private boolean isDispatchedToBackground = false;

    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Runnable autoRepeatReleaseRunnable = new Runnable() {
        @Override
        public void run() {
            if (_isOrderingMode) return;
            applyBindingState(false);
            if ((_isToggled || isPressed()) && _isRepeatMode) {
                virtualController.getHandler().postDelayed(autoRepeatRunnable, _repeatInterval);
            }
        }
    };

    private final Runnable autoRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (_isOrderingMode) return;
            if ((_isToggled || isPressed()) && _isRepeatMode) {
                applyBindingState(true);
                virtualController.getHandler().removeCallbacks(autoRepeatReleaseRunnable);
                virtualController.getHandler().postDelayed(autoRepeatReleaseRunnable, _activationTime);
            }
        }
    };

    // --- AUTOMATION ENGINE ---
    private enum AutoState { IDLE, PRESSING, GAPPING, LOOP_WAIT }
    private AutoState currentAutoState = AutoState.IDLE;
    private int currentActionIdx = 0;
    private List<BindingAction> actionSequence = null;

    private final Runnable automationRunner = new Runnable() {
        @Override
        public void run() {
            if (!_isOrderingMode || currentAutoState == AutoState.IDLE) {
                return;
            }

            // Lazy init sequence
            if (actionSequence == null || actionSequence.isEmpty()) {
                actionSequence = getAllActions();
                if (actionSequence.isEmpty()) {
                    stopAutomation();
                    return;
                }
            }

            long nextDelay = 10;
            boolean buttonActive = isPressed() || _isToggled;

            switch (currentAutoState) {
                case PRESSING:
                    // Stop if "Apply on hold" is ON and user let go
                    if (_applyOnHold && !buttonActive) {
                        stopAutomation();
                        return;
                    }

                    // Press current key
                    if (currentActionIdx >= actionSequence.size()) currentActionIdx = 0;
                    executeAction(actionSequence.get(currentActionIdx), true);
                    
                    // Transition to Release/Gap phase
                    currentAutoState = AutoState.GAPPING;
                    nextDelay = (_applyOnHold && _isHoldRepeat) ? _holdActivationTime : _orderActivationTime;
                    break;

                case GAPPING:
                    // Release the key we just pressed
                    // Fix: If "Apply on hold" is enabled and this is the last action in the sequence,
                    // do not release it until the button is actually released by the user.
                    boolean isLastAction = (currentActionIdx >= actionSequence.size() - 1);
                    boolean shouldReleaseNow = !isLastAction || !_applyOnHold || (_applyOnHold && _isHoldRepeat);

                    if (shouldReleaseNow && currentActionIdx < actionSequence.size()) {
                        executeAction(actionSequence.get(currentActionIdx), false);
                    }
                    
                    currentActionIdx++;
                    
                    // Check if we finished the full list
                    if (currentActionIdx >= actionSequence.size()) {
                        currentActionIdx = 0; // Reset index for next cycle
                        
                        // Decide if we loop
                        boolean shouldLoop = (_isRepeatMode && !_applyOnHold) || (_applyOnHold && _isHoldRepeat && buttonActive);
                        
                        if (shouldLoop) {
                            currentAutoState = AutoState.LOOP_WAIT;
                            nextDelay = (_applyOnHold && _isHoldRepeat) ? _holdRepeatDelay : _orderGapTime;
                        } else {
                            // Run once completed
                            currentAutoState = AutoState.IDLE;
                            return;
                        }
                    } else {
                        // Not the end, wait gap and move to next PRESSING state
                        currentAutoState = AutoState.PRESSING;
                        nextDelay = (_applyOnHold && _isHoldRepeat) ? _holdRepeatDelay : _orderGapTime;
                    }
                    break;

                case LOOP_WAIT:
                    // Loop delay finished, start pressing the first item again
                    currentAutoState = AutoState.PRESSING;
                    nextDelay = 10; // Trigger immediately
                    break;
            }

            // Final safety check: if we are supposed to be IDLE, stop everything and exit
            if (!_isOrderingMode || currentAutoState == AutoState.IDLE) {
                stopAutomation();
                return;
            }

            // Schedule next step
            virtualController.getHandler().postDelayed(this, Math.max(1, nextDelay));
        }
    };

    private static class BindingAction {
        enum Type { KBD, GP, MS }
        Type type;
        Object value;
        BindingAction(Type t, Object v) { type = t; value = v; }
    }

    private List<BindingAction> getAllActions() {
        List<BindingAction> actions = new ArrayList<>();
        if (isCombinedMapping() || (!isKeyboardMapping() && !isMouseMapping())) {
            if (_gamepadFlag != 0) actions.add(new BindingAction(BindingAction.Type.GP, _gamepadFlag));
            for (Integer i : _extraGamepadFlags) if (i != 0) actions.add(new BindingAction(BindingAction.Type.GP, i));
        }
        if (isCombinedMapping() || isKeyboardMapping()) {
            if (_mappedKeyCode != 0) actions.add(new BindingAction(BindingAction.Type.KBD, _mappedKeyCode));
            for (Short s : _extraKeyCodes) if (s != 0) actions.add(new BindingAction(BindingAction.Type.KBD, s));
        }
        if (isCombinedMapping() || isMouseMapping()) {
            if (_mouseAction != MouseAction.None) actions.add(new BindingAction(BindingAction.Type.MS, _mouseAction));
            for (MouseAction m : _extraMouseActions) if (m != MouseAction.None) actions.add(new BindingAction(BindingAction.Type.MS, m));
        }
        return actions;
    }

    private void executeAction(BindingAction action, boolean active) {
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;
        switch (action.type) {
            case KBD:
                byte mods = (byte) (_isShiftMode ? 0x02 : 0);
                ch.reportVirtualKeyboardInput((Short)action.value, active, mods);
                break;
            case GP: applyGpFlagInternal(ch, (Integer)action.value, active); break;
            case MS: applyMouseAction(ch, (MouseAction)action.value, active); break;
        }
    }

    private void applyGpFlagInternal(ControllerHandler ch, int flag, boolean active) {
        VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
        if (active) inputContext.inputMap |= flag;
        else inputContext.inputMap &= ~flag;
        virtualController.sendControllerInputContext();
    }

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private int layer;
    private DigitalButton movingButton = null;

    private final Runnable mouseRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPressed() || _isToggled) {
                ControllerHandler ch = virtualController.getControllerHandler();
                if (ch != null) {
                    float totalSense = _sensitivity * _globalSensitivity;
                    if (_mouseAction == MouseAction.MoveUp) ch.reportVirtualMouseMove((short)0, (short)(-20 * totalSense));
                    else if (_mouseAction == MouseAction.MoveDown) ch.reportVirtualMouseMove((short)0, (short)(20 * totalSense));
                    else if (_mouseAction == MouseAction.MoveLeft) ch.reportVirtualMouseMove((short)(-20 * totalSense), (short)0);
                    else if (_mouseAction == MouseAction.MoveRight) ch.reportVirtualMouseMove((short)(20 * totalSense), (short)0);
                    else if (_mouseAction == MouseAction.ScrollUp) ch.reportVirtualMouseScroll((byte)(2 * totalSense));
                    else if (_mouseAction == MouseAction.ScrollDown) ch.reportVirtualMouseScroll((byte)(-2 * totalSense));
                }
                virtualController.getHandler().postDelayed(this, 33);
            }
        }
    };

    public DigitalButton(VirtualController controller, int elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) { listeners.add(listener); }
    public void setText(String text) { this.text = text; invalidate(); }
    public void setIcon(int id) { this.icon = id; invalidate(); }

    @Override
    protected void onElementDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        int color = (isPressed() || _isToggled) ? getPressedColor() : getDefaultColor();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color & 0x40FFFFFF);
        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;
        if (shape == Shape.Square) canvas.drawRect(rect, paint);
        else if (shape == Shape.SquareRounded) { float radius = getPercent(getCorrectWidth(), 15); canvas.drawRoundRect(rect, radius, radius, paint); }
        else canvas.drawOval(rect, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        if (shape == Shape.Square) canvas.drawRect(rect, paint);
        else if (shape == Shape.SquareRounded) { float radius = getPercent(getCorrectWidth(), 15); canvas.drawRoundRect(rect, radius, radius, paint); }
        else canvas.drawOval(rect, paint);
        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            String display = _customText != null ? _customText : text;
            canvas.drawText(display, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }
    }

    private void onClickCallback() {
        if (_isOrderingMode) {
            if (currentAutoState == AutoState.IDLE) {
                currentAutoState = AutoState.PRESSING;
                currentActionIdx = 0;
                actionSequence = null;
                virtualController.getHandler().removeCallbacks(automationRunner);
                virtualController.getHandler().post(automationRunner);
            }
            if (!_isToggleMode) { invalidate(); return; }
        }

        if (_isToggleMode) {
            _isToggled = !_isToggled;
            if (!_isToggled) stopAutomation();
            else {
                if (_isOrderingMode) {
                    if (currentAutoState == AutoState.IDLE) {
                        currentAutoState = AutoState.PRESSING;
                        currentActionIdx = 0;
                        actionSequence = null;
                        virtualController.getHandler().removeCallbacks(automationRunner);
                        virtualController.getHandler().post(automationRunner);
                    }
                } else if (_isRepeatMode) {
                    virtualController.getHandler().removeCallbacks(autoRepeatRunnable);
                    virtualController.getHandler().post(autoRepeatRunnable);
                } else applyBindingState(true);
                
                if (isMouseMapping() || isCombinedMapping()) {
                    if (_mouseAction == MouseAction.MoveUp || _mouseAction == MouseAction.MoveDown ||
                        _mouseAction == MouseAction.MoveLeft || _mouseAction == MouseAction.MoveRight ||
                        _mouseAction == MouseAction.ScrollUp || _mouseAction == MouseAction.ScrollDown) {
                        virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
                        virtualController.getHandler().post(mouseRepeatRunnable);
                    }
                }
            }
            invalidate();
            return;
        }

        if (_isRepeatMode) {
            virtualController.getHandler().removeCallbacks(autoRepeatRunnable);
            virtualController.getHandler().post(autoRepeatRunnable);
        } else applyBindingState(true);

        if (isMouseMapping() || isCombinedMapping()) {
            if (_mouseAction == MouseAction.MoveUp || _mouseAction == MouseAction.MoveDown ||
                _mouseAction == MouseAction.MoveLeft || _mouseAction == MouseAction.MoveRight ||
                _mouseAction == MouseAction.ScrollUp || _mouseAction == MouseAction.ScrollDown) {
                virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
                virtualController.getHandler().post(mouseRepeatRunnable);
            }
        }
        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void applyBindingState(boolean active) {
        if (lastReportedState == active) return;
        if (_isOrderingMode && active) return;
        lastReportedState = active;
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;
        if (isKeyboardMapping() || isCombinedMapping()) {
            byte mods = (byte) (_isShiftMode ? 0x02 : 0);
            if (active) {
                if (_mappedKeyCode != 0) ch.reportVirtualKeyboardInput(_mappedKeyCode, true, mods);
                for (Short code : _extraKeyCodes) if (code != 0) ch.reportVirtualKeyboardInput(code, true, mods);
            } else {
                for (int i = _extraKeyCodes.size() - 1; i >= 0; i--) { short code = _extraKeyCodes.get(i); if (code != 0) ch.reportVirtualKeyboardInput(code, false, mods); }
                if (_mappedKeyCode != 0) ch.reportVirtualKeyboardInput(_mappedKeyCode, false, mods);
            }
        }
        if (isMouseMapping() || isCombinedMapping()) {
            if (active) {
                applyMouseAction(ch, _mouseAction, true);
                for (MouseAction action : _extraMouseActions) applyMouseAction(ch, action, true);
            } else {
                for (int i = _extraMouseActions.size() - 1; i >= 0; i--) applyMouseAction(ch, _extraMouseActions.get(i), false);
                applyMouseAction(ch, _mouseAction, false);
            }
        }
        if (!isKeyboardMapping() && !isMouseMapping() || isCombinedMapping()) {
            if (_gamepadFlag != 0) applyGpFlagInternal(ch, _gamepadFlag, active);
            for (Integer flag : _extraGamepadFlags) if (flag != 0) applyGpFlagInternal(ch, flag, active);
            if (_gamepadFlag == 0 && _extraGamepadFlags.isEmpty()) onDefaultGamepadAction(active);
            if (active) { for (DigitalButtonListener listener : listeners) listener.onClick(); }
            else { for (DigitalButtonListener listener : listeners) listener.onRelease(); }
        }
    }

    private void applyMouseAction(ControllerHandler ch, MouseAction action, boolean active) {
        if (action == MouseAction.LeftClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_LEFT, active);
        else if (action == MouseAction.RightClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_RIGHT, active);
        else if (action == MouseAction.MiddleClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_MIDDLE, active);
    }

    protected void onDefaultGamepadAction(boolean active) {}
    private void onLongClickCallback() { if (!isKeyboardMapping()) { for (DigitalButtonListener listener : listeners) listener.onLongClick(); } }

    private void onReleaseCallback() {
        _DBG("released");
        
        if (_isOrderingMode && !_isToggled) {
            if (_applyOnHold) {
                stopAutomation();
            }
            return; 
        }

        if (_isToggleMode) return;

        applyBindingState(false);
        stopAutomation();

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    private void stopAutomation() {
        virtualController.getHandler().removeCallbacks(autoRepeatRunnable);
        virtualController.getHandler().removeCallbacks(autoRepeatReleaseRunnable);
        virtualController.getHandler().removeCallbacks(automationRunner);
        virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
        currentAutoState = AutoState.IDLE;
        currentActionIdx = 0;
        actionSequence = null;
        lastReportedState = false;
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch != null) {
            VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
            List<BindingAction> allActions = getAllActions();
            byte mods = (byte) (_isShiftMode ? 0x02 : 0);
            for (BindingAction action : allActions) {
                if (action.type == BindingAction.Type.KBD) ch.reportVirtualKeyboardInput((Short)action.value, false, mods);
                else if (action.type == BindingAction.Type.GP) inputContext.inputMap &= ~(Integer)action.value;
                else if (action.type == BindingAction.Type.MS) applyMouseAction(ch, (MouseAction)action.value, false);
            }
            if (_mappedKeyCode != 0) ch.reportVirtualKeyboardInput(_mappedKeyCode, false, mods);
            for (Short code : _extraKeyCodes) if (code != 0) ch.reportVirtualKeyboardInput(code, false, mods);
            applyMouseAction(ch, _mouseAction, false);
            for (MouseAction action : _extraMouseActions) applyMouseAction(ch, action, false);
            inputContext.inputMap &= ~_gamepadFlag;
            for (Integer flag : _extraGamepadFlags) if (flag != 0) inputContext.inputMap &= ~flag;
            if (_gamepadFlag == 0 && _extraGamepadFlags.isEmpty()) onDefaultGamepadAction(false);
            virtualController.sendControllerInputContext();
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if (_isTouchThrough) {
            boolean hasConflict = _avoidTouchThroughConflict && virtualController.isBackgroundTouched();

            if (!hasConflict) {
                if (!isDispatchedToBackground) {
                    // Send a fake DOWN event if we are starting mid-stream (after conflict cleared)
                    if (action == MotionEvent.ACTION_MOVE) {
                        // First send a CANCEL to the background to ensure it's clean
                        MotionEvent cancelEvent = MotionEvent.obtain(event);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        dispatchToBackground(cancelEvent);
                        cancelEvent.recycle();

                        // Then send the new DOWN
                        MotionEvent downEvent = MotionEvent.obtain(event);
                        downEvent.setAction(MotionEvent.ACTION_DOWN);
                        dispatchToBackground(downEvent);
                        downEvent.recycle();
                    }
                    isDispatchedToBackground = true;
                }
                dispatchToBackground(event);
            } else if (isDispatchedToBackground) {
                // If conflict suddenly appears while we were dispatching, send an UP to stop it
                MotionEvent upEvent = MotionEvent.obtain(event);
                upEvent.setAction(MotionEvent.ACTION_UP);
                dispatchToBackground(upEvent);
                upEvent.recycle();
                isDispatchedToBackground = false;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP || event.getPointerId(actionIndex) == activePointerId) {
                    isDispatchedToBackground = false;
                }
            }
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (activePointerId == -1) {
                    float pointerX = event.getX(actionIndex);
                    float pointerY = event.getY(actionIndex);
                    // Check if this pointer is within our bounds
                    if (pointerX >= 0 && pointerX <= getWidth() && pointerY >= 0 && pointerY <= getHeight()) {
                        activePointerId = event.getPointerId(actionIndex);
                        updateGlobalSensitivity();
                        movingButton = null;
                        setPressed(true);
                        onClickCallback();
                        checkMovementForAllButtons(getX() + pointerX, getY() + pointerY);
                        invalidate();
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (activePointerId != -1) {
                    int pointerIdx = event.findPointerIndex(activePointerId);
                    if (pointerIdx != -1) {
                        checkMovementForAllButtons(getX() + event.getX(pointerIdx), getY() + event.getY(pointerIdx));
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (activePointerId != -1 && event.getPointerId(actionIndex) == activePointerId) {
                    activePointerId = -1;
                    setPressed(false);
                    onReleaseCallback();
                    checkMovementForAllButtons(getX() + event.getX(actionIndex), getY() + event.getY(actionIndex));
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activePointerId != -1) {
                    activePointerId = -1;
                    setPressed(false);
                    onReleaseCallback();
                    checkMovementForAllButtons(getX() + event.getX(actionIndex), getY() + event.getY(actionIndex));
                    invalidate();
                }
                break;
        }
        return true;
    }

    private void dispatchToBackground(MotionEvent event) {
        try {
            android.view.ViewParent parent = getParent();
            if (parent instanceof android.view.ViewGroup) {
                android.view.View bg = ((android.view.ViewGroup) parent).findViewById(com.mooncake.R.id.backgroundTouchView);
                if (bg != null) {
                    MotionEvent clone = MotionEvent.obtain(event);
                    clone.offsetLocation(getX(), getY());
                    virtualController.setDispatchingTouchThrough(true);
                    bg.dispatchTouchEvent(clone);
                    virtualController.setDispatchingTouchThrough(false);
                    clone.recycle();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public JSONObject getConfiguration() throws JSONException { JSONObject config = super.getConfiguration(); config.put("TEXT", text); return config; }
    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException { super.loadConfiguration(configuration); if (configuration.has("TEXT")) this.text = configuration.getString("TEXT"); }

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, DigitalButton movingButton) {
        if (movingButton.layer != this.layer) return false;
        boolean wasPressed = isPressed();
        if ((this.movingButton == null || movingButton == this.movingButton) && this.inRange(x, y)) {
            if (this.isPressed() != movingButton.isPressed()) this.setPressed(movingButton.isPressed());
        } else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }
        if (wasPressed != isPressed()) {
            if (isPressed()) { this.movingButton = movingButton; onClickCallback(); }
            else { this.movingButton = null; onReleaseCallback(); }
            invalidate();
            return true;
        }
        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        if (_isExclusiveTouch && isPressed()) return;
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof DigitalButton) {
                ((DigitalButton) element).checkMovement(x, y, this);
            }
        }
    }
}
