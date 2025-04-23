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
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.functional.fold
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Checks if the self user is a team member or not.
 * @return true if the self user is a team member, false otherwise.
 */
@Mockable
interface IsSelfATeamMemberUseCase {
    /**
     * Flow that emits the current value, _i.e._ whether self user is a team member or not.
     * It will _not_ emit while SlowSync isn't done, as it needs to assure that SelfUser has been properly initialised.
     */
    suspend fun observe(): Flow<Boolean>
    suspend operator fun invoke(): Boolean
}

internal class IsSelfATeamMemberUseCaseImpl internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val slowSyncRepository: SlowSyncRepository
) : IsSelfATeamMemberUseCase {

    override suspend fun observe(): Flow<Boolean> = slowSyncRepository
        .observeLastSlowSyncCompletionInstant()
        .filterNotNull()
        .map {
            selfTeamIdProvider()
                .fold({ false }, { it != null })
        }

    override suspend operator fun invoke(): Boolean = observe().first()
}
