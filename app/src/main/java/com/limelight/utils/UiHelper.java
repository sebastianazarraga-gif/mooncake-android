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
import android.preference.PreferenceManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.LocaleList;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.DisplayMetrics;
import android.transition.Slide;
import android.transition.Transition;
import android.view.Gravity;
import android.app.ActivityOptions;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
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

    private static boolean isTrueEdgeToEdgeActivity(Activity activity) {
        return activity instanceof com.limelight.Game ||
               activity instanceof com.limelight.ConfigureVirtualControllerActivity ||
               activity instanceof com.limelight.ControllerMappingActivity;
    }

    private static boolean isLandscape(Activity activity) {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
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

                        // Ensure window flags and colors match current orientation and immersive settings
                        applyImmersiveMode(activity);

                        // Fetch system bar and cutout insets
                        Insets bars;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                        } else {
                            bars = Insets.of(insets.getSystemWindowInsetLeft(),
                                             insets.getSystemWindowInsetTop(),
                                             insets.getSystemWindowInsetRight(),
                                             insets.getSystemWindowInsetBottom());
                        }

                        // Streaming and Mapping screens are always true edge-to-edge (no barriers)
                        if (isTrueEdgeToEdgeActivity(activity)) {
                            v.setPadding(0, 0, 0, 0);
                            return insets;
                        }

                        // Landscape orientation: Edge-to-edge top and sides
                        if (isLandscape(activity)) {
                            v.setPadding(0, 0, 0, 0);
                        } else {
                            // Portrait: Respect top barrier (status bar) but edge-to-edge sides
                            v.setPadding(0, bars.top, 0, 0);
                        }
                    }
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

        applyCustomThemeBackground(activity);

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Apply padding logic and window bars setup through the common listener
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
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
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

        applyCustomThemeBackground(activity);
    }

    public static void applyCustomThemeBackground(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View rootView = activity.findViewById(android.R.id.content);
        boolean isLandscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        java.io.File customBgLandscape = new java.io.File(activity.getFilesDir(), "custom_theme_bg_landscape.jpg");
        java.io.File customBgPortrait = new java.io.File(activity.getFilesDir(), "custom_theme_bg_portrait.jpg");
        java.io.File customBgFallback = new java.io.File(activity.getFilesDir(), "custom_theme_bg.jpg");

        java.io.File targetBg = null;
        if (isLandscape) {
            if (customBgLandscape.exists() && customBgLandscape.length() > 0) {
                targetBg = customBgLandscape;
            } else if (customBgFallback.exists() && customBgFallback.length() > 0) {
                targetBg = customBgFallback;
            }
        } else {
            if (customBgPortrait.exists() && customBgPortrait.length() > 0) {
                targetBg = customBgPortrait;
            } else if (customBgFallback.exists() && customBgFallback.length() > 0) {
                targetBg = customBgFallback;
            }
        }

        if (targetBg != null && targetBg.exists() && targetBg.length() > 0) {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                boolean keepAspect = isLandscape ?
                        prefs.getBoolean("checkbox_keep_aspect_ratio_landscape", false) :
                        prefs.getBoolean("checkbox_keep_aspect_ratio_portrait", false);
                int zoom = isLandscape ? prefs.getInt("theme_zoom_landscape", 50) : prefs.getInt("theme_zoom_portrait", 50);
                int x = isLandscape ? prefs.getInt("theme_stretch_x_landscape", 0) : prefs.getInt("theme_stretch_x_portrait", 0);
                int y = isLandscape ? prefs.getInt("theme_stretch_y_landscape", 0) : prefs.getInt("theme_stretch_y_portrait", 0);
                int x2 = isLandscape ? prefs.getInt("theme_stretch_x2_landscape", 0) : prefs.getInt("theme_stretch_x2_portrait", 0);
                int y2 = isLandscape ? prefs.getInt("theme_stretch_y2_landscape", 0) : prefs.getInt("theme_stretch_y2_portrait", 0);

                android.graphics.drawable.Drawable drawable = createAspectRatioDrawable(activity, targetBg, keepAspect, zoom, x, y, x2, y2);
                if (drawable != null) {
                    activity.getWindow().setBackgroundDrawable(drawable);
                    if (rootView instanceof android.view.ViewGroup && ((android.view.ViewGroup) rootView).getChildCount() > 0) {
                        View inflatedLayoutRoot = ((android.view.ViewGroup) rootView).getChildAt(0);
                        if (inflatedLayoutRoot != null) {
                            inflatedLayoutRoot.setBackground(null);
                        }
                    }
                    applyHeaderTheme(activity);
                    applyNavTheme(activity);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Restore default gradient when custom theme image is reset/deleted
        activity.getWindow().setBackgroundDrawableResource(R.drawable.main_background_gradient);
        if (rootView instanceof android.view.ViewGroup && ((android.view.ViewGroup) rootView).getChildCount() > 0) {
            View inflatedLayoutRoot = ((android.view.ViewGroup) rootView).getChildAt(0);
            if (inflatedLayoutRoot != null) {
                inflatedLayoutRoot.setBackgroundResource(R.drawable.main_background_gradient);
            }
        }

        applyHeaderTheme(activity);
        applyNavTheme(activity);
    }

    public static android.graphics.drawable.Drawable createAspectRatioDrawable(Context context, java.io.File imgFile, boolean keepAspect, int zoom, int x, int y, int x2, int y2) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            if (bitmap == null) return null;

            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            if (screenWidth == 0 || screenHeight == 0) {
                return new BitmapDrawable(context.getResources(), bitmap);
            }

            float bitmapWidth = bitmap.getWidth();
            float bitmapHeight = bitmap.getHeight();

            int targetWidth, targetHeight;
            if (keepAspect) {
                float bitmapRatio = bitmapWidth / bitmapHeight;
                float screenRatio = (float) screenWidth / screenHeight;
                if (bitmapRatio > screenRatio) {
                    targetWidth = screenWidth;
                    targetHeight = (int) (screenWidth / bitmapRatio);
                } else {
                    targetHeight = screenHeight;
                    targetWidth = (int) (screenHeight * bitmapRatio);
                }
            } else {
                targetWidth = screenWidth;
                targetHeight = screenHeight;
            }

            float zoomScale = zoom / 50.0f;
            targetWidth = (int) (targetWidth * zoomScale);
            targetHeight = (int) (targetHeight * zoomScale);

            targetWidth += (x + x2);
            targetHeight += (y + y2);

            if (targetWidth < 10) targetWidth = 10;
            if (targetHeight < 10) targetHeight = 10;

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);

            Bitmap screenBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(screenBitmap);
            canvas.drawColor(0xFF0D001A);

            float left = (screenWidth - targetWidth) / 2f + (x - x2);
            float top = (screenHeight - targetHeight) / 2f + (y - y2);
            canvas.drawBitmap(scaledBitmap, left, top, null);

            return new BitmapDrawable(context.getResources(), screenBitmap);
        } catch (Exception e) {
            e.printStackTrace();
            return android.graphics.drawable.Drawable.createFromPath(imgFile.getAbsolutePath());
        }
    }

    public static void applyPcCardTheme(Context context, View parentView, boolean isListView) {
        if (context == null || parentView == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        boolean isLandscape = metrics.widthPixels > metrics.heightPixels;

        String modeSuffix = isListView ? "_list" : "_grid";
        String orientSuffix = isListView ? (isLandscape ? "_list_landscape" : "_list_portrait") : (isLandscape ? "_grid_landscape" : "_grid_portrait");

        int alphaPercent = prefs.getInt("theme_pc_card_transparency" + orientSuffix,
                prefs.getInt("theme_pc_card_transparency" + modeSuffix, 100));
        int cornerRadiusDp = prefs.getInt("theme_pc_card_corner_radius" + orientSuffix,
                prefs.getInt("theme_pc_card_corner_radius" + modeSuffix, 16));
        float radiusPx = cornerRadiusDp * metrics.density;
        float alpha = alphaPercent / 100.0f;

        java.io.File cardImgFile = null;
        if (isListView) {
            java.io.File cardImgLandscape = new java.io.File(context.getFilesDir(), "custom_pc_card_bg_list_landscape.jpg");
            java.io.File cardImgPortrait = new java.io.File(context.getFilesDir(), "custom_pc_card_bg_list_portrait.jpg");

            if (isLandscape) {
                if (cardImgLandscape.exists() && cardImgLandscape.length() > 0) {
                    cardImgFile = cardImgLandscape;
                }
            } else {
                if (cardImgPortrait.exists() && cardImgPortrait.length() > 0) {
                    cardImgFile = cardImgPortrait;
                }
            }
        } else {
            java.io.File cardImgLandscape = new java.io.File(context.getFilesDir(), "custom_pc_card_bg_grid_landscape.jpg");
            java.io.File cardImgPortrait = new java.io.File(context.getFilesDir(), "custom_pc_card_bg_grid_portrait.jpg");
            java.io.File cardImgFallback = new java.io.File(context.getFilesDir(), "custom_pc_card_bg_grid.jpg");

            if (isLandscape) {
                if (cardImgLandscape.exists() && cardImgLandscape.length() > 0) {
                    cardImgFile = cardImgLandscape;
                } else if (cardImgFallback.exists() && cardImgFallback.length() > 0) {
                    cardImgFile = cardImgFallback;
                }
            } else {
                if (cardImgPortrait.exists() && cardImgPortrait.length() > 0) {
                    cardImgFile = cardImgPortrait;
                } else if (cardImgFallback.exists() && cardImgFallback.length() > 0) {
                    cardImgFile = cardImgFallback;
                }
            }
        }

        View gridCardLayout = parentView.findViewById(R.id.grid_image_layout);
        if (gridCardLayout != null) {
            gridCardLayout.setAlpha(alpha);

            if (cardImgFile != null && cardImgFile.exists() && cardImgFile.length() > 0) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(cardImgFile.getAbsolutePath());
                    if (bitmap != null) {
                        int cardSize = (int) (125 * metrics.density);
                        int w = bitmap.getWidth();
                        int h = bitmap.getHeight();
                        int minDim = Math.min(w, h);
                        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, (w - minDim) / 2, (h - minDim) / 2, minDim, minDim);
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, cardSize, cardSize, true);

                        BitmapDrawable bd = new BitmapDrawable(context.getResources(), scaledBitmap);
                        gridCardLayout.setBackground(bd);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                int color = prefs.getInt("theme_pc_card_color" + orientSuffix,
                        prefs.getInt("theme_pc_card_color" + modeSuffix, 0xFF1A1A1A));
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(color);
                gd.setCornerRadius(radiusPx);
                gridCardLayout.setBackground(gd);
            }
        }

        if (parentView instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) parentView;
            cardView.setAlpha(alpha);
            cardView.setRadius(radiusPx);

            if (cardImgFile != null && cardImgFile.exists() && cardImgFile.length() > 0) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(cardImgFile.getAbsolutePath());
                    if (bitmap != null) {
                        BitmapDrawable bd = new BitmapDrawable(context.getResources(), bitmap);
                        cardView.setCardBackgroundColor(Color.TRANSPARENT);
                        if (cardView.getChildCount() > 0) {
                            cardView.getChildAt(0).setBackground(bd);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                int color = prefs.getInt("theme_pc_card_color" + orientSuffix,
                        prefs.getInt("theme_pc_card_color" + modeSuffix, 0xFF1A1A1A));
                cardView.setCardBackgroundColor(color);
                if (cardView.getChildCount() > 0) {
                    cardView.getChildAt(0).setBackground(null);
                }
            }
        }
    }

    public static void applyUiTransparency(View parentView, float alpha, boolean isListView) {
        if (parentView == null) return;

        if (isListView) {
            if (parentView instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) parentView;
                if (cardView.getChildCount() > 0) {
                    setAlphaRecursive(cardView.getChildAt(0), alpha);
                }
            }
        } else {
            if (parentView instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) parentView;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child.getId() == R.id.grid_image_layout) {
                        if (child instanceof ViewGroup) {
                            ViewGroup innerVg = (ViewGroup) child;
                            for (int j = 0; j < innerVg.getChildCount(); j++) {
                                setAlphaRecursive(innerVg.getChildAt(j), alpha);
                            }
                        }
                    } else {
                        setAlphaRecursive(child, alpha);
                    }
                }
            }
        }
    }

    private static void setAlphaRecursive(View view, float alpha) {
        if (view == null) return;
        view.setAlpha(alpha);
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setAlphaRecursive(vg.getChildAt(i), alpha);
            }
        }
    }

    public static void applyStreamingWindowBackground(Activity activity, View backgroundView) {
        if (activity == null || backgroundView == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        backgroundView.post(() -> {
            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            int w = metrics.widthPixels;
            int h = metrics.heightPixels;
            if (w < h) { int temp = w; w = h; h = temp; }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            java.io.File customBg = new java.io.File(activity.getFilesDir(), "custom_streaming_window_bg.jpg");

            if (customBg.exists() && customBg.length() > 0) {
                try {
                    android.graphics.drawable.Drawable drawable = createCenterCropDrawable(activity, customBg, w, h);
                    if (drawable != null) {
                        backgroundView.setBackground(drawable);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (prefs.contains("theme_streaming_window_color")) {
                int color = prefs.getInt("theme_streaming_window_color", 0xFF0D001A);
                backgroundView.setBackgroundColor(color);
                return;
            }

            backgroundView.setBackgroundColor(0xFF000000);
        });
    }

    public static android.graphics.drawable.Drawable createCenterCropDrawable(Context context, java.io.File imgFile, int screenWidth, int screenHeight) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            if (bitmap == null) return null;

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            float bitmapRatio = (float) bitmapWidth / bitmapHeight;
            float screenRatio = (float) screenWidth / screenHeight;

            int targetWidth, targetHeight;
            if (bitmapRatio > screenRatio) {
                targetHeight = screenHeight;
                targetWidth = Math.round(screenHeight * bitmapRatio);
            } else {
                targetWidth = screenWidth;
                targetHeight = Math.round(screenWidth / bitmapRatio);
            }

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
            Bitmap screenBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(screenBitmap);
            canvas.drawColor(0xFF000000);

            float left = (screenWidth - targetWidth) / 2f;
            float top = (screenHeight - targetHeight) / 2f;
            canvas.drawBitmap(scaledBitmap, left, top, null);

            return new BitmapDrawable(context.getResources(), screenBitmap);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void applyVirtualControllerBackground(Activity activity, View targetView) {
        if (activity == null || targetView == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        java.io.File customBg = new java.io.File(activity.getFilesDir(), "custom_virtual_controller_bg.jpg");

        if (customBg.exists() && customBg.length() > 0) {
            try {
                int zoom = prefs.getInt("theme_zoom_virtual_controller", 50);
                int x = prefs.getInt("theme_stretch_x_virtual_controller", 0);
                int y = prefs.getInt("theme_stretch_y_virtual_controller", 0);
                int x2 = prefs.getInt("theme_stretch_x2_virtual_controller", 0);
                int y2 = prefs.getInt("theme_stretch_y2_virtual_controller", 0);

                android.graphics.drawable.Drawable drawable = createAspectRatioDrawable(activity, customBg, true, zoom, x, y, x2, y2);
                if (drawable != null) {
                    targetView.setBackground(drawable);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (prefs.contains("theme_virtual_controller_color")) {
            int color = prefs.getInt("theme_virtual_controller_color", Color.TRANSPARENT);
            targetView.setBackgroundColor(color);
            return;
        }
    }

    public static void applyHeaderTheme(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        View headerLayout = activity.findViewById(R.id.headerLayout);
        if (headerLayout == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean isTransparent = prefs.getBoolean("checkbox_transparent_header", false);

        if (isTransparent) {
            headerLayout.setBackgroundColor(Color.TRANSPARENT);
            headerLayout.setElevation(0f);
        } else {
            int customColor = prefs.getInt("theme_header_color", 0xFF0D001A);
            headerLayout.setBackgroundColor(customColor);
            headerLayout.setElevation(4f * activity.getResources().getDisplayMetrics().density);
        }
    }

    public static void applyNavTheme(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        View bottomNav = activity.findViewById(R.id.bottom_nav_bar);
        if (bottomNav == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean isTransparent = prefs.getBoolean("checkbox_transparent_nav", false);

        if (isTransparent) {
            bottomNav.setBackgroundColor(Color.TRANSPARENT);
            bottomNav.setElevation(0f);
        } else {
            if (prefs.contains("theme_nav_color")) {
                int customColor = prefs.getInt("theme_nav_color", 0xFF0D001A);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gd.setColor(customColor);
                float radius = 20f * activity.getResources().getDisplayMetrics().density;
                gd.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
                bottomNav.setBackground(gd);
            } else {
                bottomNav.setBackgroundResource(R.drawable.navigation);
            }
            bottomNav.setElevation(8f * activity.getResources().getDisplayMetrics().density);
        }
    }

    public static void setupBottomNav(final Activity activity, int selectedTabIndex) {
        View bottomNav = activity.findViewById(R.id.bottom_nav_bar);
        if (bottomNav == null) return;

        applyNavTheme(activity);

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            bottomNav.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int bottomInset = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bottomInset = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
                    } else {
                        bottomInset = insets.getSystemWindowInsetBottom();
                    }
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomInset);
                    return insets;
                }
            });
            bottomNav.requestApplyInsets();
        }
    }
}
