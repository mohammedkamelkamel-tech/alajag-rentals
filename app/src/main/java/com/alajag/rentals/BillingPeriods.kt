package com.alajag.rentals

import java.util.Calendar
import java.util.Locale

object BillingPeriods {
    private const val FIRST_RENTAL_PERIOD = "2026-11"

    fun nextPeriod(currentPeriod: String): String {
        require(Regex("\\d{4}-\\d{2}").matches(currentPeriod)) { "Invalid month format" }
        val parts = currentPeriod.split("-")
        require(parts[1].toInt() in 1..12) { "Invalid month value" }
        val calendar = Calendar.getInstance().apply {
            clear()
            set(parts[0].toInt(), parts[1].toInt() - 1, 1)
            add(Calendar.MONTH, 1)
        }
        val next = String.format(Locale.US, "%04d-%02d",
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        return if (next < FIRST_RENTAL_PERIOD) FIRST_RENTAL_PERIOD else next
    }

    fun firstPeriodForNewTenant(currentPeriod: String): String = nextPeriod(currentPeriod)
}
