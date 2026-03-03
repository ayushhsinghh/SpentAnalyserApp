package com.oracle.ee.spentanalyser.data.mapper

import com.oracle.ee.spentanalyser.data.database.SmsLogEntity
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog

fun SmsLogEntity.toDomain(): SmsLog = SmsLog(
    uniqueHash = uniqueHash,
    sender = sender,
    body = body,
    timestamp = timestamp,
    status = ParseStatus.valueOf(status),
    errorMessage = errorMessage,
    rawLlmOutput = rawLlmOutput
)

fun SmsLog.toEntity(): SmsLogEntity = SmsLogEntity(
    uniqueHash = uniqueHash,
    sender = sender,
    body = body,
    timestamp = timestamp,
    status = status.name,
    errorMessage = errorMessage,
    rawLlmOutput = rawLlmOutput
)
