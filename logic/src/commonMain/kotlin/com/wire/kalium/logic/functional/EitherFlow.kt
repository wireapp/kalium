package com.wire.kalium.logic.functional

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun <L, R> Flow<Either<L, R>>.onlyRight(): Flow<R> = filter { it.isRight() }.map { (it as Either.Right<R>).value }

fun <L, R, T> Flow<Either<L, R>>.mapLeft(block: suspend (L) -> T): Flow<Either<T, R>> =
    map { it.fold({ l -> Either.Left(block(l)) }) { r -> Either.Right(r) } }

fun <L, R, T> Flow<Either<L, R>>.mapRight(block: suspend (R) -> T): Flow<Either<L, T>> =
    map { it.fold({ l -> Either.Left(l) }) { r -> Either.Right(block(r)) } }

fun <L, R> Flow<Either<L, R>>.mapToRightOr(value: R): Flow<R> = map { it.getOrElse(value) }

fun <L, R, T> Flow<Either<L, R>>.flatMapRightWithEither(block: suspend (R) -> Flow<Either<L, T>>): Flow<Either<L, T>> =
    flatMapLatest { it.fold({ l -> flowOf(Either.Left(l)) }) { r -> block(r) } }
