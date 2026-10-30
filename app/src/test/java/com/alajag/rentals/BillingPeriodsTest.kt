package com.alajag.rentals

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingPeriodsTest {
    @Test fun newTenantBeforeNovemberStartsInNovember() {
        assertEquals("2026-11", BillingPeriods.firstPeriodForNewTenant("2026-10"))
    }

    @Test fun tenantAddedInNovemberStartsFollowingMonth() {
        assertEquals("2026-12", BillingPeriods.firstPeriodForNewTenant("2026-11"))
    }

    @Test fun tenantAddedLaterStartsFollowingMonth() {
        assertEquals("2027-02", BillingPeriods.firstPeriodForNewTenant("2027-01"))
    }

    @Test fun handlesYearBoundary() {
        assertEquals("2027-01", BillingPeriods.nextPeriod("2026-12"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedPeriod() {
        BillingPeriods.nextPeriod("2026-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPeriod() {
        BillingPeriods.firstPeriodForNewTenant("2026-13")
    }
}
