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
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * Use case that checks if users ContactsAmount and TeamSize cache are too old and updates it.
 * Currently max live period is [UpdateContactsAmountsCacheUseCaseImpl.CACHE_PERIOD] 7 days
 */
interface UpdateContactsAmountsCacheUseCase {
    suspend operator fun invoke()
}

class UpdateContactsAmountsCacheUseCaseImpl internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val analyticsRepository: AnalyticsRepository,
) : UpdateContactsAmountsCacheUseCase {

    override suspend fun invoke() {
        slowSyncRepository.slowSyncStatus.first { it is SlowSyncStatus.Complete }

        val nowDate = Clock.System.now()
        val updateTime = analyticsRepository.getLastContactsDateUpdateDate().getOrNull()

        if (updateTime != null && nowDate.minus(updateTime) < CACHE_PERIOD) return

        val teamId = selfTeamIdProvider().getOrNull()
        if (teamId == null) {
            updateContactsAmountCache(nowDate)
        } else {
            updateTeamSizeCache(teamId, nowDate)
        }
    }

    private suspend fun updateContactsAmountCache(nowDate: Instant) {
        with(analyticsRepository) {
            val contactsAmount = countContactsAmount().getOrNull() ?: 0

            setContactsAmountCached(contactsAmount)
            setContactsAmountCachingDate(nowDate)
        }
    }

    private suspend fun updateTeamSizeCache(teamId: TeamId, nowDate: Instant) {
        with(analyticsRepository) {
            countTeamMembersAmount(teamId).getOrNull()?.let { teamAmount ->

                setTeamMembersAmountCached(teamAmount)
                setContactsAmountCachingDate(nowDate)
            }
        }
    }

    companion object {
        private val CACHE_PERIOD = 7.days
    }

}
