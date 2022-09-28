package com.wire.kalium.testservice.models

data class Audio(
    val durationInMillis: Int = 0,
    val normalizedLoudness: List<Int> = listOf(0)
)
