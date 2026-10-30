package com.alajag.rentals

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Schedule {
    fun install(context: Context) {
        val manager = WorkManager.getInstance(context)

        // Run once on first launch so the first automatic backup is not delayed
        // until the first 12-hour periodic window.
        val initial = OneTimeWorkRequestBuilder<RentWorker>().build()
        manager.enqueueUniqueWork(
            "alajag_initial_backup_and_rent_check",
            ExistingWorkPolicy.KEEP,
            initial
        )

        val request = PeriodicWorkRequestBuilder<RentWorker>(12, TimeUnit.HOURS).build()
        manager.enqueueUniquePeriodicWork(
            "alajag_monthly_rent_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
