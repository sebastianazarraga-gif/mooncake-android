package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.virtual_controller.AnalogStick;
import com.limelight.binding.input.virtual_controller.DigitalButton;
import com.limelight.binding.input.virtual_controller.DigitalPad;
import com.limelight.binding.input.virtual_controller.LeftAnalogStick;
import com.limelight.binding.input.virtual_controller.LeftTrigger;
import com.limelight.binding.input.virtual_controller.RightAnalogStick;
import com.limelight.binding.input.virtual_controller.RightTrigger;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.nvstream.input.ControllerPacket;

public class ConfigureVirtualControllerActivity extends Activity {

    private VirtualController virtualController;
    private View sidePanel, propertiesContainer, kbdContainer, gpContainer, msContainer;
    private LinearLayout extraKbdContainer, extraGpContainer, extraMsContainer;
    private ImageButton addKbdButton, addGpButton, addMsButton;
    private Spinner mappingModeSpinner, bindingSpinner, shapeSpinner, colorSpinner;
    private SeekBar widthSlider, heightSlider, rotationSlider, sensitivitySlider, opacitySlider;
    private TextView bindingLabel, sensitivityLabel, panelTitle, rotationLabel, widthValueText, heightValueText, opacityValueText, sensitivityValueText, rotationValueText;
    private LinearLayout directionalBindings;
    private Button bindUp, bindDown, bindLeft, bindRight, setKeyboardButton, setGpButton, setMsButton, setCustomTextButton, resetButton, saveButton;
    private android.widget.CheckBox toggleModeCheckbox, touchThroughCheckbox;
    private boolean isUpdatingUI = false;

    private final String[] MODES = {"Gamepad", "Keyboard", "Mouse", "Combined"};
    private final String[] SHAPES = {"Circle", "Square", "Square Rounded"};
    
    private final String[] COLORS = {"Default", "Red", "Green", "Blue", "Yellow", "Purple", "Cyan", "White"};
    private final int[] COLOR_VALUES = {0, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFAA00FF, 0xFF00FFFF, 0xFFFFFFFF};

    private final String[] GAMEPAD_BUTTONS = {
        "None", "A", "B", "X", "Y", "DPad Up", "DPad Down", "DPad Left", "DPad Right",
        "LB", "RB", "LT", "RT", "Start", "Back", "Guide", "LS Click", "RS Click",
        "Paddle 1", "Paddle 2", "Paddle 3", "Paddle 4", "Touchpad", "Misc/Share"
    };
    private final int[] GAMEPAD_FLAGS = {
        0, ControllerPacket.A_FLAG, ControllerPacket.B_FLAG, ControllerPacket.X_FLAG, ControllerPacket.Y_FLAG,
        ControllerPacket.UP_FLAG, ControllerPacket.DOWN_FLAG, ControllerPacket.LEFT_FLAG, ControllerPacket.RIGHT_FLAG,
        ControllerPacket.LB_FLAG, ControllerPacket.RB_FLAG, 0x1000000, 0x2000000,
        ControllerPacket.PLAY_FLAG, ControllerPacket.BACK_FLAG, ControllerPacket.SPECIAL_BUTTON_FLAG,
        ControllerPacket.LS_CLK_FLAG, ControllerPacket.RS_CLK_FLAG,
        ControllerPacket.PADDLE1_FLAG, ControllerPacket.PADDLE2_FLAG, ControllerPacket.PADDLE3_FLAG, ControllerPacket.PADDLE4_FLAG,
        ControllerPacket.TOUCHPAD_FLAG, ControllerPacket.MISC_FLAG
    };

