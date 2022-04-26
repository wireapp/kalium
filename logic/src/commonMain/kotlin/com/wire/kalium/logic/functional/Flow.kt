package com.wire.kalium.logic.functional

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

suspend inline fun <A, B> Collection<A>.flatMapFromIterable(
    crossinline block: suspend (A) -> Flow<B>
): Flow<List<B>> = flow {
    val result = mutableListOf<B>()

    if (isEmpty()) emit(result)

    forEach { a -> result.add(block(a).first()) }

    emit(result)
}

fun <T1, T2> Flow<T1>.combine(flow: Flow<T2>): Flow<Pair<T1, T2>> = combine(flow) { t1, t2 -> t1 to t2 }

fun <L, R, TR> Flow<Either<L, R>>.mapRight(mapper: (R) -> TR): Flow<Either<L, TR>> =
    map { it.fold({ l -> Either.Left(l) }, { r -> Either.Right(mapper(r)) }) }

fun <L, R, TL> Flow<Either<L, R>>.mapLeft(mapper: (L) -> TL): Flow<Either<TL, R>> =
    map { it.fold({ l -> Either.Left(mapper(l)) }, { r -> Either.Right(r) }) }

fun <L, R, A> Flow<Either<L, R>>.foldEither(mapLeft: (L) -> A, mapRight: (R) -> A): Flow<A> =
    map { it.fold(mapLeft, mapRight) }
