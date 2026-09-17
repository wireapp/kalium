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

package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.networkContainer.TransientAuthenticatedNetworkContainer
import kotlinx.coroutines.CancellationException

internal fun interface FetchPendingLoginSystemSettings {
    suspend operator fun invoke(session: SessionDTO): Either<CoreFailure, Boolean>
}

internal class FetchPendingLoginSystemSettingsImpl(
    private val containerFactory: (SessionDTO) -> TransientAuthenticatedNetworkContainer,
) : FetchPendingLoginSystemSettings {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(session: SessionDTO): Either<CoreFailure, Boolean> = try {
        val container = containerFactory(session)
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
