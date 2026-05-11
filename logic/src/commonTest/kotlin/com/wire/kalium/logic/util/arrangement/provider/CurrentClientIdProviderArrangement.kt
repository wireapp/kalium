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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.common.functional.Either
import dev.mokkery.everySuspend
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface CurrentClientIdProviderArrangement {

    val currentClientIdProvider: CurrentClientIdProvider
    suspend fun withCurrentClientIdSuccess(currentClientId: ClientId)
    suspend fun withCurrentClientIdFailure(error: CoreFailure = StorageFailure.DataNotFound)
}

internal class CurrentClientIdProviderArrangementImpl : CurrentClientIdProviderArrangement {

    override val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)

    override suspend fun withCurrentClientIdSuccess(currentClientId: ClientId) {
        everySuspend {
            currentClientIdProvider.invoke()
        }.returns(Either.Right(currentClientId))
    }

    override suspend fun withCurrentClientIdFailure(error: CoreFailure) {
        everySuspend {
            currentClientIdProvider.invoke()
        }.returns(Either.Left(error))
    }
}

