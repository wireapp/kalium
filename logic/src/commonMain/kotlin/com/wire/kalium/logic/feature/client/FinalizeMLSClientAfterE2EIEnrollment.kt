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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.SlowSyncRepository

public interface FinalizeMLSClientAfterE2EIEnrollment {
    public suspend fun invoke()
}

internal class FinalizeMLSClientAfterE2EIEnrollmentImpl(
    private val clientRepository: ClientRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val registerMLSClient: RegisterMLSClientUseCase,
    private val joinExistingMLSConversationsUseCase: JoinExistingMLSConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository
) : FinalizeMLSClientAfterE2EIEnrollment {
    override suspend fun invoke() {
        currentClientIdProvider()
            .flatMap { clientId -> registerMLSClient(clientId) }
            .flatMap { result ->
                when (result) {
                    RegisterMLSClientResult.Success -> Either.Right(Unit)
                    RegisterMLSClientResult.E2EICertificateRequired ->
                        CoreFailure.Unknown(IllegalStateException("E2EI certificate is still required after enrollment")).left()
                }
            }.flatMap {
            clientRepository.clearClientRegistrationBlockedByE2EI()
        }.flatMap {
            joinExistingMLSConversationsUseCase()
        }.map {
            kaliumLogger.i("Clearing last slow sync completion instant after finalizing MLS client enrollment")
            slowSyncRepository.clearLastSlowSyncCompletionInstant()
        }
    }
}
