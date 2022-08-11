package com.wire.kalium.logic.functional

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

fun <L, R> Flow<Either<L, R>>.onlyRight(): Flow<R> = filter { it.isRight() }.map { (it as Either.Right<R>).value }

fun <L, R, T> Flow<Either<L, R>>.mapLeft(block: suspend (L) -> T): Flow<Either<T, R>> =
    map { it.fold({ l -> Either.Left(block(l)) }) { r -> Either.Right(r) } }

fun <L, R, T> Flow<Either<L, R>>.mapRight(block: suspend (R) -> T): Flow<Either<L, T>> =
    map { it.fold({ l -> Either.Left(l) }) { r -> Either.Right(block(r)) } }

fun <L, R, T : Any> Flow<Either<L, R>>.foldEither(fnL: suspend (L) -> T, fnR: suspend (R) -> T): Flow<T> =
    map { it.fold({ l -> fnL(l) }, { r -> fnR(r) }) }
