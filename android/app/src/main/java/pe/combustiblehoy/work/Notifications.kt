package pe.combustiblehoy.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pe.combustiblehoy.R
import pe.combustiblehoy.core.PriceAlerts
import pe.combustiblehoy.core.PriceChange
import pe.combustiblehoy.ui.MainActivity

object Notifications {
    private const val CHANNEL_ID = "price_alerts"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_alerts_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showPriceChanges(context: Context, changes: List<PriceChange>) {
        if (changes.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return

        val drops = changes.count { it.isDrop }
        val title = when {
            changes.size == 1 -> changes.first().stationName
            drops == changes.size -> "Bajó el precio en $drops de tus grifos"
            drops == 0 -> "Subió el precio en ${changes.size} de tus grifos"
            else -> "Cambió el precio en ${changes.size} de tus grifos"
        }
        val lines = changes.map { "${it.stationName}: ${PriceAlerts.describe(it)}" }
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) }
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fuel)
            .setContentTitle(title)
            .setContentText(if (changes.size == 1) PriceAlerts.describe(changes.first()) else lines.first())
            .setStyle(style)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
