package com.taras.nexusplay.common.kotlin

fun <T> lazySequenceOf(vararg producers: () -> T): Sequence<T> {
    return producers.asSequence()
        .map { it() }
}
