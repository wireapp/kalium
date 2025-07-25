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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.cryptography.CryptoTransactionContext
import io.mockative.Mockable

/**
 * Update self supported protocols, and if the supported protocols
 * did change we also resolve the active protocol for all one-on-one
 * conversations.
 */
@Mockable
interface UpdateSupportedProtocolsAndResolveOneOnOnesUseCase {

    /**
     * @param synchroniseUsers if true we synchronize all known users from backend
     * in order to have to up-to-date information about which protocols are supported.
     */
    suspend operator fun invoke(transactionContext: CryptoTransactionContext, synchroniseUsers: Boolean): Either<CoreFailure, Unit>
}

internal class UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl(
    private val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase,
    private val oneOnOneResolver: OneOnOneResolver
) : UpdateSupportedProtocolsAndResolveOneOnOnesUseCase {

    override suspend operator fun invoke(transactionContext: CryptoTransactionContext, synchroniseUsers: Boolean) =
        updateSupportedProtocols().flatMap { updated ->
            if (updated) {
                oneOnOneResolver.resolveAllOneOnOneConversations(transactionContext, synchronizeUsers = synchroniseUsers)
            } else {
                Either.Right(Unit)
            }
        }
}
