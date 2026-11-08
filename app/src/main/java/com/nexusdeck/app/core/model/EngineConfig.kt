package com.nexusdeck.app.core.model

data class EngineConfig(
    val driverName: String,
    val targetResolution: String,
    val rendererBackend: String,
    val dxvkVersion: String,
    val cpuDynarecPreset: String
)
