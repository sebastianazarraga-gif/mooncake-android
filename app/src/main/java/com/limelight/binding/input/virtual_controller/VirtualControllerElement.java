/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class VirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;
    public static final int EID_GDB = 16;

    protected VirtualController virtualController;
    protected int elementId;

    private final Paint paint = new Paint();

    protected int normalColor = 0xF0888888;
    protected int pressedColor = 0xF00000FF;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSelectedColor = 0xF000FF00;

    protected int _customColor = 0;

    protected int _width = -1;
    protected int _height = -1;

    protected int startSize_x;
    protected int startSize_y;

    protected boolean _isKeyboardMapping = false;
    protected short _mappedKeyCode = 0;
    protected java.util.List<Short> _extraKeyCodes = new java.util.ArrayList<>();

    protected boolean _isMouseMapping = false;
    protected boolean _isCombinedMapping = false;
    public enum MouseAction {
        None, LeftClick, RightClick, MiddleClick, MoveUp, MoveDown, MoveLeft, MoveRight, ScrollUp, ScrollDown
    }
    protected MouseAction _mouseAction = MouseAction.None;
    protected java.util.List<MouseAction> _extraMouseActions = new java.util.ArrayList<>();

    protected int _gamepadFlag = 0;
    protected java.util.List<Integer> _extraGamepadFlags = new java.util.ArrayList<>();
    protected float _sensitivity = 1.0f;
    protected float _globalSensitivity = 1.0f;

    protected short _mappedKeyUp = 0;
    protected short _mappedKeyDown = 0;
    protected short _mappedKeyLeft = 0;
    protected short _mappedKeyRight = 0;

    protected int _mappedDirUpGamepadFlag = 0;
    protected int _mappedDirDownGamepadFlag = 0;
    protected int _mappedDirLeftGamepadFlag = 0;
    protected int _mappedDirRightGamepadFlag = 0;

    protected MouseAction _mappedDirUpMouseAction = MouseAction.None;
    protected MouseAction _mappedDirDownMouseAction = MouseAction.None;
    protected MouseAction _mappedDirLeftMouseAction = MouseAction.None;
    protected MouseAction _mappedDirRightMouseAction = MouseAction.None;

    public enum Shape {
        Circle,
        Square,
        SquareRounded
    }
    protected Shape shape = Shape.Circle;
    protected int _opacity = 100;
    protected float _rotation = 0;
    
    protected boolean _isToggleMode = false;
    protected boolean _isTouchThrough = false;
    protected boolean _isToggled = false;
    protected boolean _isShiftMode = false;
    protected boolean _isRepeatMode = false;
    protected long _repeatInterval = 1000;
    protected long _activationTime = 100;
    protected boolean _isOrderingMode = false;
    protected long _orderActivationTime = 100;
    protected long _orderGapTime = 100;
    protected boolean _applyOnHold = false;
    protected boolean _isHoldRepeat = false;
    protected long _holdRepeatDelay = 1000;
    protected long _holdActivationTime = 100;
    protected boolean _isExclusiveTouch = false;
    protected boolean _avoidTouchThroughConflict = false;
    protected String _customText = null;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    protected VirtualControllerElement(VirtualController controller, Context context, int elementId) {
        super(context);

        this.virtualController = controller;
        this.elementId = elementId;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        if (virtualController.isGridSnapping()) {
            int gridSize = 20;
            newPos_x = (newPos_x / gridSize) * gridSize;
            newPos_y = (newPos_y / gridSize) * gridSize;
        }

        if (newPos_x < 0) newPos_x = 0;
        if (newPos_y < 0) newPos_y = 0;

        // if (checkCollision(newPos_x, newPos_y, getWidth(), getHeight())) {
        //     return;
        // }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = newPos_x;
        layoutParams.topMargin = newPos_y;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);
        
        if (newHeight < 20) newHeight = 20;
        if (newWidth < 20) newWidth = 20;

        // if (checkCollision((int)getX(), (int)getY(), newWidth, newHeight)) {
        //     return;
        // }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.height = newHeight;
        layoutParams.width = newWidth;

        requestLayout();
    }

    protected boolean checkCollision(int x, int y, int w, int h) {
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element == this) continue;
            
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) element.getLayoutParams();
            int otherX = lp.leftMargin;
            int otherY = lp.topMargin;
            int otherW = lp.width;
            int otherH = lp.height;

            if (x < otherX + otherW && x + w > otherX &&
                y < otherY + otherH && y + h > otherY) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.rotate(_rotation, getWidth() / 2f, getHeight() / 2f);
        onElementDraw(canvas);
        canvas.restore();

        VirtualController.ControllerMode vmode = virtualController.getControllerMode();
        boolean isSelected = virtualController.getSelectedElement() == this;

        if (vmode != VirtualController.ControllerMode.Active) {
            paint.setStyle(Paint.Style.STROKE);
            
            if (isSelected) {
                paint.setColor(0xFFBB86FC); // Purple for selection
                paint.setStrokeWidth(getDefaultStrokeWidth() * 2);
            } else {
                // Subtle border for all elements in config mode
                paint.setStrokeWidth(getDefaultStrokeWidth());
                if (vmode == VirtualController.ControllerMode.MoveButtons) {
                    paint.setColor(0x80FF0000); // Semi-transparent Red for move
                } else {
                    paint.setColor(0x80FF00FF); // Semi-transparent Pink for resize
                }
            }

            float sw = paint.getStrokeWidth();
            if (shape == Shape.Square) {
                canvas.drawRect(sw, sw, getWidth() - sw, getHeight() - sw, paint);
            } else if (shape == Shape.SquareRounded) {
                float r = getPercent(getCorrectWidth(), 15);
                canvas.drawRoundRect(sw, sw, getWidth() - sw, getHeight() - sw, r, r, paint);
            } else {
                canvas.drawOval(sw, sw, getWidth() - sw, getHeight() - sw, paint);
            }
        }

        super.onDraw(canvas);
    }

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        return normalColor;
    }

    protected int getPressedColor() {
        return pressedColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    private long downTime;
    private static final int TAP_TIMEOUT = 200;
    private static final int MOVE_THRESHOLD = 10;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionIndex() != 0) {
            return true;
        }

        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downTime = System.currentTimeMillis();
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();

                if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
                    actionEnableMove();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
                    actionEnableResize();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                long duration = System.currentTimeMillis() - downTime;
                float deltaX = Math.abs(event.getX() - position_pressed_x);
                float deltaY = Math.abs(event.getY() - position_pressed_y);

                if (duration < TAP_TIMEOUT && deltaX < MOVE_THRESHOLD && deltaY < MOVE_THRESHOLD) {
                    float absX = getX() + event.getX();
                    float absY = getY() + event.getY();
                    java.util.List<VirtualControllerElement> at = virtualController.getElementsAt(absX, absY);
                    if (at.size() > 1) {
                        int currentIndex = at.indexOf(this);
                        int nextIndex = (currentIndex + 1) % at.size();
                        virtualController.setSelectedElement(at.get(nextIndex));
                    } else {
                        virtualController.setSelectedElement(VirtualControllerElement.this);
                    }
                }

                actionCancel();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            System.out.println(text);
        }
    }

    public void setOpacity(int opacity) {
        _opacity = opacity;
        updateColors();
        invalidate();
    }

    public int getOpacity() {
        return _opacity;
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }

    public void setKeyboardMapping(boolean keyboardMapping) {
        _isKeyboardMapping = keyboardMapping;
        invalidate();
    }

    public boolean isKeyboardMapping() {
        return _isKeyboardMapping;
    }

    public void setMappedKeyCode(short mappedKeyCode) {
        _mappedKeyCode = mappedKeyCode;
        invalidate();
    }

    public short getMappedKeyCode() {
        return _mappedKeyCode;
    }

    public void setExtraKeyCodes(java.util.List<Short> codes) {
        _extraKeyCodes = codes;
        invalidate();
    }

    public java.util.List<Short> getExtraKeyCodes() {
        return _extraKeyCodes;
    }

    public java.util.List<Integer> getExtraGamepadFlags() {
        return _extraGamepadFlags;
    }

    public java.util.List<MouseAction> getExtraMouseActions() {
        return _extraMouseActions;
    }

    public void setMouseMapping(boolean mouseMapping) {
        _isMouseMapping = mouseMapping;
        invalidate();
    }

    public boolean isMouseMapping() {
        return _isMouseMapping;
    }

    public void setCombinedMapping(boolean combinedMapping) {
        _isCombinedMapping = combinedMapping;
        invalidate();
    }

    public boolean isCombinedMapping() {
        return _isCombinedMapping;
    }

    public void setMouseAction(MouseAction mouseAction) {
        _mouseAction = mouseAction;
        invalidate();
    }

    public void updateGlobalSensitivity() {
        try {
            _globalSensitivity = PreferenceConfiguration.readPreferences(getContext()).mouseSensitivity / 100.0f;
        } catch (Exception e) {
            _globalSensitivity = 1.0f;
        }
    }

    public MouseAction getMouseAction() {
        return _mouseAction;
    }

    public void setGamepadFlag(int gamepadFlag) {
        _gamepadFlag = gamepadFlag;
        invalidate();
    }

    public int getGamepadFlag() {
        return _gamepadFlag;
    }

    public void setSensitivity(float sensitivity) {
        _sensitivity = sensitivity;
    }

    public float getSensitivity() {
        return _sensitivity;
    }

    public void setMappedKeyUp(short mappedKeyUp) {
        _mappedKeyUp = mappedKeyUp;
    }

    public short getMappedKeyUp() {
        return _mappedKeyUp;
    }

    public void setMappedKeyDown(short mappedKeyDown) {
        _mappedKeyDown = mappedKeyDown;
    }

    public short getMappedKeyDown() {
        return _mappedKeyDown;
    }

    public void setMappedKeyLeft(short mappedKeyLeft) {
        _mappedKeyLeft = mappedKeyLeft;
    }

    public short getMappedKeyLeft() {
        return _mappedKeyLeft;
    }

    public void setMappedKeyRight(short mappedKeyRight) {
        _mappedKeyRight = mappedKeyRight;
    }

    public short getMappedKeyRight() {
        return _mappedKeyRight;
    }

    public void setMappedDirUpGamepadFlag(int flag) { _mappedDirUpGamepadFlag = flag; invalidate(); }
    public int getMappedDirUpGamepadFlag() { return _mappedDirUpGamepadFlag; }
    public void setMappedDirDownGamepadFlag(int flag) { _mappedDirDownGamepadFlag = flag; invalidate(); }
    public int getMappedDirDownGamepadFlag() { return _mappedDirDownGamepadFlag; }
    public void setMappedDirLeftGamepadFlag(int flag) { _mappedDirLeftGamepadFlag = flag; invalidate(); }
    public int getMappedDirLeftGamepadFlag() { return _mappedDirLeftGamepadFlag; }
    public void setMappedDirRightGamepadFlag(int flag) { _mappedDirRightGamepadFlag = flag; invalidate(); }
    public int getMappedDirRightGamepadFlag() { return _mappedDirRightGamepadFlag; }

    public void setMappedDirUpMouseAction(MouseAction action) { _mappedDirUpMouseAction = action; invalidate(); }
    public MouseAction getMappedDirUpMouseAction() { return _mappedDirUpMouseAction; }
    public void setMappedDirDownMouseAction(MouseAction action) { _mappedDirDownMouseAction = action; invalidate(); }
    public MouseAction getMappedDirDownMouseAction() { return _mappedDirDownMouseAction; }
    public void setMappedDirLeftMouseAction(MouseAction action) { _mappedDirLeftMouseAction = action; invalidate(); }
    public MouseAction getMappedDirLeftMouseAction() { return _mappedDirLeftMouseAction; }
    public void setMappedDirRightMouseAction(MouseAction action) { _mappedDirRightMouseAction = action; invalidate(); }
    public MouseAction getMappedDirRightMouseAction() { return _mappedDirRightMouseAction; }

    public void setCustomColor(int color) {
        _customColor = color;
        updateColors();
        invalidate();
    }

    private void updateColors() {
        int hexOpacity = _opacity * 255 / 100;
        if (_customColor != 0) {
            this.normalColor = (hexOpacity << 24) | (_customColor & 0x00FFFFFF);
            
            // Generate a darker color for the pressed state
            int r = android.graphics.Color.red(_customColor);
            int g = android.graphics.Color.green(_customColor);
            int b = android.graphics.Color.blue(_customColor);
            int darker = android.graphics.Color.rgb((int)(r * 0.7), (int)(g * 0.7), (int)(b * 0.7));
            
            this.pressedColor = (hexOpacity << 24) | (darker & 0x00FFFFFF);
        } else {
            this.normalColor = (hexOpacity << 24) | 0x00888888;
            this.pressedColor = (hexOpacity << 24) | 0x000000FF;
        }
    }

    public int getCustomColor() {
        return _customColor;
    }

    public void setRotation(float rotation) {
        _rotation = rotation;
        invalidate();
    }

    public float getRotation() {
        return _rotation;
    }

    public void setToggleMode(boolean toggleMode) {
        _isToggleMode = toggleMode;
    }

    public void setShiftMode(boolean shiftMode) {
        _isShiftMode = shiftMode;
    }

    public boolean isShiftMode() {
        return _isShiftMode;
    }

    public boolean isToggleMode() {
        return _isToggleMode;
    }

    public void setRepeatMode(boolean repeatMode) {
        _isRepeatMode = repeatMode;
    }

    public boolean isRepeatMode() {
        return _isRepeatMode;
    }

    public void setRepeatInterval(long interval) {
        _repeatInterval = interval;
    }

    public long getRepeatInterval() {
        return _repeatInterval;
    }

    public void setActivationTime(long time) {
        _activationTime = time;
    }

    public long getActivationTime() {
        return _activationTime;
    }

    public void setOrderingMode(boolean ordering) {
        _isOrderingMode = ordering;
    }

    public boolean isOrderingMode() {
        return _isOrderingMode;
    }

    public void setOrderActivationTime(long time) {
        _orderActivationTime = time;
    }

    public long getOrderActivationTime() {
        return _orderActivationTime;
    }

    public void setOrderGapTime(long time) {
        _orderGapTime = time;
    }

    public long getOrderGapTime() {
        return _orderGapTime;
    }

    public void setApplyOnHold(boolean hold) {
        _applyOnHold = hold;
    }

    public boolean isApplyOnHold() {
        return _applyOnHold;
    }

    public void setHoldRepeat(boolean repeat) {
        _isHoldRepeat = repeat;
    }

    public boolean isHoldRepeat() {
        return _isHoldRepeat;
    }

    public void setHoldRepeatDelay(long delay) {
        _holdRepeatDelay = delay;
    }

    public long getHoldRepeatDelay() {
        return _holdRepeatDelay;
    }

    public void setHoldActivationTime(long time) {
        _holdActivationTime = time;
    }

    public long getHoldActivationTime() {
        return _holdActivationTime;
    }

    public void setExclusiveTouch(boolean exclusive) {
        _isExclusiveTouch = exclusive;
    }

    public boolean isExclusiveTouch() {
        return _isExclusiveTouch;
    }

    public void setAvoidTouchThroughConflict(boolean avoid) {
        _avoidTouchThroughConflict = avoid;
    }

    public boolean isAvoidTouchThroughConflict() {
        return _avoidTouchThroughConflict;
    }

    public void setTouchThrough(boolean touchThrough) {
        _isTouchThrough = touchThrough;
    }

    public boolean isTouchThrough() {
        return _isTouchThrough;
    }

    public void setCustomText(String text) {
        _customText = text;
        invalidate();
    }

    public String getCustomText() {
        return _customText;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        invalidate();
    }

    public Shape getShape() {
        return shape;
    }

    public int getElementId() {
        return elementId;
    }

    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);
        configuration.put("IS_KBD", _isKeyboardMapping);
        configuration.put("KBD_CODE", _mappedKeyCode);
        
        org.json.JSONArray extraKeys = new org.json.JSONArray();
        for (Short s : _extraKeyCodes) extraKeys.put((int)s);
        configuration.put("KBD_EXTRA", extraKeys);

        configuration.put("KBD_UP", _mappedKeyUp);
        configuration.put("KBD_DOWN", _mappedKeyDown);
        configuration.put("KBD_LEFT", _mappedKeyLeft);
        configuration.put("KBD_RIGHT", _mappedKeyRight);
        configuration.put("SHAPE", shape.name());
        configuration.put("OPACITY", _opacity);
        configuration.put("TYPE", this.getClass().getSimpleName());
        configuration.put("IS_STICK", (this instanceof AnalogStick));
        configuration.put("IS_MOUSE", _isMouseMapping);
        configuration.put("IS_COMBO", _isCombinedMapping);
        configuration.put("GP_FLAG", _gamepadFlag);
        org.json.JSONArray extraGp = new org.json.JSONArray();
        for (Integer i : _extraGamepadFlags) extraGp.put((int)i);
        configuration.put("GP_EXTRA", extraGp);

        configuration.put("MOUSE_ACT", _mouseAction.name());
        org.json.JSONArray extraMouse = new org.json.JSONArray();
        for (MouseAction m : _extraMouseActions) extraMouse.put(m.name());
        configuration.put("MOUSE_EXTRA", extraMouse);
        configuration.put("SENSE", _sensitivity);
        configuration.put("COLOR", _customColor);
        configuration.put("ROT", _rotation);
        configuration.put("TOGGLE", _isToggleMode);
        configuration.put("SHIFT", _isShiftMode);
        configuration.put("TOUCH_THROUGH", _isTouchThrough);
        configuration.put("REPEAT", _isRepeatMode);
        configuration.put("INTERVAL", _repeatInterval);
        configuration.put("ACTIVE_TIME", _activationTime);
        configuration.put("ORDERING", _isOrderingMode);
        configuration.put("ORD_ACT", _orderActivationTime);
        configuration.put("ORD_GAP", _orderGapTime);
        configuration.put("ORD_HOLD", _applyOnHold);
        configuration.put("HOLD_REP", _isHoldRepeat);
        configuration.put("HOLD_DLY", _holdRepeatDelay);
        configuration.put("HOLD_ACT", _holdActivationTime);
        configuration.put("EXCLUSIVE", _isExclusiveTouch);
        configuration.put("AVOID_CONFLICT", _avoidTouchThroughConflict);
        configuration.put("TOGGLED", _isToggled);
        if (_customText != null) configuration.put("TXT", _customText);

        configuration.put("UP_GP", _mappedDirUpGamepadFlag);
        configuration.put("DOWN_GP", _mappedDirDownGamepadFlag);
        configuration.put("LEFT_GP", _mappedDirLeftGamepadFlag);
        configuration.put("RIGHT_GP", _mappedDirRightGamepadFlag);

        configuration.put("UP_MOUSE", _mappedDirUpMouseAction.name());
        configuration.put("DOWN_MOUSE", _mappedDirDownMouseAction.name());
        configuration.put("LEFT_MOUSE", _mappedDirLeftMouseAction.name());
        configuration.put("RIGHT_MOUSE", _mappedDirRightMouseAction.name());

        return configuration;
    }

    private org.json.JSONArray toJsonArray(java.util.List<Integer> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Integer i : list) arr.put((int)i);
        return arr;
    }

    private org.json.JSONArray toJsonArrayMouse(java.util.List<MouseAction> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (MouseAction m : list) arr.put(m.name());
        return arr;
    }

    private void loadExtraDir(org.json.JSONObject config, String key, java.util.List<Integer> list) {
        list.clear();
        org.json.JSONArray arr = config.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) list.add(arr.optInt(i));
        }
    }

    private void loadExtraDirMouse(org.json.JSONObject config, String key, java.util.List<MouseAction> list) {
        list.clear();
        org.json.JSONArray arr = config.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try { list.add(MouseAction.valueOf(arr.optString(i))); } catch (Exception e) {}
            }
        }
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = configuration.getInt("LEFT");
        layoutParams.topMargin = configuration.getInt("TOP");
        layoutParams.width = configuration.getInt("WIDTH");
        layoutParams.height = configuration.getInt("HEIGHT");
        _isKeyboardMapping = configuration.optBoolean("IS_KBD", false);
        _mappedKeyCode = (short) configuration.optInt("KBD_CODE", 0);
        
        _extraKeyCodes.clear();
        org.json.JSONArray extraKeys = configuration.optJSONArray("KBD_EXTRA");
        if (extraKeys != null) {
            for (int i = 0; i < extraKeys.length(); i++) {
                _extraKeyCodes.add((short) extraKeys.optInt(i));
            }
        }
        
        _extraGamepadFlags.clear();
        org.json.JSONArray extraGp = configuration.optJSONArray("GP_EXTRA");
        if (extraGp != null) {
            for (int i = 0; i < extraGp.length(); i++) {
                _extraGamepadFlags.add(extraGp.optInt(i));
            }
        }

        _extraMouseActions.clear();
        org.json.JSONArray extraMs = configuration.optJSONArray("MOUSE_EXTRA");
        if (extraMs != null) {
            for (int i = 0; i < extraMs.length(); i++) {
                try { _extraMouseActions.add(MouseAction.valueOf(extraMs.optString(i))); } catch (Exception e) {}
            }
        }

        _mappedKeyUp = (short) configuration.optInt("KBD_UP", 0);
        _mappedKeyDown = (short) configuration.optInt("KBD_DOWN", 0);
        _mappedKeyLeft = (short) configuration.optInt("KBD_LEFT", 0);
        _mappedKeyRight = (short) configuration.optInt("KBD_RIGHT", 0);

        _mappedDirUpGamepadFlag = configuration.optInt("UP_GP", 0);
        _mappedDirDownGamepadFlag = configuration.optInt("DOWN_GP", 0);
        _mappedDirLeftGamepadFlag = configuration.optInt("LEFT_GP", 0);
        _mappedDirRightGamepadFlag = configuration.optInt("RIGHT_GP", 0);

        try {
            _mappedDirUpMouseAction = MouseAction.valueOf(configuration.optString("UP_MOUSE", MouseAction.None.name()));
            _mappedDirDownMouseAction = MouseAction.valueOf(configuration.optString("DOWN_MOUSE", MouseAction.None.name()));
            _mappedDirLeftMouseAction = MouseAction.valueOf(configuration.optString("LEFT_MOUSE", MouseAction.None.name()));
            _mappedDirRightMouseAction = MouseAction.valueOf(configuration.optString("RIGHT_MOUSE", MouseAction.None.name()));
        } catch (Exception e) {
            _mappedDirUpMouseAction = _mappedDirDownMouseAction = _mappedDirLeftMouseAction = _mappedDirRightMouseAction = MouseAction.None;
        }

        _isMouseMapping = configuration.optBoolean("IS_MOUSE", false);
        _isCombinedMapping = configuration.optBoolean("IS_COMBO", false);
        _gamepadFlag = configuration.optInt("GP_FLAG", 0);
        _sensitivity = (float) configuration.optDouble("SENSE", 1.0);
        _customColor = configuration.optInt("COLOR", 0);
        _rotation = (float) configuration.optDouble("ROT", 0);
        _isToggleMode = configuration.optBoolean("TOGGLE", false);
        _isShiftMode = configuration.optBoolean("SHIFT", false);
        _isTouchThrough = configuration.optBoolean("TOUCH_THROUGH", false);
        _isRepeatMode = configuration.optBoolean("REPEAT", false);
        _repeatInterval = configuration.optLong("INTERVAL", 1000);
        _activationTime = configuration.optLong("ACTIVE_TIME", 100);
        _isOrderingMode = configuration.optBoolean("ORDERING", false);
        _orderActivationTime = configuration.optLong("ORD_ACT", 100);
        _orderGapTime = configuration.optLong("ORD_GAP", 100);
        _applyOnHold = configuration.optBoolean("ORD_HOLD", false);
        _isHoldRepeat = configuration.optBoolean("HOLD_REP", false);
        _holdRepeatDelay = configuration.optLong("HOLD_DLY", 1000);
        _holdActivationTime = configuration.optLong("HOLD_ACT", 100);
        _isExclusiveTouch = configuration.optBoolean("EXCLUSIVE", false);
        _avoidTouchThroughConflict = configuration.optBoolean("AVOID_CONFLICT", false);
        _isToggled = configuration.optBoolean("TOGGLED", false);
        
        if (configuration.has("TXT") && !configuration.isNull("TXT")) {
            _customText = configuration.getString("TXT");
            if ("null".equals(_customText)) _customText = null;
        } else {
            _customText = null;
        }
        
        String mouseActName = configuration.optString("MOUSE_ACT", MouseAction.None.name());
        try {
            _mouseAction = MouseAction.valueOf(mouseActName);
        } catch (Exception e) {
            _mouseAction = MouseAction.None;
        }
        
        String shapeName = configuration.optString("SHAPE", Shape.Circle.name());
        try {
            shape = Shape.valueOf(shapeName);
        } catch (IllegalArgumentException e) {
            shape = Shape.Circle;
        }

        setOpacity(configuration.optInt("OPACITY", 100));

        requestLayout();
    }
}
