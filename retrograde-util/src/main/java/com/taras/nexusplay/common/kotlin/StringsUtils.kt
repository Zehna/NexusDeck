package com.taras.nexusplay.common.kotlin

fun String.startsWithAny(strings: Collection<String>) = strings.any { this.startsWith(it) }
