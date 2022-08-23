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

/**
 * map Flow<Either<L, R>> to the Flow<R>.
 * @param value that Flow emits if original Either.isLeft = true
 *
 * Example1:
 * `flowOf(Either.Right(1)).mapToRightOr(0)` will emit 1
 *
 * Example2:
 * `flowOf(Either.Left("Error")).mapToRightOr(0)` will emit 0
 */
fun <L, R> Flow<Either<L, R>>.mapToRightOr(value: R): Flow<R> = map { it.getOrElse(value) }

/**
 * [flatMapLatest] the Flow<Either<L, R>> into Flow<Either<L, T>>.
 * @param block function to run on Either.Right value and that returns Flow<Either<L, T>>
 *
 * Usecase for it:
 * we have 2 functions both of it returns Flow<Either> but one of it is depends on the Right value from the other
 * and we need to combine Right values from both of it, or emit 1 error (Either.Left) if it occurs on any step.
 * Use that fun to have better code style.
 *
 * Example:
 *
 * fun getUserId(): Either<Error, ID>
 * fun getFriendsFroUser(userId: ID): Either<Error, List<String>>
 *
 * data class MeWithFriends(val myId: ID, val friends: List<String>)
 *
 * val observeMeWithFriends: Flow<Either<Error, MeWithFriends>>  =
 *       getUserId().flatMapRightWithEither { id -> getFriendsFroUser(id).mapRight { MeWithFriends(id, it) }}
 *
 */
fun <L, R, T> Flow<Either<L, R>>.flatMapRightWithEither(block: suspend (R) -> Flow<Either<L, T>>): Flow<Either<L, T>> =
    flatMapLatest { it.fold({ l -> flowOf(Either.Left(l)) }) { r -> block(r) } }
