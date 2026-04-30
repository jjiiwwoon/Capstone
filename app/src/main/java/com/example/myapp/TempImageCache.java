package com.example.myapp;

import android.net.Uri;
import android.util.LruCache;

public final class TempImageCache {
    private static final LruCache<String, Uri> CACHE = new LruCache<>(128); // teamId 최대 128개

    private TempImageCache() {}

    public static void put(String teamId, Uri localUri) {
        if (teamId != null && localUri != null) CACHE.put(teamId, localUri);
    }

    public static Uri get(String teamId) {
        return teamId == null ? null : CACHE.get(teamId);
    }

    public static void remove(String teamId) {
        if (teamId != null) CACHE.remove(teamId);
    }
}
