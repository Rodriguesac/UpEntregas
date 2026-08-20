package com.rodriguesacai.entregador;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePrefs {
    private static final String PREF = "up_entregas_ui";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_V14_LIGHT_MIGRATED = "v14_light_migrated";
    private ThemePrefs() {}

    public static void applySavedTheme(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_V14_LIGHT_MIGRATED, false)) {
            p.edit().putBoolean(KEY_DARK, true).putBoolean(KEY_V14_LIGHT_MIGRATED, true).apply();
        }
        boolean dark = p.getBoolean(KEY_DARK, false);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void toggle(Context c) {
        boolean next = !isDark(c);
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, next).apply();
        AppCompatDelegate.setDefaultNightMode(next ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
