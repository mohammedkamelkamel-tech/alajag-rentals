package com.alajag.rentals

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateRangeRules {
    private fun parseExact(value: String): Date? {
        if (value.isBlank()) return null
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return null
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val parsed = format.parse(value, position)
        return if (parsed != null && position.index == value.length) parsed else null
    }

    fun validate(from: String, to: String) {
        val start = parseExact(from)
        val end = parseExact(to)
        require(from.isBlank() || start != null) {
            "تاريخ البداية يجب أن يكون تاريخاً صحيحاً بصيغة YYYY-MM-DD"
        }
        require(to.isBlank() || end != null) {
            "تاريخ النهاية يجب أن يكون تاريخاً صحيحاً بصيغة YYYY-MM-DD"
        }
        require(start == null || end == null || !start.after(end)) {
            "تاريخ البداية يجب ألا يتجاوز تاريخ النهاية"
        }
    }
}
