package com.alajag.rentals

data class LedgerAmount(val kind: String, val amount: Long)

object LedgerMath {
    fun balance(entries: Iterable<LedgerAmount>): Long {
        var result = 0L
        entries.forEach { entry ->
            require(entry.amount > 0) { "Ledger amounts must be positive" }
            result = when (entry.kind) {
                "RENT", "CHARGE" -> Math.addExact(result, entry.amount)
                "PAYMENT", "CREDIT" -> Math.subtractExact(result, entry.amount)
                else -> throw IllegalArgumentException("Unknown ledger kind: " + entry.kind)
            }
        }
        return result
    }
}
