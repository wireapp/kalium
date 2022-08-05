package com.wire.kalium.logic.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.IOException

inline fun <reified T> flowThatFailsOnFirstTime(exception: Throwable = IOException("Oops")): Flow<T> {
    var hasFailed = false
    val sourceFlow = flow<T> {
        if (!hasFailed) {
            hasFailed = true
            throw exception
        }
    }
    return sourceFlow
}
