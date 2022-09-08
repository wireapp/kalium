package com.wire.kalium.logger

private const val START_INDEX = 0
private const val END_INDEX_ID = 7
private const val END_INDEX_DOMAIN = 2
private const val END_INDEX_URL_PATH = 3

fun String.obfuscateId(): String {
    return if (this.length >= END_INDEX_ID) {
        this.substring(START_INDEX, END_INDEX_ID)
    } else {
        this
    }
}

fun String.obfuscateDomain(): String {
    return if (this.length >= END_INDEX_DOMAIN) {
        this.substring(START_INDEX, END_INDEX_DOMAIN)
    } else {
        this
    }
}

fun String.obfuscateUrlPath(): String {
    return if (this.length >= END_INDEX_DOMAIN) {
        "${this.substring(START_INDEX, END_INDEX_URL_PATH)}****"
    } else {
        this
    }
}
