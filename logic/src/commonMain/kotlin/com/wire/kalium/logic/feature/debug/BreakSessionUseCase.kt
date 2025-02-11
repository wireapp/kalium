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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface BreakSessionUseCase {
    /**
     * Function that can be used to create a broken session with another user on purpose. This debug function
     * can be used to test correct client behaviour in case of broken sessions. It should not be used by
     * clients itself.
     *
     * Only works for proteus protocol.
     *
     * @param userId the id of the user to whom the session should be broken
     * @param clientId the id of the client of the user to whom the session should be broken
     * @return an [BreakSessionResult] containing a [CoreFailure] in case anything goes wrong
     * and [BreakSessionResult.Success] in case everything succeeds
     */
    suspend operator fun invoke(userId: UserId, clientId: ClientId): BreakSessionResult
}

internal class BreakSessionUseCaseImpl internal constructor(
    private val proteusClientProvider: ProteusClientProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : BreakSessionUseCase {
    override suspend operator fun invoke(
        userId: UserId,
        clientId: ClientId
    ): BreakSessionResult = withContext(dispatchers.io) {
        return@withContext proteusClientProvider.getOrError().fold({
            kaliumLogger.e("Failed to get proteus client for break session $it")
            return@fold BreakSessionResult.Failure(it)
        }, { proteusClient ->
            val cryptoUserID = idMapper.toCryptoQualifiedIDId(userId)
            val cryptoSessionId = CryptoSessionId(
                userId = cryptoUserID,
                cryptoClientId = CryptoClientId(clientId.value)
            )
            // create a new session with the same session id
            proteusClient.createSession(proteusClient.newLastResortPreKey(), cryptoSessionId)
            kaliumLogger.e("Created new session for ${userId.toLogString()} with ${clientId.value.obfuscateId()}")
            return@fold BreakSessionResult.Success
        })
    }
}

sealed class BreakSessionResult {
    data object Success : BreakSessionResult()
    data class Failure(val coreFailure: CoreFailure) : BreakSessionResult()
}
