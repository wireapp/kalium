package com.wire.kalium.logic.util

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import kotlin.test.fail

inline infix fun <L, R> Either<L, R>.shouldSucceed(crossinline successAssertion: (R) -> Unit) =
    this.fold({ fail("Expected a Right value but got Left") }) { successAssertion(it) }

fun <L> Either<L, Unit>.shouldSucceed() = shouldSucceed { }

inline infix fun <L, R> Either<L, R>.shouldFail(crossinline failAssertion: (L) -> Unit): Unit =
    this.fold({ failAssertion(it) }) { fail("Expected a Left value but got Right") }

fun <L> Either<L, Unit>.shouldFail(): Unit = shouldFail { Unit }
