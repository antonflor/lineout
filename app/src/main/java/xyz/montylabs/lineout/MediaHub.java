package xyz.montylabs.lineout;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.List;

/**
 * Watches active media sessions (any player: HiBy Music, UAPP, Neutron,
 * Poweramp, Spotify, ...) and exposes the most relevant controller.
 * Requires notification-listener access, granted once by the user.
 */
public class MediaHub {

    public interface Listener {
        /** Called on the main thread whenever the session, metadata or state changed. */
        void onMediaChanged();
    }

    private final Context ctx;
    private final Listener listener;
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean watching;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    adopt(controllers);
                }
            };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            listener.onMediaChanged();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            listener.onMediaChanged();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    public MediaHub(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public static boolean hasAccess(Context c) {
        String flat = Settings.Secure.getString(
                c.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.length() == 0) {
            return false;
        }
        ComponentName mine = new ComponentName(c, MediaAccessListener.class);
        String[] parts = flat.split(":");
        for (int i = 0; i < parts.length; i++) {
            ComponentName cn = ComponentName.unflattenFromString(parts[i]);
            if (mine.equals(cn)) {
                return true;
            }
        }
        return false;
    }

    /** Start watching. Safe to call when access is missing (becomes a no-op). */
    public void start() {
        if (!hasAccess(ctx)) {
            detach();
            listener.onMediaChanged();
            return;
        }
        if (msm == null) {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        }
        ComponentName cn = new ComponentName(ctx, MediaAccessListener.class);
        try {
            if (!watching) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, cn);
                watching = true;
            }
            adopt(msm.getActiveSessions(cn));
        } catch (SecurityException e) {
            detach();
            listener.onMediaChanged();
        }
    }

    public void stop() {
        if (msm != null && watching) {
            msm.removeOnActiveSessionsChangedListener(sessionsListener);
            watching = false;
        }
        detach();
    }

    public void refresh() {
        if (!watching && hasAccess(ctx)) {
            // Access was granted while we were already running.
            start();
            return;
        }
        if (msm != null && watching) {
            ComponentName cn = new ComponentName(ctx, MediaAccessListener.class);
            try {
                adopt(msm.getActiveSessions(cn));
                return;
            } catch (SecurityException ignored) {
            }
        }
        detach();
        listener.onMediaChanged();
    }

    private void adopt(List<MediaController> controllers) {
        MediaController best = null;
        if (controllers != null) {
            for (int i = 0; i < controllers.size(); i++) {
                MediaController c = controllers.get(i);
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
                if (best == null && c.getMetadata() != null) {
                    best = c;
                }
            }
            if (best == null && !controllers.isEmpty()) {
                best = controllers.get(0);
            }
        }
        if (controller != null) {
            controller.unregisterCallback(callback);
        }
        controller = best;
        if (controller != null) {
            controller.registerCallback(callback);
        }
        listener.onMediaChanged();
    }

    private void detach() {
        if (controller != null) {
            controller.unregisterCallback(callback);
            controller = null;
        }
    }

    public boolean hasSession() {
        return controller != null;
    }

    public String title() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) {
            return null;
        }
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        return t == null ? m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) : t;
    }

    public String artist() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) {
            return null;
        }
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) {
            a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        return a;
    }

    public Bitmap art() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) {
            return null;
        }
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        return b == null ? m.getBitmap(MediaMetadata.METADATA_KEY_ART) : b;
    }

    public String sourcePackage() {
        return controller == null ? null : controller.getPackageName();
    }

    public boolean isPlaying() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Progress 0..1, or -1 when unknown. Extrapolates from the last state update. */
    public float progress() {
        if (controller == null) {
            return -1f;
        }
        MediaMetadata m = controller.getMetadata();
        PlaybackState s = controller.getPlaybackState();
        if (m == null || s == null) {
            return -1f;
        }
        long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (dur <= 0) {
            return -1f;
        }
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            long elapsed = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
            pos += (long) (elapsed * s.getPlaybackSpeed());
        }
        if (pos < 0) {
            pos = 0;
        }
        if (pos > dur) {
            pos = dur;
        }
        return (float) pos / (float) dur;
    }

    public void playPause() {
        if (controller == null) {
            return;
        }
        if (isPlaying()) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    public void next() {
        if (controller != null) {
            controller.getTransportControls().skipToNext();
        }
    }

    public void previous() {
        if (controller != null) {
            controller.getTransportControls().skipToPrevious();
        }
    }

    /** Open the media app itself (session activity if offered, else its launcher intent). */
    public void openPlayer() {
        if (controller == null) {
            return;
        }
        try {
            if (controller.getSessionActivity() != null) {
                controller.getSessionActivity().send();
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            android.content.Intent i =
                    ctx.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (i != null) {
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        } catch (Exception ignored) {
        }
    }
}
