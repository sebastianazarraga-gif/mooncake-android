package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.preference.PreferenceManager;

import com.limelight.preferences.PreferenceConfiguration;

public class StreamingLayoutHelper {

    public static class ViewportBounds {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float width;
        public final float height;

        public ViewportBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.width = right - left;
            this.height = bottom - top;
        }

        public RectF toRectF() {
            return new RectF(left, top, right, bottom);
        }
    }

    public static ViewportBounds calculateViewport(Context context, float containerWidth, float containerHeight) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean stretchVideo = prefs.getBoolean("checkbox_stretch_video", false);

        if (stretchVideo) {
            return new ViewportBounds(0, 0, containerWidth, containerHeight);
        }

        int sx = prefs.getInt("theme_stretch_x_stream_screen", 0);
        int sy = prefs.getInt("theme_stretch_y_stream_screen", 0);
        int sx2 = prefs.getInt("theme_stretch_x2_stream_screen", 0);
        int sy2 = prefs.getInt("theme_stretch_y2_stream_screen", 0);

        float availWidth = Math.max(1f, containerWidth - sx - sx2);
        float availHeight = Math.max(1f, containerHeight - sy - sy2);

        PreferenceConfiguration prefConfig = PreferenceConfiguration.readPreferences(context);
        double desiredAspectRatio = 16.0 / 9.0;
        if (prefConfig != null && prefConfig.width > 0 && prefConfig.height > 0) {
            desiredAspectRatio = (double) prefConfig.width / (double) prefConfig.height;
        }

        float measuredWidth, measuredHeight;
        if (availWidth > availHeight * desiredAspectRatio) {
            measuredHeight = availHeight;
            measuredWidth = (float) (measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = availWidth;
            measuredHeight = (float) (measuredWidth / desiredAspectRatio);
        }

        float left = sx + (availWidth - measuredWidth) / 2.0f;
        float top = sy + (availHeight - measuredHeight) / 2.0f;
        float right = left + measuredWidth;
        float bottom = top + measuredHeight;

        return new ViewportBounds(left, top, right, bottom);
    }
}
