package com.wire.kalium.network.utils

private const val NOT_FOUND_ERROR_CODE = 404

enum class HttpErrorCodes(val code: Int) {
    NOT_FOUND(NOT_FOUND_ERROR_CODE)
}
