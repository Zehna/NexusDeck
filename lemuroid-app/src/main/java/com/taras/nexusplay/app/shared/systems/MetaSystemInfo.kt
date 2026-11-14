package com.taras.nexusplay.app.shared.systems

import android.content.Context
import com.taras.nexusplay.lib.library.MetaSystemID

data class MetaSystemInfo(val metaSystem: MetaSystemID, val count: Int) {
    fun getName(context: Context) = context.resources.getString(metaSystem.titleResId)
}
