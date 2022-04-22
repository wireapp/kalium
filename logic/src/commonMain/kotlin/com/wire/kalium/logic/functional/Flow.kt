package com.wire.kalium.logic.functional

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

suspend inline fun <A, B> Collection<A>.flatMapFromIterable(
    crossinline block: suspend (A) -> Flow<B>
): Flow<List<B>> = flow {
    val result = mutableListOf<B>()

    if (isEmpty()) emit(result)

    forEach { a -> result.add(block(a).first()) }

    emit(result)
}

fun <T1, T2> Flow<T1>.combine(flow: Flow<T2>): Flow<Pair<T1, T2>> = combine(flow) { t1, t2 -> t1 to t2 }
