package com.taras.nexusplay.app.shared.input.inputclass

import com.taras.nexusplay.app.shared.input.InputKey

object InputClassUnknown : InputClass {
    override fun getInputKeys(): Set<InputKey> {
        return emptySet()
    }

    override fun getAxesMap(): Map<Int, Int> {
        return emptyMap()
    }
}
