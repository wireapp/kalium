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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock

internal interface E2EIClientProviderArrangement {
    val mlsClientProvider: MLSClientProvider
    val userRepository: UserRepository
    val currentClientIdProvider: CurrentClientIdProvider

    suspend fun withCurrentClientId(result: Either<CoreFailure, ClientId>)
    suspend fun withSelfUser(result: Either<StorageFailure, SelfUser>)
    suspend fun withGetOrFetchMLSConfig(result: SupportedCipherSuite)
}

internal open class E2EIClientProviderArrangementMokkeryImpl : E2EIClientProviderArrangement {
    override val mlsClientProvider: MLSClientProvider = mock()
    override val userRepository: UserRepository = mock()
    override val currentClientIdProvider: CurrentClientIdProvider = mock()

    override suspend fun withCurrentClientId(result: Either<CoreFailure, ClientId>) {
        everySuspend { currentClientIdProvider() } returns result
    }

    override suspend fun withSelfUser(result: Either<StorageFailure, SelfUser>) {
        everySuspend { userRepository.getSelfUser() } returns result
    }

    override suspend fun withGetOrFetchMLSConfig(result: SupportedCipherSuite) {
        everySuspend { mlsClientProvider.getOrFetchMLSConfig() } returns result.right()
    }
}

internal class E2EIClientProviderArrangementImpl : E2EIClientProviderArrangementMokkeryImpl()
