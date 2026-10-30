package com.alajag.rentals

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar

class RentWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val now = Calendar.getInstance()
        RentalDb(applicationContext).generateDuePeriods(now)
        if (now.get(Calendar.DAY_OF_MONTH) == now.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val channel = "rent_due"
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channel) == null) {
                manager.createNotificationChannel(NotificationChannel(channel, "تنبيهات الإيجارات", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val allowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (allowed) {
                val notification = NotificationCompat.Builder(applicationContext, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("العجاج - الإيجارات")
                    .setContentText("تذكير: راجع استحقاقات الإيجارات لهذا الشهر")
                    .setAutoCancel(true).build()
                manager.notify(2026, notification)
            }
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
