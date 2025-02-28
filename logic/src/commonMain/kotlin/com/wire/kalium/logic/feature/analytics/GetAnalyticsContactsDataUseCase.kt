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
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import kotlinx.coroutines.flow.first

/**
 * Use case that combine contacts data necessary for analytics [AnalyticsContactsData].
 * It always get a Cached data and, except case when there is no cache, in that case useCase selects all the data from DB.
 */
interface GetAnalyticsContactsDataUseCase {
    suspend operator fun invoke(): AnalyticsContactsData
}

class GetAnalyticsContactsDataUseCaseImpl internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val userConfigRepository: UserConfigRepository,
) : GetAnalyticsContactsDataUseCase {

    override suspend fun invoke(): AnalyticsContactsData {
        slowSyncRepository.slowSyncStatus.first { it is SlowSyncStatus.Complete }

        val teamId = selfTeamIdProvider().getOrNull()
        return getAnalyticsContactsData(teamId)
    }

    private suspend fun getAnalyticsContactsData(teamId: TeamId?): AnalyticsContactsData =
        if (teamId == null) {
            val contactsSize = analyticsRepository.getContactsAmountCached()
                .flatMapLeft { analyticsRepository.countContactsAmount() }
                .getOrNull()

            AnalyticsContactsData(
                teamId = null,
                teamSize = null,
                isEnterprise = null,
                contactsSize = contactsSize,
                isTeamMember = false
            )
        } else {
            val teamSize = analyticsRepository.getTeamMembersAmountCached()
                .flatMapLeft { analyticsRepository.countTeamMembersAmount(teamId) }
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

/**
 * If val is null mean it shouldn't be provided to the analytics.
 * More details in task https://wearezeta.atlassian.net/browse/WPB-16121
 */
data class AnalyticsContactsData(
    val teamId: String?,
    val contactsSize: Int?,
    val teamSize: Int?,
    val isEnterprise: Boolean?,
    val isTeamMember: Boolean
)
