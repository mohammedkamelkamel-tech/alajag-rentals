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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RentWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val now = Calendar.getInstance()
        val db = RentalDb(applicationContext)
        db.generateDuePeriods(now)

        // Keep a rolling automatic local backup inside the app's private storage.
        val folder = File(applicationContext.filesDir, "backups")
        if (!folder.exists()) folder.mkdirs()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val backup = File(folder, "alajag-backup-" + day + ".json")
        backup.writeText(db.backup(), Charsets.UTF_8)
        folder.listFiles()?.filter { it.name.startsWith("alajag-backup-") }
            ?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }

        if (now.get(Calendar.DAY_OF_MONTH) == now.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val prefs = applicationContext.getSharedPreferences("rent_notifications", Context.MODE_PRIVATE)
            if (prefs.getString("last_month", "") != month) {
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
                    prefs.edit().putString("last_month", month).apply()
                }
            }
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
