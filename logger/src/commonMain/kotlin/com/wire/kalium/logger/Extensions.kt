package com.wire.kalium.logger

private const val START_INDEX = 0
private const val END_INDEX = 9

fun String.obfuscateId(): String {
    return if (this.length >= END_INDEX) {
        this.substring(START_INDEX, END_INDEX)
    } else {
        this
    }
}
