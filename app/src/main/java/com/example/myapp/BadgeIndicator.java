// app/src/main/java/com/example/myapp/BadgeIndicator.java
package com.example.myapp;

import android.content.Context;

public final class BadgeIndicator {
    private BadgeIndicator() {}

    private static final String PREF = "applist_badge";
    private static final String KEY_NEW_APPLIED    = "applied_last_new_ts";
    private static final String KEY_OPENED_APPLIED = "applied_last_opened_ts";

    public static void markAppliedNew(Context c) {
        if (c == null) return;
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEW_APPLIED, System.currentTimeMillis())
                .apply();
    }

    public static void markAppliedOpened(Context c) {
        if (c == null) return;
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_OPENED_APPLIED, System.currentTimeMillis())
                .apply();
    }

    public static boolean hasNewApplied(Context c) {
        if (c == null) return false;
        long lastNew    = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_NEW_APPLIED, 0L);
        long lastOpened = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_OPENED_APPLIED, 0L);
        return lastNew > lastOpened;
    }
}
