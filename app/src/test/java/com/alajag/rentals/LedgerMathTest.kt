package com.alajag.rentals

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerMathTest {
    @Test fun rentLessPartialPaymentLeavesBalance() {
        val result = LedgerMath.balance(listOf(
            LedgerAmount("RENT", 133000),
            LedgerAmount("PAYMENT", 50000)
        ))
        assertEquals(83000L, result)
    }

    @Test fun overpaymentBecomesCreditAndOffsetsNextRent() {
        val result = LedgerMath.balance(listOf(
            LedgerAmount("RENT", 100000),
            LedgerAmount("PAYMENT", 130000),
            LedgerAmount("RENT", 100000)
        ))
        assertEquals(70000L, result)
    }

    @Test fun creditAdjustmentReducesOutstanding() {
        val result = LedgerMath.balance(listOf(
            LedgerAmount("RENT", 90000),
            LedgerAmount("CREDIT", 15000),
            LedgerAmount("CHARGE", 5000)
        ))
        assertEquals(80000L, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownLedgerKind() {
        LedgerMath.balance(listOf(LedgerAmount("UNKNOWN", 10)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroOrNegativeEntries() {
        LedgerMath.balance(listOf(LedgerAmount("PAYMENT", 0)))
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsBalanceOverflowInsteadOfWrapping() {
        LedgerMath.balance(listOf(
            LedgerAmount("RENT", Long.MAX_VALUE),
            LedgerAmount("CHARGE", 1)
        ))
    }
}
