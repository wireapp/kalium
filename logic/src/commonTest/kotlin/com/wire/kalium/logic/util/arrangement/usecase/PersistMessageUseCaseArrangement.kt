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
package com.wire.kalium.logic.util.arrangement.usecase

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface PersistMessageUseCaseArrangement {
    val persistMessageUseCase: PersistMessageUseCase
    suspend fun withPersistingMessage(
        result: Either<CoreFailure, Unit>,
        messageMatcher: Matcher<Message.Standalone> = AnyMatcher(valueOf())
    ): PersistMessageUseCaseArrangementImpl
}

internal open class PersistMessageUseCaseArrangementImpl : PersistMessageUseCaseArrangement {
    @Mock
    override val persistMessageUseCase: PersistMessageUseCase = mock(PersistMessageUseCase::class)

    override suspend fun withPersistingMessage(
        result: Either<CoreFailure, Unit>,
        messageMatcher: Matcher<Message.Standalone>
    ) = apply {
        coEvery {
            persistMessageUseCase.invoke(matches { messageMatcher.matches(it) })
        }.returns(result)
    }
}