    private final String[] KEY_NAMES = {
        "None", "Space", "Enter", "Escape", "Backspace", "Tab", "Shift", "Ctrl", "Alt",
        "W", "A", "S", "D", "Q", "E", "R", "F", "C", "X", "Z", "V",
        "Up", "Down", "Left", "Right", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    };
    private final short[] KEY_CODES = {
        0, 0x20, 0x0D, 0x1B, 0x08, 0x09, 0xA0, 0xA2, 0xA4,
        0x57, 0x41, 0x53, 0x44, 0x51, 0x45, 0x52, 0x46, 0x43, 0x58, 0x5A, 0x56,
        0x26, 0x28, 0x25, 0x27, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x30,
        0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C
    };

    private final String[] MOUSE_ACTIONS = {
        "None", "Left Click", "Right Click", "Middle Click", "Move Up", "Move Down", "Move Left", "Move Right", "Scroll Up", "Scroll Down"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure_virtual_controller);

        FrameLayout frameLayout = findViewById(R.id.configure_virtual_controller_frameLayout);
        virtualController = new VirtualController(null, frameLayout, this);

        initSidePanel();
        
        virtualController.setSelectionListener(element -> {
            if (element != null) {
                updateSidePanel(element);
                sidePanel.setVisibility(View.VISIBLE);
            }
        });

        try {
            java.lang.reflect.Field modeField = VirtualController.class.getDeclaredField("currentMode");
            modeField.setAccessible(true);
            modeField.set(virtualController, VirtualController.ControllerMode.MoveButtons);
        } catch (Exception e) {}

