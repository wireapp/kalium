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

import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.functional.fold

/**
 * Checks if the self user is a team member or not.
 * @return true if the self user is a team member, false otherwise.
 */
fun interface IsSelfATeamMemberUseCase {
    suspend operator fun invoke(): Boolean
}

class IsSelfATeamMemberUseCaseImpl internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider
) : IsSelfATeamMemberUseCase {
    override suspend operator fun invoke(): Boolean = selfTeamIdProvider().fold({ false }, {
        it != null
    })
}
