package com.oracle.ee.spentanalyser.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val date: String,
    val type: String,
    val sourceSmsHash: String // the hash to tie it back to the SMS
)
