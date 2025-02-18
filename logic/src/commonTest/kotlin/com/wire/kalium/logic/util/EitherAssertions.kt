/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.util

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.test.fail

@OptIn(ExperimentalContracts::class)
inline infix fun <L, R> Either<L, R>.shouldSucceed(crossinline successAssertion: (R) -> Unit) {
    contract { returns() implies (this@shouldSucceed is Either.Right<R>) }
    this.fold({ fail("Expected a Right value but got Left: $it") }) { successAssertion(it) }
}

@OptIn(ExperimentalContracts::class)
fun <L, R> Either<L, R>.shouldSucceed() {
    contract { returns() implies (this@shouldSucceed is Either.Right<R>) }
    shouldSucceed { }
}

@OptIn(ExperimentalContracts::class)
inline infix fun <L, R> Either<L, R>.shouldFail(crossinline failAssertion: (L) -> Unit) {
    contract { returns() implies (this@shouldFail is Either.Left<L>) }
    this.fold({ failAssertion(it) }) { fail("Expected a Left value but got Right") }
}

@OptIn(ExperimentalContracts::class)
fun <L, R> Either<L, R>.shouldFail() {
    contract { returns() implies (this@shouldFail is Either.Left<L>) }
    shouldFail { }
}
