/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.fold

/**
 * Return if self user is part of a team or noz
 */
class IsSelfATeamMemberUseCase internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider
) {
    suspend operator fun invoke(): Boolean = selfTeamIdProvider().fold({ false }, {
        it != null
    })
}
