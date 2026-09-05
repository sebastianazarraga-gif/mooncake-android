package com.limelight;

import com.mooncake.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
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
import com.limelight.utils.UiHelper;

public class ConfigureVirtualControllerActivity extends Activity {

    private VirtualController virtualController;
    private View sidePanel, propertiesContainer, kbdContainer, gpContainer, msContainer, repeatContainer, activationContainer, orderingContainer, orderSettingsContainer, holdRepeatContainer, holdRepeatSettings;
    private LinearLayout extraKbdContainer, extraGpContainer, extraMsContainer;
    private ImageButton addKbdButton, addGpButton, addMsButton;
    private Spinner mappingModeSpinner, bindingSpinner, shapeSpinner, colorSpinner, repeatUnitSpinner, activationUnitSpinner, orderActivationUnitSpinner, orderGapUnitSpinner, holdRepeatDelayUnit, holdActivationUnit, savesSpinner, useOnAppSpinner, dynamicStickSpinner, mouseReturnTypeSpinner;
    private SeekBar widthSlider, heightSlider, rotationSlider, sensitivitySlider, opacitySlider, returnSpeedSlider;
    private TextView bindingLabel, sensitivityLabel, panelTitle, rotationLabel, widthValueText, heightValueText, opacityValueText, sensitivityValueText, rotationValueText, returnSpeedValueText;
    private View dynamicStickContainer, returnSpeedContainer, mouseReturnProperties;
    private LinearLayout directionalBindings;
    private Button bindUp, bindDown, bindLeft, bindRight, setKeyboardButton, setGpButton, setMsButton, setCustomTextButton, resetButton, saveButton, removeSaveButton, importSaveButton, exportSaveButton;
    private android.widget.CheckBox toggleModeCheckbox, shiftModeCheckbox, touchThroughCheckbox, avoidConflictCheckbox, exclusiveTouchCheckbox, repeatModeCheckbox, orderingCheckbox, applyOnHoldCheckbox, holdRepeatCheckbox, dynamicModeCheckbox, dynamicReturnCheckbox, mouseStaticReturnCheckbox;
    private android.widget.EditText repeatIntervalEdit, activationTimeEdit, orderActivationEdit, orderGapEdit, holdRepeatDelayEdit, holdActivationEdit;
    private boolean isUpdatingUI = false;

