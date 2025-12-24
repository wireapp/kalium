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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMissingLegalHoldConsent

/**
 * Use Case that allows a user send a connection request to connect with another User
 */
public interface SendConnectionRequestUseCase {
    /**
     * Use case [SendConnectionRequestUseCase] operation
     *
     * @param userId the target user to connect with
     * @return a [SendConnectionRequestResult] indicating the operation result
     */
    public suspend operator fun invoke(userId: UserId): SendConnectionRequestResult
}

internal class SendConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val transactionProvider: CryptoTransactionProvider
) : SendConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): SendConnectionRequestResult {
        return userRepository.fetchUserInfo(userId).flatMap {
            transactionProvider.transaction("SendConnectionRequest") {
                connectionRepository.sendUserConnection(it, userId)
            }
        }.fold({ coreFailure ->
            kaliumLogger.e("An error occurred when sending a connection request to $userId")
            when (coreFailure) {
                is NetworkFailure.FederatedBackendFailure.FederationDenied ->
                    SendConnectionRequestResult.Failure.FederationDenied
                is NetworkFailure.ServerMiscommunication -> handleServerMissCommunicationError(coreFailure)
                else -> SendConnectionRequestResult.Failure.GenericFailure(coreFailure)
            }
        }, {
            SendConnectionRequestResult.Success
        })
    }

    private fun handleServerMissCommunicationError(failure: NetworkFailure.ServerMiscommunication): SendConnectionRequestResult.Failure =
        when (failure.kaliumException) {
            is KaliumException.InvalidRequestError -> {
                with(failure.kaliumException as KaliumException.InvalidRequestError) {
                    when {
                        isMissingLegalHoldConsent() -> SendConnectionRequestResult.Failure.MissingLegalHoldConsent
                        else -> SendConnectionRequestResult.Failure.GenericFailure(failure)
                    }
                }
            }
            else -> SendConnectionRequestResult.Failure.GenericFailure(failure)
        }
}

public sealed class SendConnectionRequestResult {
    public data object Success : SendConnectionRequestResult()

    public sealed class Failure : SendConnectionRequestResult() {
        public class GenericFailure(public val coreFailure: CoreFailure) : Failure()
        public data object FederationDenied : Failure()
        public data object MissingLegalHoldConsent : Failure()
    }

}
