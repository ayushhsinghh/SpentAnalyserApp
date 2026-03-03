package com.oracle.ee.spentanalyser.domain.model

data class SystemStatus(
    val currentTask: String = "Idle",
    val memoryUsageMb: Long = 0,
    val maxMemoryMb: Long = 0,
    val activeModelName: String? = null,
    val hardwareBackend: String = "CPU",
    val activeErrors: List<String> = emptyList(),
    val totalInferences: Int = 0,
    val successfulInferences: Int = 0
)
