package com.alajag.rentals

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Schedule {
    fun install(context: Context) {
        val request = PeriodicWorkRequestBuilder<RentWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "alajag_monthly_rent_check", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
