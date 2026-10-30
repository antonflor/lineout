package xyz.montylabs.lineout;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;

/** Loads and caches the brand typefaces from assets. */
public final class Fonts {

    public static final int PLEX = 0;
    public static final int PLEX_BOLD = 1;
    public static final int JB = 2;
    public static final int JB_MEDIUM = 3;
    public static final int JB_BOLD = 4;

    private static final HashMap<String, Typeface> CACHE = new HashMap<String, Typeface>();

    private Fonts() {
    }

    public static Typeface get(Context ctx, int which) {
        String path;
        switch (which) {
            case PLEX_BOLD:
                path = "fonts/IBMPlexMono-Bold.ttf";
                break;
            case JB:
                path = "fonts/JetBrainsMono-Regular.ttf";
                break;
            case JB_MEDIUM:
                path = "fonts/JetBrainsMono-Medium.ttf";
                break;
            case JB_BOLD:
                path = "fonts/JetBrainsMono-Bold.ttf";
                break;
            case PLEX:
            default:
                path = "fonts/IBMPlexMono-Regular.ttf";
                break;
        }
        Typeface tf = CACHE.get(path);
        if (tf == null) {
            try {
                tf = Typeface.createFromAsset(ctx.getApplicationContext().getAssets(), path);
            } catch (RuntimeException e) {
                tf = Typeface.MONOSPACE;
            }
            CACHE.put(path, tf);
        }
        return tf;
    }
}
