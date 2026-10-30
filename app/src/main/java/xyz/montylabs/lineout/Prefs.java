package xyz.montylabs.lineout;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SharedPreferences wrapper: pinned apps (ordered), hidden apps, UI toggles. */
public final class Prefs {

    private static final String FILE = "monty";
    private static final String KEY_PINS = "pins";      // '\n'-separated ordered keys
    private static final String KEY_HIDDEN = "hidden";  // string set
    private static final String KEY_ICONS = "show_icons";
    private static final String KEY_SECONDS = "clock_seconds";
    private static final String KEY_SEEDED = "pins_seeded";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static List<String> getPins(Context c) {
        String raw = sp(c).getString(KEY_PINS, "");
        ArrayList<String> out = new ArrayList<String>();
        if (raw != null && raw.length() > 0) {
            String[] parts = raw.split("\n");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].length() > 0 && !out.contains(parts[i])) {
                    out.add(parts[i]);
                }
            }
        }
        return out;
    }

    public static void setPins(Context c, List<String> pins) {
        sp(c).edit().putString(KEY_PINS, TextUtils.join("\n", pins)).apply();
    }

    public static boolean isPinned(Context c, String key) {
        return getPins(c).contains(key);
    }

    public static void togglePin(Context c, String key) {
        List<String> pins = getPins(c);
        if (pins.contains(key)) {
            pins.remove(key);
        } else {
            pins.add(key);
        }
        setPins(c, pins);
    }

    public static void movePin(Context c, String key, int delta) {
        List<String> pins = getPins(c);
        int i = pins.indexOf(key);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= pins.size()) {
            return;
        }
        pins.remove(i);
        pins.add(j, key);
        setPins(c, pins);
    }

    public static Set<String> getHidden(Context c) {
        Set<String> s = sp(c).getStringSet(KEY_HIDDEN, null);
        return s == null ? new HashSet<String>() : new HashSet<String>(s);
    }

    public static void setHidden(Context c, Set<String> hidden) {
        sp(c).edit().putStringSet(KEY_HIDDEN, hidden).apply();
    }

    public static void hide(Context c, String key) {
        Set<String> h = getHidden(c);
        h.add(key);
        setHidden(c, h);
    }

    public static void unhide(Context c, String key) {
        Set<String> h = getHidden(c);
        h.remove(key);
        setHidden(c, h);
    }

    public static boolean isSeeded(Context c) {
        return sp(c).getBoolean(KEY_SEEDED, false);
    }

    public static void setSeeded(Context c) {
        sp(c).edit().putBoolean(KEY_SEEDED, true).apply();
    }

    public static boolean showIcons(Context c) {
        return sp(c).getBoolean(KEY_ICONS, false);
    }

    public static void setShowIcons(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_ICONS, v).apply();
    }

    public static boolean clockSeconds(Context c) {
        return sp(c).getBoolean(KEY_SECONDS, true);
    }

    public static void setClockSeconds(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_SECONDS, v).apply();
    }
}
