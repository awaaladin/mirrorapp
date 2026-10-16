package com.mirror.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mirror.app.R
import com.mirror.app.data.network.NetworkConfig
import com.mirror.app.data.network.createMirrorBackendApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives push notifications sent by mirrorcentral via Firebase Cloud Messaging - today this is
 * only used for "a new version of Mirror is available", since there's no Play Store auto-update
 * to rely on. Tapping the notification opens the browser at the release's download page rather
 * than updating in-app (installing an APK always needs the user's explicit action on Android).
 */
class MirrorMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
            val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 20)
            registerDeviceToken(localApi, deployedApi, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val downloadUrl = message.data["download_url"]
        showNotification(title, body, downloadUrl)
    }

    private fun showNotification(title: String, body: String, downloadUrl: String?) {
        val channelId = getString(R.string.notification_channel_updates_id)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.notification_channel_updates_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val contentIntent = downloadUrl?.let {
            PendingIntent.getActivity(
                this,
                0,
                Intent(Intent.ACTION_VIEW, Uri.parse(it)),
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val UPDATE_NOTIFICATION_ID = 1001
    }
}
