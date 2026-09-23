/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.auth.settings

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.networkContainer.TransientAuthenticatedNetworkContainer
import kotlinx.coroutines.CancellationException

internal fun interface PendingLoginSystemSettingsRepository {
    suspend fun isIdpChangeDetectionEnabled(accountTokens: AccountTokens): Either<CoreFailure, Boolean>
}

internal class PendingLoginSystemSettingsRepositoryImpl(
    private val containerFactory: (SessionDTO) -> TransientAuthenticatedNetworkContainer,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
) : PendingLoginSystemSettingsRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun isIdpChangeDetectionEnabled(accountTokens: AccountTokens): Either<CoreFailure, Boolean> = try {
        val container = containerFactory(sessionMapper.toSessionDTO(accountTokens))
        try {
            wrapApiRequest { container.systemSettingsApi.settings() }.map {
                it.ssoIdpChangeDetectionEnabled == true
            }
        } finally {
            container.close()
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Either.Left(CoreFailure.Unknown(exception))
    }
}
