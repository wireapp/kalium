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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either

/**
 * Use case to get current client id.
 */
interface GetCurrentClientIdUseCase {
    suspend operator fun invoke(): Either<CoreFailure, ClientId>
}

internal class GetCurrentClientIdUseCaseImpl internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider
) : GetCurrentClientIdUseCase {
    override suspend fun invoke(): Either<CoreFailure, ClientId> {
        return currentClientIdProvider()
    }
}
