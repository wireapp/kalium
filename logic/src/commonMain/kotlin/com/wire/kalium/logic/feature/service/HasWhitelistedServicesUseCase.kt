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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.team.TeamRepository

/**
 * Checks whether the signed-in user's team has at least one whitelisted legacy service (bot).
 *
 * Use this to determine whether to offer the Allow apps option when creating a Proteus
 * conversation.
 * For MLS conversations, use the team's Apps feature flag instead.
 * The check uses the backend whitelist directly to avoid counting removed bots in the local cache.
 */
public interface HasWhitelistedServicesUseCase {
    /**
     * Returns `true` when the backend confirms at least one whitelisted service. Returns `false`
     * when there is no team, the whitelist is empty, or the lookup fails.
     */
    public suspend operator fun invoke(): Boolean
}

internal class HasWhitelistedServicesUseCaseImpl(
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider
) : HasWhitelistedServicesUseCase {
    override suspend fun invoke(): Boolean {
        val teamId = selfTeamIdProvider().getOrNull() ?: return false
        return teamRepository.hasWhitelistedServices(teamId).getOrNull() == true
    }
}
