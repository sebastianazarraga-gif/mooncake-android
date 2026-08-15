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

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private boolean lastReportedState = false;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

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

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, DigitalButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof DigitalButton) {
                ((DigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public DigitalButton(VirtualController controller, int elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        int color = (isPressed() || _isToggled) ? getPressedColor() : getDefaultColor();
        
        // Draw fill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color & 0x40FFFFFF); // 25% opacity fill
        
        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        if (shape == Shape.Square) {
            canvas.drawRect(rect, paint);
        } else if (shape == Shape.SquareRounded) {
            float radius = getPercent(getCorrectWidth(), 15);
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            canvas.drawOval(rect, paint);
        }

        // Draw stroke
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        
        if (shape == Shape.Square) {
            canvas.drawRect(rect, paint);
        } else if (shape == Shape.SquareRounded) {
            float radius = getPercent(getCorrectWidth(), 15);
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            canvas.drawOval(rect, paint);
        }

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
        _DBG("clicked");
        
        if (_isToggleMode) {
            _isToggled = !_isToggled;
            applyBindingState(_isToggled);
            
            if (_isToggled && (isMouseMapping() || isCombinedMapping())) {
                if (_mouseAction == MouseAction.MoveUp || _mouseAction == MouseAction.MoveDown ||
                    _mouseAction == MouseAction.MoveLeft || _mouseAction == MouseAction.MoveRight ||
                    _mouseAction == MouseAction.ScrollUp || _mouseAction == MouseAction.ScrollDown) {
                    virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
                    virtualController.getHandler().post(mouseRepeatRunnable);
                }
            } else if (!_isToggled) {
                virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);
            }
            
            invalidate();
            return;
        }

        applyBindingState(true);

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
        lastReportedState = active;
        
        ControllerHandler ch = virtualController.getControllerHandler();
        if (ch == null) return;

        if (isKeyboardMapping() || isCombinedMapping()) {
            if (active) {
                if (_mappedKeyCode != 0) ch.reportVirtualKeyboardInput(_mappedKeyCode, true);
                for (Short code : _extraKeyCodes) {
                    if (code != 0) ch.reportVirtualKeyboardInput(code, true);
                }
            } else {
                // Release in reverse order
                for (int i = _extraKeyCodes.size() - 1; i >= 0; i--) {
                    short code = _extraKeyCodes.get(i);
                    if (code != 0) ch.reportVirtualKeyboardInput(code, false);
                }
                if (_mappedKeyCode != 0) ch.reportVirtualKeyboardInput(_mappedKeyCode, false);
            }
        }
        
        if (isMouseMapping() || isCombinedMapping()) {
            if (active) {
                applyMouseAction(ch, _mouseAction, true);
                for (MouseAction action : _extraMouseActions) {
                    applyMouseAction(ch, action, true);
                }
            } else {
                for (int i = _extraMouseActions.size() - 1; i >= 0; i--) {
                    applyMouseAction(ch, _extraMouseActions.get(i), false);
                }
                applyMouseAction(ch, _mouseAction, false);
            }
        }
        
        if (!isKeyboardMapping() && !isMouseMapping() || isCombinedMapping()) {
            // Main gamepad flag
            if (_gamepadFlag != 0) {
                applyGpFlag(ch, _gamepadFlag, active);
            }
            // Extra gamepad flags
            for (Integer flag : _extraGamepadFlags) {
                if (flag != 0) applyGpFlag(ch, flag, active);
            }
            
            if (_gamepadFlag == 0 && _extraGamepadFlags.isEmpty()) {
                onDefaultGamepadAction(active);
            }
            
            // notify listeners
            if (active) {
                for (DigitalButtonListener listener : listeners) {
                    listener.onClick();
                }
            } else {
                for (DigitalButtonListener listener : listeners) {
                    listener.onRelease();
                }
            }
        }
    }

    private void applyGpFlag(ControllerHandler ch, int flag, boolean active) {
        VirtualController.ControllerInputContext inputContext = virtualController.getControllerInputContext();
        if (active) inputContext.inputMap |= flag;
        else inputContext.inputMap &= ~flag;
        virtualController.sendControllerInputContext();
    }

    private void applyMouseAction(ControllerHandler ch, MouseAction action, boolean active) {
        if (action == MouseAction.LeftClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_LEFT, active);
        else if (action == MouseAction.RightClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_RIGHT, active);
        else if (action == MouseAction.MiddleClick) ch.reportVirtualMouseButton(MouseButtonPacket.BUTTON_MIDDLE, active);
    }

    protected void onDefaultGamepadAction(boolean active) {
        // To be overridden by specialized buttons like triggers
    }

    private void onLongClickCallback() {
        _DBG("long click");

        if (!isKeyboardMapping()) {
            // notify listeners
            for (DigitalButtonListener listener : listeners) {
                listener.onLongClick();
            }
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        
        if (_isToggleMode) return;

        applyBindingState(false);

        virtualController.getHandler().removeCallbacks(mouseRepeatRunnable);

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        if (_isTouchThrough) {
            dispatchToBackground(event);
        }
        
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                updateGlobalSensitivity();
                movingButton = null;
                setPressed(true);
                onClickCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }

    private void dispatchToBackground(MotionEvent event) {
        try {
            android.view.ViewParent parent = getParent();
            if (parent instanceof android.view.ViewGroup) {
                android.view.View bg = ((android.view.ViewGroup) parent).findViewById(com.limelight.R.id.backgroundTouchView);
                if (bg != null) {
                    MotionEvent clone = MotionEvent.obtain(event);
                    clone.offsetLocation(getX(), getY());
                    bg.dispatchTouchEvent(clone);
                    clone.recycle();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject config = super.getConfiguration();
        config.put("TEXT", text);
        return config;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        if (configuration.has("TEXT")) {
            this.text = configuration.getString("TEXT");
        }
    }
}
