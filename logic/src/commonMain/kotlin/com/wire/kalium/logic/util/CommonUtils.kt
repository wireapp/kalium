package com.wire.kalium.logic.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun Boolean.toInt() = if (this) 1 else 0

/*
    Proper file name with copy counter has a space between, like this: "file_name (1).jpg", if the name has a number in brackets but without
    a space then it's considered a part of its name: copy of "file_name(1).jpg" will be "file_name(1) (1).jpg".
    This is how it usually works on many operating systems.
 */
fun buildFileName(name: String, extension: String? = null, copyCounter: Int = 0): String {
    val nameWithCopyCounter = if (copyCounter <= 0) name else "$name ($copyCounter)"
    return extension?.let { "$nameWithCopyCounter.$extension" } ?: nameWithCopyCounter
}

fun String.splitFileExtension(): Pair<String, String?> {
    val splitElements = split(".")
    val startsWithADot = splitElements.isNotEmpty() && splitElements.first().isEmpty()
    // Most authors define extension in a way that doesn't allow more than one in the same file name,
    // .tar.gz actually represents nested transformations, .tar is only for informational purposes and `gz` is the final extension.
    val extension: String? = when {
        startsWithADot && splitElements.size > 2 -> splitElements.last()
        !startsWithADot && splitElements.size > 1 -> splitElements.last()
        else -> null
    }
    val name = extension?.let { this.removeSuffix(".$it") } ?: this
    return (name to extension)
}

fun String.splitFileExtensionAndCopyCounter(): Triple<String, Int, String?> {
    val (name, extension) = this.splitFileExtension()
    val copyCounterRegex = " \\(\\d+\\)\$".toRegex()
    val (nameWithoutCopyCounter, copyCounter) = copyCounterRegex.find(name)?.let {
        val copyCounter = it.value.removePrefix(" (").removeSuffix(")").toIntOrNull() ?: 0
        val nameWithoutCopyCounter = name.removeSuffix(it.value)
        (nameWithoutCopyCounter to copyCounter)
    } ?: (name to 0)
    return Triple(nameWithoutCopyCounter, copyCounter, extension)
}

fun String.fileExtension(): String? = splitFileExtension().second

@OptIn(ExperimentalContracts::class)
fun Int?.isGreaterThan(other: Int?): Boolean {
    contract {
        returns(true) implies (this@isGreaterThan != null)
        returns(true) implies (other != null)
    }
    return this is Int && other is Int && this > other
}