    private void hideSystemUi() {
        UiHelper.applyImmersiveMode(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    private static final int REQUEST_EXPORT = 1001;
    private static final int REQUEST_IMPORT = 1002;

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

    private final String[] KEY_CATEGORIES = {
        "Letters", "Numbers", "Function Keys", "Modifiers", "Navigation", "Editing", "Numpad", "Punctuation / Symbols", "System / Special"
    };

    private final String[][] KEY_NAMES_CATEGORIZED = {
        // Letters
        {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"},
        // Numbers
        {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"},
        // Function Keys
        {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24"},
        // Modifiers
        {"Left Shift", "Right Shift", "Left Ctrl", "Right Ctrl", "Left Alt", "Right Alt", "Left Windows", "Right Windows"},
        // Navigation
        {"Up", "Down", "Left", "Right", "Insert", "Delete", "Home", "End", "Page Up", "Page Down"},
        // Editing
        {"Backspace", "Tab", "Enter", "Escape", "Space", "Caps Lock"},
        // Numpad
        {"Num Lock", "Numpad 0", "Numpad 1", "Numpad 2", "Numpad 3", "Numpad 4", "Numpad 5", "Numpad 6", "Numpad 7", "Numpad 8", "Numpad 9", "Numpad .", "Numpad /", "Numpad *", "Numpad -", "Numpad +", "Numpad Enter"},
        // Punctuation / Symbols
        {"Semicolon ;", "Equal =", "Comma ,", "Minus -", "Period .", "Slash /", "Grave `", "Left Bracket [", "Backslash \\", "Right Bracket ]", "Apostrophe '"},
        // System / Special
        {"Print Screen", "Scroll Lock", "Pause/Break", "Menu/Application"}
    };
    
    private final short[][] KEY_CODES_CATEGORIZED = {
        // Letters
        {0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A},
        // Numbers
        {0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39},
        // Function Keys
        {0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87},
        // Modifiers
        {0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0x5B, 0x5C},
        // Navigation
        {0x26, 0x28, 0x25, 0x27, 0x2D, 0x2E, 0x24, 0x23, 0x21, 0x22},
        // Editing
        {0x08, 0x09, 0x0D, 0x1B, 0x20, 0x14},
        // Numpad
        {0x90, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6E, 0x6F, 0x6A, 0x6D, 0x6B, 0x0D},
        // Punctuation / Symbols
        {0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF, 0xC0, 0xDB, 0xDC, 0xDD, 0xDE},
        // System / Special
        {0x2C, 0x91, 0x13, 0x5D}
    };

    private final String[] KEY_NAMES = {
        "None", "Space", "Enter", "Escape", "Backspace", "Tab", "Left Shift", "Right Shift", "Left Ctrl", "Right Ctrl", "Left Alt", "Right Alt", "Left Windows", "Right Windows",
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
        "Up", "Down", "Left", "Right", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24",
        "Del", "PrtSc", "Caps Lock", "Ins", "Home", "End", "PgUp", "PgDn",
        "Semicolon ;", "Equal =", "Comma ,", "Minus -", "Period .", "Slash /", "Grave `",
        "Left Bracket [", "Backslash \\", "Right Bracket ]", "Apostrophe '",
        "Num Lock", "Scroll Lock", "Pause",
        "Numpad 0", "Numpad 1", "Numpad 2", "Numpad 3", "Numpad 4", "Numpad 5", "Numpad 6", "Numpad 7", "Numpad 8", "Numpad 9",
        "Numpad *", "Numpad +", "Numpad -", "Numpad .", "Numpad /", "Numpad Enter"
    };
    private final short[] KEY_CODES = {
        0, 0x20, 0x0D, 0x1B, 0x08, 0x09, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0x5B, 0x5C,
        0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A,
        0x26, 0x28, 0x25, 0x27, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x30,
        0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
        0x2E, 0x2C, 0x14, 0x2D, 0x24, 0x23, 0x21, 0x22,
        0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF, 0xC0,
        0xDB, 0xDC, 0xDD, 0xDE,
        0x90, 0x91, 0x13,
        0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6A, 0x6B, 0x6D, 0x6E, 0x6F, 0x0D
    };

    private final String[] MOUSE_ACTIONS = {
        "None", "Left Click", "Right Click", "Middle Click", "Move Up", "Move Down", "Move Left", "Move Right", "Scroll Up", "Scroll Down"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure_virtual_controller);

        UiHelper.notifyNewRootView(this);

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

        handleIntent(getIntent());

        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> {
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setText(VirtualControllerConfigurationLoader.getCurrentProfileName(this));

            new AlertDialog.Builder(this)
                .setTitle(R.string.save_profile_dialog_title)
                .setMessage(R.string.enter_profile_name)
                .setView(input)
                .setPositiveButton(R.string.save_button, (dialog, which) -> {
                    String name = input.getText().toString();
                    if (name.isEmpty()) name = "Default";
                    VirtualControllerConfigurationLoader.saveProfile(virtualController, this, name);
                    Toast.makeText(this, String.format(getString(R.string.profile_saved_toast), name), Toast.LENGTH_SHORT).show();
                    dynamicModeCheckbox = findViewById(R.id.dynamicModeCheckbox);
        dynamicStickContainer = findViewById(R.id.dynamicStickContainer);
        dynamicStickSpinner = findViewById(R.id.dynamicStickSpinner);

        dynamicReturnCheckbox = findViewById(R.id.dynamicReturnCheckbox);
        returnSpeedContainer = findViewById(R.id.returnSpeedContainer);
        returnSpeedSlider = findViewById(R.id.returnSpeedSlider);
        returnSpeedValueText = findViewById(R.id.returnSpeedValueText);

        mouseStaticReturnCheckbox = findViewById(R.id.mouseStaticReturnCheckbox);
        mouseReturnProperties = findViewById(R.id.mouseReturnProperties);
        mouseReturnTypeSpinner = findViewById(R.id.mouseReturnTypeSpinner);

        updateSavesSpinner();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });

        resetButton = findViewById(R.id.resetButton);

        resetButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_reset_osc)
                    .setMessage(R.string.dialog_text_reset_osc)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        virtualController.resetToDefaults();
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
        savesSpinner = findViewById(R.id.savesSpinner);
        removeSaveButton = findViewById(R.id.removeSaveButton);
        importSaveButton = findViewById(R.id.importSaveButton);
        exportSaveButton = findViewById(R.id.exportSaveButton);
        useOnAppSpinner = findViewById(R.id.useOnAppSpinner);
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
        shiftModeCheckbox = findViewById(R.id.shiftModeCheckbox);
        touchThroughCheckbox = findViewById(R.id.touchThroughCheckbox);
        avoidConflictCheckbox = findViewById(R.id.avoidConflictCheckbox);
        exclusiveTouchCheckbox = findViewById(R.id.exclusiveTouchCheckbox);
        repeatModeCheckbox = findViewById(R.id.repeatModeCheckbox);
        repeatContainer = findViewById(R.id.repeatContainer);
        repeatIntervalEdit = findViewById(R.id.repeatIntervalEdit);
        repeatUnitSpinner = findViewById(R.id.repeatUnitSpinner);
        
        activationContainer = findViewById(R.id.activationContainer);
        activationTimeEdit = findViewById(R.id.activationTimeEdit);
        activationUnitSpinner = findViewById(R.id.activationUnitSpinner);
        
        orderingContainer = findViewById(R.id.orderingContainer);
        orderSettingsContainer = findViewById(R.id.orderSettingsContainer);
        orderingCheckbox = findViewById(R.id.orderingCheckbox);
        applyOnHoldCheckbox = findViewById(R.id.applyOnHoldCheckbox);
        holdRepeatContainer = findViewById(R.id.holdRepeatContainer);
        holdRepeatCheckbox = findViewById(R.id.holdRepeatCheckbox);
        holdRepeatSettings = findViewById(R.id.holdRepeatSettings);
        holdRepeatDelayEdit = findViewById(R.id.holdRepeatDelayEdit);
        holdActivationEdit = findViewById(R.id.holdActivationEdit);
        holdRepeatDelayUnit = findViewById(R.id.holdRepeatDelayUnit);
        holdActivationUnit = findViewById(R.id.holdActivationUnit);
        
        orderActivationEdit = findViewById(R.id.orderActivationEdit);
        orderGapEdit = findViewById(R.id.orderGapEdit);
        orderActivationUnitSpinner = findViewById(R.id.orderActivationUnitSpinner);
        orderGapUnitSpinner = findViewById(R.id.orderGapUnitSpinner);

        dynamicModeCheckbox = findViewById(R.id.dynamicModeCheckbox);
        dynamicStickContainer = findViewById(R.id.dynamicStickContainer);
        dynamicStickSpinner = findViewById(R.id.dynamicStickSpinner);

        dynamicReturnCheckbox = findViewById(R.id.dynamicReturnCheckbox);
        returnSpeedContainer = findViewById(R.id.returnSpeedContainer);
        returnSpeedSlider = findViewById(R.id.returnSpeedSlider);
        returnSpeedValueText = findViewById(R.id.returnSpeedValueText);

        mouseStaticReturnCheckbox = findViewById(R.id.mouseStaticReturnCheckbox);
        mouseReturnProperties = findViewById(R.id.mouseReturnProperties);
        mouseReturnTypeSpinner = findViewById(R.id.mouseReturnTypeSpinner);

        updateSavesSpinner();
        updateAppsSpinner();
        
        savesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                String selected = (String) parent.getItemAtPosition(position);
                if (!selected.equals(VirtualControllerConfigurationLoader.getCurrentProfileName(ConfigureVirtualControllerActivity.this))) {
                    VirtualControllerConfigurationLoader.setCurrentProfileName(ConfigureVirtualControllerActivity.this, selected);
                    virtualController.refreshLayout();
                    updateAppsSpinner();
                }
                removeSaveButton.setVisibility(selected.equals("Default") ? View.GONE : View.VISIBLE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        useOnAppSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                String appName = (String) parent.getItemAtPosition(position);
                String profile = VirtualControllerConfigurationLoader.getCurrentProfileName(ConfigureVirtualControllerActivity.this);
                VirtualControllerConfigurationLoader.setAssociatedAppForProfile(ConfigureVirtualControllerActivity.this, profile, appName);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        importSaveButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimetypes = {"application/json", "text/plain"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
            startActivityForResult(intent, REQUEST_IMPORT);
        });

        exportSaveButton.setOnClickListener(v -> {
            String current = VirtualControllerConfigurationLoader.getCurrentProfileName(this);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "Moonlight_" + current + ".json");
            startActivityForResult(intent, REQUEST_EXPORT);
        });

        removeSaveButton.setOnClickListener(v -> {
            String current = VirtualControllerConfigurationLoader.getCurrentProfileName(this);
            if (current.equals("Default")) return;

            new AlertDialog.Builder(this)
                .setTitle("Remove Save")
                .setMessage("Are you sure you want to delete profile '" + current + "'?")
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    VirtualControllerConfigurationLoader.deleteProfile(this, current);
                    virtualController.refreshLayout();
                    dynamicModeCheckbox = findViewById(R.id.dynamicModeCheckbox);
        dynamicStickContainer = findViewById(R.id.dynamicStickContainer);
        dynamicStickSpinner = findViewById(R.id.dynamicStickSpinner);

        dynamicReturnCheckbox = findViewById(R.id.dynamicReturnCheckbox);
        returnSpeedContainer = findViewById(R.id.returnSpeedContainer);
        returnSpeedSlider = findViewById(R.id.returnSpeedSlider);
        returnSpeedValueText = findViewById(R.id.returnSpeedValueText);

        mouseStaticReturnCheckbox = findViewById(R.id.mouseStaticReturnCheckbox);
        mouseReturnProperties = findViewById(R.id.mouseReturnProperties);
        mouseReturnTypeSpinner = findViewById(R.id.mouseReturnTypeSpinner);

        updateSavesSpinner();
                    Toast.makeText(this, "Profile '" + current + "' removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
        });

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

        dynamicModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setDynamicMode(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        dynamicStickSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null) {
                    selected.setDynamicStickType(position);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        dynamicReturnCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setDynamicReturn(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        returnSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUI) return;
                float val = (float) progress / 100.0f;
                returnSpeedValueText.setText(String.format(java.util.Locale.US, "%.2f", val));
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null && fromUser) {
                    selected.setDynamicReturnSpeed(val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mouseStaticReturnCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setMouseStaticReturn(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        mouseReturnTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                VirtualControllerElement selected = virtualController.getSelectedElement();
                if (selected != null) {
                    selected.setMouseReturnType(position);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
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
            
            new AlertDialog.Builder(this)
                .setTitle("Select Key Category")
                .setItems(KEY_CATEGORIES, (dialog, categoryIdx) -> {
                    new AlertDialog.Builder(this)
                        .setTitle(KEY_CATEGORIES[categoryIdx])
                        .setItems(KEY_NAMES_CATEGORIZED[categoryIdx], (dialog2, keyIdx) -> {
                            selected.setMappedKeyCode(KEY_CODES_CATEGORIZED[categoryIdx][keyIdx]);
                            updateSidePanel(selected);
                        }).show();
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
                updatePropertiesVisibility(selected);
            }
        });

        shiftModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setShiftMode(isChecked);
            }
        });

        touchThroughCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setTouchThrough(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        avoidConflictCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setAvoidTouchThroughConflict(isChecked);
            }
        });

        exclusiveTouchCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setExclusiveTouch(isChecked);
            }
        });

        orderingCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setOrderingMode(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        applyOnHoldCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setApplyOnHold(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        holdRepeatCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setHoldRepeat(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        holdRepeatDelayEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateHoldRepeatDelay();
            }
        });

        holdActivationEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateHoldActivation();
            }
        });

        holdRepeatDelayUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateHoldRepeatDelay();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        holdActivationUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateHoldActivation();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        repeatModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI) return;
            VirtualControllerElement selected = virtualController.getSelectedElement();
            if (selected != null) {
                selected.setRepeatMode(isChecked);
                updatePropertiesVisibility(selected);
            }
        });

        repeatIntervalEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateRepeatInterval();
            }
        });

        repeatUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateRepeatInterval();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        activationTimeEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateActivationTime();
            }
        });

        activationUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateActivationTime();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        orderActivationEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateOrderActivation();
            }
        });

        orderGapEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateOrderGap();
            }
        });

        orderActivationUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateOrderActivation();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        orderGapUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateOrderGap();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updatePropertiesVisibility(VirtualControllerElement selected) {
        boolean isStick = (selected instanceof AnalogStick) || (selected instanceof DigitalPad);
        
        dynamicModeCheckbox.setVisibility(isStick ? View.VISIBLE : View.GONE);
        boolean dynamicOn = isStick && selected.isDynamicMode();
        
        int mode = selected.isCombinedMapping() ? 3 : (selected.isKeyboardMapping() ? 1 : (selected.isMouseMapping() ? 2 : 0));
        
        // Dynamic Stick type selection (only for controller stick, not mouse)
        boolean isControllerStick = isStick && (mode == 0 || mode == 3);
        dynamicStickContainer.setVisibility(dynamicOn && isControllerStick ? View.VISIBLE : View.GONE);
        
        dynamicReturnCheckbox.setVisibility(dynamicOn && isControllerStick ? View.VISIBLE : View.GONE);
        boolean returnOn = dynamicOn && isControllerStick && selected.isDynamicReturn();
        returnSpeedContainer.setVisibility(returnOn ? View.VISIBLE : View.GONE);

        // Mouse Static Return
        boolean isMouseStick = isStick && (mode == 2 || mode == 3);
        mouseStaticReturnCheckbox.setVisibility(dynamicOn && isMouseStick ? View.VISIBLE : View.GONE);
        
        boolean mouseReturnActive = dynamicOn && isMouseStick && selected.isMouseStaticReturn();
        mouseReturnProperties.setVisibility(mouseReturnActive ? View.VISIBLE : View.GONE);

        directionalBindings.setVisibility(isStick && !dynamicOn ? View.VISIBLE : View.GONE);
        
        // Automation / Logic section
        boolean orderingOn = !isStick && selected.isOrderingMode();
        boolean applyOnHold = orderingOn && selected.isApplyOnHold();
        boolean holdRepeatOn = applyOnHold && selected.isHoldRepeat();

        orderingContainer.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        applyOnHoldCheckbox.setVisibility(orderingOn ? View.VISIBLE : View.GONE);
        holdRepeatContainer.setVisibility(applyOnHold ? View.VISIBLE : View.GONE);
        
        // Hide Order Activation/Gap if Hold Repeat is active to avoid conflict
        orderSettingsContainer.setVisibility(orderingOn && !holdRepeatOn ? View.VISIBLE : View.GONE);
        holdRepeatSettings.setVisibility(holdRepeatOn ? View.VISIBLE : View.GONE);

        gpContainer.setVisibility(!isStick && (mode == 0 || mode == 3) ? View.VISIBLE : View.GONE);
        kbdContainer.setVisibility(!isStick && (mode == 1 || mode == 3) ? View.VISIBLE : View.GONE);
        msContainer.setVisibility(!isStick && (mode == 2 || mode == 3) ? View.VISIBLE : View.GONE);

        toggleModeCheckbox.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        shiftModeCheckbox.setVisibility(!isStick && (mode == 1 || mode == 3) ? View.VISIBLE : View.GONE);
        touchThroughCheckbox.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        avoidConflictCheckbox.setVisibility(!isStick && selected.isTouchThrough() ? View.VISIBLE : View.GONE);
        exclusiveTouchCheckbox.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        
        // Repeat mode should be available for all buttons (repeating while held)
        // or for toggle buttons (repeating while toggled).
        repeatModeCheckbox.setVisibility(!isStick ? View.VISIBLE : View.GONE);
        
        // Repeat settings are visible if repeat mode is enabled
        boolean repeatOn = !isStick && selected.isRepeatMode();
        repeatContainer.setVisibility(repeatOn ? View.VISIBLE : View.GONE);
        activationContainer.setVisibility(repeatOn ? View.VISIBLE : View.GONE);
        
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
        dynamicModeCheckbox.setChecked(element.isDynamicMode());
        dynamicStickSpinner.setSelection(element.getDynamicStickType());
        dynamicReturnCheckbox.setChecked(element.isDynamicReturn());
        returnSpeedSlider.setProgress((int) (element.getDynamicReturnSpeed() * 100));
        returnSpeedValueText.setText(String.format(java.util.Locale.US, "%.2f", element.getDynamicReturnSpeed()));

        mouseStaticReturnCheckbox.setChecked(element.isMouseStaticReturn());
        mouseReturnTypeSpinner.setSelection(element.getMouseReturnType());

        toggleModeCheckbox.setChecked(element.isToggleMode());
        shiftModeCheckbox.setChecked(element.isShiftMode());
        touchThroughCheckbox.setChecked(element.isTouchThrough());
        avoidConflictCheckbox.setChecked(element.isAvoidTouchThroughConflict());
        exclusiveTouchCheckbox.setChecked(element.isExclusiveTouch());
        orderingCheckbox.setChecked(element.isOrderingMode());
        applyOnHoldCheckbox.setChecked(element.isApplyOnHold());
        holdRepeatCheckbox.setChecked(element.isHoldRepeat());
        
        long hDelay = element.getHoldRepeatDelay();
        if (hDelay >= 1000 && hDelay % 1000 == 0) {
            holdRepeatDelayEdit.setText(String.valueOf(hDelay / 1000));
            holdRepeatDelayUnit.setSelection(1);
        } else {
            holdRepeatDelayEdit.setText(String.valueOf(hDelay));
            holdRepeatDelayUnit.setSelection(0);
        }

        long hAct = element.getHoldActivationTime();
        if (hAct >= 1000 && hAct % 1000 == 0) {
            holdActivationEdit.setText(String.valueOf(hAct / 1000));
            holdActivationUnit.setSelection(1);
        } else {
            holdActivationEdit.setText(String.valueOf(hAct));
            holdActivationUnit.setSelection(0);
        }
        
        // Repeating Settings
        repeatModeCheckbox.setChecked(element.isRepeatMode());
        long interval = element.getRepeatInterval();
        if (interval >= 1000 && interval % 1000 == 0) {
            repeatIntervalEdit.setText(String.valueOf(interval / 1000));
            repeatUnitSpinner.setSelection(1);
        } else {
            repeatIntervalEdit.setText(String.valueOf(interval));
            repeatUnitSpinner.setSelection(0);
        }

        long activeTime = element.getActivationTime();
        if (activeTime >= 1000 && activeTime % 1000 == 0) {
            activationTimeEdit.setText(String.valueOf(activeTime / 1000));
            activationUnitSpinner.setSelection(1);
        } else {
            activationTimeEdit.setText(String.valueOf(activeTime));
            activationUnitSpinner.setSelection(0);
        }

        // Ordering Settings
        orderingCheckbox.setChecked(element.isOrderingMode());
        long oActTime = element.getOrderActivationTime();
        if (oActTime >= 1000 && oActTime % 1000 == 0) {
            orderActivationEdit.setText(String.valueOf(oActTime / 1000));
            orderActivationUnitSpinner.setSelection(1);
        } else {
            orderActivationEdit.setText(String.valueOf(oActTime));
            orderActivationUnitSpinner.setSelection(0);
        }

        long oGapTime = element.getOrderGapTime();
        if (oGapTime >= 1000 && oGapTime % 1000 == 0) {
            orderGapEdit.setText(String.valueOf(oGapTime / 1000));
            orderGapUnitSpinner.setSelection(1);
        } else {
            orderGapEdit.setText(String.valueOf(oGapTime));
            orderGapUnitSpinner.setSelection(0);
        }

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
        boolean isStick = (element instanceof AnalogStick) || (element instanceof DigitalPad);

        if (!isStick) {
            // Gamepad Section
            if (mode == 0 || mode == 3) {
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
            if (mode == 1 || mode == 3) {
                setKeyboardButton.setText("Key 1: " + getKeyName(element.getMappedKeyCode()));
                java.util.List<Short> extraKeys = element.getExtraKeyCodes();
                for (int i = 0; i < extraKeys.size(); i++) {
                    final int index = i;
                    View row = getLayoutInflater().inflate(R.layout.kbd_key_row, extraKbdContainer, false);
                    Button keyBtn = row.findViewById(R.id.setKeyboardButton);
                    ImageButton delBtn = row.findViewById(R.id.delKbdButton);
                    keyBtn.setText("Key " + (i + 2) + ": " + getKeyName(extraKeys.get(i)));
                    keyBtn.setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                            .setTitle("Select Key Category")
                            .setItems(KEY_CATEGORIES, (dialog, categoryIdx) -> {
                                new AlertDialog.Builder(this)
                                    .setTitle(KEY_CATEGORIES[categoryIdx])
                                    .setItems(KEY_NAMES_CATEGORIZED[categoryIdx], (dialog2, keyIdx) -> {
                                        extraKeys.set(index, KEY_CODES_CATEGORIZED[categoryIdx][keyIdx]);
                                        updateSidePanel(element);
                                    }).show();
                            }).show();
                    });
                    delBtn.setOnClickListener(v -> { extraKeys.remove(index); updateSidePanel(element); });
                    extraKbdContainer.addView(row);
                }
            }

            // Mouse Section
            if (mode == 2 || mode == 3) {
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
        new AlertDialog.Builder(this)
            .setTitle("Select Key Category")
            .setItems(KEY_CATEGORIES, (dialog, categoryIdx) -> {
                new AlertDialog.Builder(this)
                    .setTitle(KEY_CATEGORIES[categoryIdx])
                    .setItems(KEY_NAMES_CATEGORIZED[categoryIdx], (dialog2, keyIdx) -> {
                        VirtualControllerElement selected = virtualController.getSelectedElement();
                        if (selected == null) return;
                        short code = KEY_CODES_CATEGORIZED[categoryIdx][keyIdx];
                        if (dir == 0) { selected.setMappedKeyUp(code); selected.setMappedDirUpGamepadFlag(0); selected.setMappedDirUpMouseAction(VirtualControllerElement.MouseAction.None); }
                        else if (dir == 1) { selected.setMappedKeyDown(code); selected.setMappedDirDownGamepadFlag(0); selected.setMappedDirDownMouseAction(VirtualControllerElement.MouseAction.None); }
                        else if (dir == 2) { selected.setMappedKeyLeft(code); selected.setMappedDirLeftGamepadFlag(0); selected.setMappedDirLeftMouseAction(VirtualControllerElement.MouseAction.None); }
                        else if (dir == 3) { selected.setMappedKeyRight(code); selected.setMappedDirRightGamepadFlag(0); selected.setMappedDirRightMouseAction(VirtualControllerElement.MouseAction.None); }
                        updateSidePanel(selected);
                    }).show();
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

    private void updateHoldRepeatDelay() {
        if (isUpdatingUI) return;
        VirtualControllerElement selected = virtualController.getSelectedElement();
        if (selected == null) return;
        try {
            String s = holdRepeatDelayEdit.getText().toString();
            if (s.isEmpty()) return;
            long val = Long.parseLong(s);
            if (holdRepeatDelayUnit.getSelectedItemPosition() == 1) val *= 1000;
            selected.setHoldRepeatDelay(val);
        } catch (Exception e) {}
    }

    private void updateHoldActivation() {
        if (isUpdatingUI) return;
        VirtualControllerElement selected = virtualController.getSelectedElement();
        if (selected == null) return;
        try {
            String s = holdActivationEdit.getText().toString();
            if (s.isEmpty()) return;
            long val = Long.parseLong(s);
            if (holdActivationUnit.getSelectedItemPosition() == 1) val *= 1000;
            selected.setHoldActivationTime(val);
        } catch (Exception e) {}
    }

    private void updateRepeatInterval() {
        if (isUpdatingUI) return;
        VirtualControllerElement selected = virtualController.getSelectedElement();
        if (selected == null) return;
        try {
            String s = repeatIntervalEdit.getText().toString();
            if (s.isEmpty()) return;
            long val = Long.parseLong(s);
            if (repeatUnitSpinner.getSelectedItemPosition() == 1) val *= 1000;
            selected.setRepeatInterval(val);
        } catch (Exception e) {}
    }

    private void updateActivationTime() {
        if (isUpdatingUI) return;
        VirtualControllerElement selected = virtualController.getSelectedElement();
        if (selected == null) return;
        try {
            String s = activationTimeEdit.getText().toString();
            if (s.isEmpty()) return;
            long val = Long.parseLong(s);
            if (activationUnitSpinner.getSelectedItemPosition() == 1) val *= 1000;
            selected.setActivationTime(val);
        } catch (Exception e) {}
    }

    private void updateOrderActivation() {
        if (isUpdatingUI) return;
        VirtualControllerElement selected = virtualController.getSelectedElement();
        if (selected == null) return;
        try {
            String s = orderActivationEdit.getText().toString();
            if (s.isEmpty()) return;
            long val = Long.parseLong(s);
            if (orderActivationUnitSpinner.getSelectedItemPosition() == 1) val *= 1000;
            selected.setOrderActivationTime(val);
        } catch (Exception e) {}
    }

    private void updateOrderGap() {
        if (isUpdatingUI) return;
        VirtualControllerElement selected = virtualController.getSelectedElement();
        if (selected == null) return;
        try {
            String s = orderGapEdit.getText().toString();
            if (s.isEmpty()) return;
            long val = Long.parseLong(s);
            if (orderGapUnitSpinner.getSelectedItemPosition() == 1) val *= 1000;
            selected.setOrderGapTime(val);
        } catch (Exception e) {}
    }

    private void updateSavesSpinner() {
        isUpdatingUI = true;
        java.util.List<String> profiles = VirtualControllerConfigurationLoader.getProfileList(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profiles);
        savesSpinner.setAdapter(adapter);
        String current = VirtualControllerConfigurationLoader.getCurrentProfileName(this);
        int pos = profiles.indexOf(current);
        if (pos >= 0) savesSpinner.setSelection(pos);
        removeSaveButton.setVisibility(current.equals("Default") ? View.GONE : View.VISIBLE);
        isUpdatingUI = false;
    }

    private void updateAppsSpinner() {
        isUpdatingUI = true;
        java.util.List<String> appNames = new java.util.ArrayList<>();
        appNames.add("None");
        
        java.io.File appListDir = new java.io.File(getCacheDir(), "applist");
        if (appListDir.exists() && appListDir.isDirectory()) {
            java.io.File[] files = appListDir.listFiles();
            if (files != null) {
                java.util.Set<String> uniqueApps = new java.util.HashSet<>();
                for (java.io.File file : files) {
                    try (java.io.InputStream is = new java.io.FileInputStream(file)) {
                        String xml = com.limelight.utils.CacheHelper.readInputStreamToString(is);
                        java.util.List<com.limelight.nvstream.http.NvApp> apps = com.limelight.nvstream.http.NvHTTP.getAppListByReader(new java.io.StringReader(xml));
                        for (com.limelight.nvstream.http.NvApp app : apps) {
                            uniqueApps.add(app.getAppName());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                java.util.List<String> sortedApps = new java.util.ArrayList<>(uniqueApps);
                java.util.Collections.sort(sortedApps);
                appNames.addAll(sortedApps);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, appNames);
        useOnAppSpinner.setAdapter(adapter);
        
        String currentProfile = VirtualControllerConfigurationLoader.getCurrentProfileName(this);
        String associatedApp = VirtualControllerConfigurationLoader.getAssociatedAppForProfile(this, currentProfile);
        int pos = appNames.indexOf(associatedApp);
        if (pos >= 0) useOnAppSpinner.setSelection(pos);
        isUpdatingUI = false;
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            android.net.Uri uri = intent.getData();
            if (uri != null) {
                importProfileFromUri(uri);
            }
        }
    }

    private void importProfileFromUri(android.net.Uri uri) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Imported_Profile");

        new AlertDialog.Builder(this)
            .setTitle(R.string.import_profile_dialog_title)
            .setMessage(R.string.enter_import_name)
            .setView(input)
            .setPositiveButton(R.string.import_button, (dialog, which) -> {
                String name = input.getText().toString();
                if (name.isEmpty()) name = "Imported";

                try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                    String json = s.hasNext() ? s.next() : "";
                    if (VirtualControllerConfigurationLoader.importProfileFromJson(this, name, json)) {
                        VirtualControllerConfigurationLoader.setCurrentProfileName(this, name);
                        virtualController.refreshLayout();
                        dynamicModeCheckbox = findViewById(R.id.dynamicModeCheckbox);
        dynamicStickContainer = findViewById(R.id.dynamicStickContainer);
        dynamicStickSpinner = findViewById(R.id.dynamicStickSpinner);

        dynamicReturnCheckbox = findViewById(R.id.dynamicReturnCheckbox);
        returnSpeedContainer = findViewById(R.id.returnSpeedContainer);
        returnSpeedSlider = findViewById(R.id.returnSpeedSlider);
        returnSpeedValueText = findViewById(R.id.returnSpeedValueText);

        mouseStaticReturnCheckbox = findViewById(R.id.mouseStaticReturnCheckbox);
        mouseReturnProperties = findViewById(R.id.mouseReturnProperties);
        mouseReturnTypeSpinner = findViewById(R.id.mouseReturnTypeSpinner);

        updateSavesSpinner();
                        Toast.makeText(this, String.format(getString(R.string.profile_imported_toast), name), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.invalid_profile_toast, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.import_failed_toast, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (isFinishing()) {
            return;
        }

        if (sidePanel.getVisibility() == View.VISIBLE) {
            sidePanel.setVisibility(View.GONE);
            virtualController.setSelectedElement(null);
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;

        android.net.Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            String current = VirtualControllerConfigurationLoader.getCurrentProfileName(this);
            String json = VirtualControllerConfigurationLoader.exportProfileToJson(this, current);
            try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor())) {
                fos.write(json.getBytes());
                Toast.makeText(this, R.string.profile_exported_toast, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.export_failed_toast, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_IMPORT) {
            importProfileFromUri(uri);
        }
    }
}
