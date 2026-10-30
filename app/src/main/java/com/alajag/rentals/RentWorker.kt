package com.alajag.rentals

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar

class RentWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val db = Db(applicationContext)
        val now = Calendar.getInstance()
        db.generateDuePeriods(now)
        if (now.get(Calendar.DAY_OF_MONTH) == now.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val channel = "rent_due"
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channel) == null) {
                manager.createNotificationChannel(NotificationChannel(channel, "تنبيهات الإيجارات", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val notification = NotificationCompat.Builder(applicationContext, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("العجاج - الإيجارات")
                .setContentText("حان موعد استحقاق الإيجارات الشهرية. افتح التطبيق لمراجعة الأرصدة.")
                .setAutoCancel(true).build()
            manager.notify(2026, notification)
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
