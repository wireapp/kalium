/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import kotlin.test.fail

inline infix fun <L, R> Either<L, R>.shouldSucceed(crossinline successAssertion: (R) -> Unit) =
    this.fold({ fail("Expected a Right value but got Left") }) { successAssertion(it) }

fun <L, R> Either<L, R>.shouldSucceed() = shouldSucceed { }

inline infix fun <L, R> Either<L, R>.shouldFail(crossinline failAssertion: (L) -> Unit): Unit =
    this.fold({ failAssertion(it) }) { fail("Expected a Left value but got Right") }

fun <L, R> Either<L, R>.shouldFail(): Unit = shouldFail { }
