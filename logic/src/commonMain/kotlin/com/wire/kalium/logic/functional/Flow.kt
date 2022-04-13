package com.wire.kalium.logic.functional

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take

suspend fun <A, B> Collection<A>.flatMapFromIterable(
    block: suspend (A) -> Flow<B>
): Flow<List<B>> {
    return flow {
        val result = mutableListOf<B>()
        this@flatMapFromIterable.forEach { a ->
            block(a)
                .take(1)
                .collect { b ->
                    result.add(b)
                    if (result.size == this@flatMapFromIterable.size) emit(result)
                }
        }
    }
}
