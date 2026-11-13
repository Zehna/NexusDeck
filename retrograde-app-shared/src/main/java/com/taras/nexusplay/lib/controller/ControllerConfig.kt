package com.taras.nexusplay.lib.controller

import com.taras.nexusplay.touchinput.radial.sensors.TiltConfiguration
import com.taras.nexusplay.touchinput.radial.settings.TouchControllerID
import java.io.Serializable

data class ControllerConfig(
    val name: String,
    val displayName: Int,
    val touchControllerID: TouchControllerID,
    val allowTouchRotation: Boolean = false,
    val allowTouchOverlay: Boolean = true,
    val mergeDPADAndLeftStickEvents: Boolean = false,
    val libretroDescriptor: String? = null,
    val libretroId: Int? = null,
    val tiltConfigurations: List<TiltConfiguration> = emptyList(),
) : Serializable {
    fun getTouchControllerConfig(): TouchControllerID.Config {
        return TouchControllerID.getConfig(touchControllerID)
    }
}
