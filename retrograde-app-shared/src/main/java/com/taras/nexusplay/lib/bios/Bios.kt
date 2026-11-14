package com.taras.nexusplay.lib.bios

import com.taras.nexusplay.lib.library.SystemID

data class Bios(
    val libretroFileName: String,
    val md5: String,
    val description: String,
    val systemID: SystemID,
    val externalCRC32: String? = null,
    val externalName: String? = null,
) {
    fun displayName() = externalName ?: libretroFileName
}
