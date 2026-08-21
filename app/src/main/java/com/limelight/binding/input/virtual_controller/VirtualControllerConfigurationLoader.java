/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VirtualControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "OSC";
    public static final String PROFILES_LIST_KEY = "PROFILES_LIST";
    public static final String CURRENT_PROFILE_KEY = "CURRENT_PROFILE";

    public static String getPrefName(String profileName) {
        if (profileName == null || profileName.isEmpty() || profileName.equals("Default")) {
            return OSC_PREFERENCE;
        }
        return OSC_PREFERENCE + "_" + profileName;
    }

    public static List<String> getProfileList(Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE);
        Set<String> set = pref.getStringSet(PROFILES_LIST_KEY, new HashSet<>());
        List<String> list = new ArrayList<>(set);
        if (!list.contains("Default")) list.add(0, "Default");
        java.util.Collections.sort(list.subList(1, list.size()));
        return list;
    }

    public static void addProfileToList(Context context, String name) {
        SharedPreferences pref = context.getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(pref.getStringSet(PROFILES_LIST_KEY, new HashSet<>()));
        set.add(name);
        pref.edit().putStringSet(PROFILES_LIST_KEY, set).apply();
    }

    public static String getCurrentProfileName(Context context) {
        return context.getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE).getString(CURRENT_PROFILE_KEY, "Default");
    }

    public static void setCurrentProfileName(Context context, String name) {
        context.getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE).edit().putString(CURRENT_PROFILE_KEY, name).apply();
    }

    public static void deleteProfile(Context context, String profileName) {
        if (profileName == null || profileName.equals("Default")) return;

        SharedPreferences mainPref = context.getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(mainPref.getStringSet(PROFILES_LIST_KEY, new HashSet<>()));
        if (set.remove(profileName)) {
            mainPref.edit().putStringSet(PROFILES_LIST_KEY, set).apply();
        }

        // Delete the profile's SharedPreferences file content
        String prefName = getPrefName(profileName);
        context.getSharedPreferences(prefName, Activity.MODE_PRIVATE).edit().clear().apply();

        // Reset current profile to Default if we just deleted the current one
        if (getCurrentProfileName(context).equals(profileName)) {
            setCurrentProfileName(context, "Default");
        }
    }

    public static String exportProfileToJson(Context context, String profileName) {
        String prefName = getPrefName(profileName);
        SharedPreferences pref = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();
        try {
            Map<String, ?> all = pref.getAll();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                root.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return root.toString();
    }

    public static boolean importProfileFromJson(Context context, String profileName, String json) {
        try {
            JSONObject root = new JSONObject(json);
            String prefName = getPrefName(profileName);
            SharedPreferences.Editor editor = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit();
            editor.clear();
            java.util.Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = root.get(key);
                if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
            }
            editor.apply();
            if (!profileName.equals("Default")) {
                addProfileToList(context, profileName);
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static int getPercent(
            int percent,
            int total) {
        return (int) (((float) total / (float) 100) * (float) percent);
    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static DigitalPad createDigitalPad(
            final VirtualController controller,
            final Context context) {
        return new DigitalPad(controller, context);
    }

    public static DigitalButton createDigitalButton(
            final int elementId,
            final int keyShort,
            final int keyLong,
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        DigitalButton button = new DigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.setGamepadFlag(keyShort);

        return button;
    }

    private static DigitalButton createLeftTrigger(
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        LeftTrigger button = new LeftTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
    }

    private static DigitalButton createRightTrigger(
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        RightTrigger button = new RightTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
    }

    private static AnalogStick createLeftStick(
            final VirtualController controller,
            final Context context) {
        return new LeftAnalogStick(controller, context);
    }

    private static AnalogStick createRightStick(
            final VirtualController controller,
            final Context context) {
        return new RightAnalogStick(controller, context);
    }


    private static final int TRIGGER_L_BASE_X = 1;
    private static final int TRIGGER_R_BASE_X = 92;
    private static final int TRIGGER_DISTANCE = 23;
    private static final int TRIGGER_BASE_Y = 31;
    private static final int TRIGGER_WIDTH = 12;
    private static final int TRIGGER_HEIGHT = 9;

    // Face buttons are defined based on the Y button (button number 9)
    private static final int BUTTON_BASE_X = 106;
    private static final int BUTTON_BASE_Y = 1;
    private static final int BUTTON_SIZE = 10;

    private static final int DPAD_BASE_X = 4;
    private static final int DPAD_BASE_Y = 41;
    private static final int DPAD_SIZE = 30;

    private static final int ANALOG_L_BASE_X = 6;
    private static final int ANALOG_L_BASE_Y = 4;
    private static final int ANALOG_R_BASE_X = 98;
    private static final int ANALOG_R_BASE_Y = 42;
    private static final int ANALOG_SIZE = 26;

    private static final int L3_R3_BASE_Y = 60;

    private static final int START_X = 83;
    private static final int BACK_X = 34;
    private static final int START_BACK_Y = 64;
    private static final int START_BACK_WIDTH = 12;
    private static final int START_BACK_HEIGHT = 7;

    // Make the Guide Menu be in the center of START and BACK menu
    private static final int GUIDE_X = START_X-BACK_X;
    private static final int GUIDE_Y = START_BACK_Y;

    public static void createDefaultLayout(final VirtualController controller, final Context context) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        int width = controller.getFrameLayout().getWidth();
        int height = controller.getFrameLayout().getHeight();

        if (width <= 0 || height <= 0) {
            width = screen.widthPixels;
            height = screen.heightPixels;
        }

        int rightDisplacement = width - height * 16 / 9;

        // NOTE: Some of these getPercent() expressions seem like they can be combined
        // into a single call. Due to floating point rounding, this isn't actually possible.

        if (!config.onlyL3R3)
        {
            controller.addElement(createDigitalPad(controller, context),
                    screenScale(DPAD_BASE_X, height),
                    screenScale(DPAD_BASE_Y, height),
                    screenScale(DPAD_SIZE, height),
                    screenScale(DPAD_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_A,
                    !config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    !config.flipFaceButtons ? "A" : "B", -1, controller, context),
                    screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + 2 * BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_B,
                    config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    config.flipFaceButtons ? "A" : "B", -1, controller, context),
                    screenScale(BUTTON_BASE_X + BUTTON_SIZE, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_X,
                    !config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    !config.flipFaceButtons ? "X" : "Y", -1, controller, context),
                    screenScale(BUTTON_BASE_X - BUTTON_SIZE, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_Y,
                    config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    config.flipFaceButtons ? "X" : "Y", -1, controller, context),
                    screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createLeftTrigger(
                    1, "LT", -1, controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createRightTrigger(
                    1, "RT", -1, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_LB,
                    ControllerPacket.LB_FLAG, 0, 1, "LB", -1, controller, context),
                    screenScale(TRIGGER_L_BASE_X + TRIGGER_DISTANCE, height),
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_RB,
                    ControllerPacket.RB_FLAG, 0, 1, "RB", -1, controller, context),
                    screenScale(TRIGGER_R_BASE_X, height) + rightDisplacement,
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createLeftStick(controller, context),
                    screenScale(ANALOG_L_BASE_X, height),
                    screenScale(ANALOG_L_BASE_Y, height),
                    screenScale(ANALOG_SIZE, height),
                    screenScale(ANALOG_SIZE, height)
            );

            controller.addElement(createRightStick(controller, context),
                    screenScale(ANALOG_R_BASE_X, height) + rightDisplacement,
                    screenScale(ANALOG_R_BASE_Y, height),
                    screenScale(ANALOG_SIZE, height),
                    screenScale(ANALOG_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_BACK,
                    ControllerPacket.BACK_FLAG, 0, 2, "BACK", -1, controller, context),
                    screenScale(BACK_X, height),
                    screenScale(START_BACK_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_START,
                    ControllerPacket.PLAY_FLAG, 0, 3, "START", -1, controller, context),
                    screenScale(START_X, height) + rightDisplacement,
                    screenScale(START_BACK_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );
        }
        else {
            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_LSB,
                    ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", -1, controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_RSB,
                    ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", -1, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );
        }

        if(config.showGuideButton){
            controller.addElement(createDigitalButton(VirtualControllerElement.EID_GDB,
                            ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 1, "GUIDE", -1, controller, context),
                    screenScale(GUIDE_X, height)+ rightDisplacement,
                    screenScale(GUIDE_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );
        }

        controller.setOpacity(config.oscOpacity);
    }

    public static void saveProfile(final VirtualController controller,
                                   final Context context) {
        saveProfile(controller, context, getCurrentProfileName(context));
    }

    public static void saveProfile(final VirtualController controller,
                                   final Context context,
                                   final String profileName) {
        String prefName = getPrefName(profileName);
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(prefName, Activity.MODE_PRIVATE).edit();
        
        // Track which default elements are present
        HashSet<Integer> presentIds = new HashSet<>();
        JSONArray customIds = new JSONArray();

        for (VirtualControllerElement element : controller.getElements()) {

            presentIds.add(element.elementId);
            String prefKey = ""+element.elementId;
            try {
                JSONObject config = element.getConfiguration();
                config.put("HIDDEN", false);
                prefEditor.putString(prefKey, config.toString());
                if (element.elementId >= 100) {
                    customIds.put(element.elementId);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Mark missing default elements (1-16) as HIDDEN so they don't reappear
        for (int i = 1; i <= 16; i++) {
            if (!presentIds.contains(i)) {
                try {
                    JSONObject config = new JSONObject();
                    config.put("HIDDEN", true);
                    prefEditor.putString(""+i, config.toString());
                } catch (JSONException e) {}
            }
        }

        prefEditor.putString("CUSTOM_IDS", customIds.toString());
        prefEditor.apply();
        
        if (!profileName.equals("Default")) {
            addProfileToList(context, profileName);
        }
        setCurrentProfileName(context, profileName);
    }

    public static void saveElement(final VirtualControllerElement element, final Context context) {
        saveElement(element, context, getCurrentProfileName(context));
    }

    public static void saveElement(final VirtualControllerElement element, final Context context, final String profileName) {
        String prefName = getPrefName(profileName);
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(prefName, Activity.MODE_PRIVATE).edit();
        try {
            JSONObject config = element.getConfiguration();
            config.put("HIDDEN", false);
            prefEditor.putString(""+element.elementId, config.toString());
            
            // If it's a custom element, ensure it's in the CUSTOM_IDS list
            if (element.elementId >= 100) {
                SharedPreferences pref = context.getSharedPreferences(prefName, Activity.MODE_PRIVATE);
                String customIdsJson = pref.getString("CUSTOM_IDS", "[]");
                JSONArray customIds = new JSONArray(customIdsJson);
                boolean exists = false;
                for (int i = 0; i < customIds.length(); i++) {
                    if (customIds.getInt(i) == element.elementId) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    customIds.put(element.elementId);
                    prefEditor.putString("CUSTOM_IDS", customIds.toString());
                }
            }
            prefEditor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void loadFromPreferences(final VirtualController controller, final Context context) {
        loadFromPreferences(controller, context, getCurrentProfileName(context));
    }

    public static void loadFromPreferences(final VirtualController controller, final Context context, final String profileName) {
        String prefName = getPrefName(profileName);
        SharedPreferences pref = context.getSharedPreferences(prefName, Activity.MODE_PRIVATE);

        // Track IDs we've already loaded
        Set<Integer> loadedIds = new HashSet<>();
        List<VirtualControllerElement> toRemove = new ArrayList<>();

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;
            loadedIds.add(element.elementId);

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    JSONObject config = new JSONObject(jsonConfig);
                    if (config.optBoolean("HIDDEN", false)) {
                        toRemove.add(element);
                    } else {
                        element.loadConfiguration(config);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    pref.edit().remove(prefKey).apply();
                }
            }
        }

        for (VirtualControllerElement element : toRemove) {
            controller.removeElement(element);
        }

        // Load custom elements
        String customIdsJson = pref.getString("CUSTOM_IDS", "[]");
        try {
            JSONArray customIds = new JSONArray(customIdsJson);
            for (int i = 0; i < customIds.length(); i++) {
                int id = customIds.getInt(i);
                if (loadedIds.contains(id)) continue;

                String jsonConfig = pref.getString(""+id, null);
                if (jsonConfig != null) {
                    JSONObject config = new JSONObject(jsonConfig);
                    if (config.optBoolean("HIDDEN", false)) continue;

                    VirtualControllerElement element;
                    String type = config.optString("TYPE", "");
                    if (type.equals("DigitalPad")) {
                        element = new DigitalPad(controller, context, id);
                    } else if (type.equals("LeftAnalogStick")) {
                        element = new LeftAnalogStick(controller, context, id);
                    } else if (type.equals("RightAnalogStick")) {
                        element = new RightAnalogStick(controller, context);
                        setElementId(element, id);
                    } else if (type.equals("LeftTrigger")) {
                        element = new LeftTrigger(controller, 1, context);
                        setElementId(element, id);
                    } else if (type.equals("RightTrigger")) {
                        element = new RightTrigger(controller, 1, context);
                        setElementId(element, id);
                    } else {
                        element = createDigitalButton(id, 0, 0, 1, "Btn", -1, controller, context);
                    }
                    controller.addElement(element, 0, 0, 100, 100);
                    element.loadConfiguration(config);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void setElementId(VirtualControllerElement element, int id) {
        try {
            java.lang.reflect.Field f = VirtualControllerElement.class.getDeclaredField("elementId");
            f.setAccessible(true);
            f.set(element, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
