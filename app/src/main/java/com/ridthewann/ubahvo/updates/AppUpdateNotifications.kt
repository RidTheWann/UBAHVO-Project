package com.ridthewann.ubahvo.updates

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ridthewann.ubahvo.R

internal object AppUpdateNotifications {
    private const val CHANNEL_ID = "ubahvo_app_updates"
    private const val NOTIFICATION_ID = 1601

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    fun show(context: Context, update: AppUpdateInfo): Boolean {
        if (!canPost(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Pembaruan UBAHVO",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Pemberitahuan saat versi baru UBAHVO tersedia."
                }
            )
        }

        val openRelease = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openRelease,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Pembaruan UBAHVO tersedia")
            .setContentText("Versi " + update.versionName + " siap diunduh. Ketuk untuk melihat pembaruan.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "UBAHVO versi " + update.versionName + " sudah tersedia. Buka halaman rilis untuk mengunduh APK dan ikuti konfirmasi pemasangan Android."
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
