/**
 * Copyright (C) 2019 Fernando Cejas Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wire.kalium.logic.functional

import com.wire.kalium.logic.functional.Either.Left
import com.wire.kalium.logic.functional.Either.Right
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Represents a value of one of two possible types (a disjoint union).
 * Instances of [Either] are either an instance of [Left] or [Right].
 * FP Convention dictates that [Left] is used for "failure"
 * and [Right] is used for "success".
 *
 * @see Left
 * @see Right
 */
sealed class Either<out L, out R> {
    /** * Represents the left side of [Either] class which by convention is a "Failure". */
    data class Left<out L>(val value: L) : Either<L, Nothing>()

    /** * Represents the right side of [Either] class which by convention is a "Success". */
    data class Right<out R>(val value: R) : Either<Nothing, R>()


    /**
     * Creates a Left type.
     * @see Left
     */
    fun <L> left(a: L) = Left(a)

    /**
     * Creates a right type.
     * @see Right
     */
    fun <R> right(b: R) = Right(b)
}


/**
 * Applies fnL if this is a Left or fnR if this is a Right.
 * @see Left
 * @see Right
 */
inline fun <L, R, T> Either<L, R>.fold(fnL: (L) -> T, fnR: (R) -> T): T = nullableFold(fnL, fnR)!!


/**
 * Applies fnL if this is a Left or fnR if this is a Right.
 * @see Left
 * @see Right
 */
inline fun <L, R, T> Either<L, R>.nullableFold(fnL: (L) -> T?, fnR: (R) -> T?): T? =
    when (this) {
        is Left -> fnL(value)
        is Right -> fnR(value)
    }


/**
 * Returns true if this is a Right, false otherwise.
 * @see Right
 */
@OptIn(ExperimentalContracts::class)
fun <L, R> Either<L, R>.isRight(): Boolean {
    contract {
        returns(true) implies (this@isRight is Right<R>)
        returns(false) implies (this@isRight is Left<L>)
    }
    return this is Right<R>
}

/**
 * Returns true if this is a Left, false otherwise.
 * @see Left
 */
@OptIn(ExperimentalContracts::class)
fun <L, R> Either<L, R>.isLeft(): Boolean {
    contract {
        returns(true) implies (this@isLeft is Left<L>)
        returns(false) implies (this@isLeft is Right<R>)
    }
    return this is Left<L>
}

/**
 * Right-biased flatMap() FP convention which means that Right is assumed to be the default case
 * to operate on. If it is Left, operations like map, flatMap, ... return the Left value unchanged.
 */
inline fun <T, L, R> Either<L, R>.flatMap(fn: (R) -> Either<L, T>): Either<L, T> =
    when (this) {
        is Left -> Left(value)
        is Right -> fn(value)
    }

/**
 * Left-biased onFailure() FP convention dictates that when this class is Left, it'll perform
 * the onFailure functionality passed as a parameter, but, overall will still return an [Either]
 * object, so you chain calls.
 */
inline fun <L, R> Either<L, R>.onFailure(fn: (failure: L) -> Unit): Either<L, R> =
    this.apply { if (this is Left) fn(value) }

/**
 * Right-biased onSuccess() FP convention dictates that when this class is Right, it'll perform
 * the onSuccess functionality passed as a parameter, but, overall will still return an [Either]
 * object, so you chain calls.
 */
inline fun <L, R> Either<L, R>.onSuccess(fn: (success: R) -> Unit): Either<L, R> =
    this.apply { if (this is Right) fn(value) }

/**
 * Right-biased map() FP convention which means that Right is assumed to be the default case
 * to operate on. If it is Left, operations like map, flatMap, ... return the Left value unchanged.
 */
inline fun <T, L, R> Either<L, R>.map(fn: (R) -> (T)): Either<L, T> =
    when (this) {
        is Left -> Left(value)
        is Right -> Right(fn(value))
    }

/**
 * Left-biased map() FP convention which means that Right is assumed to be the default case
 * to operate on. If it is Right, operations like map, flatMap, ... return the Right value unchanged.
 */
inline fun <T, L, R> Either<L, R>.mapLeft(fn: (L) -> (T)): Either<T, R> =
    when (this) {
        is Left -> Left(fn(value))
        is Right -> Right(value)
    }

/**
 * Returns the value from this `Right` or the given argument if this is a `Left`.
 * Right(12).getOrElse(17) RETURNS 12 and Left(12).getOrElse(17) RETURNS 17
 */
fun <L, R> Either<L, R>.getOrElse(value: R): R =
    when (this) {
        is Left -> value
        is Right -> this.value
    }

/**
 * Folds a list into an Either while it doesn't go Left.
 * Allows for accumulation of value through iterations.
 * @return the final accumulated value if there are NO Left results, or the first Left result otherwise.
 */
inline fun <T, L, R> Iterable<T>.foldToEitherWhileRight(initialValue: R, fn: (item: T, accumulated: R) -> Either<L, R>): Either<L, R> {
    return this.fold<T, Either<L, R>>(Right(initialValue)) { acc, item ->
        acc.flatMap { accumulatedValue -> fn(item, accumulatedValue) }
    }
}
