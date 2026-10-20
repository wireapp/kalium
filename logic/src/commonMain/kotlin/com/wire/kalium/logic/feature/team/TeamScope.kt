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

package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase

/** Compatibility facade retaining only its own fresh extension and its constructor captures. */
public class TeamScope internal constructor(private val api: LegacyTeamApi) {
    public val syncSelfTeamInfoUseCase: SyncSelfTeamInfoUseCase
        get() = api.teamSyncSelfTeamInfoUseCase

    public val isSelfATeamMember: IsSelfATeamMemberUseCase
        get() = api.teamIsSelfATeamMember
}
