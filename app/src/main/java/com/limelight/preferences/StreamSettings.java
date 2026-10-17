package com.limelight.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Range;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.limelight.LimeLog;
import com.limelight.PcView;
import com.mooncake.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;

import java.lang.reflect.Method;
import java.util.Arrays;

public class StreamSettings extends Activity {
    public static final int REQUEST_PICK_LANDSCAPE_IMAGE = 3001;
    public static final int REQUEST_CROP_LANDSCAPE_IMAGE = 3002;
    public static final int REQUEST_PICK_PORTRAIT_IMAGE = 3003;
    public static final int REQUEST_CROP_PORTRAIT_IMAGE = 3004;
    public static final int REQUEST_PICK_PC_CARD_GRID_LANDSCAPE_IMAGE = 3005;
    public static final int REQUEST_PICK_PC_CARD_GRID_PORTRAIT_IMAGE = 3006;
    public static final int REQUEST_PICK_PC_CARD_LIST_LANDSCAPE_IMAGE = 3007;
    public static final int REQUEST_PICK_PC_CARD_LIST_PORTRAIT_IMAGE = 3008;
    public static final int REQUEST_PICK_STREAMING_WINDOW_IMAGE = 3009;
    public static final int REQUEST_PICK_VIRTUAL_CONTROLLER_IMAGE = 3010;

    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    private OnBackInvokedCallback onBackInvokedCallback;

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
        UiHelper.updateLastActivity(this);
        UiHelper.setupBottomNav(this, 1);
        hideSystemUi();
    }

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, new SettingsFragment()
        ).commitAllowingStateLoss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback = this::onBackPressed;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBackInvokedCallback);
        }

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);

        UiHelper.notifyNewRootView(this);

        UiHelper.setupBottomNav(this, 1);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (onBackInvokedCallback != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(onBackInvokedCallback);
            }
        }
        super.onDestroy();
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        if (isFinishing()) {
            return;
        }

        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
            if (!newPrefs.language.equals(previousPrefs.language)) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            }
        }
    }

    public void copyUriToFile(android.net.Uri uri, String targetFileName) {
        try {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            try (java.io.InputStream is = getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(new java.io.File(getFilesDir(), targetFileName))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                if ("custom_pc_card_bg_grid_landscape.jpg".equals(targetFileName) || "custom_pc_card_bg_grid_portrait.jpg".equals(targetFileName) || "custom_pc_card_bg_grid.jpg".equals(targetFileName)) {
                    prefs.edit().remove("theme_pc_card_color_grid").apply();
                } else if ("custom_pc_card_bg_list_landscape.jpg".equals(targetFileName) || "custom_pc_card_bg_list_portrait.jpg".equals(targetFileName) || "custom_pc_card_bg_list.jpg".equals(targetFileName)) {
                    prefs.edit().remove("theme_pc_card_color_list").apply();
                } else if ("custom_streaming_window_bg.jpg".equals(targetFileName)) {
                    prefs.edit().remove("theme_streaming_window_color").apply();
                }

                Toast.makeText(this, "Background updated successfully!", Toast.LENGTH_SHORT).show();
                UiHelper.applyCustomThemeBackground(this);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to update background: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        if ((requestCode == REQUEST_PICK_LANDSCAPE_IMAGE || requestCode == REQUEST_PICK_PORTRAIT_IMAGE ||
             requestCode == REQUEST_PICK_PC_CARD_GRID_LANDSCAPE_IMAGE || requestCode == REQUEST_PICK_PC_CARD_GRID_PORTRAIT_IMAGE ||
             requestCode == REQUEST_PICK_PC_CARD_LIST_LANDSCAPE_IMAGE || requestCode == REQUEST_PICK_PC_CARD_LIST_PORTRAIT_IMAGE ||
             requestCode == REQUEST_PICK_STREAMING_WINDOW_IMAGE || requestCode == REQUEST_PICK_VIRTUAL_CONTROLLER_IMAGE) && data != null && data.getData() != null) {
            android.net.Uri sourceUri = data.getData();

            String targetFileName;
            if (requestCode == REQUEST_PICK_PC_CARD_GRID_LANDSCAPE_IMAGE) {
                targetFileName = "custom_pc_card_bg_grid_landscape.jpg";
            } else if (requestCode == REQUEST_PICK_PC_CARD_GRID_PORTRAIT_IMAGE) {
                targetFileName = "custom_pc_card_bg_grid_portrait.jpg";
            } else if (requestCode == REQUEST_PICK_PC_CARD_LIST_LANDSCAPE_IMAGE) {
                targetFileName = "custom_pc_card_bg_list_landscape.jpg";
            } else if (requestCode == REQUEST_PICK_PC_CARD_LIST_PORTRAIT_IMAGE) {
                targetFileName = "custom_pc_card_bg_list_portrait.jpg";
            } else if (requestCode == REQUEST_PICK_STREAMING_WINDOW_IMAGE) {
                targetFileName = "custom_streaming_window_bg.jpg";
            } else if (requestCode == REQUEST_PICK_VIRTUAL_CONTROLLER_IMAGE) {
                targetFileName = "custom_virtual_controller_bg.jpg";
            } else if (requestCode == REQUEST_PICK_LANDSCAPE_IMAGE) {
                targetFileName = "custom_theme_bg_landscape.jpg";
            } else {
                targetFileName = "custom_theme_bg_portrait.jpg";
            }

            copyUriToFile(sourceUri, targetFileName);
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false);
        }

        private void addNativeFrameRateEntry(float framerate) {
            int frameRateRounded = Math.round(framerate);
            if (frameRateRounded == 0) {
                return;
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = Integer.toString(frameRateRounded);
            String fpsName = getResources().getString(R.string.resolution_prefix_native) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(res, fps))
                    .apply();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            UiHelper.applyStatusBarPadding(view);
            return view;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();

            // hide on-screen controls category on non touch screen devices
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_onscreen_controls");
                screen.removePreference(category);
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_absolute_mouse_mode"));
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback"));
            }

            // Hide USB driver options on devices without USB host support
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getActivity().getPackageManager().hasSystemFeature("android.software.picture_in_picture") ||
                    getActivity().getPackageManager().hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                screen.removePreference(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                category_gamepad_settings.removePreference(findPreference("checkbox_vibrate_fallback"));
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = (PreferenceCategory) findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl() ) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
            }

            Display display = getActivity().getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(getContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    final Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Ensure the activity is still open when this timeout expires
                            StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                            if (settingsActivity != null) {
                                settingsActivity.reloadSettings();
                            }
                        }
                    }, 500);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_advanced_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    CheckBoxPreference hdrPref = (CheckBoxPreference) category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            findPreference(PreferenceConfiguration.IMMERSIVE_MODE_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // We need to wait for the preference to be written before applying
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() != null) {
                                UiHelper.applyImmersiveMode(getActivity());
                            }
                        }
                    });
                    return true;
                }
            });

            CheckBoxPreference chkTransparentHeader = (CheckBoxPreference) findPreference("checkbox_transparent_header");
            Preference btnChangeHeaderColor = findPreference("button_change_header_color");

            if (chkTransparentHeader != null && btnChangeHeaderColor != null) {
                btnChangeHeaderColor.setEnabled(!chkTransparentHeader.isChecked());

                chkTransparentHeader.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isTransparent = (Boolean) newValue;
                    btnChangeHeaderColor.setEnabled(!isTransparent);
                    new Handler().post(() -> {
                        if (getActivity() != null) {
                            UiHelper.applyHeaderTheme(getActivity());
                        }
                    });
                    return true;
                });
            }

            if (btnChangeHeaderColor != null) {
                btnChangeHeaderColor.setOnPreferenceClickListener(preference -> {
                    showColorPicker("Header Color Picker", "theme_header_color", () -> {
                        if (getActivity() != null) UiHelper.applyHeaderTheme(getActivity());
                    });
                    return true;
                });
            }

            CheckBoxPreference chkTransparentNav = (CheckBoxPreference) findPreference("checkbox_transparent_nav");
            Preference btnChangeNavColor = findPreference("button_change_nav_color");

            if (chkTransparentNav != null && btnChangeNavColor != null) {
                btnChangeNavColor.setEnabled(!chkTransparentNav.isChecked());

                chkTransparentNav.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isTransparent = (Boolean) newValue;
                    btnChangeNavColor.setEnabled(!isTransparent);
                    new Handler().post(() -> {
                        if (getActivity() != null) {
                            UiHelper.applyNavTheme(getActivity());
                        }
                    });
                    return true;
                });
            }

            if (btnChangeNavColor != null) {
                btnChangeNavColor.setOnPreferenceClickListener(preference -> {
                    showColorPicker("Bottom Navigation Color Picker", "theme_nav_color", () -> {
                        if (getActivity() != null) UiHelper.applyNavTheme(getActivity());
                    });
                    return true;
                });
            }

            Preference btnThemesLandscape = findPreference("Button_themes_landscape");
            if (btnThemesLandscape != null) {
                btnThemesLandscape.setOnPreferenceClickListener(preference -> {
                    openImagePicker(REQUEST_PICK_LANDSCAPE_IMAGE);
                    return true;
                });
            }

            Preference btnThemesPortrait = findPreference("Button_themes_portrait");
            if (btnThemesPortrait != null) {
                btnThemesPortrait.setOnPreferenceClickListener(preference -> {
                    openImagePicker(REQUEST_PICK_PORTRAIT_IMAGE);
                    return true;
                });
            }

            Preference btnZoomLandscape = findPreference("preference_zoom_landscape");
            if (btnZoomLandscape != null) {
                btnZoomLandscape.setOnPreferenceClickListener(preference -> {
                    showZoomDialog("Landscape Background Zoom", "theme_zoom_landscape");
                    return true;
                });
            }

            Preference btnZoomPortrait = findPreference("preference_zoom_portrait");
            if (btnZoomPortrait != null) {
                btnZoomPortrait.setOnPreferenceClickListener(preference -> {
                    showZoomDialog("Portrait Background Zoom", "theme_zoom_portrait");
                    return true;
                });
            }

            Preference btnStretchLandscape = findPreference("preference_stretch_landscape");
            if (btnStretchLandscape != null) {
                btnStretchLandscape.setOnPreferenceClickListener(preference -> {
                    showStretchDialog("Landscape Stretch / Compress", "theme_stretch_x_landscape", "theme_stretch_y_landscape", "theme_stretch_x2_landscape", "theme_stretch_y2_landscape");
                    return true;
                });
            }

            Preference btnStretchPortrait = findPreference("preference_stretch_portrait");
            if (btnStretchPortrait != null) {
                btnStretchPortrait.setOnPreferenceClickListener(preference -> {
                    showStretchDialog("Portrait Stretch / Compress", "theme_stretch_x_portrait", "theme_stretch_y_portrait", "theme_stretch_x2_portrait", "theme_stretch_y2_portrait");
                    return true;
                });
            }

            Preference btnCardBgGridLandscape = findPreference("preference_pc_card_background_grid_landscape");
            if (btnCardBgGridLandscape != null) {
                btnCardBgGridLandscape.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        String[] options = {"Option 1: Color Picker", "Option 2: Custom Image"};
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Change PC Cards Background (Grid Mode - Landscape)")
                                .setItems(options, (dialog, which) -> {
                                    if (which == 0) {
                                        showColorPicker("PC Card Color Picker (Grid Mode)", "theme_pc_card_color_grid", () -> {
                                            if (getActivity() != null) {
                                                java.io.File bg1 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_landscape.jpg");
                                                if (bg1.exists()) bg1.delete();
                                                java.io.File bg2 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_portrait.jpg");
                                                if (bg2.exists()) bg2.delete();
                                                java.io.File bg3 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid.jpg");
                                                if (bg3.exists()) bg3.delete();
                                            }
                                        });
                                    } else {
                                        openImagePicker(REQUEST_PICK_PC_CARD_GRID_LANDSCAPE_IMAGE);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            Preference btnCardBgGridPortrait = findPreference("preference_pc_card_background_grid_portrait");
            if (btnCardBgGridPortrait != null) {
                btnCardBgGridPortrait.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        String[] options = {"Option 1: Color Picker", "Option 2: Custom Image"};
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Change PC Cards Background (Grid Mode - Portrait)")
                                .setItems(options, (dialog, which) -> {
                                    if (which == 0) {
                                        showColorPicker("PC Card Color Picker (Grid Mode)", "theme_pc_card_color_grid", () -> {
                                            if (getActivity() != null) {
                                                java.io.File bg1 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_landscape.jpg");
                                                if (bg1.exists()) bg1.delete();
                                                java.io.File bg2 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_portrait.jpg");
                                                if (bg2.exists()) bg2.delete();
                                                java.io.File bg3 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid.jpg");
                                                if (bg3.exists()) bg3.delete();
                                            }
                                        });
                                    } else {
                                        openImagePicker(REQUEST_PICK_PC_CARD_GRID_PORTRAIT_IMAGE);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            Preference btnCardBgListLandscape = findPreference("preference_pc_card_background_list_landscape");
            if (btnCardBgListLandscape != null) {
                btnCardBgListLandscape.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        String[] options = {"Option 1: Color Picker", "Option 2: Custom Image"};
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Change PC Cards Background (List Mode - Landscape)")
                                .setItems(options, (dialog, which) -> {
                                    if (which == 0) {
                                        showColorPicker("PC Card Color Picker (List Mode)", "theme_pc_card_color_list", () -> {
                                            if (getActivity() != null) {
                                                java.io.File bg1 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_landscape.jpg");
                                                if (bg1.exists()) bg1.delete();
                                                java.io.File bg2 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_portrait.jpg");
                                                if (bg2.exists()) bg2.delete();
                                                java.io.File bg3 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list.jpg");
                                                if (bg3.exists()) bg3.delete();
                                            }
                                        });
                                    } else {
                                        openImagePicker(REQUEST_PICK_PC_CARD_LIST_LANDSCAPE_IMAGE);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            Preference btnCardBgListPortrait = findPreference("preference_pc_card_background_list_portrait");
            if (btnCardBgListPortrait != null) {
                btnCardBgListPortrait.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        String[] options = {"Option 1: Color Picker", "Option 2: Custom Image"};
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Change PC Cards Background (List Mode - Portrait)")
                                .setItems(options, (dialog, which) -> {
                                    if (which == 0) {
                                        showColorPicker("PC Card Color Picker (List Mode)", "theme_pc_card_color_list", () -> {
                                            if (getActivity() != null) {
                                                java.io.File bg1 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_landscape.jpg");
                                                if (bg1.exists()) bg1.delete();
                                                java.io.File bg2 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_portrait.jpg");
                                                if (bg2.exists()) bg2.delete();
                                                java.io.File bg3 = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list.jpg");
                                                if (bg3.exists()) bg3.delete();
                                            }
                                        });
                                    } else {
                                        openImagePicker(REQUEST_PICK_PC_CARD_LIST_PORTRAIT_IMAGE);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            Preference btnCardTransGridLandscape = findPreference("preference_pc_card_transparency_grid_landscape");
            if (btnCardTransGridLandscape != null) {
                btnCardTransGridLandscape.setOnPreferenceClickListener(preference -> {
                    showCardTransparencyDialog("PC Card Transparency (Grid - Landscape)", "theme_pc_card_transparency_grid_landscape");
                    return true;
                });
            }

            Preference btnCardTransGridPortrait = findPreference("preference_pc_card_transparency_grid_portrait");
            if (btnCardTransGridPortrait != null) {
                btnCardTransGridPortrait.setOnPreferenceClickListener(preference -> {
                    showCardTransparencyDialog("PC Card Transparency (Grid - Portrait)", "theme_pc_card_transparency_grid_portrait");
                    return true;
                });
            }

            Preference btnCardTransListLandscape = findPreference("preference_pc_card_transparency_list_landscape");
            if (btnCardTransListLandscape != null) {
                btnCardTransListLandscape.setOnPreferenceClickListener(preference -> {
                    showCardTransparencyDialog("PC Card Transparency (List - Landscape)", "theme_pc_card_transparency_list_landscape");
                    return true;
                });
            }

            Preference btnCardTransListPortrait = findPreference("preference_pc_card_transparency_list_portrait");
            if (btnCardTransListPortrait != null) {
                btnCardTransListPortrait.setOnPreferenceClickListener(preference -> {
                    showCardTransparencyDialog("PC Card Transparency (List - Portrait)", "theme_pc_card_transparency_list_portrait");
                    return true;
                });
            }

            Preference btnCardCornersGridLandscape = findPreference("preference_pc_card_corners_grid_landscape");
            if (btnCardCornersGridLandscape != null) {
                btnCardCornersGridLandscape.setOnPreferenceClickListener(preference -> {
                    showCardCornersDialog("PC Card Corners (Grid - Landscape)", "theme_pc_card_corner_radius_grid_landscape");
                    return true;
                });
            }

            Preference btnCardCornersGridPortrait = findPreference("preference_pc_card_corners_grid_portrait");
            if (btnCardCornersGridPortrait != null) {
                btnCardCornersGridPortrait.setOnPreferenceClickListener(preference -> {
                    showCardCornersDialog("PC Card Corners (Grid - Portrait)", "theme_pc_card_corner_radius_grid_portrait");
                    return true;
                });
            }

            Preference btnCardCornersListLandscape = findPreference("preference_pc_card_corners_list_landscape");
            if (btnCardCornersListLandscape != null) {
                btnCardCornersListLandscape.setOnPreferenceClickListener(preference -> {
                    showCardCornersDialog("PC Card Corners (List - Landscape)", "theme_pc_card_corner_radius_list_landscape");
                    return true;
                });
            }

            Preference btnCardCornersListPortrait = findPreference("preference_pc_card_corners_list_portrait");
            if (btnCardCornersListPortrait != null) {
                btnCardCornersListPortrait.setOnPreferenceClickListener(preference -> {
                    showCardCornersDialog("PC Card Corners (List - Portrait)", "theme_pc_card_corner_radius_list_portrait");
                    return true;
                });
            }

            Preference btnTemplateGrid = findPreference("preference_download_template_grid");
            if (btnTemplateGrid != null) {
                btnTemplateGrid.setOnPreferenceClickListener(preference -> {
                    showTemplateCustomizationDialog(0);
                    return true;
                });
            }

            Preference btnTemplateListLandscape = findPreference("preference_download_template_list_landscape");
            if (btnTemplateListLandscape != null) {
                btnTemplateListLandscape.setOnPreferenceClickListener(preference -> {
                    showTemplateCustomizationDialog(1);
                    return true;
                });
            }

            Preference btnTemplateListPortrait = findPreference("preference_download_template_list_portrait");
            if (btnTemplateListPortrait != null) {
                btnTemplateListPortrait.setOnPreferenceClickListener(preference -> {
                    showTemplateCustomizationDialog(2);
                    return true;
                });
            }

            Preference btnStreamingBg = findPreference("button_streaming_window_background");
            if (btnStreamingBg != null) {
                btnStreamingBg.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        String[] options = {"Option 1: Color Picker", "Option 2: Custom Image"};
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Streaming Window Background")
                                .setItems(options, (dialog, which) -> {
                                    if (which == 0) {
                                        showColorPicker("Streaming Window Color", "theme_streaming_window_color", () -> {
                                            if (getActivity() != null) {
                                                java.io.File customStreamBg = new java.io.File(getActivity().getFilesDir(), "custom_streaming_window_bg.jpg");
                                                if (customStreamBg.exists()) customStreamBg.delete();
                                            }
                                        });
                                    } else {
                                        openImagePicker(REQUEST_PICK_STREAMING_WINDOW_IMAGE);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            Preference btnZoomStreaming = findPreference("preference_zoom_streaming");
            if (btnZoomStreaming != null) {
                btnZoomStreaming.setOnPreferenceClickListener(preference -> {
                    showZoomDialog("Streaming Background Zoom", "theme_zoom_streaming");
                    return true;
                });
            }

            Preference btnStretchStreaming = findPreference("preference_stretch_streaming");
            if (btnStretchStreaming != null) {
                btnStretchStreaming.setOnPreferenceClickListener(preference -> {
                    showStretchDialog("Streaming Stretch / Compress", "theme_stretch_x_streaming", "theme_stretch_y_streaming", "theme_stretch_x2_streaming", "theme_stretch_y2_streaming");
                    return true;
                });
            }

            CheckBoxPreference chkStretchVideo = (CheckBoxPreference) findPreference("checkbox_stretch_video");
            Preference btnStretchStreamScreen = findPreference("preference_stretch_stream_screen");
            if (btnStretchStreamScreen != null) {
                if (chkStretchVideo != null) {
                    btnStretchStreamScreen.setEnabled(!chkStretchVideo.isChecked());
                    chkStretchVideo.setOnPreferenceChangeListener((preference, newValue) -> {
                        boolean isChecked = (Boolean) newValue;
                        btnStretchStreamScreen.setEnabled(!isChecked);
                        return true;
                    });
                }
                btnStretchStreamScreen.setOnPreferenceClickListener(preference -> {
                    showStretchDialog("Streaming Screen Stretch / Compress", "theme_stretch_x_stream_screen", "theme_stretch_y_stream_screen", "theme_stretch_x2_stream_screen", "theme_stretch_y2_stream_screen");
                    return true;
                });
            }

            Preference btnTemplateStreaming = findPreference("preference_download_template_streaming");
            if (btnTemplateStreaming != null) {
                btnTemplateStreaming.setOnPreferenceClickListener(preference -> {
                    showStreamingTemplateCustomizationDialog();
                    return true;
                });
            }

            Preference btnVcBg = findPreference("button_virtual_controller_background");
            if (btnVcBg != null) {
                btnVcBg.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        String[] options = {"Option 1: Color Picker", "Option 2: Custom Image"};
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Virtual Controller Background")
                                .setItems(options, (dialog, which) -> {
                                    if (which == 0) {
                                        showColorPicker("Virtual Controller Color", "theme_virtual_controller_color", () -> {
                                            if (getActivity() != null) {
                                                java.io.File customVcBg = new java.io.File(getActivity().getFilesDir(), "custom_virtual_controller_bg.jpg");
                                                if (customVcBg.exists()) customVcBg.delete();
                                            }
                                        });
                                    } else {
                                        openImagePicker(REQUEST_PICK_VIRTUAL_CONTROLLER_IMAGE);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            Preference btnZoomVc = findPreference("preference_zoom_virtual_controller");
            if (btnZoomVc != null) {
                btnZoomVc.setOnPreferenceClickListener(preference -> {
                    showZoomDialog("Virtual Controller Background Zoom", "theme_zoom_virtual_controller");
                    return true;
                });
            }

            Preference btnStretchVc = findPreference("preference_stretch_virtual_controller");
            if (btnStretchVc != null) {
                btnStretchVc.setOnPreferenceClickListener(preference -> {
                    showStretchDialog("Virtual Controller Stretch / Compress", "theme_stretch_x_virtual_controller", "theme_stretch_y_virtual_controller", "theme_stretch_x2_virtual_controller", "theme_stretch_y2_virtual_controller");
                    return true;
                });
            }

            // Category Revert Buttons
            Preference btnRevertHeader = findPreference("button_revert_header_themes");
            if (btnRevertHeader != null) {
                btnRevertHeader.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        CheckBoxPreference chkHeader = (CheckBoxPreference) findPreference("checkbox_transparent_header");
                        Preference btnHeaderColor = findPreference("button_change_header_color");
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit().remove("theme_header_color").remove("checkbox_transparent_header").apply();
                        if (chkHeader != null) chkHeader.setChecked(false);
                        if (btnHeaderColor != null) btnHeaderColor.setEnabled(true);
                        Toast.makeText(getActivity(), "Header themes reverted to default!", Toast.LENGTH_SHORT).show();
                        UiHelper.applyCustomThemeBackground(getActivity());
                    }
                    return true;
                });
            }

            Preference btnRevertNav = findPreference("button_revert_nav_themes");
            if (btnRevertNav != null) {
                btnRevertNav.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        CheckBoxPreference chkNav = (CheckBoxPreference) findPreference("checkbox_transparent_nav");
                        Preference btnNavColor = findPreference("button_change_nav_color");
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit().remove("theme_nav_color").remove("checkbox_transparent_nav").apply();
                        if (chkNav != null) chkNav.setChecked(false);
                        if (btnNavColor != null) btnNavColor.setEnabled(true);
                        Toast.makeText(getActivity(), "Navigation bar themes reverted to default!", Toast.LENGTH_SHORT).show();
                        UiHelper.applyCustomThemeBackground(getActivity());
                    }
                    return true;
                });
            }

            Preference btnRevertAppBg = findPreference("button_revert_app_background_themes");
            if (btnRevertAppBg != null) {
                btnRevertAppBg.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        java.io.File customBg = new java.io.File(getActivity().getFilesDir(), "custom_theme_bg.jpg");
                        if (customBg.exists()) customBg.delete();
                        java.io.File customBgLandscape = new java.io.File(getActivity().getFilesDir(), "custom_theme_bg_landscape.jpg");
                        if (customBgLandscape.exists()) customBgLandscape.delete();
                        java.io.File customBgPortrait = new java.io.File(getActivity().getFilesDir(), "custom_theme_bg_portrait.jpg");
                        if (customBgPortrait.exists()) customBgPortrait.delete();

                        CheckBoxPreference chkAspectLandscape = (CheckBoxPreference) findPreference("checkbox_keep_aspect_ratio_landscape");
                        CheckBoxPreference chkAspectPortrait = (CheckBoxPreference) findPreference("checkbox_keep_aspect_ratio_portrait");

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .remove("checkbox_keep_aspect_ratio_landscape")
                                .remove("checkbox_keep_aspect_ratio_portrait")
                                .remove("theme_zoom_landscape")
                                .remove("theme_zoom_portrait")
                                .remove("theme_stretch_x_landscape")
                                .remove("theme_stretch_y_landscape")
                                .remove("theme_stretch_x2_landscape")
                                .remove("theme_stretch_y2_landscape")
                                .remove("theme_stretch_x_portrait")
                                .remove("theme_stretch_y_portrait")
                                .remove("theme_stretch_x2_portrait")
                                .remove("theme_stretch_y2_portrait")
                                .apply();
                        if (chkAspectLandscape != null) chkAspectLandscape.setChecked(false);
                        if (chkAspectPortrait != null) chkAspectPortrait.setChecked(false);
                        Toast.makeText(getActivity(), "App background themes reverted to default!", Toast.LENGTH_SHORT).show();
                        UiHelper.applyCustomThemeBackground(getActivity());
                    }
                    return true;
                });
            }

            Preference btnRevertPcCard = findPreference("button_revert_pc_card_themes");
            if (btnRevertPcCard != null) {
                btnRevertPcCard.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        java.io.File customCardGridBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid.jpg");
                        if (customCardGridBg.exists()) customCardGridBg.delete();
                        java.io.File customCardGridLandscapeBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_landscape.jpg");
                        if (customCardGridLandscapeBg.exists()) customCardGridLandscapeBg.delete();
                        java.io.File customCardGridPortraitBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_portrait.jpg");
                        if (customCardGridPortraitBg.exists()) customCardGridPortraitBg.delete();
                        java.io.File customCardListBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list.jpg");
                        if (customCardListBg.exists()) customCardListBg.delete();
                        java.io.File customCardListLandscapeBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_landscape.jpg");
                        if (customCardListLandscapeBg.exists()) customCardListLandscapeBg.delete();
                        java.io.File customCardListPortraitBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_portrait.jpg");
                        if (customCardListPortraitBg.exists()) customCardListPortraitBg.delete();

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .remove("theme_pc_card_color_grid")
                                .remove("theme_pc_card_color_list")
                                .remove("theme_pc_card_transparency_grid")
                                .remove("theme_pc_card_transparency_grid_landscape")
                                .remove("theme_pc_card_transparency_grid_portrait")
                                .remove("theme_pc_card_transparency_list")
                                .remove("theme_pc_card_transparency_list_landscape")
                                .remove("theme_pc_card_transparency_list_portrait")
                                .remove("theme_pc_card_corner_radius_grid")
                                .remove("theme_pc_card_corner_radius_grid_landscape")
                                .remove("theme_pc_card_corner_radius_grid_portrait")
                                .remove("theme_pc_card_corner_radius_list")
                                .remove("theme_pc_card_corner_radius_list_landscape")
                                .remove("theme_pc_card_corner_radius_list_portrait")
                                .remove("theme_pc_card_corner_radius_list_portrait")
                                .apply();
                        Toast.makeText(getActivity(), "PC Card themes reverted to default!", Toast.LENGTH_SHORT).show();
                        UiHelper.applyCustomThemeBackground(getActivity());
                    }
                    return true;
                });
            }

            Preference btnRevertStreaming = findPreference("button_revert_streaming_themes");
            if (btnRevertStreaming != null) {
                btnRevertStreaming.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        java.io.File customStreamingBg = new java.io.File(getActivity().getFilesDir(), "custom_streaming_window_bg.jpg");
                        if (customStreamingBg.exists()) customStreamingBg.delete();

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .remove("theme_streaming_window_color")
                                .remove("theme_zoom_streaming")
                                .remove("theme_stretch_x_streaming")
                                .remove("theme_stretch_y_streaming")
                                .remove("theme_stretch_x2_streaming")
                                .remove("theme_stretch_y2_streaming")
                                .remove("theme_stretch_x_stream_screen")
                                .remove("theme_stretch_y_stream_screen")
                                .remove("theme_stretch_x2_stream_screen")
                                .remove("theme_stretch_y2_stream_screen")
                                .apply();
                        Toast.makeText(getActivity(), "Streaming themes reverted to default!", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }

            Preference btnRevertVc = findPreference("button_revert_virtual_controller_themes");
            if (btnRevertVc != null) {
                btnRevertVc.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        java.io.File customVcBg = new java.io.File(getActivity().getFilesDir(), "custom_virtual_controller_bg.jpg");
                        if (customVcBg.exists()) customVcBg.delete();

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .remove("theme_virtual_controller_color")
                                .remove("theme_zoom_virtual_controller")
                                .remove("theme_stretch_x_virtual_controller")
                                .remove("theme_stretch_y_virtual_controller")
                                .remove("theme_stretch_x2_virtual_controller")
                                .remove("theme_stretch_y2_virtual_controller")
                                .apply();
                        Toast.makeText(getActivity(), "Virtual Controller themes reverted to default!", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }

            Preference btnResetTheme = findPreference("button_reset_theme");
            if (btnResetTheme != null) {
                btnResetTheme.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        java.io.File customBg = new java.io.File(getActivity().getFilesDir(), "custom_theme_bg.jpg");
                        if (customBg.exists()) customBg.delete();
                        java.io.File customBgLandscape = new java.io.File(getActivity().getFilesDir(), "custom_theme_bg_landscape.jpg");
                        if (customBgLandscape.exists()) customBgLandscape.delete();
                        java.io.File customBgPortrait = new java.io.File(getActivity().getFilesDir(), "custom_theme_bg_portrait.jpg");
                        if (customBgPortrait.exists()) customBgPortrait.delete();
                        java.io.File customCardGridBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid.jpg");
                        if (customCardGridBg.exists()) customCardGridBg.delete();
                        java.io.File customCardGridLandscapeBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_landscape.jpg");
                        if (customCardGridLandscapeBg.exists()) customCardGridLandscapeBg.delete();
                        java.io.File customCardGridPortraitBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_grid_portrait.jpg");
                        if (customCardGridPortraitBg.exists()) customCardGridPortraitBg.delete();
                        java.io.File customCardListBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list.jpg");
                        if (customCardListBg.exists()) customCardListBg.delete();
                        java.io.File customCardListLandscapeBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_landscape.jpg");
                        if (customCardListLandscapeBg.exists()) customCardListLandscapeBg.delete();
                        java.io.File customCardListPortraitBg = new java.io.File(getActivity().getFilesDir(), "custom_pc_card_bg_list_portrait.jpg");
                        if (customCardListPortraitBg.exists()) customCardListPortraitBg.delete();
                        java.io.File customStreamingBg = new java.io.File(getActivity().getFilesDir(), "custom_streaming_window_bg.jpg");
                        if (customStreamingBg.exists()) customStreamingBg.delete();

                        CheckBoxPreference chkAspectLandscape = (CheckBoxPreference) findPreference("checkbox_keep_aspect_ratio_landscape");
                        CheckBoxPreference chkAspectPortrait = (CheckBoxPreference) findPreference("checkbox_keep_aspect_ratio_portrait");

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .remove("theme_header_color")
                                .remove("checkbox_transparent_header")
                                .remove("theme_nav_color")
                                .remove("checkbox_transparent_nav")
                                .remove("checkbox_keep_aspect_ratio_landscape")
                                .remove("checkbox_keep_aspect_ratio_portrait")
                                .remove("theme_zoom_landscape")
                                .remove("theme_zoom_portrait")
                                .remove("theme_stretch_x_landscape")
                                .remove("theme_stretch_y_landscape")
                                .remove("theme_stretch_x2_landscape")
                                .remove("theme_stretch_y2_landscape")
                                .remove("theme_stretch_x_portrait")
                                .remove("theme_stretch_y_portrait")
                                .remove("theme_stretch_x2_portrait")
                                .remove("theme_stretch_y2_portrait")
                                .remove("theme_streaming_window_color")
                                .remove("theme_zoom_streaming")
                                .remove("theme_stretch_x_streaming")
                                .remove("theme_stretch_y_streaming")
                                .remove("theme_stretch_x2_streaming")
                                .remove("theme_stretch_y2_streaming")
                                .remove("theme_stretch_x_stream_screen")
                                .remove("theme_stretch_y_stream_screen")
                                .remove("theme_stretch_x2_stream_screen")
                                .remove("theme_stretch_y2_stream_screen")
                                .remove("theme_pc_card_color_grid")
                                .remove("theme_pc_card_color_list")
                                .remove("theme_pc_card_transparency_grid")
                                .remove("theme_pc_card_transparency_grid_landscape")
                                .remove("theme_pc_card_transparency_grid_portrait")
                                .remove("theme_pc_card_transparency_list")
                                .remove("theme_pc_card_transparency_list_landscape")
                                .remove("theme_pc_card_transparency_list_portrait")
                                .remove("theme_pc_card_corner_radius_grid")
                                .remove("theme_pc_card_corner_radius_grid_landscape")
                                .remove("theme_pc_card_corner_radius_grid_portrait")
                                .remove("theme_pc_card_corner_radius_list")
                                .remove("theme_pc_card_corner_radius_list_landscape")
                                .remove("theme_pc_card_corner_radius_list_portrait")
                                .remove("theme_pc_card_corner_radius_list_portrait")
                                .apply();
                        if (chkTransparentHeader != null) chkTransparentHeader.setChecked(false);
                        if (btnChangeHeaderColor != null) btnChangeHeaderColor.setEnabled(true);
                        if (chkTransparentNav != null) chkTransparentNav.setChecked(false);
                        if (btnChangeNavColor != null) btnChangeNavColor.setEnabled(true);
                        if (chkAspectLandscape != null) chkAspectLandscape.setChecked(false);
                        if (chkAspectPortrait != null) chkAspectPortrait.setChecked(false);

                        Toast.makeText(getActivity(), "Theme background & headers reset to default!", Toast.LENGTH_SHORT).show();
                        UiHelper.applyCustomThemeBackground(getActivity());
                    }
                    return true;
                });
            }
        }

        private void showColorPicker(String title, String prefKey, Runnable onApplied) {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int initialColor = prefs.getInt(prefKey, 0xFF0D001A);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);

            // Live Color Preview Box
            View previewView = new View(activity);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (50 * activity.getResources().getDisplayMetrics().density));
            previewParams.bottomMargin = pad;
            previewView.setLayoutParams(previewParams);
            previewView.setBackgroundColor(initialColor);
            container.addView(previewView);

            // Hex Input
            EditText hexInput = new EditText(activity);
            hexInput.setHint("#AARRGGBB");
            hexInput.setText(String.format("#%08X", initialColor));
            hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
            container.addView(hexInput);

            int a = Color.alpha(initialColor);
            int r = Color.red(initialColor);
            int g = Color.green(initialColor);
            int b = Color.blue(initialColor);

            TextView alphaLabel = new TextView(activity);
            SeekBar alphaSeek = new SeekBar(activity);
            alphaSeek.setMax(255);
            alphaSeek.setProgress(a);

            TextView redLabel = new TextView(activity);
            SeekBar redSeek = new SeekBar(activity);
            redSeek.setMax(255);
            redSeek.setProgress(r);

            TextView greenLabel = new TextView(activity);
            SeekBar greenSeek = new SeekBar(activity);
            greenSeek.setMax(255);
            greenSeek.setProgress(g);

            TextView blueLabel = new TextView(activity);
            SeekBar blueSeek = new SeekBar(activity);
            blueSeek.setMax(255);
            blueSeek.setProgress(b);

            final boolean[] isUpdating = {false};
            Runnable updateColor = () -> {
                if (isUpdating[0]) return;
                int color = Color.argb(alphaSeek.getProgress(), redSeek.getProgress(), greenSeek.getProgress(), blueSeek.getProgress());
                previewView.setBackgroundColor(color);
                alphaLabel.setText("Opacity: " + (alphaSeek.getProgress() * 100 / 255) + "%");
                redLabel.setText("Red: " + redSeek.getProgress());
                greenLabel.setText("Green: " + greenSeek.getProgress());
                blueLabel.setText("Blue: " + blueSeek.getProgress());
                isUpdating[0] = true;
                hexInput.setText(String.format("#%08X", color));
                isUpdating[0] = false;
            };

            SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateColor.run(); }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            };

            alphaSeek.setOnSeekBarChangeListener(listener);
            redSeek.setOnSeekBarChangeListener(listener);
            greenSeek.setOnSeekBarChangeListener(listener);
            blueSeek.setOnSeekBarChangeListener(listener);

            hexInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdating[0]) return;
                    try {
                        String hex = s.toString().trim();
                        if (!hex.startsWith("#")) hex = "#" + hex;
                        int parsed = Color.parseColor(hex);
                        isUpdating[0] = true;
                        alphaSeek.setProgress(Color.alpha(parsed));
                        redSeek.setProgress(Color.red(parsed));
                        greenSeek.setProgress(Color.green(parsed));
                        blueSeek.setProgress(Color.blue(parsed));
                        previewView.setBackgroundColor(parsed);
                        isUpdating[0] = false;
                    } catch (Exception ignored) {}
                }
            });

            container.addView(alphaLabel);
            container.addView(alphaSeek);
            container.addView(redLabel);
            container.addView(redSeek);
            container.addView(greenLabel);
            container.addView(greenSeek);
            container.addView(blueLabel);
            container.addView(blueSeek);

            updateColor.run();

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", (dialog, which) -> {
                        int currentColor = Color.argb(alphaSeek.getProgress(), redSeek.getProgress(), greenSeek.getProgress(), blueSeek.getProgress());
                        prefs.edit().putInt(prefKey, currentColor).apply();
                        if (onApplied != null) onApplied.run();
                        Toast.makeText(activity, "Updated successfully!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void openImagePicker(int requestCode) {
            Activity activity = getActivity();
            if (activity == null) return;

            try {
                Intent pickIntent = new Intent(Intent.ACTION_PICK);
                pickIntent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");

                Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
                getContentIntent.setType("image/*");

                Intent openDocIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                openDocIntent.addCategory(Intent.CATEGORY_OPENABLE);
                openDocIntent.setType("image/*");
                openDocIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                Intent chooserIntent = Intent.createChooser(pickIntent, "Open with...");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{getContentIntent, openDocIntent});

                activity.startActivityForResult(chooserIntent, requestCode);
            } catch (Exception e) {
                try {
                    Intent fallbackIntent = new Intent(Intent.ACTION_PICK);
                    fallbackIntent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                    activity.startActivityForResult(Intent.createChooser(fallbackIntent, "Open with..."), requestCode);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Toast.makeText(activity, "Error opening gallery", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void showZoomDialog(String title, String prefKey) {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int currentZoom = prefs.getInt(prefKey, 50);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);

            TextView label = new TextView(activity);
            label.setText("Zoom Level: " + currentZoom + " / 100");
            container.addView(label);

            SeekBar seekBar = new SeekBar(activity);
            seekBar.setMax(99);
            seekBar.setProgress(currentZoom - 1);
            container.addView(seekBar);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    label.setText("Zoom Level: " + (progress + 1) + " / 100");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", (dialog, which) -> {
                        int val = seekBar.getProgress() + 1;
                        prefs.edit().putInt(prefKey, val).apply();
                        UiHelper.applyCustomThemeBackground(activity);
                        Toast.makeText(activity, "Zoom updated to " + val, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showStretchDialog(String title, String prefX, String prefY, String prefX2, String prefY2) {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int curX = prefs.getInt(prefX, 0);
            int curY = prefs.getInt(prefY, 0);
            int curX2 = prefs.getInt(prefX2, 0);
            int curY2 = prefs.getInt(prefY2, 0);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);

            TextView labelX = new TextView(activity);
            SeekBar seekX = new SeekBar(activity);
            seekX.setMax(1000);
            seekX.setProgress(curX + 500);
            labelX.setText("X (Right/Down): " + curX);

            TextView labelY = new TextView(activity);
            SeekBar seekY = new SeekBar(activity);
            seekY.setMax(1000);
            seekY.setProgress(curY + 500);
            labelY.setText("Y (Right/Down): " + curY);

            TextView labelX2 = new TextView(activity);
            SeekBar seekX2 = new SeekBar(activity);
            seekX2.setMax(1000);
            seekX2.setProgress(curX2 + 500);
            labelX2.setText("X2 (Right/Up): " + curX2);

            TextView labelY2 = new TextView(activity);
            SeekBar seekY2 = new SeekBar(activity);
            seekY2.setMax(1000);
            seekY2.setProgress(curY2 + 500);
            labelY2.setText("Y2 (Right/Up): " + curY2);

            SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    labelX.setText("X (Right/Down): " + (seekX.getProgress() - 500));
                    labelY.setText("Y (Right/Down): " + (seekY.getProgress() - 500));
                    labelX2.setText("X2 (Right/Up): " + (seekX2.getProgress() - 500));
                    labelY2.setText("Y2 (Right/Up): " + (seekY2.getProgress() - 500));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            };

            seekX.setOnSeekBarChangeListener(listener);
            seekY.setOnSeekBarChangeListener(listener);
            seekX2.setOnSeekBarChangeListener(listener);
            seekY2.setOnSeekBarChangeListener(listener);

            container.addView(labelX);
            container.addView(seekX);
            container.addView(labelY);
            container.addView(seekY);
            container.addView(labelX2);
            container.addView(seekX2);
            container.addView(labelY2);
            container.addView(seekY2);

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", (dialog, which) -> {
                        prefs.edit()
                                .putInt(prefX, seekX.getProgress() - 500)
                                .putInt(prefY, seekY.getProgress() - 500)
                                .putInt(prefX2, seekX2.getProgress() - 500)
                                .putInt(prefY2, seekY2.getProgress() - 500)
                                .apply();
                        UiHelper.applyCustomThemeBackground(activity);
                        Toast.makeText(activity, "Stretch settings updated!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showCardTransparencyDialog(String title, String prefKey) {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int currentVal = prefs.getInt(prefKey, 100);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);

            TextView label = new TextView(activity);
            label.setText("Transparency / Opacity: " + currentVal + "%");
            container.addView(label);

            SeekBar seekBar = new SeekBar(activity);
            seekBar.setMax(100);
            seekBar.setProgress(currentVal);
            container.addView(seekBar);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    label.setText("Transparency / Opacity: " + progress + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", (dialog, which) -> {
                        prefs.edit().putInt(prefKey, seekBar.getProgress()).apply();
                        Toast.makeText(activity, "Card transparency updated!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showCardCornersDialog(String title, String prefKey) {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int currentVal = prefs.getInt(prefKey, 16);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);

            TextView label = new TextView(activity);
            label.setText("Corner Roundness: " + currentVal + " dp");
            container.addView(label);

            SeekBar seekBar = new SeekBar(activity);
            seekBar.setMax(50);
            seekBar.setProgress(currentVal);
            container.addView(seekBar);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    label.setText("Corner Roundness: " + progress + " dp");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", (dialog, which) -> {
                        prefs.edit().putInt(prefKey, seekBar.getProgress()).apply();
                        Toast.makeText(activity, "Card corner roundness updated!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showTemplateCustomizationDialog(int templateType) {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            String prefKey = (templateType == 0) ? "theme_pc_card_corner_radius_grid" : "theme_pc_card_corner_radius_list";
            int currentVal = prefs.getInt(prefKey, 16);

            ScrollView scrollView = new ScrollView(activity);
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);
            scrollView.addView(container);

            TextView cornerLabel = new TextView(activity);
            cornerLabel.setText("Corner Roundness: " + currentVal + " dp");
            container.addView(cornerLabel);

            SeekBar cornerSeek = new SeekBar(activity);
            cornerSeek.setMax(50);
            cornerSeek.setProgress(currentVal);
            container.addView(cornerSeek);

            cornerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    cornerLabel.setText("Corner Roundness: " + progress + " dp");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            android.widget.CheckBox gradientCheck = new android.widget.CheckBox(activity);
            gradientCheck.setText("Use Background Gradient");
            gradientCheck.setChecked(true);
            container.addView(gradientCheck);

            final int[] solidBgColor = {0xFF1A1A1A};
            android.widget.Button solidColorBtn = new android.widget.Button(activity);
            solidColorBtn.setText("Choose Solid Background Color");
            solidColorBtn.setVisibility(View.GONE);
            container.addView(solidColorBtn);

            gradientCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                solidColorBtn.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            });

            solidColorBtn.setOnClickListener(v -> {
                showColorPicker("Solid Card Background Color", "temp_card_solid_color", () -> {
                    solidBgColor[0] = prefs.getInt("temp_card_solid_color", 0xFF1A1A1A);
                    solidColorBtn.setBackgroundColor(solidBgColor[0]);
                });
            });

            android.widget.CheckBox outlineCheck = new android.widget.CheckBox(activity);
            outlineCheck.setText("Include Border Outline");
            outlineCheck.setChecked(true);
            container.addView(outlineCheck);

            TextView thicknessLabel = new TextView(activity);
            thicknessLabel.setText("Outline Thickness: 6 px");
            container.addView(thicknessLabel);

            SeekBar thicknessSeek = new SeekBar(activity);
            thicknessSeek.setMax(20);
            thicknessSeek.setProgress(6);
            container.addView(thicknessSeek);

            thicknessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    thicknessLabel.setText("Outline Thickness: " + Math.max(1, progress) + " px");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            final int[] outlineColorVal = {0xFF7B2CBF};
            android.widget.Button outlineColorBtn = new android.widget.Button(activity);
            outlineColorBtn.setText("Choose Outline Color");
            container.addView(outlineColorBtn);

            outlineColorBtn.setOnClickListener(v -> {
                showColorPicker("Outline Color", "temp_outline_color", () -> {
                    outlineColorVal[0] = prefs.getInt("temp_outline_color", 0xFF7B2CBF);
                    outlineColorBtn.setBackgroundColor(outlineColorVal[0]);
                });
            });

            outlineCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                thicknessLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                thicknessSeek.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                outlineColorBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            android.widget.CheckBox textCheck = new android.widget.CheckBox(activity);
            textCheck.setText("Include Guide Text");
            textCheck.setChecked(true);
            container.addView(textCheck);

            String dialogTitle = (templateType == 0) ? "Customize Grid Template" :
                                 (templateType == 1) ? "Customize List Template (Landscape)" : "Customize List Template (Portrait)";

            new AlertDialog.Builder(activity)
                    .setTitle(dialogTitle)
                    .setView(scrollView)
                    .setPositiveButton("Download Template", (dialog, which) -> {
                        generateAndSavePcCardTemplateAdvanced(
                                templateType,
                                cornerSeek.getProgress(),
                                gradientCheck.isChecked(),
                                solidBgColor[0],
                                outlineCheck.isChecked(),
                                Math.max(1, thicknessSeek.getProgress()),
                                outlineColorVal[0],
                                textCheck.isChecked()
                        );
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void generateAndSavePcCardTemplateAdvanced(int templateType, int cornerRadiusDp, boolean useGradient, int solidColor, boolean includeOutline, int outlineThickness, int outlineColor, boolean includeText) {
            Activity activity = getActivity();
            if (activity == null) return;

            int width = (templateType == 0) ? 500 : (templateType == 1) ? 1500 : 600;
            int height = (templateType == 0) ? 500 : 300;
            String modeName = (templateType == 0) ? "Grid" : (templateType == 1) ? "List_Landscape" : "List_Portrait";
            float radiusPx = cornerRadiusDp * activity.getResources().getDisplayMetrics().density;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (useGradient) {
                LinearGradient gradient = new LinearGradient(0, 0, width, height, 0xFF1F1B2E, 0xFF0D001A, Shader.TileMode.CLAMP);
                paint.setShader(gradient);
            } else {
                paint.setColor(solidColor);
            }
            canvas.drawRoundRect(new RectF(0, 0, width, height), radiusPx, radiusPx, paint);

            if (includeOutline) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(outlineColor);
                paint.setStrokeWidth(outlineThickness);
                float offset = outlineThickness / 2f;
                canvas.drawRoundRect(new RectF(offset, offset, width - offset, height - offset), radiusPx, radiusPx, paint);
            }

            if (includeText) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextSize(templateType == 0 ? 32 : 36);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("PC Card Template (" + modeName.replace('_', ' ') + ")", width / 2f, height / 2f - 15, paint);

                paint.setColor(0xFFAAAAAA);
                paint.setTextSize(templateType == 0 ? 20 : 22);
                paint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(width + " x " + height + " px (" + cornerRadiusDp + "dp corners)", width / 2f, height / 2f + 30, paint);
            }

            try {
                String filename = "PCCard_Template_" + modeName + "_" + cornerRadiusDp + "dp_" + System.currentTimeMillis() + ".png";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mooncake");

                    Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                            }
                        }
                        Toast.makeText(activity, "Template saved to Pictures/Mooncake!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mooncake");
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                Toast.makeText(activity, "Template saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity, "Failed to save template", Toast.LENGTH_SHORT).show();
            }
        }

        private void showStreamingTemplateCustomizationDialog() {
            Activity activity = getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            ScrollView scrollView = new ScrollView(activity);
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);
            scrollView.addView(container);

            android.widget.CheckBox gradientCheck = new android.widget.CheckBox(activity);
            gradientCheck.setText("Use Background Gradient");
            gradientCheck.setChecked(true);
            container.addView(gradientCheck);

            final int[] solidBgColor = {0xFF1A1A1A};
            android.widget.Button solidColorBtn = new android.widget.Button(activity);
            solidColorBtn.setText("Choose Solid Background Color");
            solidColorBtn.setVisibility(View.GONE);
            container.addView(solidColorBtn);

            gradientCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                solidColorBtn.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            });

            solidColorBtn.setOnClickListener(v -> {
                showColorPicker("Solid Streaming Background Color", "temp_stream_solid_color", () -> {
                    solidBgColor[0] = prefs.getInt("temp_stream_solid_color", 0xFF1A1A1A);
                    solidColorBtn.setBackgroundColor(solidBgColor[0]);
                });
            });

            android.widget.CheckBox outlineCheck = new android.widget.CheckBox(activity);
            outlineCheck.setText("Include Border Outline");
            outlineCheck.setChecked(true);
            container.addView(outlineCheck);

            TextView thicknessLabel = new TextView(activity);
            thicknessLabel.setText("Outline Thickness: 6 px");
            container.addView(thicknessLabel);

            SeekBar thicknessSeek = new SeekBar(activity);
            thicknessSeek.setMax(20);
            thicknessSeek.setProgress(6);
            container.addView(thicknessSeek);

            thicknessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    thicknessLabel.setText("Outline Thickness: " + Math.max(1, progress) + " px");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            final int[] outlineColorVal = {0xFF7B2CBF};
            android.widget.Button outlineColorBtn = new android.widget.Button(activity);
            outlineColorBtn.setText("Choose Outline Color");
            container.addView(outlineColorBtn);

            outlineColorBtn.setOnClickListener(v -> {
                showColorPicker("Outline Color", "temp_outline_color", () -> {
                    outlineColorVal[0] = prefs.getInt("temp_outline_color", 0xFF7B2CBF);
                    outlineColorBtn.setBackgroundColor(outlineColorVal[0]);
                });
            });

            outlineCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                thicknessLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                thicknessSeek.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                outlineColorBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            android.widget.CheckBox safeZoneCheck = new android.widget.CheckBox(activity);
            safeZoneCheck.setText("Include Safe Zone Lines (Visible Viewport)");
            safeZoneCheck.setChecked(true);
            container.addView(safeZoneCheck);

            TextView safeZoneThicknessLabel = new TextView(activity);
            safeZoneThicknessLabel.setText("Safe Zone Line Thickness: 4 px");
            container.addView(safeZoneThicknessLabel);

            SeekBar safeZoneThicknessSeek = new SeekBar(activity);
            safeZoneThicknessSeek.setMax(30);
            safeZoneThicknessSeek.setProgress(4);
            container.addView(safeZoneThicknessSeek);

            safeZoneThicknessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    safeZoneThicknessLabel.setText("Safe Zone Line Thickness: " + Math.max(1, progress) + " px");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            final int[] safeZoneColorVal = {0xFF00FFCC};
            android.widget.Button safeZoneColorBtn = new android.widget.Button(activity);
            safeZoneColorBtn.setText("Choose Safe Zone Line Color");
            container.addView(safeZoneColorBtn);

            safeZoneColorBtn.setOnClickListener(v -> {
                showColorPicker("Safe Zone Line Color", "temp_safe_zone_color", () -> {
                    safeZoneColorVal[0] = prefs.getInt("temp_safe_zone_color", 0xFF00FFCC);
                    safeZoneColorBtn.setBackgroundColor(safeZoneColorVal[0]);
                });
            });

            safeZoneCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                safeZoneThicknessLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                safeZoneThicknessSeek.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                safeZoneColorBtn.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            android.widget.CheckBox textCheck = new android.widget.CheckBox(activity);
            textCheck.setText("Include Guide Text");
            textCheck.setChecked(true);
            container.addView(textCheck);

            new AlertDialog.Builder(activity)
                    .setTitle("Customize Streaming Window Template")
                    .setView(scrollView)
                    .setPositiveButton("Download Template", (dialog, which) -> {
                        generateAndSaveStreamingTemplateAdvanced(
                                gradientCheck.isChecked(),
                                solidBgColor[0],
                                outlineCheck.isChecked(),
                                Math.max(1, thicknessSeek.getProgress()),
                                outlineColorVal[0],
                                safeZoneCheck.isChecked(),
                                Math.max(1, safeZoneThicknessSeek.getProgress()),
                                safeZoneColorVal[0],
                                textCheck.isChecked()
                        );
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void generateAndSaveStreamingTemplateAdvanced(boolean useGradient, int solidColor, boolean includeOutline, int outlineThickness, int outlineColor, boolean includeSafeZone, int safeZoneThickness, int safeZoneColor, boolean includeText) {
            Activity activity = getActivity();
            if (activity == null) return;

            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            if (width < height) {
                int temp = width;
                width = height;
                height = temp;
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (useGradient) {
                LinearGradient gradient = new LinearGradient(0, 0, width, height, 0xFF1F1B2E, 0xFF0D001A, Shader.TileMode.CLAMP);
                paint.setShader(gradient);
            } else {
                paint.setColor(solidColor);
            }
            canvas.drawRect(0, 0, width, height, paint);

            com.limelight.utils.StreamingLayoutHelper.ViewportBounds bounds =
                    com.limelight.utils.StreamingLayoutHelper.calculateViewport(activity, width, height);

            float safeLeft = bounds.left;
            float safeTop = bounds.top;
            float safeRight = bounds.right;
            float safeBottom = bounds.bottom;

            if (includeOutline) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(outlineColor);
                paint.setStrokeWidth(outlineThickness);
                float offset = outlineThickness / 2f;
                canvas.drawRect(new RectF(offset, offset, width - offset, height - offset), paint);
            }

            if (includeSafeZone) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(safeZoneColor);
                paint.setStrokeWidth(safeZoneThickness);

                // Draw streaming video viewport rectangle
                canvas.drawRect(safeLeft, safeTop, safeRight, safeBottom, paint);

                // Draw vertical side indicator lines for pillarbox gaps
                if (safeLeft > 5) {
                    paint.setStrokeWidth(Math.max(2, safeZoneThickness + 2));
                    canvas.drawLine(safeLeft, 0, safeLeft, height, paint);
                    canvas.drawLine(safeRight, 0, safeRight, height, paint);
                }

                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(safeZoneColor);
                paint.setTextSize(22);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("◄ Game Stream Viewport ►", width / 2f, height / 2f - 20, paint);

                if (safeLeft > 5) {
                    paint.setTextSize(18);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("Side Gap", safeLeft / 2f, height / 2f, paint);
                    canvas.drawText("Side Gap", safeRight + (width - safeRight) / 2f, height / 2f, paint);
                }
            }

            if (includeText) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextSize(36);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Streaming Window Template (" + width + " x " + height + " px)", width / 2f, height / 2f - 65, paint);

                paint.setColor(0xFFAAAAAA);
                paint.setTextSize(22);
                paint.setTypeface(Typeface.DEFAULT);
                canvas.drawText("Letterbox sides outside safe zone fill screen background", width / 2f, height / 2f + 65, paint);
            }

            try {
                String filename = "Streaming_Template_" + System.currentTimeMillis() + ".png";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mooncake");

                    Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                            }
                        }
                        Toast.makeText(activity, "Streaming template saved to Pictures/Mooncake!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mooncake");
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                Toast.makeText(activity, "Streaming template saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity, "Failed to save streaming template", Toast.LENGTH_SHORT).show();
            }
        }

    }
}
