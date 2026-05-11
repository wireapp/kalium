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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.common.functional.Either
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface PersistMessageUseCaseArrangement {
    val persistMessageUseCase: PersistMessageUseCase
    suspend fun withPersistingMessage(
        result: Either<CoreFailure, Unit>,
        messageMatcher: (Message.Standalone) -> Boolean = { true }
    ): PersistMessageUseCaseArrangementImpl
}

internal open class PersistMessageUseCaseArrangementImpl : PersistMessageUseCaseArrangement {

    override val persistMessageUseCase: PersistMessageUseCase = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)

    override suspend fun withPersistingMessage(
        result: Either<CoreFailure, Unit>,
        messageMatcher: (Message.Standalone) -> Boolean
    ) = apply {
        everySuspend {
            persistMessageUseCase.invoke(matches { messageMatcher(it) })
        }.returns(result)
    }
}
