/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.analytics

import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.first

interface GetAnalyticsContactsDataUseCase {
    suspend operator fun invoke(): AnalyticsContactsData
}

class GetAnalyticsContactsDataUseCaseImpl internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val userRepository: UserRepository,
    private val userConfigRepository: UserConfigRepository,
) : GetAnalyticsContactsDataUseCase {

    override suspend fun invoke(): AnalyticsContactsData {
        slowSyncRepository.slowSyncStatus.first { it is SlowSyncStatus.Complete }

        val teamId = selfTeamIdProvider().getOrNull()
        return getAnalyticsContactsData(teamId)
    }

    private suspend fun getAnalyticsContactsData(teamId: TeamId?): AnalyticsContactsData =
        if (teamId == null) {
            val contactsSize = userRepository.getContactsAmountCached()
                .flatMapLeft { userRepository.countContactsAmount() }
                .getOrNull()

            AnalyticsContactsData(
                teamId = null,
                teamSize = null,
                isEnterprise = null,
                contactsSize = contactsSize,
                isTeamMember = false
            )
        } else {
            val teamSize = userRepository.getTeamMembersAmountCached()
                .flatMapLeft { userRepository.countTeamMembersAmount() }
                .getOrNull() ?: 0
            val isEnterprise = userConfigRepository.isConferenceCallingEnabled().getOrElse { false }

            if (teamSize > SMALL_TEAM_MAX) {
                AnalyticsContactsData(
                    teamId = teamId.value,
                    teamSize = teamSize,
                    contactsSize = null,
                    isEnterprise = isEnterprise,
                    isTeamMember = true
                )
            } else {
                // Smaller teams are not tracked due to legal precautions and the potential for user identification.
                AnalyticsContactsData(
                    teamId = null,
                    teamSize = null,
                    contactsSize = null,
                    isEnterprise = isEnterprise,
                    isTeamMember = true
                )
            }
        }

    companion object {
        private const val SMALL_TEAM_MAX = 5
    }

}

data class AnalyticsContactsData(
    val teamId: String?,
    val contactsSize: Int?,
    val teamSize: Int?,
    val isEnterprise: Boolean?,
    val isTeamMember: Boolean
)
