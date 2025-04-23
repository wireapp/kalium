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
package com.wire.kalium.logic.util.arrangement.provider

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import io.mockative.coEvery
import io.mockative.mock

interface CurrentClientIdProviderArrangement {

    val currentClientIdProvider: CurrentClientIdProvider
    suspend fun withCurrentClientIdSuccess(currentClientId: ClientId)
    suspend fun withCurrentClientIdFailure(error: CoreFailure = StorageFailure.DataNotFound)
}

class CurrentClientIdProviderArrangementImpl : CurrentClientIdProviderArrangement {

    override val currentClientIdProvider = mock(CurrentClientIdProvider::class)

    override suspend fun withCurrentClientIdSuccess(currentClientId: ClientId) {
        coEvery {
            currentClientIdProvider.invoke()
        }.returns(Either.Right(currentClientId))
    }

    override suspend fun withCurrentClientIdFailure(error: CoreFailure) {
        coEvery {
            currentClientIdProvider.invoke()
        }.returns(Either.Left(error))
    }
}

