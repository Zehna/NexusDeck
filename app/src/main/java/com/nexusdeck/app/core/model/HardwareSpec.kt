package com.nexusdeck.app.core.model

data class HardwareSpec(
    val socModel: String,
    val gpuModel: String,
    val totalRamGb: Int,
    val vulkanVersion: String
)