        virtualController.refreshLayout();
        virtualController.show();

        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> {
            VirtualControllerConfigurationLoader.saveProfile(virtualController, this);
            Toast.makeText(this, "Layout saved", Toast.LENGTH_SHORT).show();
            finish();
        });

        resetButton = findViewById(R.id.resetButton);

        resetButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_reset_osc)
                    .setMessage(R.string.dialog_text_reset_osc)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, MODE_PRIVATE).edit().clear().apply();
                        virtualController.refreshLayout();
                        sidePanel.setVisibility(View.GONE);
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        });

        findViewById(R.id.addButton).setOnClickListener(v -> {
            final String[] types = {"Button", "Joystick", "D-Pad"};
            new AlertDialog.Builder(this).setTitle("Select Type").setItems(types, (dialog, which) -> {
                int newId = getNextId();
                VirtualControllerElement element;
                if (which == 0) {
                    element = VirtualControllerConfigurationLoader.createDigitalButton(newId, ControllerPacket.A_FLAG, 0, 1, "A", -1, virtualController, this);
                } else if (which == 1) {
                    element = new LeftAnalogStick(virtualController, this, newId);
                } else {
                    element = new DigitalPad(virtualController, this, newId);
                }
                
                virtualController.addElement(element, 100, 100, 150, 150);
                virtualController.setSelectedElement(element);
            }).show();
        });

        ImageButton gridButton = findViewById(R.id.gridButton);
        gridButton.setOnClickListener(v -> {
            boolean grid = !virtualController.isGridSnapping();
            virtualController.setGridSnapping(grid);
            gridButton.setBackgroundTintList(ColorStateList.valueOf(grid ? 0xFFFFFFFF : 0xFFBB86FC));
            Toast.makeText(this, "Grid Snapping: " + (grid ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.menuButton).setOnClickListener(v -> {
            if (sidePanel.getVisibility() == View.VISIBLE) {
                sidePanel.setVisibility(View.GONE);
                virtualController.setSelectedElement(null);
            } else {
                propertiesContainer.setVisibility(View.GONE);
                sidePanel.setVisibility(View.VISIBLE);
                virtualController.setSelectedElement(null);
            }
        });

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (sidePanel.getVisibility() == View.VISIBLE && velocityX > 1000) {
                    sidePanel.setVisibility(View.GONE);
                    virtualController.setSelectedElement(null);
                    return true;
                }
                return false;
            }
        });
        sidePanel.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private int getNextId() {
        int maxId = 99;
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element.getElementId() > maxId) maxId = element.getElementId();
        }
        return maxId + 1;
    }

    private void initSidePanel() {
        sidePanel = findViewById(R.id.sidePanel);
        propertiesContainer = findViewById(R.id.propertiesContainer);
        panelTitle = findViewById(R.id.panelTitle);
        mappingModeSpinner = findViewById(R.id.mappingModeSpinner);
        bindingSpinner = findViewById(R.id.bindingSpinner);
        bindingLabel = findViewById(R.id.bindingLabel);
        widthSlider = findViewById(R.id.widthSlider);
        heightSlider = findViewById(R.id.heightSlider);
        widthValueText = findViewById(R.id.widthValueText);
        heightValueText = findViewById(R.id.heightValueText);
        opacityValueText = findViewById(R.id.opacityValueText);
        sensitivityValueText = findViewById(R.id.sensitivityValueText);
        rotationValueText = findViewById(R.id.rotationValueText);
        rotationSlider = findViewById(R.id.rotationSlider);
        rotationLabel = findViewById(R.id.rotationLabel);
        sensitivitySlider = findViewById(R.id.sensitivitySlider);
        sensitivityLabel = findViewById(R.id.sensitivityLabel);
        directionalBindings = findViewById(R.id.directionalBindings);
        shapeSpinner = findViewById(R.id.shapeSpinner);
        opacitySlider = findViewById(R.id.opacitySlider);
        colorSpinner = findViewById(R.id.colorSpinner);
        
        bindUp = findViewById(R.id.bindUp);
        bindDown = findViewById(R.id.bindDown);
        bindLeft = findViewById(R.id.bindLeft);
        bindRight = findViewById(R.id.bindRight);
        setKeyboardButton = findViewById(R.id.setKeyboardButton);
        setGpButton = findViewById(R.id.setGpButton);
        setMsButton = findViewById(R.id.setMsButton);
        
        addKbdButton = findViewById(R.id.addKbdButton);
        addGpButton = findViewById(R.id.addGpButton);
        addMsButton = findViewById(R.id.addMsButton);
        
        kbdContainer = findViewById(R.id.kbdContainer);
        gpContainer = findViewById(R.id.gpContainer);
        msContainer = findViewById(R.id.msContainer);
        
        extraKbdContainer = findViewById(R.id.extraKbdContainer);
        extraGpContainer = findViewById(R.id.extraGpContainer);
        extraMsContainer = findViewById(R.id.extraMsContainer);
        
        setCustomTextButton = findViewById(R.id.setCustomTextButton);
        toggleModeCheckbox = findViewById(R.id.toggleModeCheckbox);
        touchThroughCheckbox = findViewById(R.id.touchThroughCheckbox);

        mappingModeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MODES));
        shapeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SHAPES));
        colorSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLORS));

        mappingModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected == null) return;

                selected.setKeyboardMapping(position == 1);
                selected.setMouseMapping(position == 2);
                selected.setCombinedMapping(position == 3);

                updateSidePanel(selected);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        bindingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // redundant, using setButtons
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        shapeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null) {
                    selected.setShape(VirtualControllerElement.Shape.values()[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        colorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null) {
                    selected.setCustomColor(COLOR_VALUES[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUI) return;
                int snapped = (progress / 5) * 5;
                int value = 50 + snapped * 5;
                widthValueText.setText(String.valueOf(value));
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null && fromUser) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selected.getLayoutParams();
                    if (lp.width != value) {
                        lp.width = value;
                        selected.requestLayout();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int snapped = (seekBar.getProgress() / 5) * 5;
                isUpdatingUI = true;
                seekBar.setProgress(snapped);
                isUpdatingUI = false;
            }
        });

        heightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUI) return;
                int snapped = (progress / 5) * 5;
                int value = 50 + snapped * 5;
                heightValueText.setText(String.valueOf(value));
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null && fromUser) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selected.getLayoutParams();
                    if (lp.height != value) {
                        lp.height = value;
                        selected.requestLayout();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int snapped = (seekBar.getProgress() / 5) * 5;
                isUpdatingUI = true;
                seekBar.setProgress(snapped);
                isUpdatingUI = false;
            }
        });

        opacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUI) return;
                int snapped = (progress / 5) * 5;
                opacityValueText.setText(snapped + "%");
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null && fromUser) selected.setOpacity(snapped);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int snapped = (seekBar.getProgress() / 5) * 5;
                isUpdatingUI = true;
                seekBar.setProgress(snapped);
                isUpdatingUI = false;
            }
        });

        sensitivitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUI) return;
                // Range: 0.1 to 5.0
                float val = 0.1f + ((float) progress / 100.0f) * 4.9f;
                sensitivityValueText.setText(String.format(java.util.Locale.US, "%.2f", val));
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null && fromUser) {
                    selected.setSensitivity(val);
                    selected.invalidate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        rotationSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUI) return;
                int snapped = (progress / 5) * 5;
                rotationValueText.setText(snapped + "°");
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null && fromUser) selected.setRotation(snapped);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int snapped = (seekBar.getProgress() / 5) * 5;
                isUpdatingUI = true;
                seekBar.setProgress(snapped);
                isUpdatingUI = false;
            }
        });

        addKbdButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.getExtraKeyCodes().add((short)0);
                updateSidePanel(selected);
            }
        });
        
        addGpButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.getExtraGamepadFlags().add(0);
                updateSidePanel(selected);
            }
        });
        
        addMsButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.getExtraMouseActions().add(VirtualControllerElement.MouseAction.None);
                updateSidePanel(selected);
            }
        });

        findViewById(R.id.deleteButton).setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Remove Control")
                        .setMessage("Are you sure you want to delete this control?")
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            virtualController.removeElement(selected);
                            virtualController.setSelectedElement(null);
                            sidePanel.setVisibility(View.GONE);
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
            }
        });

        findViewById(R.id.saveElementButton).setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                VirtualControllerConfigurationLoader.saveElement(selected, this);
                Toast.makeText(this, "Control settings saved", Toast.LENGTH_SHORT).show();
            }
        });

        bindUp.setOnClickListener(v -> showDirectionMappingPicker(0));
        bindDown.setOnClickListener(v -> showDirectionMappingPicker(1));
        bindLeft.setOnClickListener(v -> showDirectionMappingPicker(2));
        bindRight.setOnClickListener(v -> showDirectionMappingPicker(3));
        
        setKeyboardButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            new AlertDialog.Builder(this).setTitle("Select Key").setItems(KEY_NAMES, (dialog, which) -> {
                selected.setMappedKeyCode(KEY_CODES[which]);
                updateSidePanel(selected);
            }).show();
        });

        setGpButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            new AlertDialog.Builder(this).setTitle("Select Gamepad Button").setItems(GAMEPAD_BUTTONS, (dialog, which) -> {
                selected.setGamepadFlag(GAMEPAD_FLAGS[which]);
                updateSidePanel(selected);
            }).show();
        });

        setMsButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            new AlertDialog.Builder(this).setTitle("Select Mouse Action").setItems(MOUSE_ACTIONS, (dialog, which) -> {
                selected.setMouseAction(VirtualControllerElement.MouseAction.values()[which]);
                updateSidePanel(selected);
            }).show();
        });

        setCustomTextButton.setOnClickListener(v -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;

            final android.widget.EditText input = new android.widget.EditText(this);
            input.setText("");

            new AlertDialog.Builder(this)
                .setTitle("Set Button Text")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    selected.setCustomText(input.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });

        toggleModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setToggleMode(isChecked);
            }
        });

        touchThroughCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setTouchThrough(isChecked);
            }
        });
    }

    private void updatePropertiesVisibility(VirtualControllerElement selected) {
        boolean isStick = (selected instanceof AnalogStick) || (selected instanceof DigitalPad);
        boolean isCombo = selected.isCombinedMapping();
        boolean isKbd = selected.isKeyboardMapping();

        directionalBindings.setVisibility(isStick ? View.VISIBLE : View.GONE);

        int mode = selected.isCombinedMapping() ? 3 : (selected.isKeyboardMapping() ? 1 : (selected.isMouseMapping() ? 2 : 0));

        gpContainer.setVisibility(!isStick && (mode == 0 || mode == 3) ? View.VISIBLE : View.GONE);
        kbdContainer.setVisibility(!isStick && (mode == 1 || mode == 3) ? View.VISIBLE : View.GONE);
        msContainer.setVisibility(!isStick && (mode == 2 || mode == 3) ? View.VISIBLE : View.GONE);

        toggleModeCheckbox.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        touchThroughCheckbox.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        setCustomTextButton.setVisibility(!isStick ? View.VISIBLE : View.GONE);

        bindingLabel.setVisibility(View.GONE);
        bindingSpinner.setVisibility(View.GONE);
        sensitivityLabel.setVisibility(isStick ? View.VISIBLE : View.GONE);
        findViewById(R.id.sensitivityContainer).setVisibility(isStick ? View.VISIBLE : View.GONE);
    }

    private void updateBindingSpinner(String[] items) {
        bindingSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
    }

    private void updateSidePanel(VirtualControllerElement element) {
        isUpdatingUI = true;
        panelTitle.setText("Properties");
        propertiesContainer.setVisibility(View.VISIBLE);


        if (element.isCombinedMapping()) mappingModeSpinner.setSelection(3);
        else if (element.isKeyboardMapping()) mappingModeSpinner.setSelection(1);
        else if (element.isMouseMapping()) mappingModeSpinner.setSelection(2);
        else mappingModeSpinner.setSelection(0);

        int mode = mappingModeSpinner.getSelectedItemPosition();
        if (mode == 0) updateBindingSpinner(GAMEPAD_BUTTONS);
        else if (mode == 1) updateBindingSpinner(KEY_NAMES);
        else if (mode == 2) updateBindingSpinner(MOUSE_ACTIONS);
        else if (mode == 3) updateBindingSpinner(GAMEPAD_BUTTONS);

        syncBindingSpinnerSelection(element);
        updatePropertiesVisibility(element);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) element.getLayoutParams();
        widthSlider.setProgress((lp.width - 50) / 5);
        heightSlider.setProgress((lp.height - 50) / 5);
        widthValueText.setText(String.valueOf(lp.width));
        heightValueText.setText(String.valueOf(lp.height));

        opacitySlider.setProgress(element.getOpacity());
        opacityValueText.setText(element.getOpacity() + "%");

        rotationSlider.setProgress((int) element.getRotation());
        rotationValueText.setText((int) element.getRotation() + "°");

        // Map 0.1-5.0 range back to 0-100 progress
        float sense = element.getSensitivity();
        int progress = (int) (((sense - 0.1f) / 4.9f) * 100.0f);
        sensitivitySlider.setProgress(progress);
        sensitivityValueText.setText(String.format(java.util.Locale.US, "%.2f", sense));

        shapeSpinner.setSelection(element.getShape().ordinal());
        toggleModeCheckbox.setChecked(element.isToggleMode());
        touchThroughCheckbox.setChecked(element.isTouchThrough());

        int customColor = element.getCustomColor();
        int colorPos = 0;
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == customColor) {
                colorPos = i;
                break;
            }
        }
        colorSpinner.setSelection(colorPos);

        extraGpContainer.removeAllViews();
        extraKbdContainer.removeAllViews();
        extraMsContainer.removeAllViews();
        int mappingMode = mappingModeSpinner.getSelectedItemPosition();
        boolean isStick = (element instanceof AnalogStick) || (element instanceof DigitalPad);

        if (!isStick) {
            // Gamepad Section
            if (mappingMode == 0 || mappingMode == 3) {
                setGpButton.setText("Button 1: " + getGamepadButtonName(element.getGamepadFlag()));
                java.util.List<Integer> extraGp = element.getExtraGamepadFlags();
                for (int i = 0; i < extraGp.size(); i++) {
                    final int index = i;
                    View row = getLayoutInflater().inflate(R.layout.kbd_key_row, extraGpContainer, false);
                    Button keyBtn = row.findViewById(R.id.setKeyboardButton);
                    ImageButton delBtn = row.findViewById(R.id.delKbdButton);
                    keyBtn.setText("Button " + (i + 2) + ": " + getGamepadButtonName(extraGp.get(i)));
                    keyBtn.setOnClickListener(v -> {
                        new AlertDialog.Builder(this).setTitle("Select Gamepad Button").setItems(GAMEPAD_BUTTONS, (dialog, which) -> {
                            extraGp.set(index, GAMEPAD_FLAGS[which]);
                            updateSidePanel(element);
                        }).show();
                    });
                    delBtn.setOnClickListener(v -> { extraGp.remove(index); updateSidePanel(element); });
                    extraGpContainer.addView(row);
                }
            }

            // Keyboard Section
            if (mappingMode == 1 || mappingMode == 3) {
                setKeyboardButton.setText("Key 1: " + getKeyName(element.getMappedKeyCode()));
                java.util.List<Short> extraKeys = element.getExtraKeyCodes();
                for (int i = 0; i < extraKeys.size(); i++) {
                    final int index = i;
                    View row = getLayoutInflater().inflate(R.layout.kbd_key_row, extraKbdContainer, false);
                    Button keyBtn = row.findViewById(R.id.setKeyboardButton);
                    ImageButton delBtn = row.findViewById(R.id.delKbdButton);
                    keyBtn.setText("Key " + (i + 2) + ": " + getKeyName(extraKeys.get(i)));
                    keyBtn.setOnClickListener(v -> {
                        new AlertDialog.Builder(this).setTitle("Select Key").setItems(KEY_NAMES, (dialog, which) -> {
                            extraKeys.set(index, KEY_CODES[which]);
                            updateSidePanel(element);
                        }).show();
                    });
                    delBtn.setOnClickListener(v -> { extraKeys.remove(index); updateSidePanel(element); });
                    extraKbdContainer.addView(row);
                }
            }

            // Mouse Section
            if (mappingMode == 2 || mappingMode == 3) {
                setMsButton.setText("Action 1: " + element.getMouseAction().name());
                java.util.List<VirtualControllerElement.MouseAction> extraMs = element.getExtraMouseActions();
                for (int i = 0; i < extraMs.size(); i++) {
                    final int index = i;
                    View row = getLayoutInflater().inflate(R.layout.kbd_key_row, extraMsContainer, false);
                    Button keyBtn = row.findViewById(R.id.setKeyboardButton);
                    ImageButton delBtn = row.findViewById(R.id.delKbdButton);
                    keyBtn.setText("Action " + (i + 2) + ": " + extraMs.get(index).name());
                    keyBtn.setOnClickListener(v -> {
                        new AlertDialog.Builder(this).setTitle("Select Mouse Action").setItems(MOUSE_ACTIONS, (dialog, which) -> {
                            extraMs.set(index, VirtualControllerElement.MouseAction.values()[which]);
                            updateSidePanel(element);
                        }).show();
                    });
                    delBtn.setOnClickListener(v -> { extraMs.remove(index); updateSidePanel(element); });
                    extraMsContainer.addView(row);
                }
            }
        } else {
            // Analog stick / Digital pad
            updateDirButton(bindUp, "UP", element.getMappedKeyUp(), element.getMappedDirUpGamepadFlag(), element.getMappedDirUpMouseAction());
            updateDirButton(bindDown, "DOWN", element.getMappedKeyDown(), element.getMappedDirDownGamepadFlag(), element.getMappedDirDownMouseAction());
            updateDirButton(bindLeft, "LEFT", element.getMappedKeyLeft(), element.getMappedDirLeftGamepadFlag(), element.getMappedDirLeftMouseAction());
            updateDirButton(bindRight, "RIGHT", element.getMappedKeyRight(), element.getMappedDirRightGamepadFlag(), element.getMappedDirRightMouseAction());
        }

        boolean isStickVar = (element instanceof AnalogStick) || (element instanceof DigitalPad);
        if (isStick) {
            updateDirButton(bindUp, "UP", element.getMappedKeyUp(), element.getMappedDirUpGamepadFlag(), element.getMappedDirUpMouseAction());
            updateDirButton(bindDown, "DOWN", element.getMappedKeyDown(), element.getMappedDirDownGamepadFlag(), element.getMappedDirDownMouseAction());
            updateDirButton(bindLeft, "LEFT", element.getMappedKeyLeft(), element.getMappedDirLeftGamepadFlag(), element.getMappedDirLeftMouseAction());
            updateDirButton(bindRight, "RIGHT", element.getMappedKeyRight(), element.getMappedDirRightGamepadFlag(), element.getMappedDirRightMouseAction());
        }
        isUpdatingUI = false;
    }

    private void updateDirButton(Button btn, String prefix, short key, int gp, VirtualControllerElement.MouseAction mouse) {
        String val = "None";
        if (key != 0) val = getKeyName(key);
        else if (gp != 0) val = getGamepadButtonName(gp);
        else if (mouse != VirtualControllerElement.MouseAction.None) val = mouse.name();
        btn.setText(prefix + ": " + val);
    }

    private String getGamepadButtonName(int flag) {
        for (int i = 0; i < GAMEPAD_FLAGS.length; i++) {
            if (GAMEPAD_FLAGS[i] == flag) return GAMEPAD_BUTTONS[i];
        }
        return "Gamepad";
    }

    private void syncBindingSpinnerSelection(VirtualControllerElement element) {
        int mode = mappingModeSpinner.getSelectedItemPosition();
        if (mode == 0 || mode == 3) {
            int flag = element.getGamepadFlag();
            for (int i = 0; i < GAMEPAD_FLAGS.length; i++) {
                if (GAMEPAD_FLAGS[i] == flag) {
                    bindingSpinner.setSelection(i);
                    break;
                }
            }
        } else if (mode == 1) {
            short code = element.getMappedKeyCode();
            for (int i = 0; i < KEY_CODES.length; i++) {
                if (KEY_CODES[i] == code) {
                    bindingSpinner.setSelection(i);
                    break;
                }
            }
        } else if (mode == 2) {
            bindingSpinner.setSelection(element.getMouseAction().ordinal());
        }
    }

    private String getKeyName(short code) {
        for (int i = 0; i < KEY_CODES.length; i++) {
            if (KEY_CODES[i] == code) return KEY_NAMES[i];
        }
        return "None";
    }

    private void showDirectionMappingPicker(int dir) {
        final String[] types = {"Gamepad", "Keyboard", "Mouse", "Scroll"};
        new AlertDialog.Builder(this)
                .setTitle("Select Mapping Type")
                .setItems(types, (dialog, which) -> {
                    if (which == 0) showDirGamepadPicker(dir);
                    else if (which == 1) showDirKeyboardPicker(dir);
                    else if (which == 2) showDirMousePicker(dir);
                    else showDirScrollPicker(dir);
                }).show();
    }

    private void showDirScrollPicker(int dir) {
        final String[] scrollActions = {"Scroll Up", "Scroll Down"};
        new AlertDialog.Builder(this).setTitle("Select Scroll Direction").setItems(scrollActions, (dialog, which) -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            VirtualControllerElement.MouseAction action = which == 0 ? VirtualControllerElement.MouseAction.ScrollUp : VirtualControllerElement.MouseAction.ScrollDown;
            if (dir == 0) { selected.setMappedDirUpMouseAction(action); selected.setMappedKeyUp((short)0); selected.setMappedDirUpGamepadFlag(0); }
            else if (dir == 1) { selected.setMappedDirDownMouseAction(action); selected.setMappedKeyDown((short)0); selected.setMappedDirDownGamepadFlag(0); }
            else if (dir == 2) { selected.setMappedDirLeftMouseAction(action); selected.setMappedKeyLeft((short)0); selected.setMappedDirLeftGamepadFlag(0); }
            else if (dir == 3) { selected.setMappedDirRightMouseAction(action); selected.setMappedKeyRight((short)0); selected.setMappedDirRightGamepadFlag(0); }
            updateSidePanel(selected);
        }).show();
    }

    private void showDirGamepadPicker(int dir) {
        new AlertDialog.Builder(this).setTitle("Select Gamepad Button").setItems(GAMEPAD_BUTTONS, (dialog, which) -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            int flag = GAMEPAD_FLAGS[which];
            if (dir == 0) { selected.setMappedDirUpGamepadFlag(flag); selected.setMappedKeyUp((short)0); selected.setMappedDirUpMouseAction(VirtualControllerElement.MouseAction.None); }
            else if (dir == 1) { selected.setMappedDirDownGamepadFlag(flag); selected.setMappedKeyDown((short)0); selected.setMappedDirDownMouseAction(VirtualControllerElement.MouseAction.None); }
            else if (dir == 2) { selected.setMappedDirLeftGamepadFlag(flag); selected.setMappedKeyLeft((short)0); selected.setMappedDirLeftMouseAction(VirtualControllerElement.MouseAction.None); }
            else if (dir == 3) { selected.setMappedDirRightGamepadFlag(flag); selected.setMappedKeyRight((short)0); selected.setMappedDirRightMouseAction(VirtualControllerElement.MouseAction.None); }
            updateSidePanel(selected);
        }).show();
    }

    private void showDirKeyboardPicker(int dir) {
        new AlertDialog.Builder(this).setTitle("Select Key").setItems(KEY_NAMES, (dialog, which) -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            short code = KEY_CODES[which];
            if (dir == 0) { selected.setMappedKeyUp(code); selected.setMappedDirUpGamepadFlag(0); selected.setMappedDirUpMouseAction(VirtualControllerElement.MouseAction.None); }
            else if (dir == 1) { selected.setMappedKeyDown(code); selected.setMappedDirDownGamepadFlag(0); selected.setMappedDirDownMouseAction(VirtualControllerElement.MouseAction.None); }
            else if (dir == 2) { selected.setMappedKeyLeft(code); selected.setMappedDirLeftGamepadFlag(0); selected.setMappedDirLeftMouseAction(VirtualControllerElement.MouseAction.None); }
            else if (dir == 3) { selected.setMappedKeyRight(code); selected.setMappedDirRightGamepadFlag(0); selected.setMappedDirRightMouseAction(VirtualControllerElement.MouseAction.None); }
            updateSidePanel(selected);
        }).show();
    }

    private void showDirMousePicker(int dir) {
        new AlertDialog.Builder(this).setTitle("Select Mouse Action").setItems(MOUSE_ACTIONS, (dialog, which) -> {
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected == null) return;
            VirtualControllerElement.MouseAction action = VirtualControllerElement.MouseAction.values()[which];
            if (dir == 0) { selected.setMappedDirUpMouseAction(action); selected.setMappedKeyUp((short)0); selected.setMappedDirUpGamepadFlag(0); }
            else if (dir == 1) { selected.setMappedDirDownMouseAction(action); selected.setMappedKeyDown((short)0); selected.setMappedDirDownGamepadFlag(0); }
            else if (dir == 2) { selected.setMappedDirLeftMouseAction(action); selected.setMappedKeyLeft((short)0); selected.setMappedDirLeftGamepadFlag(0); }
            else if (dir == 3) { selected.setMappedDirRightMouseAction(action); selected.setMappedKeyRight((short)0); selected.setMappedDirRightGamepadFlag(0); }
            updateSidePanel(selected);
        }).show();
    }
}
