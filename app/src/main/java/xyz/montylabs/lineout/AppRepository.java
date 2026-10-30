package xyz.montylabs.lineout;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Queries launchable activities. No caching games: a launcher list is small. */
public final class AppRepository {

    private AppRepository() {
    }

    /** All launchable apps except ourselves, sorted by label. Does not filter hidden. */
    public static List<AppEntry> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        String self = c.getPackageName();
        if (ris != null) {
            for (int i = 0; i < ris.size(); i++) {
                ResolveInfo ri = ris.get(i);
                if (ri.activityInfo == null) {
                    continue;
                }
                if (self.equals(ri.activityInfo.packageName)) {
                    continue;
                }
                CharSequence label = ri.loadLabel(pm);
                String text = label == null ? ri.activityInfo.packageName : label.toString().trim();
                out.add(new AppEntry(text, ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(a.label, b.label);
            }
        });
        return out;
    }

    /** Find an entry by its prefs key. */
    public static AppEntry findByKey(List<AppEntry> all, String key) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).key().equals(key)) {
                return all.get(i);
            }
        }
        return null;
    }
}
