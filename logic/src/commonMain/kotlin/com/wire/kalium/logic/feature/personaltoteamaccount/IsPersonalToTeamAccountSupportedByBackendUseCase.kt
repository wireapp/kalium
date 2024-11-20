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
package com.wire.kalium.logic.feature.personaltoteamaccount

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

/**
 * Use case to check if the backend supports personal to team account migration.
 */
interface IsPersonalToTeamAccountSupportedByBackendUseCase {
    suspend operator fun invoke(): Boolean
}

internal class IsPersonalToTeamAccountSupportedByBackendUseCaseImpl(
    val serverConfigRepository: ServerConfigRepository,
    val userId: UserId
) : IsPersonalToTeamAccountSupportedByBackendUseCase {
    override suspend fun invoke(): Boolean {
        return serverConfigRepository.commonApiVersion(userId.domain).fold(
            { false },
            { it >= MIN_API_VERSION }
        )
    }

    companion object {
        const val MIN_API_VERSION = 7
    }
}
