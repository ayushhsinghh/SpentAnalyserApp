package com.oracle.ee.spentanalyser.data.database

data class MonthlySpendProjection(
    val monthYear: String,
    val totalAmount: Double
)
