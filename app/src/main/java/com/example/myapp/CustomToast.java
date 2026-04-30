package com.example.myapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public final class CustomToast {

    public enum Type { SUCCESS, ERROR, INFO, WARNING }

    private CustomToast() {}

    @MainThread
    public static void show(@NonNull Context context, @NonNull String message, @NonNull Type type) {
        show(context, message, type, Toast.LENGTH_SHORT);
    }

    @MainThread
    public static void show(@NonNull Context context, @NonNull String message, @NonNull Type type, int duration) {
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (!isOnUiThread()) {
                act.runOnUiThread(() -> inflateAndShow(act.getApplicationContext(), message, type, duration));
                return;
            }
        }
        inflateAndShow(context.getApplicationContext(), message, type, duration);
    }

    public static void success(Context c, String msg) { show(c, msg, Type.SUCCESS); }
    public static void error(Context c, String msg)   { show(c, msg, Type.ERROR); }
    public static void info(Context c, String msg)    { show(c, msg, Type.INFO); }
    public static void warning(Context c, String msg) { show(c, msg, Type.WARNING); }

    private static void inflateAndShow(Context appCtx, String message, Type type, int duration) {
        View view = LayoutInflater.from(appCtx).inflate(R.layout.toast_base, null, false);
        TextView tv = view.findViewById(R.id.text);
        ImageView iv = view.findViewById(R.id.icon);

        tv.setText(message);

        // 타입별 컬러/아이콘
        int color;
        @DrawableRes int iconRes;
        switch (type) {
            case SUCCESS:
                color = 0xFF4CAF50; // 초록
                iconRes = R.drawable.ic_check_circle;
                break;
            case ERROR:
                color = 0xFFF44336; // 빨강
                iconRes = R.drawable.ic_error;
                break;
            case WARNING:
                color = 0xFFFFA000; // 주황
                iconRes = R.drawable.ic_warning;
                break;
            default:
                color = 0xFF2196F3; // 파랑(INFO)
                iconRes = R.drawable.ic_info;
        }

        Drawable icon = ContextCompat.getDrawable(appCtx, iconRes);
        if (icon != null) {
            icon.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            iv.setImageDrawable(icon);
        }

        Toast toast = new Toast(appCtx);
        toast.setView(view);
        toast.setDuration(duration);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(appCtx, 72));
        toast.show();
    }

    private static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    private static boolean isOnUiThread() {
        return android.os.Looper.getMainLooper().isCurrentThread();
    }
}
