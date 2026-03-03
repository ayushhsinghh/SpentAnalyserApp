package com.oracle.ee.spentanalyser.domain.model

data class SystemStatus(
    val currentTask: String = "Idle",
    val memoryUsageMb: Long = 0,     // Total PSS in MB (Includes Native/GPU allocations)
    val maxMemoryMb: Long = 0,       // Still kept for backward compatibility and basic OS hints
    val thermalHeadroomNanos: Long = 0L, // Thermal hint headroom
    val activeModelName: String? = null,
    val hardwareBackend: String = "CPU",
    val activeErrors: List<String> = emptyList(),
    val totalInferences: Int = 0,
    val successfulInferences: Int = 0
)
