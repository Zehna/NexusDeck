package com.taras.nexusplay.common.kotlin

fun Int.kiloBytes(): Int = this * 1000

fun Int.megaBytes(): Int = this.kiloBytes() * 1000
