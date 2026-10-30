package com.alajag.rentals

import org.junit.Test

class DateRangeRulesTest {
    @Test fun acceptsBothDatesBlank() {
        DateRangeRules.validate("", "")
    }

    @Test fun acceptsValidSingleAndClosedRanges() {
        DateRangeRules.validate("2026-10-01", "")
        DateRangeRules.validate("", "2026-10-30")
        DateRangeRules.validate("2026-10-01", "2026-10-30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleCalendarDate() {
        DateRangeRules.validate("2026-02-30", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTrailingCharacters() {
        DateRangeRules.validate("2026-10-01junk", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedRange() {
        DateRangeRules.validate("2026-11-01", "2026-10-31")
    }
}
