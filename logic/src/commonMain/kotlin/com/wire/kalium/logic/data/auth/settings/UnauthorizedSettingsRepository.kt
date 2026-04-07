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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.network.api.base.unauthenticated.systemsettings.UnauthorizedSettingsApi

internal interface UnauthorizedSettingsRepository {
    suspend fun isNomadProfilesEnabled(): Either<NetworkFailure, Boolean>
}

internal class UnauthorizedSettingsRepositoryImpl(
    private val unauthorizedSettingsApi: UnauthorizedSettingsApi
) : UnauthorizedSettingsRepository {

    override suspend fun isNomadProfilesEnabled(): Either<NetworkFailure, Boolean> =
        wrapApiRequest {
            unauthorizedSettingsApi.settings()
        }.map {
            it.nomadProfiles ?: false
        }
}
