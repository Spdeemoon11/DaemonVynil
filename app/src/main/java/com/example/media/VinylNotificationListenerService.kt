package com.example.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * VinylNotificationListenerService
 *
 * System-level NotificationListenerService fulfilling Android's security requirement
 * for reading active MediaSession metadata and transport controls from external media apps.
 *
 * Background:
 * On modern Android versions (Android 5.0+ through Android 14+), querying MediaSessionManager
 * for active sessions requires passing a valid ComponentName of an enabled NotificationListenerService.
 * Without this service enabled by the user in Android Settings, getActiveSessions() throws a SecurityException.
 *
 * This service operates purely as a passive observer:
 * - Detects when external media notifications are posted or removed
 * - Emits notifications to [MediaBridgeManager]
 * - Extracts MediaSession.Token from Notification.EXTRA_MEDIA_SESSION where available
 */
class VinylNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected to Android system.")
        instance = this
        MediaBridgeManager.getInstance(applicationContext).onNotificationListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected.")
        if (instance == this) {
            instance = null
        }
        MediaBridgeManager.getInstance(applicationContext).onNotificationListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        // Check if the posted notification contains an active MediaSession token
        val extras = sbn.notification?.extras ?: return
        val mediaSessionToken = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        if (mediaSessionToken != null) {
            Log.d(TAG, "Media notification posted from package: ${sbn.packageName}")
            MediaBridgeManager.getInstance(applicationContext).onMediaNotificationDetected(
                sbn.packageName,
                mediaSessionToken
            )
        } else {
            // Also notify bridge to refresh active sessions in case an existing session updated
            MediaBridgeManager.getInstance(applicationContext).refreshActiveSessions()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
        MediaBridgeManager.getInstance(applicationContext).refreshActiveSessions()
    }

    companion object {
        private const val TAG = "VinylNotificationService"

        @Volatile
        private var instance: VinylNotificationListenerService? = null

        /**
         * Returns the ComponentName of this service for use with MediaSessionManager.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, VinylNotificationListenerService::class.java)
        }

        /**
         * Checks whether notification listener access is currently enabled in Android system settings.
         */
        fun isAccessGranted(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) {
                return true
            }

            // Fallback check against Settings.Secure for legacy or non-standard Android OEM skins
            return try {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                flat != null && flat.contains(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Creates an Intent directing the user to Android's Notification Access settings page.
         */
        fun createSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
