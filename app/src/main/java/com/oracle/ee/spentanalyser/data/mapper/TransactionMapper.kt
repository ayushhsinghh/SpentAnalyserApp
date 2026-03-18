package com.oracle.ee.spentanalyser.data.mapper

import com.oracle.ee.spentanalyser.data.database.TransactionEntity
import com.oracle.ee.spentanalyser.domain.model.Transaction

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    merchant = merchant,
    category = category,
    date = date,
    type = type,
    sourceSmsHash = sourceSmsHash,
    timestamp = timestamp
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    merchant = merchant,
    category = category,
    date = date,
    type = type,
    sourceSmsHash = sourceSmsHash,
    timestamp = timestamp
)
