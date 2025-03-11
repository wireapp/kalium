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

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * Use case that checks if users ContactsAmount and TeamSize cache are too old and updates it.
 * Currently max live period is [UpdateContactsAmountsCacheUseCaseImpl.CACHE_PERIOD] 7 days
 */

class AsyncUpdateContactsAmountsCacheUseCase internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val analyticsRepository: AnalyticsRepository,
) {

    suspend operator fun invoke() {
        kaliumLogger.d("cccc: AsyncUpdateContactsAmountsCacheUseCase")
        val nowDate = Clock.System.now()
        val updateTime = analyticsRepository.getLastContactsDateUpdateDate().getOrNull()

        kaliumLogger.d("cccc: updateTime: $updateTime")
        kaliumLogger.d("cccc: nowDate: $nowDate")
        if (updateTime != null && nowDate.minus(updateTime) < CACHE_PERIOD) return

        val teamId = selfTeamIdProvider().getOrNull()
        if (teamId == null) {
            updateContactsAmountCache()
        } else {
            updateTeamSizeCache(teamId)
        }
        analyticsRepository.setLastContactsDateUpdateDate(nowDate)
    }

    private suspend fun updateContactsAmountCache() {
        with(analyticsRepository) {
            val contactsAmount = countContactsAmount().getOrNull() ?: 0
            setContactsAmountCached(contactsAmount)
        }
    }

    private suspend fun updateTeamSizeCache(teamId: TeamId) {
        with(analyticsRepository) {
            countTeamMembersAmount(teamId).getOrNull()?.let { teamAmount ->
                setTeamMembersAmountCached(teamAmount)
            }
        }
    }

    companion object {
        private val CACHE_PERIOD = 7.days
    }
}
