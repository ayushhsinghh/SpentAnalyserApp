package com.oracle.ee.spentanalyser.domain.model

data class Transaction(
    val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val date: String,
    val type: String,
    val sourceSmsHash: String,
    val timestamp: Long = System.currentTimeMillis()
)
