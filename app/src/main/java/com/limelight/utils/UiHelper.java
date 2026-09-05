package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.GameManager;
import android.app.GameState;
import android.app.LocaleManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.LocaleList;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.transition.Slide;
import android.transition.Transition;
import android.view.Gravity;
import android.app.ActivityOptions;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.content.Intent;

import com.limelight.Game;
import com.mooncake.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Locale;

public class UiHelper {

    private static final int TV_VERTICAL_PADDING_DP = 15;
    private static final int TV_HORIZONTAL_PADDING_DP = 15;

    private static Class<? extends Activity> lastComputerActivity = com.limelight.PcView.class;
    private static Class<? extends Activity> lastSettingsActivity = com.limelight.preferences.StreamSettings.class;

    private static boolean isTrueEdgeToEdgeActivity(Activity activity) {
        return activity instanceof com.limelight.Game ||
               activity instanceof com.limelight.ConfigureVirtualControllerActivity ||
               activity instanceof com.limelight.ControllerMappingActivity;
    }

    private static boolean isLandscape(Activity activity) {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static void updateLastActivity(Activity activity) {
        if (activity instanceof com.limelight.PcView ||
            activity instanceof com.limelight.AppView ||
            activity instanceof com.limelight.preferences.AddComputerManually) {
            lastComputerActivity = activity.getClass();
        } else if (activity instanceof com.limelight.preferences.StreamSettings) {
            lastSettingsActivity = activity.getClass();
        }
    }

    private static void setGameModeStatus(Context context, boolean streaming, boolean interruptible) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GameManager gameManager = context.getSystemService(GameManager.class);

            if (streaming) {
                gameManager.setGameState(new GameState(false, interruptible ? GameState.MODE_GAMEPLAY_INTERRUPTIBLE : GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE));
            }
            else {
                gameManager.setGameState(new GameState(false, GameState.MODE_NONE));
            }
        }
    }

    public static void notifyStreamConnecting(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamConnected(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnteringPiP(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamExitingPiP(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnded(Context context) {
        setGameModeStatus(context, false, false);
    }

    public static void setLocale(Activity activity)
    {
        String locale = PreferenceConfiguration.readPreferences(activity).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // On Android 13, migrate this non-default language setting into the OS native API
                LocaleManager localeManager = activity.getSystemService(LocaleManager.class);
                localeManager.setApplicationLocales(LocaleList.forLanguageTags(locale));
                PreferenceConfiguration.completeLanguagePreferenceMigration(activity);
            }
            else {
                Configuration config = new Configuration(activity.getResources().getConfiguration());

                // Some locales include both language and country which must be separated
                // before calling the Locale constructor.
                if (locale.contains("-"))
                {
                    config.locale = new Locale(locale.substring(0, locale.indexOf('-')),
                            locale.substring(locale.indexOf('-') + 1));
                }
                else
                {
                    config.locale = new Locale(locale);
                }

                activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
            }
        }
    }

    public static void applyStatusBarPadding(final View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    Context context = v.getContext();
                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;
                        // Streaming and Mapping screens are always true edge-to-edge (no barriers)
                        // Landscape orientation is also always edge-to-edge (no barriers)
                        if (isTrueEdgeToEdgeActivity(activity) || isLandscape(activity)) {
                            v.setPadding(0, 0, 0, 0);
                            return insets;
                        }
                    }

                    // Portrait Menu screens: Apply top barrier for status bar symbols (batteries, wifi)
                    int topInset;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Combine status bars and display cutout insets for a complete upper barrier
                        topInset = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
                    } else {
                        topInset = insets.getSystemWindowInsetTop();
                    }

                    v.setPadding(0, topInset, 0, 0);
                    return insets;
                }
            });
            view.requestApplyInsets();
        }
    }

    public static void notifyNewRootView(final Activity activity)
    {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        // Set GameState.MODE_NONE initially for all activities
        setGameModeStatus(activity, false, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Enable transitions and setup directional animations
            int direction = activity.getIntent().getIntExtra("anim_direction", Gravity.END);
            Slide slide = new Slide(direction);
            slide.excludeTarget(android.R.id.statusBarBackground, true);
            slide.excludeTarget(android.R.id.navigationBarBackground, true);
            slide.excludeTarget(R.id.bottom_nav_bar, true);

            // Exclude AdapterView and its containers to avoid UnsupportedOperationException
            slide.excludeTarget(android.widget.AdapterView.class, true);
            slide.excludeTarget(R.id.fragmentView, true);
            slide.excludeTarget(R.id.appFragmentContainer, true);
            slide.excludeTarget(R.id.pcFragmentContainer, true);

            activity.getWindow().setEnterTransition(slide);
            activity.getWindow().setExitTransition(slide);

            // Disable overlays for shared elements to prevent recreation-orientation crashes
            activity.getWindow().setSharedElementsUseOverlay(false);

            // Prevent transition overlapping to stabilize orientation changes
            activity.getWindow().setAllowEnterTransitionOverlap(false);
            activity.getWindow().setAllowReturnTransitionOverlap(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Allow all activities to layout under notches and barriers
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Draw under system bars globally to allow edge-to-edge bottom navigation
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

            // Set status bar color based on orientation/activity
            if (isTrueEdgeToEdgeActivity(activity) || isLandscape(activity)) {
                activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            } else {
                activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.black_purple));
            }
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);

            // Apply padding logic to the content view directly
            applyStatusBarPadding(rootView);
        }
    }

    public static void showDecoderCrashDialog(Activity activity) {
        final SharedPreferences prefs = activity.getSharedPreferences("DecoderTombstone", 0);
        final int crashCount = prefs.getInt("CrashCount", 0);
        int lastNotifiedCrashCount = prefs.getInt("LastNotifiedCrashCount", 0);

        // Remember the last crash count we notified at, so we don't
        // display the crash dialog every time the app is started until
        // they stream again
        if (crashCount != 0 && crashCount != lastNotifiedCrashCount) {
            if (crashCount % 3 == 0) {
                // At 3 consecutive crashes, we'll forcefully reset their settings
                PreferenceConfiguration.resetStreamingSettings(activity);
                Dialog.displayDialog(activity,
                        activity.getResources().getString(R.string.title_decoding_reset),
                        activity.getResources().getString(R.string.message_decoding_reset),
                        new Runnable() {
                            @Override
                            public void run() {
                                // Mark notification as acknowledged on dismissal
                                prefs.edit().putInt("LastNotifiedCrashCount", crashCount).apply();
                            }
                        });
            }
            else {
                Dialog.displayDialog(activity,
                        activity.getResources().getString(R.string.title_decoding_error),
                        activity.getResources().getString(R.string.message_decoding_error),
                        new Runnable() {
                            @Override
                            public void run() {
                                // Mark notification as acknowledged on dismissal
                                prefs.edit().putInt("LastNotifiedCrashCount", crashCount).apply();
                            }
                        });
            }
        }
    }

    public static void displayQuitConfirmationDialog(Activity parent, final Runnable onYes, final Runnable onNo) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        if (onYes != null) {
                            onYes.run();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        if (onNo != null) {
                            onNo.run();
                        }
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        builder.setMessage(parent.getResources().getString(R.string.applist_quit_confirmation))
                .setPositiveButton(parent.getResources().getString(R.string.yes), dialogClickListener)
                .setNegativeButton(parent.getResources().getString(R.string.no), dialogClickListener)
                .show();
    }

    public static void displayDeletePcConfirmationDialog(Activity parent, ComputerDetails computer, final Runnable onYes, final Runnable onNo) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        if (onYes != null) {
                            onYes.run();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        if (onNo != null) {
                            onNo.run();
                        }
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        builder.setMessage(parent.getResources().getString(R.string.delete_pc_msg))
                .setTitle(computer.name)
                .setPositiveButton(parent.getResources().getString(R.string.yes), dialogClickListener)
                .setNegativeButton(parent.getResources().getString(R.string.no), dialogClickListener)
                .show();
    }

    @SuppressLint("InlinedApi")
    public static void applyImmersiveMode(Activity activity) {
        // Sticky Immersive Mode controls the Android SYSTEM navigation bar.
        if (PreferenceConfiguration.readPreferences(activity).immersiveMode) {
            // In multi-window mode on N+, we need to drop our layout flags or we'll
            // be drawing underneath the system UI.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode()) {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            else {
                // Use sticky immersive mode to hide status and SYSTEM navigation bars
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

                activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
                activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            }
        }
        else {
            // Restore orientation-aware system bars logic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode()) {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

                if (isTrueEdgeToEdgeActivity(activity) || isLandscape(activity)) {
                    activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
                } else {
                    activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.black_purple));
                }
                activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            }
            else {
                // Show system bars normally
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }

    public static void setupBottomNav(final Activity activity, int selectedTabIndex) {
        View bottomNav = activity.findViewById(R.id.bottom_nav_bar);
        if (bottomNav == null) return;

        LinearLayout computersTab = activity.findViewById(R.id.nav_computers);
        LinearLayout settingsTab = activity.findViewById(R.id.nav_settings);

        ImageView computersIcon = activity.findViewById(R.id.nav_computers_icon);
        TextView computersText = activity.findViewById(R.id.nav_computers_text);
        ImageView settingsIcon = activity.findViewById(R.id.nav_settings_icon);
        TextView settingsText = activity.findViewById(R.id.nav_settings_text);

        int selectedColor = activity.getResources().getColor(R.color.purple_accent_light);
        int unselectedColor = 0xFF888888; // Subtle grey

        if (selectedTabIndex == 0) {
            if (computersIcon != null) computersIcon.setColorFilter(selectedColor, PorterDuff.Mode.SRC_IN);
            if (computersText != null) computersText.setTextColor(selectedColor);
            if (settingsIcon != null) settingsIcon.setColorFilter(unselectedColor, PorterDuff.Mode.SRC_IN);
            if (settingsText != null) settingsText.setTextColor(unselectedColor);
        } else {
            if (settingsIcon != null) settingsIcon.setColorFilter(selectedColor, PorterDuff.Mode.SRC_IN);
            if (settingsText != null) settingsText.setTextColor(selectedColor);
            if (computersIcon != null) computersIcon.setColorFilter(unselectedColor, PorterDuff.Mode.SRC_IN);
            if (computersText != null) computersText.setTextColor(unselectedColor);
        }

        computersTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                if (selectedTabIndex != 0) {
                    Intent intent = new Intent(activity, lastComputerActivity);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    intent.putExtra("anim_direction", Gravity.START);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity,
                                bottomNav, "bottom_nav_bar");
                        activity.startActivity(intent, options.toBundle());
                    } else {
                        activity.startActivity(intent);
                    }
                }
            }
        });

        settingsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                if (selectedTabIndex != 1) {
                    Intent intent = new Intent(activity, lastSettingsActivity);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    intent.putExtra("anim_direction", Gravity.END);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity,
                                bottomNav, "bottom_nav_bar");
                        activity.startActivity(intent, options.toBundle());
                    } else {
                        activity.startActivity(intent);
                    }
                }
            }
        });

        // Handle System Navigation Bar Insets
        // Barriers removed for edge-to-edge content
    }
}
