/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.mooncake.R;
import com.limelight.binding.input.ControllerHandler;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public static class ControllerInputContext {
        public int inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();
    private VirtualControllerElement selectedElement = null;
    private boolean gridSnapping = false;
    private boolean isVisible = true;
    private int realBackgroundTouchCount = 0;
    private boolean isDispatchingTouchThrough = false;

    private float cursorX = 0.5f, cursorY = 0.5f; // Normalized 0.0 to 1.0
    private int refWidth = 1280, refHeight = 720;

    public void setReferenceResolution(int w, int h) {
        this.refWidth = w;
        this.refHeight = h;
    }

    public int getRefWidth() { return refWidth; }
    public int getRefHeight() { return refHeight; }

    public void updateCursorPosition(float x, float y) {
        this.cursorX = Math.max(0.0f, Math.min(1.0f, x));
        this.cursorY = Math.max(0.0f, Math.min(1.0f, y));
    }

    public float getCursorX() { return cursorX; }
    public float getCursorY() { return cursorY; }

    public void incrementBackgroundTouchCount() {
        this.realBackgroundTouchCount++;
    }

    public void decrementBackgroundTouchCount() {
        this.realBackgroundTouchCount = Math.max(0, this.realBackgroundTouchCount - 1);
    }

    public boolean isBackgroundTouched() {
        return realBackgroundTouchCount > 0;
    }

    public boolean isAnyElementExclusivePressed() {
        for (VirtualControllerElement element : elements) {
            if (element.isExclusiveTouch() && element.isPressed()) {
                return true;
            }
        }
        return false;
    }

    public void setDispatchingTouchThrough(boolean dispatching) {
        this.isDispatchingTouchThrough = dispatching;
    }

    public boolean isDispatchingTouchThrough() {
        return isDispatchingTouchThrough;
    }

    public interface SelectionListener {
        void onElementSelected(VirtualControllerElement element);
    }
    private SelectionListener selectionListener;

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setSelectedElement(VirtualControllerElement element) {
        if (selectedElement != null) {
            selectedElement.invalidate();
        }
        selectedElement = element;
        if (selectedElement != null) {
            selectedElement.invalidate();
        }
        if (selectionListener != null) {
            selectionListener.onElementSelected(element);
        }
    }

    public VirtualControllerElement getSelectedElement() {
        return selectedElement;
    }

    public void setGridSnapping(boolean enabled) {
        this.gridSnapping = enabled;
    }

    public boolean isGridSnapping() {
        return gridSnapping;
    }

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active){
                    currentMode = ControllerMode.MoveButtons;
                    message = "Entering configuration mode (Move buttons)";
                } else if (currentMode == ControllerMode.MoveButtons) {
                    currentMode = ControllerMode.ResizeButtons;
                    message = "Entering configuration mode (Resize buttons)";
                } else {
                    currentMode = ControllerMode.Active;
                    VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                    message = "Exiting configuration mode";
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();

                for (VirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });

    }

    public ControllerHandler getControllerHandler() {
        return controllerHandler;
    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        isVisible = false;
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }

        buttonConfigure.setVisibility(View.INVISIBLE);
    }

    public void show() {
        isVisible = true;
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }

        buttonConfigure.setVisibility(View.VISIBLE);
    }

    public void removeElement(VirtualControllerElement element) {
        elements.remove(element);
        frame_layout.removeView(element);
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    public FrameLayout getFrameLayout() {
        return frame_layout;
    }

    public List<VirtualControllerElement> getElementsAt(float x, float y) {
        List<VirtualControllerElement> result = new ArrayList<>();
        for (VirtualControllerElement element : elements) {
            float relX = x - element.getX();
            float relY = y - element.getY();
            if (relX >= 0 && relX <= element.getWidth() && relY >= 0 && relY <= element.getHeight()) {
                result.add(element);
            }
        }
        return result;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        refreshLayout(true);
    }

    public void resetToDefaults() {
        refreshLayout(false);
    }

    private void refreshLayout(boolean loadFromPrefs) {
        frame_layout.post(new Runnable() {
            @Override
            public void run() {
                // Clear existing elements before re-adding
                removeElements();

                if (controllerHandler != null) {
                    DisplayMetrics screen = context.getResources().getDisplayMetrics();

                    int buttonSize = (int)(screen.heightPixels * 0.06f);
                    FrameLayout.LayoutParams params =
                            new FrameLayout.LayoutParams(buttonSize, buttonSize);
                    params.leftMargin = 15;
                    params.topMargin = 15;
                    if (buttonConfigure.getParent() == null) {
                        frame_layout.addView(buttonConfigure, params);
                    }
                }

                // Create the layout AFTER FrameLayout has been measured
                VirtualControllerConfigurationLoader.createDefaultLayout(
                        VirtualController.this, context);

                if (loadFromPrefs) {
                    // Apply saved positions
                    VirtualControllerConfigurationLoader.loadFromPreferences(
                            VirtualController.this, context);
                }

                if (isVisible) {
                    show();
                } else {
                    hide();
                }
            }
        });
    }
    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    void sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();

        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }
}
