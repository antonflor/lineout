package xyz.montylabs.lineout;

import android.service.notification.NotificationListenerService;

/**
 * Intentionally empty. Granting this listener notification access is what
 * authorizes MediaSessionManager.getActiveSessions() for the now-playing card.
 * We never read notification content.
 */
public class MediaAccessListener extends NotificationListenerService {
}
