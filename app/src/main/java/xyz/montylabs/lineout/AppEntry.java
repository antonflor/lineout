package xyz.montylabs.lineout;

import android.content.ComponentName;

/** One launchable activity. */
public class AppEntry {

    public final String label;
    public final String packageName;
    public final String className;

    public AppEntry(String label, String packageName, String className) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
    }

    public ComponentName component() {
        return new ComponentName(packageName, className);
    }

    /** Stable key used in preferences. */
    public String key() {
        return packageName + "/" + className;
    }
}
