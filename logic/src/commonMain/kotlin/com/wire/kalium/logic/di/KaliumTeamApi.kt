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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.team.LegacyTeamDependencies
import com.wire.kalium.logic.feature.team.LegacyTeamFacadeGraph
import com.wire.kalium.logic.feature.team.SyncSelfTeamInfoUseCase
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumTeamApi {
    public val teamSyncSelfTeamInfoUseCase: SyncSelfTeamInfoUseCase
    public val teamIsSelfATeamMember: IsSelfATeamMemberUseCase

    /** Every call creates a fresh graph with the original per-facade captures and caches. */
    public fun interface Factory {
        public fun create(): KaliumTeamApi
    }
}

/** Same generated graph/capture boundary as the compatibility facade. */
internal fun createTeamApi(
    factory: LegacyTeamFacadeGraph.Factory,
    dependencies: LegacyTeamDependencies,
): KaliumTeamApi = factory.create(dependencies)
